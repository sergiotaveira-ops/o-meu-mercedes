package pt.sergiotaveira.mercedes.api

import pt.sergiotaveira.mercedes.proto.ClientMessage
import pt.sergiotaveira.mercedes.proto.CommandRequest
import pt.sergiotaveira.mercedes.proto.DoorsLock
import pt.sergiotaveira.mercedes.proto.DoorsUnlock
import pt.sergiotaveira.mercedes.proto.ZEVPreconditioningStart
import pt.sergiotaveira.mercedes.proto.ZEVPreconditioningStop
import pt.sergiotaveira.mercedes.proto.ZEVPreconditioningType

/** Car commands, as built by mbapi2020 client.py doors_lock / doors_unlock_with_pin / preheat_start_immediate / preheat_stop. */
object Commands {
    private fun wrap(vin: String, requestId: String, f: CommandRequest.Builder.() -> Unit): ClientMessage =
        ClientMessage.newBuilder().setCommandRequest(CommandRequest.newBuilder().setVin(vin).setRequestId(requestId).apply(f)).build()

    fun lock(vin: String, requestId: String) = wrap(vin, requestId) { setDoorsLock(DoorsLock.getDefaultInstance()) }

    fun unlock(vin: String, pin: String, requestId: String) = wrap(vin, requestId) { setDoorsUnlock(DoorsUnlock.newBuilder().setPin(pin)) }

    fun climateStart(vin: String, requestId: String) = wrap(vin, requestId) {
        setZevPreconditioningStart(ZEVPreconditioningStart.newBuilder().setDepartureTime(0).setType(ZEVPreconditioningType.immediate))
    }

    fun climateStop(vin: String, requestId: String) = wrap(vin, requestId) {
        setZevPreconditioningStop(ZEVPreconditioningStop.newBuilder().setType(ZEVPreconditioningType.now))
    }
}
