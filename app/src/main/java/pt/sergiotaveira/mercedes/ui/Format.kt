package pt.sergiotaveira.mercedes.ui

import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.api.ChargingState
import java.time.Duration
import java.time.Instant
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

object Format {
    const val NOT_CHARGING = "Não está a carregar"
    private val hhmm = DateTimeFormatter.ofPattern("HH:mm")

    fun remaining(end: ZonedDateTime?, now: ZonedDateTime): String {
        if (end == null || !end.isAfter(now)) return NOT_CHARGING
        val mins = Duration.between(now, end).toMinutes()
        val h = mins / 60
        val m = mins % 60
        val left = when {
            h == 0L -> "$m min"
            m == 0L -> "$h h"
            else -> "$h h $m min"
        }
        return "Termina às ${end.withZoneSameInstant(now.zone).format(hhmm)} · faltam $left"
    }

    fun charging(car: CarState, now: ZonedDateTime): String = when (car.charging) {
        ChargingState.CHARGING -> if (car.endOfCharge != null && car.endOfCharge.isAfter(now)) remaining(car.endOfCharge, now) else "A carregar"
        ChargingState.PLUGGED_NOT_CHARGING -> "Ligado ao carregador, sem carregar"
        else -> NOT_CHARGING
    }

    fun updatedAgo(at: Instant, now: Instant): String {
        val s = Duration.between(at, now).seconds
        return when {
            s < 60 -> "Atualizado agora"
            s < 3600 -> "Atualizado há ${s / 60} min"
            s < 86400 -> "Atualizado há ${s / 3600} h"
            else -> (s / 86400).let { d -> "Atualizado há $d ${if (d == 1L) "dia" else "dias"}" }
        }
    }
}
