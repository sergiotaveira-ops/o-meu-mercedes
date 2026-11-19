package pt.sergiotaveira.mercedes.api

import org.junit.Test
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals
import kotlin.test.assertNull

class CarStateParserTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    // Wednesday 18 Nov 2026, 22:00 Lisbon
    private val now = ZonedDateTime.of(2026, 11, 18, 22, 0, 0, 0, zone)
    private val vin = "W1K0000000000000"

    private fun i(v: Long, status: Int = 0) = Attr(status = status, int = v)

    private fun merge(prev: CarState?, attrs: Map<String, Attr>) = CarStateParser.merge(prev, vin, attrs, zone, now)

    @Test
    fun parsesSocRangeAndLock() {
        val s = merge(null, mapOf("soc" to i(67), "rangeelectric" to i(38), "doorlockstatusvehicle" to i(2)))
        assertEquals(67, s.socPercent)
        assertEquals(38, s.electricRangeKm)
        assertEquals(LockState.LOCKED, s.lock)
        assertEquals(vin, s.vin)
    }

    @Test
    fun partialUpdateKeepsPrevious() {
        val first = merge(null, mapOf("soc" to i(67), "doorlockstatusvehicle" to i(2)))
        val second = merge(first, mapOf("doorlockstatusvehicle" to i(0)))
        assertEquals(67, second.socPercent)
        assertEquals(LockState.UNLOCKED, second.lock)
    }

    @Test
    fun lockValues() {
        assertEquals(LockState.LOCKED, merge(null, mapOf("doorlockstatusvehicle" to i(1))).lock)
        assertEquals(LockState.UNLOCKED, merge(null, mapOf("doorlockstatusvehicle" to i(3))).lock)
        assertEquals(LockState.UNKNOWN, merge(null, mapOf("doorlockstatusvehicle" to i(9))).lock)
    }

    @Test
    fun chargingStatusMapping() {
        assertEquals(ChargingState.CHARGING, merge(null, mapOf("chargingstatus" to i(13))).charging)
        assertEquals(ChargingState.PLUGGED_NOT_CHARGING, merge(null, mapOf("chargingstatus" to i(1))).charging)
        assertEquals(ChargingState.UNPLUGGED, merge(null, mapOf("chargingstatus" to i(3))).charging)
        assertEquals(ChargingState.UNKNOWN, merge(null, mapOf("chargingstatus" to i(16))).charging)
        assertEquals(ChargingState.UNKNOWN, merge(null, emptyMap()).charging)
    }

    @Test
    fun endOfChargeTodayLaterUsesToday() {
        val s = merge(null, mapOf("chargingstatus" to i(0), "endofchargetime" to i(1410), "endofChargeTimeWeekday" to i(2)))
        assertEquals(ZonedDateTime.of(2026, 11, 18, 23, 30, 0, 0, zone), s.endOfCharge)
    }

    @Test
    fun endOfChargeEarlierMeansTomorrowWhenNoWeekday() {
        val s = merge(null, mapOf("chargingstatus" to i(0), "endofchargetime" to i(90)))
        assertEquals(ZonedDateTime.of(2026, 11, 19, 1, 30, 0, 0, zone), s.endOfCharge)
    }

    @Test
    fun endOfChargeWeekdayTomorrow() {
        val s = merge(null, mapOf("chargingstatus" to i(0), "endofchargetime" to i(90), "endofChargeTimeWeekday" to i(3)))
        assertEquals(ZonedDateTime.of(2026, 11, 19, 1, 30, 0, 0, zone), s.endOfCharge)
    }

    @Test
    fun notChargingHasNoEndOfCharge() {
        val charging = merge(null, mapOf("chargingstatus" to i(0), "endofchargetime" to i(1410), "endofChargeTimeWeekday" to i(2)))
        val stopped = merge(charging, mapOf("chargingstatus" to i(3)))
        assertNull(stopped.endOfCharge)
        assertNull(merge(null, mapOf("chargingstatus" to i(3), "endofchargetime" to i(1410))).endOfCharge)
    }

    @Test
    fun invalidStatusIgnored() {
        assertNull(merge(null, mapOf("soc" to i(67, status = 3))).socPercent)
        val prev = merge(null, mapOf("soc" to i(50)))
        assertEquals(50, merge(prev, mapOf("soc" to i(10, status = 4))).socPercent)
    }

    @Test
    fun positionAndClimate() {
        val s = merge(null, mapOf(
            "positionLat" to Attr(0, dbl = 41.1), "positionLong" to Attr(0, dbl = -7.5),
            "precondNow" to Attr(0, bool = true),
        ))
        assertEquals(41.1, s.lat); assertEquals(-7.5, s.lon); assertEquals(true, s.climateOn)
        assertEquals(false, merge(s, mapOf("precondNow" to Attr(0, bool = false))).climateOn)
    }

    @Test
    fun climateFallsBackToPrecondActive() {
        assertEquals(true, merge(null, mapOf("precondActive" to Attr(0, bool = true))).climateOn)
    }

    @Test
    fun targetTemperatureParsedAndKept() {
        val first = merge(null, mapOf("targetTemp" to Attr(0, dbl = 21.5)))
        assertEquals(21.5, first.targetTempCelsius)
        val second = merge(first, mapOf("soc" to i(50)))
        assertEquals(21.5, second.targetTempCelsius)
    }

    @Test
    fun updatedAtIsNow() {
        assertEquals(now.toInstant(), merge(null, emptyMap()).updatedAt)
    }
}
