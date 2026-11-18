package pt.sergiotaveira.mercedes.api

import org.junit.Test
import pt.sergiotaveira.mercedes.proto.AttributeStatus
import pt.sergiotaveira.mercedes.proto.Chargingstatus
import pt.sergiotaveira.mercedes.proto.ChargingstatusEnumAttribute
import pt.sergiotaveira.mercedes.proto.DoubleAttribute
import pt.sergiotaveira.mercedes.proto.Int64ClockHourAttribute
import pt.sergiotaveira.mercedes.proto.Int64RatioAttribute
import pt.sergiotaveira.mercedes.proto.PrecondNow
import pt.sergiotaveira.mercedes.proto.PrecondNowEnumAttribute
import pt.sergiotaveira.mercedes.proto.VEPUpdate
import pt.sergiotaveira.mercedes.proto.VSUMetadata
import pt.sergiotaveira.mercedes.proto.VehicleAttributeStatus
import pt.sergiotaveira.mercedes.proto.VehicleStatusUpdate
import pt.sergiotaveira.mercedes.proto.Weekday
import pt.sergiotaveira.mercedes.proto.WeekdayEnumAttribute
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class ProtoAdapterTest {
    @Test
    fun vepAttributesAreConverted() {
        val u = VEPUpdate.newBuilder().setVin("V")
            .putAttributes("soc", VehicleAttributeStatus.newBuilder().setIntValue(67).build())
            .putAttributes("positionLat", VehicleAttributeStatus.newBuilder().setDoubleValue(41.1).build())
            .putAttributes("precondNow", VehicleAttributeStatus.newBuilder().setBoolValue(true).build())
            .putAttributes("rangeelectric", VehicleAttributeStatus.newBuilder().setStatus(4).build())
            .build()
        val a = ProtoAdapter.attrs(u)
        assertEquals(Attr(0, int = 67), a["soc"])
        assertEquals(Attr(0, dbl = 41.1), a["positionLat"])
        assertEquals(Attr(0, bool = true), a["precondNow"])
        assertEquals(4, a["rangeelectric"]!!.status)
    }

    @Test
    fun vsuFieldsMapToLegacyKeys() {
        val valid = VSUMetadata.newBuilder().setStatus(AttributeStatus.VALUE_VALID).build()
        val u = VehicleStatusUpdate.newBuilder().setFinOrVin("V")
            .setSoc(Int64RatioAttribute.newBuilder().setValue(55).setMetadata(valid))
            .setChargingstatus(ChargingstatusEnumAttribute.newBuilder().setValue(Chargingstatus.CHARGINGSTATUS_CHARGING).setMetadata(valid))
            .setEndofchargetime(Int64ClockHourAttribute.newBuilder().setValue(1410).setMetadata(valid))
            .setEndofChargeTimeWeekday(WeekdayEnumAttribute.newBuilder().setValue(Weekday.WEEKDAY_WEDNESDAY).setMetadata(valid))
            .setPositionLong(DoubleAttribute.newBuilder().setValue(-7.5).setMetadata(valid))
            .setPrecondNow(PrecondNowEnumAttribute.newBuilder().setValue(PrecondNow.PRECOND_NOW_ACTIVE).setMetadata(valid))
            .build()
        val a = ProtoAdapter.attrs(u)
        assertEquals(Attr(0, int = 55), a["soc"])
        assertEquals(Attr(0, int = 0), a["chargingstatus"])
        assertEquals(Attr(0, int = 1410), a["endofchargetime"])
        assertEquals(Attr(0, int = 2), a["endofChargeTimeWeekday"])
        assertEquals(Attr(0, dbl = -7.5), a["positionLong"])
        assertEquals(Attr(0, bool = true), a["precondNow"])
        assertFalse("doorlockstatusvehicle" in a)
    }

    @Test
    fun vsuWeekdayNotSetIsUnavailable() {
        val u = VehicleStatusUpdate.newBuilder().setFinOrVin("V")
            .setEndofChargeTimeWeekday(WeekdayEnumAttribute.newBuilder().setValue(Weekday.WEEKDAY_NOT_SET))
            .build()
        assertEquals(4, ProtoAdapter.attrs(u)["endofChargeTimeWeekday"]!!.status)
    }
}
