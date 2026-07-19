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
        vehicleHelper = VehiclePropertyHelper(this)

        CoroutineScope(Dispatchers.IO).launch {
            while (true) {
                collectData()
                delay(60000) // Collect every minute
            }
        }
    }

    private fun collectData() {
        if (!vehicleHelper.isConnected) return

        val odometer = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.ODOMETER, 0)
        val battery = vehicleHelper.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
        val fuel = vehicleHelper.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 1).toFloat()

        if (odometer > 0) {
            Log.i(TAG, "Collected: ODO=$odometer, BAT=$battery, FUEL=$fuel")
            dbHelper.insertLog(System.currentTimeMillis(), odometer, battery, fuel)
            RangeWidgetProvider.updateAllWidgets(this)
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
