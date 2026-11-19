package pt.sergiotaveira.mercedes.api

import pt.sergiotaveira.mercedes.proto.VEPUpdate
import pt.sergiotaveira.mercedes.proto.VSUMetadata
import pt.sergiotaveira.mercedes.proto.VehicleAttributeStatus
import pt.sergiotaveira.mercedes.proto.VehicleStatusUpdate

/** Converts both Mercedes update formats (legacy VEPUpdate and newer VehicleStatusUpdate) into parser attributes. */
object ProtoAdapter {
    fun attrs(u: VEPUpdate): Map<String, Attr> = buildMap {
        u.attributesMap.forEach { (k, v) -> put(k, attr(v)) }
        val tp = u.attributesMap["temperaturePoints"]
        if (tp != null && tp.attributeTypeCase == VehicleAttributeStatus.AttributeTypeCase.TEMPERATURE_POINTS_VALUE) {
            val points = tp.temperaturePointsValue.temperaturePointsList
            val front = points.firstOrNull { it.zone.contains("FRONT", true) && it.zone.contains("LEFT", true) }
                ?: points.firstOrNull { it.zone.contains("FRONT", true) } ?: points.firstOrNull()
            if (front != null) put("targetTemp", Attr(tp.status, dbl = front.temperature))
        }
    }

    private fun attr(v: VehicleAttributeStatus): Attr = when (v.attributeTypeCase) {
        VehicleAttributeStatus.AttributeTypeCase.INT_VALUE -> Attr(v.status, int = v.intValue)
        VehicleAttributeStatus.AttributeTypeCase.BOOL_VALUE -> Attr(v.status, bool = v.boolValue)
        VehicleAttributeStatus.AttributeTypeCase.DOUBLE_VALUE -> Attr(v.status, dbl = v.doubleValue)
        VehicleAttributeStatus.AttributeTypeCase.STRING_VALUE -> Attr(v.status, str = v.stringValue)
        else -> Attr(if (v.status == 0) 4 else v.status)
    }

    /** Only the fields this app uses; keys are the legacy attribute names (mbapi2020 vsu_helper.py). */
    fun attrs(u: VehicleStatusUpdate): Map<String, Attr> = buildMap {
        fun st(m: VSUMetadata) = m.statusValue
        if (u.hasSoc()) put("soc", Attr(st(u.soc.metadata), int = u.soc.value))
        if (u.hasRangeelectric()) put("rangeelectric", Attr(st(u.rangeelectric.metadata), int = u.rangeelectric.value))
        if (u.hasDoorlockstatusvehicle()) put("doorlockstatusvehicle", Attr(st(u.doorlockstatusvehicle.metadata), int = u.doorlockstatusvehicle.valueValue.toLong()))
        if (u.hasChargingstatus()) put("chargingstatus", Attr(st(u.chargingstatus.metadata), int = u.chargingstatus.valueValue.toLong()))
        if (u.hasEndofchargetime()) put("endofchargetime", Attr(st(u.endofchargetime.metadata), int = u.endofchargetime.value))
        if (u.hasEndofChargeTimeWeekday()) {
            val w = u.endofChargeTimeWeekday.valueValue
            put("endofChargeTimeWeekday", if (w < 0) Attr(4) else Attr(st(u.endofChargeTimeWeekday.metadata), int = w.toLong()))
        }
        if (u.hasPositionLat()) put("positionLat", Attr(st(u.positionLat.metadata), dbl = u.positionLat.value))
        if (u.hasPositionLong()) put("positionLong", Attr(st(u.positionLong.metadata), dbl = u.positionLong.value))
        if (u.hasPrecondNow()) put("precondNow", Attr(st(u.precondNow.metadata), bool = u.precondNow.valueValue == 1))
        if (u.hasPrecondActive()) put("precondActive", Attr(st(u.precondActive.metadata), bool = u.precondActive.value))
        if (u.hasTemperaturePoints()) {
            val points = u.temperaturePoints.valueList
            val front = points.firstOrNull {
                it.zone == pt.sergiotaveira.mercedes.proto.TemperaturePointsArrayAttribute.TemperaturePoint.Zone.FRONT_LEFT
            } ?: points.firstOrNull()
            if (front != null) put("targetTemp", Attr(st(u.temperaturePoints.metadata), dbl = front.temperature.value))
        }
    }
}
