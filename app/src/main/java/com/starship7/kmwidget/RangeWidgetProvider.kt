package com.starship7.kmwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent

class RangeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, RangeUpdateService::class.java)
        context.startForegroundService(intent)

        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId)
        }
    }

    override fun onEnabled(context: Context) {
        val intent = Intent(context, RangeUpdateService::class.java)
        context.startForegroundService(intent)
    }

    override fun onDisabled(context: Context) {
        val intent = Intent(context, RangeUpdateService::class.java)
        context.stopService(intent)
    }

    companion object {

        /** Called from RangeUpdateService — reuses existing vehicleHelper (already on main thread) */
        fun updateAppWidgetFromService(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            dbHelper: RangeDatabaseHelper,
            vehicleHelper: com.starship7.kmwidget.car.VehiclePropertyHelper
        ) {
            val result = RangeCalculator.calculateWithHelper(context, appWidgetId, dbHelper, vehicleHelper)
            applyViews(context, appWidgetManager, appWidgetId, result)
        }

        /** Called by the Android widget system (onUpdate) — creates helpers on calling thread */
        fun updateAllWidgets(context: Context) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RangeWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int
        ) {
            val result = RangeCalculator.calculate(context, appWidgetId)

            applyViews(context, appWidgetManager, appWidgetId, result)
        }

        private fun applyViews(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            result: RangeCalculator.RangeResult
        ) {
            val views = android.widget.RemoteViews(context.packageName, R.layout.widget_range)
            views.setTextViewText(R.id.textRange, result.rangeText)
            views.setTextViewText(R.id.textInfo, result.infoText)

            val configIntent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                appWidgetId,
                configIntent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
