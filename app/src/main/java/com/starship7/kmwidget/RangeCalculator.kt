package com.starship7.kmwidget

import android.content.Context
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

object RangeCalculator {

    data class RangeResult(
        val rangeText: String,
        val infoText: String
    )

    fun calculate(context: Context, widgetId: Int): RangeResult {
        val config = WidgetPreferences.load(context, widgetId)
        val dbHelper = RangeDatabaseHelper(context)
        val vehicleHelper = VehiclePropertyHelper(context)

        val stats = if (config.isTimeBased) {
            dbHelper.getStatsSinceMinutes(config.timeValue)
        } else {
            dbHelper.getStatsSinceKm(config.kmValue)
        }

        val currentBat = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 1).toFloat()

        var estimatedRangeStr = "-- km"

        if (stats != null && stats.deltaOdo > 1) {
            var evRange = 0f
            var fuelRange = 0f

            if (stats.deltaBat > 1) {
                val kmPerBatPercent = stats.deltaOdo / stats.deltaBat
                evRange = kmPerBatPercent * currentBat
            }
            if (stats.deltaFuel > 1) {
                val kmPerFuelPercent = stats.deltaOdo / stats.deltaFuel
                fuelRange = kmPerFuelPercent * currentFuel
            }

            val totalRange = evRange + fuelRange
            if (totalRange > 0) {
                estimatedRangeStr = "${totalRange.toInt()} km"
            }
        }

        val infoStr = if (config.isTimeBased) {
            "Based on last ${config.timeValue} min"
        } else {
            "Based on last ${config.kmValue.toInt()} km"
        }

        return RangeResult(estimatedRangeStr, infoStr)
    }
}
