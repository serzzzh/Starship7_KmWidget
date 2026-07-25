package com.starship7.kmwidget

import android.content.Context

object WidgetPreferences {
    const val DEFAULT_WIDGET_ID = 0

    fun prefsName(widgetId: Int) = if (widgetId == DEFAULT_WIDGET_ID) "widget_default" else "widget_$widgetId"

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(prefsName(widgetId), Context.MODE_PRIVATE)
        return WidgetConfig(
            isTimeBased = prefs.getBoolean("isTimeBased", true),
            timeValue = prefs.getInt("timeValue", 30),
            kmValue = prefs.getFloat("kmValue", 50f)
        )
    }

    fun save(context: Context, widgetId: Int, config: WidgetConfig) {
        context.getSharedPreferences(prefsName(widgetId), Context.MODE_PRIVATE).edit()
            .putBoolean("isTimeBased", config.isTimeBased)
            .putInt("timeValue", config.timeValue)
            .putFloat("kmValue", config.kmValue)
            .apply()
    }
}

data class WidgetConfig(
    val isTimeBased: Boolean,
    val timeValue: Int,
    val kmValue: Float
)

// ── Настройки отображения оверлея ──────────────────────────────────────────

/** Размер текста в sp. 0 = скрыть строку. */
data class OverlayDisplayConfig(
    val totalSizeSp: Int    = SIZE_L,    // Общий запас хода
    val breakdownSizeSp: Int = SIZE_M,  // EV + Fuel в км
    val infoSizeSp: Int     = SIZE_S    // % заряда и топлива
) {
    companion object {
        const val SIZE_OFF = 0
        const val SIZE_S   = 14
        const val SIZE_M   = 20
        const val SIZE_L   = 28
        const val SIZE_XL  = 38
    }
}

object OverlaySettings {
    private const val PREFS = "overlay_prefs"

    fun loadDisplay(ctx: Context): OverlayDisplayConfig {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return OverlayDisplayConfig(
            totalSizeSp    = p.getInt("size_total",     OverlayDisplayConfig.SIZE_L),
            breakdownSizeSp = p.getInt("size_breakdown", OverlayDisplayConfig.SIZE_M),
            infoSizeSp     = p.getInt("size_info",      OverlayDisplayConfig.SIZE_S)
        )
    }

    fun saveDisplay(ctx: Context, cfg: OverlayDisplayConfig) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("size_total",     cfg.totalSizeSp)
            .putInt("size_breakdown", cfg.breakdownSizeSp)
            .putInt("size_info",      cfg.infoSizeSp)
            .apply()
    }

    fun getPosition(ctx: Context): Pair<Int, Int> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(p.getInt("x", 20), p.getInt("y", 100))
    }

    fun savePosition(ctx: Context, x: Int, y: Int) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("x", x).putInt("y", y).apply()
    }

    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)

    fun setEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", v).apply()
}
