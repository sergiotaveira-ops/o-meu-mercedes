package pt.sergiotaveira.mercedes.api

import java.time.ZoneId
import java.time.ZonedDateTime

/** Folds Mercedes attribute updates into a [CarState]. Mappings from mbapi2020 (MIT) const.py, lock.py, client.py, vsu_enums.py. */
object CarStateParser {
    private val CHARGING = setOf(0, 5, 6, 9, 10, 11, 13, 14)
    private val PLUGGED = setOf(1, 2, 4, 7, 8, 12, 15)

    fun merge(previous: CarState?, vin: String, attrs: Map<String, Attr>, zone: ZoneId, now: ZonedDateTime): CarState {
        val valid = attrs.filterValues { it.status == 0 }
        fun int(k: String) = valid[k]?.let { it.int ?: it.dbl?.toLong() }
        fun dbl(k: String) = valid[k]?.let { it.dbl ?: it.int?.toDouble() }
        fun bool(k: String) = valid[k]?.let { it.bool ?: it.int?.let { v -> v != 0L } }

        val base = previous ?: CarState(vin = vin, updatedAt = now.toInstant())
        val charging = if ("chargingstatus" in attrs) chargingState(int("chargingstatus")) else base.charging
        val minutes = int("endofchargetime")?.toInt() ?: base.endOfChargeMinutes
        val weekday = int("endofChargeTimeWeekday")?.toInt() ?: base.endOfChargeWeekday

        return base.copy(
            vin = vin,
            socPercent = int("soc")?.toInt() ?: base.socPercent,
            electricRangeKm = int("rangeelectric")?.toInt() ?: base.electricRangeKm,
            charging = charging,
            lock = int("doorlockstatusvehicle")?.let(::lockState) ?: base.lock,
            lat = dbl("positionLat") ?: base.lat,
            lon = dbl("positionLong") ?: base.lon,
            climateOn = bool("precondNow") ?: bool("precondActive") ?: base.climateOn,
            targetTempCelsius = dbl("targetTemp") ?: base.targetTempCelsius,
            endOfChargeMinutes = minutes,
            endOfChargeWeekday = weekday,
            endOfCharge = if (charging == ChargingState.CHARGING && minutes != null) endOfCharge(minutes, weekday, zone, now) else null,
            updatedAt = now.toInstant(),
        )
    }

    private fun lockState(v: Long) = when (v) {
        1L, 2L -> LockState.LOCKED
        0L, 3L -> LockState.UNLOCKED
        else -> LockState.UNKNOWN
    }

    private fun chargingState(v: Long?) = when (v?.toInt()) {
        null -> ChargingState.UNKNOWN
        in CHARGING -> ChargingState.CHARGING
        in PLUGGED -> ChargingState.PLUGGED_NOT_CHARGING
        3 -> ChargingState.UNPLUGGED
        else -> ChargingState.UNKNOWN
    }

    /** client.py _get_car_values_handle_endofchargetime, legacy branch. */
    private fun endOfCharge(minutes: Int, weekday: Int?, zone: ZoneId, now: ZonedDateTime): ZonedDateTime {
        val local = now.withZoneSameInstant(zone)
        val todayAt = local.toLocalDate().atTime(minutes / 60, minutes % 60).atZone(zone)
        val nowWeekday = local.dayOfWeek.value - 1 // Monday = 0
        val target = weekday?.let { ((it % 7) + 7) % 7 } ?: if (todayAt.isBefore(local)) (nowWeekday + 1) % 7 else nowWeekday
        val daysAhead = ((target - nowWeekday) % 7 + 7) % 7
        return todayAt.plusDays(daysAhead.toLong())
    }
}
