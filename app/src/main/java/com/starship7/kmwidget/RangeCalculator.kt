package com.starship7.kmwidget

import android.content.Context
import android.util.Log
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

object RangeCalculator {

    private const val TAG = "RangeCalculator"

    data class RangeResult(
        val totalText: String,   // "1288 km" — большая цифра
        val breakdownText: String, // "🔋14 + ⛽1274" — разбивка
        val infoText: String     // "🔋14% ⛽80% | last 30min"
    )

    fun calculate(context: Context, widgetId: Int): RangeResult {
        val config = WidgetPreferences.load(context, widgetId)
        val dbHelper = RangeDatabaseHelper(context)
        val vehicleHelper = VehiclePropertyHelper(context)
        return calculateInternal(config, dbHelper, vehicleHelper)
    }

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

        if (!vehicleHelper.isConnected) {
            Log.w(TAG, "Car API not connected")
            return RangeResult("-- km", "--", "Car API not connected")
        }

        // ── Читаем данные от авто ────────────────────────────────────────
        val currentBat  = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()

        // RANGE_EV / RANGE_FUEL — данные от самого авто в км
        // (не конвертируем: если > 5000 — вероятно метры, иначе уже км)
        val rawEv   = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_EV, 0)
        val rawFuel = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_FUEL, 0)

        val carRangeEv   = if (rawEv   > 5000f) rawEv   / 1000f else rawEv.coerceAtLeast(0f)
        val carRangeFuel = if (rawFuel > 5000f) rawFuel / 1000f else rawFuel.coerceAtLeast(0f)

        Log.i(TAG, "rawEv=$rawEv rawFuel=$rawFuel → evKm=$carRangeEv fuelKm=$carRangeFuel | bat=$currentBat% fuel=$currentFuel%")

        val batStr  = if (currentBat  >= 0) "${currentBat.toInt()}%"  else "?"
        val fuelStr = if (currentFuel >= 0) "${currentFuel.toInt()}%" else "?"

        // ── История поездок ──────────────────────────────────────────────
        val stats = if (config.isTimeBased) {
            dbHelper.getStatsSinceMinutes(config.timeValue)
        } else {
            dbHelper.getStatsSinceKm(config.kmValue)
        }

        // ── Расчёт по истории (приоритет над данными авто) ───────────────
        val hasHistory = stats != null && stats.deltaOdo > 1
        if (hasHistory && stats != null) {
            val evRange   = if (stats.deltaBat  > 1f && currentBat  > 0) (stats.deltaOdo / stats.deltaBat)  * currentBat  else carRangeEv
            val fuelRange = if (stats.deltaFuel > 1f && currentFuel > 0) (stats.deltaOdo / stats.deltaFuel) * currentFuel else carRangeFuel

            val total = evRange + fuelRange
            val period = if (config.isTimeBased) "${config.timeValue}m" else "${config.kmValue.toInt()}km"
            return RangeResult(
                totalText     = "${total.toInt()} km",
                breakdownText = "🔋${evRange.toInt()} + ⛽${fuelRange.toInt()} km",
                infoText      = "🔋$batStr ⛽$fuelStr | calc/$period"
            )
        }

        // ── Fallback: данные от авто ─────────────────────────────────────
        val hasCarRange = carRangeEv > 0 || carRangeFuel > 0
        return if (hasCarRange) {
            val total = carRangeEv + carRangeFuel
            RangeResult(
                totalText     = "${total.toInt()} km",
                breakdownText = buildBreakdown(carRangeEv, carRangeFuel),
                infoText      = "🔋$batStr ⛽$fuelStr | car estimate"
            )
        } else {
            val period = if (config.isTimeBased) "${config.timeValue}m" else "${config.kmValue.toInt()}km"
            RangeResult(
                totalText     = "-- km",
                breakdownText = "no data",
                infoText      = "🔋$batStr ⛽$fuelStr | no history ($period)"
            )
        }
    }

    private fun buildBreakdown(evKm: Float, fuelKm: Float): String = when {
        evKm > 0 && fuelKm > 0 -> "🔋${evKm.toInt()} + ⛽${fuelKm.toInt()} km"
        evKm > 0               -> "EV ${evKm.toInt()} km"
        fuelKm > 0             -> "⛽ ${fuelKm.toInt()} km"
        else                   -> "--"
    }
}
