package com.starship7.kmwidget

import android.content.Context

object WidgetPreferences {
    const val DEFAULT_WIDGET_ID = 0

    fun prefsName(widgetId: Int) = if (widgetId == DEFAULT_WIDGET_ID) "widget_default" else "widget_$widgetId"

    fun load(context: Context, widgetId: Int): WidgetConfig {
        val prefs = context.getSharedPreferences(prefsName(widgetId), Context.MODE_PRIVATE)
        return WidgetConfig(
            isTimeBased = prefs.getBoolean("isTimeBased", true),
            timeValue   = prefs.getInt("timeValue", 30),
            kmValue     = prefs.getFloat("kmValue", 50f)
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

// ── Настройки оверлея ──────────────────────────────────────────────────────

object OverlaySettings {
    private const val PREFS = "overlay_prefs"

    // ── Позиция ──────────────────────────────────────────────────────────
    fun getPosition(ctx: Context): Pair<Int, Int> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        return Pair(p.getInt("x", 20), p.getInt("y", 100))
    }
    fun savePosition(ctx: Context, x: Int, y: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit()
            .putInt("x", x).putInt("y", y).apply()

    // ── Включён ──────────────────────────────────────────────────────────
    fun isEnabled(ctx: Context) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getBoolean("enabled", false)
    fun setEnabled(ctx: Context, v: Boolean) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putBoolean("enabled", v).apply()

    // ── Размер строки "Запас хода" ────────────────────────────────────────
    fun getRangeSize(ctx: Context): Int =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getInt("range_size", OverlayLine.SIZE_L)
    fun saveRangeSize(ctx: Context, sp: Int) =
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().putInt("range_size", sp).apply()

    // ── Дополнительные записи ────────────────────────────────────────────
    fun loadEntries(ctx: Context): List<OverlayEntry> {
        val p = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val count = p.getInt("entry_count", 0)
        return (0 until count).mapNotNull { i ->
            try {
                OverlayEntry(
                    batteryEnabled = p.getBoolean("e${i}_bat_en",    true),
                    batteryDisplay = OverlayLineDisplay.valueOf(p.getString("e${i}_bat_disp", "PERCENT")!!),
                    batterySizeSp  = p.getInt("e${i}_bat_sz",  OverlayLine.SIZE_M),
                    fuelEnabled    = p.getBoolean("e${i}_fuel_en",   true),
                    fuelDisplay    = OverlayLineDisplay.valueOf(p.getString("e${i}_fuel_disp", "KM")!!),
                    fuelSizeSp     = p.getInt("e${i}_fuel_sz", OverlayLine.SIZE_M),
                    combineLine    = p.getBoolean("e${i}_combine",   true)
                )
            } catch (_: Exception) { null }
        }
    }

    fun saveEntries(ctx: Context, entries: List<OverlayEntry>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("entry_count", entries.size)
            entries.forEachIndexed { i, e ->
                putBoolean("e${i}_bat_en",    e.batteryEnabled)
                putString ("e${i}_bat_disp",  e.batteryDisplay.name)
                putInt    ("e${i}_bat_sz",     e.batterySizeSp)
                putBoolean("e${i}_fuel_en",   e.fuelEnabled)
                putString ("e${i}_fuel_disp", e.fuelDisplay.name)
                putInt    ("e${i}_fuel_sz",    e.fuelSizeSp)
                putBoolean("e${i}_combine",   e.combineLine)
            }
            apply()
        }
    }

    // Legacy
    fun loadLines(ctx: Context): List<OverlayLine> = loadEntries(ctx).flatMap { it.toLines() }
    fun saveLines(ctx: Context, lines: List<OverlayLine>) { /* use saveEntries */ }
}

// ── Legacy alias (не используется в новом коде) ────────────────────────────
data class OverlayDisplayConfig(
    val totalSizeSp: Int     = OverlayLine.SIZE_L,
    val breakdownSizeSp: Int = OverlayLine.SIZE_M,
    val infoSizeSp: Int      = OverlayLine.SIZE_S
) {
    companion object {
        const val SIZE_OFF = 0
        const val SIZE_S   = OverlayLine.SIZE_S
        const val SIZE_M   = OverlayLine.SIZE_M
        const val SIZE_L   = OverlayLine.SIZE_L
        const val SIZE_XL  = OverlayLine.SIZE_XL
    }
}
