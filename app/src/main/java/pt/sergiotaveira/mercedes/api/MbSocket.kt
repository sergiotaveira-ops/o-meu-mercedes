package pt.sergiotaveira.mercedes.api

import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeoutOrNull
import pt.sergiotaveira.mercedes.proto.AcknowledgeAppTwinCommandStatusUpdatesByVIN
import pt.sergiotaveira.mercedes.proto.AcknowledgeDataChangeEvent
import pt.sergiotaveira.mercedes.proto.AcknowledgePreferredDealerChange
import pt.sergiotaveira.mercedes.proto.AcknowledgeServiceStatusUpdate
import pt.sergiotaveira.mercedes.proto.AcknowledgeUserDataUpdate
import pt.sergiotaveira.mercedes.proto.AcknowledgeUserPINUpdate
import pt.sergiotaveira.mercedes.proto.AcknowledgeUserPictureUpdate
import pt.sergiotaveira.mercedes.proto.AcknowledgeVEPUpdatesByVIN
import pt.sergiotaveira.mercedes.proto.AcknowledgeVehicleStatusUpdates
import pt.sergiotaveira.mercedes.proto.AcknowledgeVehicleUpdated
import pt.sergiotaveira.mercedes.proto.ClientMessage
import pt.sergiotaveira.mercedes.proto.PushMessage
import pt.sergiotaveira.mercedes.proto.VehicleAPI
import java.time.ZoneId
import java.time.ZonedDateTime
import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

interface WsListener {
    fun onOpen()
    fun onMessage(bytes: ByteArray)
    /** [httpCode] is the handshake status when the server refused the upgrade, otherwise null. */
    fun onFailure(httpCode: Int?, message: String)
    fun onClosed()
}

interface WsConnection {
    fun send(bytes: ByteArray): Boolean
    fun close()
}

fun interface WsFactory {
    fun open(url: String, headers: Map<String, String>, listener: WsListener): WsConnection
}

sealed class CommandResult {
    data object Success : CommandResult()
    data class Failed(val reason: String) : CommandResult()
    data object Timeout : CommandResult()
}

sealed class SocketError {
    data object Blocked : SocketError()
    data object AuthExpired : SocketError()
    data class Network(val msg: String) : SocketError()
}

