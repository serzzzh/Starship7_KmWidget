package com.starship7.kmwidget

import android.content.Context
import android.util.Log
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

/** Снимок данных от авто — используется для рендеринга отдельных строк оверлея. */
data class CarSnapshot(
    val evRangeKm:   Float = 0f,
    val fuelRangeKm: Float = 0f,
    val batteryPct:  Float = -1f,
    val fuelPct:     Float = -1f,
    val fuelLiters:  Float = -1f,  // литры (fuelPct * 50 / 100)
    val isConnected: Boolean = false
)

object RangeCalculator {

    private const val TAG = "RangeCalculator"

    data class RangeResult(
        val totalText: String,       // "1 288 км"
        val breakdownText: String,   // "🔋14 + ⛽1274 км"
        val infoText: String,        // "🔋14%  ⛽80%"
        val snapshot: CarSnapshot    // сырые данные для строк оверлея
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
            return RangeResult("-- км", "--", "Car API не подключён",
                CarSnapshot(isConnected = false))
        }

        // ── Читаем данные от авто ─────────────────────────────────────────
        val currentBat  = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()

        val rawEv   = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_EV, 0)
        val rawFuel = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.RANGE_FUEL, 0)

        // Если > 5000 — скорее всего метры, конвертируем в км
        val carEv   = (if (rawEv   > 5000f) rawEv   / 1000f else rawEv).coerceAtLeast(0f)
        val carFuel = (if (rawFuel > 5000f) rawFuel / 1000f else rawFuel).coerceAtLeast(0f)

        Log.i(TAG, "rawEv=$rawEv rawFuel=$rawFuel → ev=$carEv fuel=$carFuel | bat=$currentBat% fuel=$currentFuel%")

        val batStr  = if (currentBat  >= 0) "${currentBat.toInt()}%"  else "?"
        val fuelStr = if (currentFuel >= 0) "${currentFuel.toInt()}%" else "?"

        val snapshot = CarSnapshot(
            evRangeKm   = carEv,
            fuelRangeKm = carFuel,
            batteryPct  = currentBat,
            fuelPct     = currentFuel,
            fuelLiters  = if (currentFuel >= 0) currentFuel / 100f * OverlayLine.FUEL_TANK_LITERS else -1f,
            isConnected = true
        )

        // ── История поездок ───────────────────────────────────────────────
        
        // 1. Долгосрочная эффективность (вся история за 7 дней, реальные старые периоды)
        val longTermEff = dbHelper.getEfficiencySinceTime(0L)
        
        // 2. Краткосрочная эффективность (текущий режим - скорость убывания сейчас)
        val shortTermEff = if (config.isTimeBased) {
            dbHelper.getEfficiencySinceTime(System.currentTimeMillis() - config.timeValue * 60_000L)
        } else {
            dbHelper.getEfficiencySinceKm(config.kmValue)
        }

        var finalEvEff = 0f
        var finalFuelEff = 0f

        val shortValidEv = shortTermEff.isValidEv(1.0f)
        val longValidEv = longTermEff.isValidEv(2.0f)
        
        val shortValidFuel = shortTermEff.isValidFuel(0.5f)
        val longValidFuel = longTermEff.isValidFuel(1.0f)

        // Подсчет эффективности EV (км на 1% батареи)
        if (shortValidEv && longValidEv) {
            // Смешиваем: 70% на текущий режим (скорость 130 или город) и 30% на старые данные
            finalEvEff = shortTermEff.evKmPerPct * 0.7f + longTermEff.evKmPerPct * 0.3f
        } else if (shortValidEv) {
            finalEvEff = shortTermEff.evKmPerPct
        } else if (longValidEv) {
            finalEvEff = longTermEff.evKmPerPct
        } else if (currentBat > 0 && carEv > 0) {
            finalEvEff = carEv / currentBat // нативный запас (fallback)
        }

        // Подсчет эффективности Fuel (км на 1% бака)
        if (shortValidFuel && longValidFuel) {
            finalFuelEff = shortTermEff.fuelKmPerPct * 0.7f + longTermEff.fuelKmPerPct * 0.3f
        } else if (shortValidFuel) {
            finalFuelEff = shortTermEff.fuelKmPerPct
        } else if (longValidFuel) {
            finalFuelEff = longTermEff.fuelKmPerPct
        } else if (currentFuel > 0 && carFuel > 0) {
            finalFuelEff = carFuel / currentFuel // нативный запас (fallback)
        }

        // Вычисляем финальный запас хода на основе посчитанных эффективностей
        val calculatedEvRange = if (finalEvEff > 0 && currentBat > 0) finalEvEff * currentBat else carEv
        val calculatedFuelRange = if (finalFuelEff > 0 && currentFuel > 0) finalFuelEff * currentFuel else carFuel

        val isUsingCalculatedData = finalEvEff > 0 || finalFuelEff > 0

        if (isUsingCalculatedData) {
            val total = calculatedEvRange + calculatedFuelRange
            val period = if (config.isTimeBased) "${config.timeValue} мин" else "${config.kmValue.toInt()} км"
            return RangeResult(
                totalText     = "${formatKm(total)} км",
                breakdownText = "🔋${formatKm(calculatedEvRange)} + ⛽${formatKm(calculatedFuelRange)} км",
                infoText      = "🔋$batStr  ⛽$fuelStr  ($period/Hist)",
                snapshot      = snapshot.copy(evRangeKm = calculatedEvRange, fuelRangeKm = calculatedFuelRange)
            )
        }

        // ── Fallback: данные от авто (если вообще нет истории) ────────────
        return if (carEv > 0 || carFuel > 0) {
            val total = carEv + carFuel
            RangeResult(
                totalText     = "${formatKm(total)} км",
                breakdownText = buildBreakdown(carEv, carFuel),
                infoText      = "🔋$batStr  ⛽$fuelStr",
                snapshot      = snapshot
            )
        } else {
            RangeResult(
                totalText     = "-- км",
                breakdownText = "--",
                infoText      = "🔋$batStr  ⛽$fuelStr",
                snapshot      = snapshot
            )
        }
    }

    private fun formatKm(km: Float): String {
        val i = km.toInt()
        return if (i >= 1000) {
            val t = i / 1000
            val rest = (i % 1000).toString().padStart(3, '0')
            "$t $rest"
        } else i.toString()
    }

    private fun buildBreakdown(evKm: Float, fuelKm: Float): String = when {
        evKm > 0 && fuelKm > 0 -> "🔋${evKm.toInt()} + ⛽${fuelKm.toInt()} км"
        evKm > 0               -> "EV ${evKm.toInt()} км"
        fuelKm > 0             -> "⛽ ${fuelKm.toInt()} км"
        else                   -> "--"
    }
}
