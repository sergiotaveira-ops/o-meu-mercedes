package pt.sergiotaveira.mercedes.api

import java.time.Instant
import java.time.ZonedDateTime

enum class LockState { LOCKED, UNLOCKED, UNKNOWN }

enum class ChargingState { CHARGING, PLUGGED_NOT_CHARGING, UNPLUGGED, UNKNOWN }

/** One vehicle attribute as sent by Mercedes; [status] 0 = valid. Plain Kotlin so the parser is testable without protobuf. */
data class Attr(
    val status: Int,
    val int: Long? = null,
    val bool: Boolean? = null,
    val dbl: Double? = null,
    val str: String? = null,
)

data class CarState(
    val vin: String,
    val socPercent: Int? = null,
    val electricRangeKm: Int? = null,
    val charging: ChargingState = ChargingState.UNKNOWN,
    val endOfCharge: ZonedDateTime? = null,
    val lock: LockState = LockState.UNKNOWN,
    val lat: Double? = null,
    val lon: Double? = null,
    val climateOn: Boolean? = null,
    val updatedAt: Instant,
    /** Raw end-of-charge wall clock (minutes after midnight) and weekday (Monday = 0), kept across partial updates. */
    val endOfChargeMinutes: Int? = null,
    val endOfChargeWeekday: Int? = null,
)
