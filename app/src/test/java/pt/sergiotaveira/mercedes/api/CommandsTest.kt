package pt.sergiotaveira.mercedes.api

import org.junit.Test
import pt.sergiotaveira.mercedes.proto.ZEVPreconditioningType
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class CommandsTest {
    private val vin = "W1K0000000000000"

    @Test
    fun lockCarriesVinAndRequestId() {
        val c = Commands.lock(vin, "r1").commandRequest
        assertEquals(vin, c.vin); assertEquals("r1", c.requestId); assertTrue(c.hasDoorsLock())
    }

    @Test
    fun unlockCarriesPin() {
        assertEquals("1234", Commands.unlock(vin, "1234", "r2").commandRequest.doorsUnlock.pin)
    }

    @Test
    fun climateStartIsImmediate() {
        val s = Commands.climateStart(vin, "r3").commandRequest.zevPreconditioningStart
        assertEquals(ZEVPreconditioningType.immediate, s.type)
        assertEquals(0, s.departureTime)
    }

    @Test
    fun temperatureSetsFrontZones() {
        val t = Commands.setTemperature(vin, 22.0, "r5").commandRequest.temperatureConfigure
        assertEquals(2, t.temperaturePointsList.size)
        assertEquals(
            setOf(pt.sergiotaveira.mercedes.proto.TemperatureConfigure.TemperaturePoint.Zone.frontLeft,
                   pt.sergiotaveira.mercedes.proto.TemperatureConfigure.TemperaturePoint.Zone.frontRight),
            t.temperaturePointsList.map { it.zone }.toSet(),
        )
        t.temperaturePointsList.forEach { assertEquals(22.0, it.temperatureInCelsius, 0.001) }
    }

    @Test
    fun climateStopIsNow() {
        assertEquals(ZEVPreconditioningType.now, Commands.climateStop(vin, "r4").commandRequest.zevPreconditioningStop.type)
    }
}
