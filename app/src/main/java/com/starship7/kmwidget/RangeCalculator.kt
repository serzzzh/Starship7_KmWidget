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
    val fuelLiters:  Float = -1f,  // литры (fuelPct * 55 / 100)
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
        val stats = if (config.isTimeBased) {
            dbHelper.getStatsSinceMinutes(config.timeValue)
        } else {
            dbHelper.getStatsSinceKm(config.kmValue)
        }

        if (stats != null && stats.deltaOdo > 1) {
            val evRange   = if (stats.deltaBat  > 1f && currentBat  > 0) (stats.deltaOdo / stats.deltaBat)  * currentBat  else carEv
            val fuelRange = if (stats.deltaFuel > 1f && currentFuel > 0) (stats.deltaOdo / stats.deltaFuel) * currentFuel else carFuel

            val total  = evRange + fuelRange
            val period = if (config.isTimeBased) "${config.timeValue} мин" else "${config.kmValue.toInt()} км"
            return RangeResult(
                totalText     = "${formatKm(total)} км",
                breakdownText = "🔋${formatKm(evRange)} + ⛽${formatKm(fuelRange)} км",
                infoText      = "🔋$batStr  ⛽$fuelStr  ($period)",
                snapshot      = snapshot.copy(evRangeKm = evRange, fuelRangeKm = fuelRange)
            )
        }

        // ── Fallback: данные от авто ──────────────────────────────────────
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
            val h = (i % 1000) / 100
            "$t ${h}00"   // e.g. "1 200"
        } else i.toString()
    }

    private fun buildBreakdown(evKm: Float, fuelKm: Float): String = when {
        evKm > 0 && fuelKm > 0 -> "🔋${evKm.toInt()} + ⛽${fuelKm.toInt()} км"
        evKm > 0               -> "EV ${evKm.toInt()} км"
        fuelKm > 0             -> "⛽ ${fuelKm.toInt()} км"
        else                   -> "--"
    }
}
