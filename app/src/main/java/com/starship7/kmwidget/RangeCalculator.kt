package com.starship7.kmwidget

import android.content.Context
import android.util.Log
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

object RangeCalculator {

    private const val TAG = "RangeCalculator"

    data class RangeResult(
        val rangeText: String,   // Основной текст: "350 km" или "EV 180 | Fuel 170"
        val infoText: String     // Доп. строка: "🔋65% ⛽80%"
    )

    /** Called from main thread (WidgetActivity, onUpdate). Creates helpers internally. */
    fun calculate(context: Context, widgetId: Int): RangeResult {
        val config = WidgetPreferences.load(context, widgetId)
        val dbHelper = RangeDatabaseHelper(context)
        val vehicleHelper = VehiclePropertyHelper(context)  // safe: called from main thread
        return calculateInternal(config, dbHelper, vehicleHelper)
    }

    /** Called from RangeUpdateService / OverlayService — reuses already-created helpers. */
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
            return RangeResult("-- km", "Car API not connected")
        }

        // ── 1. Читаем базовые данные авто ───────────────────────────────
        val currentBat  = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()

        // RANGE_EV / RANGE_FUEL — готовый запас хода от авто (км или метры — определим по величине)
        val rawRangeEv   = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_EV, 0)
        val rawRangeFuel = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_FUEL, 0)

        Log.i(TAG, "rawRangeEv=$rawRangeEv rawRangeFuel=$rawRangeFuel bat=$currentBat fuel=$currentFuel")

        // Если авто возвращает метры (> 1000 и похоже на реальный пробег) — конвертируем в км
        val carRangeEv   = if (rawRangeEv   > 1000f) rawRangeEv   / 1000f else rawRangeEv
        val carRangeFuel = if (rawRangeFuel > 1000f) rawRangeFuel / 1000f else rawRangeFuel

        val batStr  = if (currentBat  >= 0) "${currentBat.toInt()}%"  else "?"
        val fuelStr = if (currentFuel >= 0) "${currentFuel.toInt()}%" else "?"

        // ── 2. Собираем исторические данные ─────────────────────────────
        val stats = if (config.isTimeBased) {
            dbHelper.getStatsSinceMinutes(config.timeValue)
        } else {
            dbHelper.getStatsSinceKm(config.kmValue)
        }

        // ── 3. Если есть готовые данные от авто — показываем их ─────────
        val hasCarRange = carRangeEv > 0 || carRangeFuel > 0

        return if (hasCarRange) {
            // Приоритет: данные от самого авто (мгновенно, без истории)
            buildCarRangeResult(carRangeEv, carRangeFuel, batStr, fuelStr, stats, config)
        } else {
            // Fallback: вычисляем по истории поездок
            buildCalculatedResult(currentBat, currentFuel, batStr, fuelStr, stats, config)
        }
    }

    /** Показываем данные непосредственно от авто (RANGE_EV / RANGE_FUEL). */
    private fun buildCarRangeResult(
        evKm: Float, fuelKm: Float,
        batStr: String, fuelStr: String,
        stats: RangeDatabaseHelper.Stats?,
        config: WidgetConfig
    ): RangeResult {
        val totalKm = evKm + fuelKm

        val rangeText = when {
            evKm > 0 && fuelKm > 0 -> "EV ${evKm.toInt()} | ⛽${fuelKm.toInt()} km"
            evKm > 0               -> "EV ${evKm.toInt()} km"
            fuelKm > 0             -> "⛽ ${fuelKm.toInt()} km"
            else                   -> "-- km"
        }

        // Дополнительно: расчётный пробег из истории (если есть)
        val calcNote = if (stats != null && stats.deltaOdo > 1) {
            val period = if (config.isTimeBased) "${config.timeValue}m" else "${config.kmValue.toInt()}km"
            " (hist $period)"
        } else ""

        val infoText = "🔋$batStr ⛽$fuelStr$calcNote"
        return RangeResult(rangeText, infoText)
    }

    /** Fallback: расчёт по истории поездок (если Car не даёт RANGE_EV/FUEL). */
    private fun buildCalculatedResult(
        currentBat: Float, currentFuel: Float,
        batStr: String, fuelStr: String,
        stats: RangeDatabaseHelper.Stats?,
        config: WidgetConfig
    ): RangeResult {
        if (stats == null || stats.deltaOdo <= 1) {
            val period = if (config.isTimeBased) "${config.timeValue} min" else "${config.kmValue.toInt()} km"
            return RangeResult("-- km", "🔋$batStr ⛽$fuelStr | no data yet ($period window)")
        }

        var evRange   = 0f
        var fuelRange = 0f

        if (stats.deltaBat > 1 && currentBat >= 0) {
            evRange = (stats.deltaOdo / stats.deltaBat) * currentBat
        }
        if (stats.deltaFuel > 1 && currentFuel >= 0) {
            fuelRange = (stats.deltaOdo / stats.deltaFuel) * currentFuel
        }

        val total = evRange + fuelRange
        val rangeText = if (total > 0) "${total.toInt()} km" else "-- km"
        val period = if (config.isTimeBased) "${config.timeValue}m" else "${config.kmValue.toInt()}km"
        val infoText = "🔋$batStr ⛽$fuelStr | calc/$period"

        return RangeResult(rangeText, infoText)
    }
}