/** Mercedes push WebSocket: receives car state, sends commands. Ported from mbapi2020 websocket.py / client.py (MIT). */
class MbSocket(
    private val factory: WsFactory,
    private val auth: MbAuth,
    private val scope: CoroutineScope,
    private val url: String = MbUrls.EMEA.websocket,
    private val zone: ZoneId = ZoneId.systemDefault(),
    private val backoff: suspend (Long) -> Unit = { delay(it) },
    private val clock: () -> ZonedDateTime = { ZonedDateTime.now(zone) },
) {
    private val _states = MutableStateFlow<Map<String, CarState>>(emptyMap())
    val states: StateFlow<Map<String, CarState>> = _states
    /** Delivered once each, buffered until collected (a single collector is expected). */
    private val _errors = Channel<SocketError>(Channel.BUFFERED)
    val errors: Flow<SocketError> = _errors.receiveAsFlow()

    private val open = MutableStateFlow(false)
    private val pending = ConcurrentHashMap<String, CompletableDeferred<CommandResult>>()
    private val sessionId = UUID.randomUUID().toString()
    @Volatile private var conn: WsConnection? = null
    @Volatile private var wanted = false
    @Volatile private var connecting = false
    @Volatile private var retries = 0

    fun connect() {
        if (connecting || conn != null) return
        wanted = true
        connecting = true
        scope.launch { openNow() }
    }

    fun disconnect() {
        wanted = false
        conn?.close()
        conn = null
        open.value = false
    }

    suspend fun send(cmd: ClientMessage, timeoutMs: Long = 60_000): CommandResult {
        val id = cmd.commandRequest.requestId
        val result = CompletableDeferred<CommandResult>()
        pending[id] = result
        return try {
            withTimeoutOrNull(timeoutMs) {
                if (!open.value) { connect(); open.first { it } }
                if (conn?.send(cmd.toByteArray()) != true) return@withTimeoutOrNull CommandResult.Failed("sem ligação")
                result.await()
            } ?: CommandResult.Timeout
        } finally {
            pending.remove(id)
        }
    }

    private suspend fun openNow() {
        val token = try {
            auth.validAccessToken()
        } catch (e: AuthError.Blocked) {
            fail(SocketError.Blocked); return
        } catch (e: AuthError.Network) {
            connecting = false; _errors.trySend(SocketError.Network(e.message ?: "")); scheduleReconnect(); return
        } catch (e: AuthError) {
            fail(SocketError.AuthExpired); return
        }
        conn = factory.open(url, headers(token), listener)
    }

    private fun fail(e: SocketError) {
        wanted = false; connecting = false; conn = null; open.value = false
        _errors.trySend(e)
    }

    private fun scheduleReconnect() {
        if (!wanted) return
        val wait = minOf(10_000L shl minOf(retries, 4), 120_000L)
        retries++
        connecting = true
        scope.launch { backoff(wait); if (wanted) openNow() else connecting = false }
    }

    private val listener = object : WsListener {
        override fun onOpen() { retries = 0; connecting = false; open.value = true }

        override fun onMessage(bytes: ByteArray) {
            val msg = try { PushMessage.parseFrom(bytes) } catch (e: Exception) { return }
            handle(msg)
        }

        override fun onFailure(httpCode: Int?, message: String) {
            conn = null; open.value = false
            when (httpCode) {
                429 -> fail(SocketError.Blocked)
                401, 403 -> fail(SocketError.AuthExpired)
                else -> { connecting = false; _errors.trySend(SocketError.Network(message)); scheduleReconnect() }
            }
        }

        override fun onClosed() {
            conn = null; open.value = false; connecting = false
            scheduleReconnect()
        }
    }

    private fun headers(token: String) = mapOf(
        "Authorization" to token,
        "APP-SESSION-ID" to sessionId,
        "OUTPUT-FORMAT" to "PROTO",
        "X-SessionId" to sessionId,
        "X-TrackingId" to UUID.randomUUID().toString().uppercase(),
        "ris-os-name" to MbConstants.RIS_OS_NAME,
        "ris-os-version" to MbConstants.RIS_OS_VERSION,
        "ris-sdk-version" to MbConstants.RIS_SDK_VERSION,
        "X-Locale" to "de-DE",
        "User-Agent" to MbConstants.APP_USER_AGENT,
        "X-ApplicationName" to MbConstants.APPLICATION_NAME,
        "ris-application-version" to MbConstants.RIS_APPLICATION_VERSION,
    )

    private fun merge(vin: String, attrs: Map<String, Attr>) {
        val now = clock()
        _states.value = _states.value + (vin to CarStateParser.merge(_states.value[vin], vin, attrs, zone, now))
    }

    private fun ack(build: ClientMessage.Builder.() -> Unit) {
        conn?.send(ClientMessage.newBuilder().apply(build).build().toByteArray())
    }

    private fun handle(m: PushMessage) {
        when (m.msgCase) {
            PushMessage.MsgCase.VEPUPDATES -> {
                m.vepUpdates.updatesMap.forEach { (vin, u) -> merge(u.vin.ifEmpty { vin }, ProtoAdapter.attrs(u)) }
                ack { setAcknowledgeVepUpdatesByVin(AcknowledgeVEPUpdatesByVIN.newBuilder().setSequenceNumber(m.vepUpdates.sequenceNumber)) }
            }
            PushMessage.MsgCase.VEHICLE_STATUS_UPDATES -> {
                m.vehicleStatusUpdates.vehicleStatusUpdatesMap.forEach { (vin, u) -> merge(u.finOrVin.ifEmpty { vin }, ProtoAdapter.attrs(u)) }
                ack { setAcknowledgeVehicleStatusUpdates(AcknowledgeVehicleStatusUpdates.newBuilder().setSequenceNumber(m.vehicleStatusUpdates.sequenceNumber)) }
            }
            PushMessage.MsgCase.APPTWIN_COMMAND_STATUS_UPDATES_BY_VIN -> {
                val u = m.apptwinCommandStatusUpdatesByVin
                u.updatesByVinMap.values.flatMap { it.updatesByPidMap.values }.forEach { s ->
                    val result = when (s.state) {
                        VehicleAPI.CommandState.FINISHED -> CommandResult.Success
                        VehicleAPI.CommandState.FAILED -> CommandResult.Failed(
                            s.errorsList.firstOrNull()?.let { it.message.ifEmpty { it.code } } ?: "FAILED")
                        else -> null
                    }
                    if (result != null) pending[s.requestId]?.complete(result)
                }
                ack { setAcknowledgeApptwinCommandStatusUpdateByVin(AcknowledgeAppTwinCommandStatusUpdatesByVIN.newBuilder().setSequenceNumber(u.sequenceNumber)) }
            }
            PushMessage.MsgCase.SERVICE_STATUS_UPDATES ->
                ack { setAcknowledgeServiceStatusUpdate(AcknowledgeServiceStatusUpdate.newBuilder().setSequenceNumber(m.serviceStatusUpdates.sequenceNumber)) }
            PushMessage.MsgCase.USER_DATA_UPDATE ->
                ack { setAcknowledgeUserDataUpdate(AcknowledgeUserDataUpdate.newBuilder().setSequenceNumber(m.userDataUpdate.sequenceNumber)) }
            PushMessage.MsgCase.USER_PICTURE_UPDATE ->
                ack { setAcknowledgeUserPictureUpdate(AcknowledgeUserPictureUpdate.newBuilder().setSequenceNumber(m.userPictureUpdate.sequenceNumber)) }
            PushMessage.MsgCase.USER_PIN_UPDATE ->
                ack { setAcknowledgeUserPinUpdate(AcknowledgeUserPINUpdate.newBuilder().setSequenceNumber(m.userPinUpdate.sequenceNumber)) }
            PushMessage.MsgCase.VEHICLE_UPDATED ->
                ack { setAcknowledgeVehicleUpdated(AcknowledgeVehicleUpdated.newBuilder().setSequenceNumber(m.vehicleUpdated.sequenceNumber)) }
            PushMessage.MsgCase.PREFERRED_DEALER_CHANGE ->
                ack { setAcknowledgePreferredDealerChange(AcknowledgePreferredDealerChange.newBuilder().setSequenceNumber(m.preferredDealerChange.sequenceNumber)) }
            PushMessage.MsgCase.DATA_CHANGE_EVENT ->
                ack { setAcknowledgeDataChangeEvent(AcknowledgeDataChangeEvent.newBuilder().setSequenceNumber(m.dataChangeEvent.sequenceNumber)) }
            PushMessage.MsgCase.APPTWIN_PENDING_COMMAND_REQUEST -> conn?.send(byteArrayOf(0xaa.toByte(), 0x01, 0x00))
            PushMessage.MsgCase.ASSIGNED_VEHICLES -> conn?.send(byteArrayOf(0xba.toByte(), 0x01, 0x00))
            else -> Unit
        }
    }
}
