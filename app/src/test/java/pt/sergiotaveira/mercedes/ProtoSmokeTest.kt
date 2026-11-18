package pt.sergiotaveira.mercedes

import org.junit.Test
import pt.sergiotaveira.mercedes.proto.ClientMessage
import pt.sergiotaveira.mercedes.proto.CommandRequest
import pt.sergiotaveira.mercedes.proto.DoorsLock
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ProtoSmokeTest {
    @Test
    fun buildsDoorsLockCommand() {
        val msg = ClientMessage.newBuilder()
            .setCommandRequest(
                CommandRequest.newBuilder().setVin("W1K0000000000000").setRequestId("r1")
                    .setDoorsLock(DoorsLock.getDefaultInstance())
            ).build()
        val parsed = ClientMessage.parseFrom(msg.toByteArray())
        assertEquals("W1K0000000000000", parsed.commandRequest.vin)
        assertTrue(parsed.commandRequest.hasDoorsLock())
    }
}
