package com.starship7.kmwidget

import android.content.Context
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

object RangeCalculator {

    data class RangeResult(
        val rangeText: String,
        val infoText: String
    )

    /** Called from main thread (WidgetActivity, onUpdate). Creates helpers internally. */
    fun calculate(context: Context, widgetId: Int): RangeResult {
        val config = WidgetPreferences.load(context, widgetId)
        val dbHelper = RangeDatabaseHelper(context)
        val vehicleHelper = VehiclePropertyHelper(context)  // safe: called from main thread
        return calculateInternal(config, dbHelper, vehicleHelper)
    }

    /** Called from RangeUpdateService — reuses already-created helpers to avoid Looper crash. */
    fun calculateWithHelper(
        context: Context,
        widgetId: Int,
        dbHelper: RangeDatabaseHelper,
        vehicleHelper: VehiclePropertyHelper
    ): RangeResult {
        val config = WidgetPreferences.load(context, widgetId)
        return calculateInternal(config, dbHelper, vehicleHelper)
    }

    private fun calculateInternal(
        config: WidgetConfig,
        dbHelper: RangeDatabaseHelper,
        vehicleHelper: VehiclePropertyHelper
    ): RangeResult {

        val stats = if (config.isTimeBased) {
            dbHelper.getStatsSinceMinutes(config.timeValue)
        } else {
            dbHelper.getStatsSinceKm(config.kmValue)
        }

        val currentBat = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 1).toFloat()

        // Show current car data regardless of history
        val batStr = if (currentBat >= 0) "${currentBat.toInt()}%" else "?"
        val fuelStr = if (currentFuel >= 0) "${currentFuel.toInt()}%" else "?"
        val carConnected = vehicleHelper.isConnected

        var estimatedRangeStr: String
        var infoStr: String

        if (!carConnected) {
            estimatedRangeStr = "-- km"
            infoStr = "Car API not connected"
        } else if (stats != null && stats.deltaOdo > 1) {
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
            estimatedRangeStr = if (totalRange > 0) "${totalRange.toInt()} km" else "-- km"
            val period = if (config.isTimeBased) "${config.timeValue} min" else "${config.kmValue.toInt()} km"
            infoStr = "🔋$batStr ⛽$fuelStr | last $period"
        } else {
            // No driving history yet — show car data only
            estimatedRangeStr = "-- km"
            val period = if (config.isTimeBased) "${config.timeValue} min" else "${config.kmValue.toInt()} km"
            infoStr = "🔋$batStr ⛽$fuelStr | no data yet"
        }

        return RangeResult(estimatedRangeStr, infoStr)
    }
}
