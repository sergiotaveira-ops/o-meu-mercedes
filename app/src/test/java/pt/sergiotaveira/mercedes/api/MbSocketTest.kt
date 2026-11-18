package pt.sergiotaveira.mercedes.api

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Test
import pt.sergiotaveira.mercedes.proto.AppTwinCommandStatus
import pt.sergiotaveira.mercedes.proto.AppTwinCommandStatusUpdatesByPID
import pt.sergiotaveira.mercedes.proto.AppTwinCommandStatusUpdatesByVIN
import pt.sergiotaveira.mercedes.proto.ClientMessage
import pt.sergiotaveira.mercedes.proto.PushMessage
import pt.sergiotaveira.mercedes.proto.VEPUpdate
import pt.sergiotaveira.mercedes.proto.VEPUpdatesByVIN
import pt.sergiotaveira.mercedes.proto.VehicleAPI
import pt.sergiotaveira.mercedes.proto.VehicleAPIError
import pt.sergiotaveira.mercedes.proto.VehicleAttributeStatus
import java.time.ZoneId
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class FakeWs : WsFactory {
    val opened = mutableListOf<Map<String, String>>()
    val sent = mutableListOf<ClientMessage>()
    var listener: WsListener? = null
    val listeners = mutableListOf<WsListener>()
    var failWith: Int? = null
    override fun open(url: String, headers: Map<String, String>, listener: WsListener): WsConnection {
        opened += headers
        this.listener = listener
        listeners += listener
        val code = failWith
        if (code != null) listener.onFailure(code, "HTTP $code") else listener.onOpen()
        return object : WsConnection {
            override fun send(bytes: ByteArray): Boolean { sent += ClientMessage.parseFrom(bytes); return true }
            override fun close() {}
        }
    }
    fun push(m: PushMessage) = listener!!.onMessage(m.toByteArray())
}

class MbSocketTest {
    private val vin = "W1K0000000000000"
    private val store = InMemoryTokenStore().apply { save(Tokens("AT", "RT", Long.MAX_VALUE / 2)) }
    private val auth = MbAuth(FakeTransport(), store)

    private fun <T> withSocket(ws: FakeWs = FakeWs(), block: suspend CoroutineScope.(MbSocket, FakeWs) -> T): T = runBlocking {
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        val socket = MbSocket(ws, auth, scope, zone = ZoneId.of("Europe/Lisbon"), backoff = { })
        try { block(this, socket, ws) } finally { socket.disconnect(); scope.cancel() }
    }

    private suspend fun waitFor(cond: () -> Boolean) = withTimeout(2000) { while (!cond()) delay(5) }

    private fun vep(seq: Int, vararg attrs: Pair<String, VehicleAttributeStatus>) = PushMessage.newBuilder().setVepUpdates(
        VEPUpdatesByVIN.newBuilder().setSequenceNumber(seq).putUpdates(vin,
            VEPUpdate.newBuilder().setVin(vin).apply { attrs.forEach { putAttributes(it.first, it.second) } }.build())
    ).build()

    private fun status(requestId: String, state: VehicleAPI.CommandState, error: String? = null) = PushMessage.newBuilder()
        .setApptwinCommandStatusUpdatesByVin(AppTwinCommandStatusUpdatesByVIN.newBuilder().setSequenceNumber(7).putUpdatesByVin(vin,
            AppTwinCommandStatusUpdatesByPID.newBuilder().setVin(vin).putUpdatesByPid(1L,
                AppTwinCommandStatus.newBuilder().setRequestId(requestId).setState(state).apply {
                    if (error != null) addErrors(VehicleAPIError.newBuilder().setCode("E").setMessage(error))
                }.build()).build())).build()

    @Test
    fun sendsAuthHeaders() = withSocket { s, ws ->
        s.connect(); waitFor { ws.opened.isNotEmpty() }
        val h = ws.opened.first()
        assertEquals("AT", h["Authorization"])
        assertEquals("PROTO", h["OUTPUT-FORMAT"])
        assertEquals(MbConstants.APPLICATION_NAME, h["X-ApplicationName"])
    }

    @Test
    fun vepUpdateUpdatesState() = withSocket { s, ws ->
        s.connect(); waitFor { ws.listener != null }
        ws.push(vep(1, "soc" to VehicleAttributeStatus.newBuilder().setIntValue(67).build()))
        waitFor { s.states.value[vin] != null }
        assertEquals(67, s.states.value[vin]!!.socPercent)
    }

    @Test
    fun pushMessagesAreAcknowledged() = withSocket { s, ws ->
        s.connect(); waitFor { ws.listener != null }
        ws.push(vep(42))
        waitFor { ws.sent.isNotEmpty() }
        assertEquals(42, ws.sent.first().acknowledgeVepUpdatesByVin.sequenceNumber)
    }

    @Test
    fun commandFinishedReturnsSuccess() = withSocket { s, ws ->
        s.connect(); waitFor { ws.listener != null }
        val r = async { s.send(Commands.lock(vin, "req-1")) }
        waitFor { ws.sent.any { it.hasCommandRequest() } }
        ws.push(status("req-1", VehicleAPI.CommandState.PROCESSING))
        ws.push(status("req-1", VehicleAPI.CommandState.FINISHED))
        assertEquals(CommandResult.Success, r.await())
        assertTrue(ws.sent.any { it.hasAcknowledgeApptwinCommandStatusUpdateByVin() })
    }

    @Test
    fun commandFailedReturnsFailed() = withSocket { s, ws ->
        s.connect(); waitFor { ws.listener != null }
        val r = async { s.send(Commands.unlock(vin, "0000", "req-2")) }
        waitFor { ws.sent.any { it.hasCommandRequest() } }
        ws.push(status("req-2", VehicleAPI.CommandState.FAILED, "PIN invalid"))
        assertEquals(CommandResult.Failed("PIN invalid"), r.await())
    }

    @Test
    fun commandWithoutStatusTimesOut() = withSocket { s, ws ->
        s.connect(); waitFor { ws.listener != null }
        assertEquals(CommandResult.Timeout, s.send(Commands.lock(vin, "req-3"), timeoutMs = 200))
    }

    @Test
    fun handshake429EmitsBlockedAndDoesNotReconnect() = withSocket(FakeWs().apply { failWith = 429 }) { s, ws ->
        val err = async { s.errors.first() }
        s.connect()
        assertEquals(SocketError.Blocked, withTimeout(2000) { err.await() })
        delay(100)
        assertEquals(1, ws.opened.size)
    }

    @Test
    fun refreshDoesNotOpenTwoConnections() = withSocket { s, ws ->
        s.connect(); waitFor { ws.opened.size == 1 }
        s.disconnect(); s.connect(); waitFor { ws.opened.size == 2 }
        ws.listeners[0].onClosed()   // late close event from the old connection
        delay(100)
        assertEquals(2, ws.opened.size)
    }

    @Test
    fun otherFailureReconnects() = withSocket(FakeWs().apply { failWith = 500 }) { s, ws ->
        s.connect()
        waitFor { ws.opened.size >= 3 }
    }
}
