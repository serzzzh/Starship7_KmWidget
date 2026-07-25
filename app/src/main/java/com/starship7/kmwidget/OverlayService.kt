package com.starship7.kmwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.TextView
import com.starship7.kmwidget.car.VehiclePropertyHelper
import com.starship7.kmwidget.tools.PropertyConstants

class OverlayService : Service() {

    companion object {
        private const val TAG = "OverlayService"
        const val ACTION_START = "com.starship7.kmwidget.OVERLAY_START"
        const val ACTION_STOP  = "com.starship7.kmwidget.OVERLAY_STOP"
        private const val PREFS = "overlay_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_X = "x"
        private const val KEY_Y = "y"

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun setEnabled(ctx: Context, enabled: Boolean) =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    private lateinit var windowManager: WindowManager
    private var overlayView: View? = null
    private var vehicleHelper: VehiclePropertyHelper? = null
    private var dbHelper: RangeDatabaseHelper? = null
    private val handler = Handler(Looper.getMainLooper())
    private val updateRunnable = object : Runnable {
        override fun run() {
            updateOverlay()
            handler.postDelayed(this, 30_000L)
        }
    }

    // drag state
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragTouchX = 0f
    private var dragTouchY = 0f

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dbHelper = RangeDatabaseHelper(this)
        vehicleHelper = VehiclePropertyHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                setEnabled(this, false)
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                setEnabled(this, true)
                showOverlay()
            }
        }
        return START_STICKY
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        removeOverlay()
        super.onDestroy()
    }

    private fun showOverlay() {
        if (overlayView != null) return

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedX = prefs.getInt(KEY_X, 20)
        val savedY = prefs.getInt(KEY_Y, 100)

        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                    WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            x = savedX
            y = savedY
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_range, null)

        // Drag support
        view.setOnTouchListener { v, event ->
            val lp = params
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = lp.x
                    dragInitialY = lp.y
                    dragTouchX = event.rawX
                    dragTouchY = event.rawY
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    lp.x = dragInitialX + (event.rawX - dragTouchX).toInt()
                    lp.y = dragInitialY + (event.rawY - dragTouchY).toInt()
                    windowManager.updateViewLayout(v, lp)
                    true
                }
                MotionEvent.ACTION_UP -> {
                    // Save position
                    getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                        .putInt(KEY_X, lp.x)
                        .putInt(KEY_Y, lp.y)
                        .apply()
                    true
                }
                else -> false
            }
        }

        overlayView = view
        windowManager.addView(view, params)
        handler.post(updateRunnable)
    }

    private fun updateOverlay() {
        val view = overlayView ?: return
        val vh = vehicleHelper ?: return
        val db = dbHelper ?: return

        val config = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val result = RangeCalculator.calculateWithHelper(this, WidgetPreferences.DEFAULT_WIDGET_ID, db, vh)

        // Also collect current data point
        if (vh.isConnected) {
            val odo = vh.getFloatProperty(PropertyConstants.VehicleInfo.ODOMETER, 0)
            val bat = vh.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
            val fuel = vh.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 1).toFloat()
            if (odo > 0) {
                db.insertLog(System.currentTimeMillis(), odo, bat, fuel)
                Log.i(TAG, "Overlay collected: ODO=$odo BAT=$bat FUEL=$fuel")
            }
        }

        view.findViewById<TextView>(R.id.overlay_range).text = result.rangeText
        view.findViewById<TextView>(R.id.overlay_info).text = result.infoText
    }

    private fun removeOverlay() {
        handler.removeCallbacks(updateRunnable)
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun buildNotification() = Notification.Builder(this, "overlay_channel")
        .setContentTitle("Range Overlay")
        .setContentText("Displaying range estimate")
        .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel("overlay_channel", "Range Overlay", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}
