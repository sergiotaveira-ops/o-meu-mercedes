package pt.sergiotaveira.mercedes.ui

import org.junit.Test
import pt.sergiotaveira.mercedes.api.CarState
import pt.sergiotaveira.mercedes.api.ChargingState
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlin.test.assertEquals

class FormatTest {
    private val zone = ZoneId.of("Europe/Lisbon")
    private val now = ZonedDateTime.of(2026, 11, 18, 22, 0, 0, 0, zone)

    @Test
    fun remainingWhenCharging() {
        assertEquals("Termina às 23:30 · faltam 1 h 30 min", Format.remaining(now.withHour(23).withMinute(30), now))
    }

    @Test
    fun remainingUnderOneHour() {
        assertEquals("Termina às 22:25 · faltam 25 min", Format.remaining(now.withMinute(25), now))
    }

    @Test
    fun remainingExactHours() {
        assertEquals("Termina às 00:00 · faltam 2 h", Format.remaining(now.plusHours(2), now))
    }

    @Test
    fun remainingNullOrPast() {
        assertEquals("Não está a carregar", Format.remaining(null, now))
        assertEquals("Não está a carregar", Format.remaining(now.minusMinutes(1), now))
    }

    @Test
    fun chargingLine() {
        fun car(c: ChargingState, end: ZonedDateTime? = null) = CarState(vin = "V", charging = c, endOfCharge = end, updatedAt = now.toInstant())
        assertEquals("Termina às 23:30 · faltam 1 h 30 min", Format.charging(car(ChargingState.CHARGING, now.withHour(23).withMinute(30)), now))
        assertEquals("A carregar", Format.charging(car(ChargingState.CHARGING), now))
        assertEquals("Ligado ao carregador, sem carregar", Format.charging(car(ChargingState.PLUGGED_NOT_CHARGING), now))
        assertEquals("Não está a carregar", Format.charging(car(ChargingState.UNPLUGGED), now))
        assertEquals("Não está a carregar", Format.charging(car(ChargingState.UNKNOWN), now))
    }

    @Test
    fun updatedAgoMinutes() {
        val i = now.toInstant()
        assertEquals("Atualizado agora", Format.updatedAgo(i.minusSeconds(30), i))
        assertEquals("Atualizado há 5 min", Format.updatedAgo(i.minusSeconds(300), i))
        assertEquals("Atualizado há 2 h", Format.updatedAgo(i.minusSeconds(7300), i))
        assertEquals("Atualizado há 3 dias", Format.updatedAgo(i.minusSeconds(3 * 86400 + 10), i))
        assertEquals("Atualizado há 1 dia", Format.updatedAgo(i.minusSeconds(86400 + 10), i))
    }
}
