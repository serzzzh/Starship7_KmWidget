package com.starship7.kmwidget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.widget.RemoteViews
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants
import android.util.Log

class RangeWidgetProvider : AppWidgetProvider() {

    override fun onUpdate(context: Context, appWidgetManager: AppWidgetManager, appWidgetIds: IntArray) {
        val intent = Intent(context, RangeUpdateService::class.java)
        context.startForegroundService(intent)

        val dbHelper = RangeDatabaseHelper(context)
        val vehicleHelper = VehiclePropertyHelper(context)
        
        for (appWidgetId in appWidgetIds) {
            updateAppWidget(context, appWidgetManager, appWidgetId, dbHelper, vehicleHelper)
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
        fun updateAllWidgets(context: Context, dbHelper: RangeDatabaseHelper, vehicleHelper: VehiclePropertyHelper) {
            val appWidgetManager = AppWidgetManager.getInstance(context)
            val componentName = ComponentName(context, RangeWidgetProvider::class.java)
            val appWidgetIds = appWidgetManager.getAppWidgetIds(componentName)
            for (id in appWidgetIds) {
                updateAppWidget(context, appWidgetManager, id, dbHelper, vehicleHelper)
            }
        }

        private fun updateAppWidget(
            context: Context,
            appWidgetManager: AppWidgetManager,
            appWidgetId: Int,
            dbHelper: RangeDatabaseHelper,
            vehicleHelper: VehiclePropertyHelper
        ) {
            val prefs = context.getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE)
            val isTimeBased = prefs.getBoolean("isTimeBased", true)
            val timeValue = prefs.getInt("timeValue", 30) // Default 30 mins
            val kmValue = prefs.getFloat("kmValue", 50f) // Default 50 km

            val stats = if (isTimeBased) {
                dbHelper.getStatsSinceMinutes(timeValue)
            } else {
                dbHelper.getStatsSinceKm(kmValue)
            }

            val currentBat = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
            val currentFuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 1).toFloat()

            var estimatedRangeStr = "-- km"

            if (stats != null && stats.deltaOdo > 1) {
                // Calculate consumption: total distance / total energy equivalent.
                // For simplicity: if mostly using battery, calculate battery range. If mostly fuel, calculate fuel range.
                // Or calculate EV km/1% and Fuel km/1%.
                
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
                
                // If hybrid, just add them or use total weighted?
                // Let's assume the user just wants the combined range based on recent average.
                val totalRange = evRange + fuelRange
                if (totalRange > 0) {
                    estimatedRangeStr = "${totalRange.toInt()} km"
                }
            }

            val views = RemoteViews(context.packageName, R.layout.widget_range)
            views.setTextViewText(R.id.textRange, estimatedRangeStr)
            
            val infoStr = if (isTimeBased) "Based on last $timeValue min" else "Based on last ${kmValue.toInt()} km"
            views.setTextViewText(R.id.textInfo, infoStr)

            // Intent to open config
            val configIntent = Intent(context, ConfigActivity::class.java).apply {
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            }
            val pendingIntent = PendingIntent.getActivity(context, appWidgetId, configIntent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
            views.setOnClickPendingIntent(R.id.widget_root, pendingIntent)

            appWidgetManager.updateAppWidget(appWidgetId, views)
        }
    }
}
