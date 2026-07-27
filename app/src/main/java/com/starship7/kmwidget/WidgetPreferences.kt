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
        val count = p.getInt("entry_count", -1)
        if (count == -1) {
            // Default setup for the first launch
            return listOf(
                OverlayEntry(
                    renderOrder = listOf("SUM", "TEXT"),
                    sumEnabled = true,
                    sumSizeSp = OverlayLine.SIZE_L,
                    textEnabled = true,
                    textValue = "км",
                    textSizeSp = OverlayLine.SIZE_L
                ),
                OverlayEntry(
                    renderOrder = listOf("BATTERY", "FUEL"),
                    batteryEnabled = true,
                    batteryDisplay = OverlayLineDisplay.KM,
                    batterySizeSp = OverlayLine.SIZE_L,
                    fuelEnabled = true,
                    fuelDisplay = OverlayLineDisplay.KM,
                    fuelSizeSp = OverlayLine.SIZE_L
                )
            )
        }
        
        return (0 until count).mapNotNull { i ->
            try {
                val orderString = p.getString("e${i}_order", "") ?: ""
                val renderOrder = if (orderString.isNotEmpty()) orderString.split(",") else emptyList()
                
                OverlayEntry(
                    renderOrder    = renderOrder,
                    textEnabled    = p.getBoolean("e${i}_text_en",   false),
                    textValue      = p.getString ("e${i}_text_val",  "") ?: "",
                    textSizeSp     = p.getInt    ("e${i}_text_sz",   OverlayLine.SIZE_L),

                    sumEnabled     = p.getBoolean("e${i}_sum_en",    false),
                    sumSizeSp      = p.getInt    ("e${i}_sum_sz",    OverlayLine.SIZE_L),

                    batteryEnabled = p.getBoolean("e${i}_bat_en",    false),
                    batteryDisplay = OverlayLineDisplay.valueOf(p.getString("e${i}_bat_disp", "KM")!!),
                    batterySizeSp  = p.getInt    ("e${i}_bat_sz",    OverlayLine.SIZE_L),

                    fuelEnabled    = p.getBoolean("e${i}_fuel_en",   false),
                    fuelDisplay    = OverlayLineDisplay.valueOf(p.getString("e${i}_fuel_disp", "KM")!!),
                    fuelSizeSp     = p.getInt    ("e${i}_fuel_sz",   OverlayLine.SIZE_L),

                    modeEnabled    = p.getBoolean("e${i}_mode_en",   false),
                    modeSizeSp     = p.getInt    ("e${i}_mode_sz",   OverlayLine.SIZE_L)
                )
            } catch (_: Exception) { null }
        }
    }

    fun saveEntries(ctx: Context, entries: List<OverlayEntry>) {
        ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit().apply {
            putInt("entry_count", entries.size)
            entries.forEachIndexed { i, e ->
                putString ("e${i}_order",      e.renderOrder.joinToString(","))
                putBoolean("e${i}_text_en",    e.textEnabled)
                putString ("e${i}_text_val",   e.textValue)
                putInt    ("e${i}_text_sz",    e.textSizeSp)

                putBoolean("e${i}_sum_en",     e.sumEnabled)
                putInt    ("e${i}_sum_sz",     e.sumSizeSp)

                putBoolean("e${i}_bat_en",     e.batteryEnabled)
                putString ("e${i}_bat_disp",   e.batteryDisplay.name)
                putInt    ("e${i}_bat_sz",     e.batterySizeSp)

                putBoolean("e${i}_fuel_en",    e.fuelEnabled)
                putString ("e${i}_fuel_disp",  e.fuelDisplay.name)
                putInt    ("e${i}_fuel_sz",    e.fuelSizeSp)
                
                putBoolean("e${i}_mode_en",    e.modeEnabled)
                putInt    ("e${i}_mode_sz",    e.modeSizeSp)
            }
            apply()
        }
    }
}
