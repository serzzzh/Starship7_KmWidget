package com.starship7.kmwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.IBinder
import android.util.Log
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class RangeUpdateService : Service() {
    private val TAG = "RangeUpdateService"
    private lateinit var vehicleHelper: VehiclePropertyHelper
    private lateinit var dbHelper: RangeDatabaseHelper

    override fun onCreate() {
        super.onCreate()
        Log.i(TAG, "Service Created")
        createNotificationChannel()
        startForeground(1, Notification.Builder(this, "range_channel")
            .setContentTitle("Range Tracker")
            .setContentText("Tracking vehicle range...")
            .build())

        dbHelper = RangeDatabaseHelper(this)
        // VehiclePropertyHelper MUST be created on main thread (requires Looper for CarPropertyManager)
        vehicleHelper = VehiclePropertyHelper(this)

        // Use Main dispatcher — Car API reads are fast and require main thread Looper
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                collectData()
                delay(60000) // Collect every minute
            }
        }
    }

    private fun collectData() {
        if (!vehicleHelper.isConnected) {
            Log.w(TAG, "Car API not connected, skipping data collection")
            return
        }

        val odometer = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.ODOMETER, 0)
        val evOdo = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.MILEAGE_SINCE_LAST_EV, 0)
        val fuelOdo = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.MILEAGE_SINCE_LAST_OIL, 0)
        val battery = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val fuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()

        Log.i(TAG, "Collected: ODO=$odometer EV_ODO=$evOdo FUEL_ODO=$fuelOdo BAT=$battery FUEL=$fuel")

        if (odometer > 0) {
            dbHelper.insertLog(System.currentTimeMillis(), odometer, evOdo, fuelOdo, battery, fuel)
        }
        // Update widgets regardless — show current battery/fuel even if no history yet
        updateWidgetsOnMainThread()
    }

    private fun updateWidgetsOnMainThread() {
        val appWidgetManager = android.appwidget.AppWidgetManager.getInstance(this)
        val componentName = android.content.ComponentName(this, RangeWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        for (id in widgetIds) {
            RangeWidgetProvider.updateAppWidgetFromService(this, appWidgetManager, id, dbHelper, vehicleHelper)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        val channel = NotificationChannel("range_channel", "Range Tracker", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(channel)
    }
}
