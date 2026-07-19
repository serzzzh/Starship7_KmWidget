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
