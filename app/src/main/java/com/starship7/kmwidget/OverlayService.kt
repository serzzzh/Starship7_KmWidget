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
import android.view.GestureDetector
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
        private const val PREFS      = "overlay_prefs"
        private const val KEY_ENABLED = "enabled"
        private const val KEY_X       = "x"
        private const val KEY_Y       = "y"
        private const val KEY_SIZE    = "size"   // 0=S, 1=M, 2=L

        fun isEnabled(ctx: Context): Boolean =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).getBoolean(KEY_ENABLED, false)

        fun setEnabled(ctx: Context, enabled: Boolean) =
            ctx.getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean(KEY_ENABLED, enabled).apply()
    }

    // 3 размера: [главный текст sp, разбивка sp, инфо sp, padding dp]
    private val sizes = arrayOf(
        floatArrayOf(20f, 11f, 9f,  8f),   // 0 — Small
        floatArrayOf(28f, 13f, 10f, 12f),  // 1 — Medium (default)
        floatArrayOf(38f, 17f, 13f, 16f)   // 2 — Large
    )
    private var sizeLevel = 1

    private lateinit var windowManager: WindowManager
    private lateinit var params: WindowManager.LayoutParams
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

    // ── Drag state ──────────────────────────────────────────────────────
    private var dragInitialX = 0
    private var dragInitialY = 0
    private var dragTouchX   = 0f
    private var dragTouchY   = 0f
    private var isDragging   = false

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dbHelper      = RangeDatabaseHelper(this)
        vehicleHelper = VehiclePropertyHelper(this)

        val prefs = getSharedPreferences(PREFS, MODE_PRIVATE)
        sizeLevel = prefs.getInt(KEY_SIZE, 1).coerceIn(0, 2)
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

    // ── Показать оверлей ────────────────────────────────────────────────
    private fun showOverlay() {
        if (overlayView != null) return

        val prefs  = getSharedPreferences(PREFS, MODE_PRIVATE)
        val savedX = prefs.getInt(KEY_X, 20)
        val savedY = prefs.getInt(KEY_Y, 100)

        params = WindowManager.LayoutParams(
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
        setupTouch(view)
        applySize(view)

        overlayView = view
        windowManager.addView(view, params)
        handler.post(updateRunnable)
    }

    // ── Обработка касаний: drag + long press ────────────────────────────
    private fun setupTouch(view: View) {
        val gestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onLongPress(e: MotionEvent) {
                // Цикл по размерам: S → M → L → S
                sizeLevel = (sizeLevel + 1) % sizes.size
                getSharedPreferences(PREFS, MODE_PRIVATE).edit().putInt(KEY_SIZE, sizeLevel).apply()
                applySize(view)
                try { windowManager.updateViewLayout(view, params) } catch (_: Exception) {}
                Log.i(TAG, "Size changed to level $sizeLevel")
            }
        })

        view.setOnTouchListener { v, event ->
            gestureDetector.onTouchEvent(event)
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = params.x
                    dragInitialY = params.y
                    dragTouchX   = event.rawX
                    dragTouchY   = event.rawY
                    isDragging   = false
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragTouchX).toInt()
                    val dy = (event.rawY - dragTouchY).toInt()
                    if (!isDragging && (Math.abs(dx) > 8 || Math.abs(dy) > 8)) isDragging = true
                    if (isDragging) {
                        params.x = dragInitialX + dx
                        params.y = dragInitialY + dy
                        try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    if (isDragging) {
                        getSharedPreferences(PREFS, MODE_PRIVATE).edit()
                            .putInt(KEY_X, params.x)
                            .putInt(KEY_Y, params.y)
                            .apply()
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    // ── Применить размер к view ─────────────────────────────────────────
    private fun applySize(view: View) {
        val s = sizes[sizeLevel]
        val density = resources.displayMetrics.density
        val pad = (s[3] * density).toInt()
        view.setPadding(pad, pad, pad, pad)
        view.findViewById<TextView>(R.id.overlay_range)    .textSize = s[0]
        view.findViewById<TextView>(R.id.overlay_breakdown).textSize = s[1]
        view.findViewById<TextView>(R.id.overlay_info)     .textSize = s[2]
        // Заголовок тоже масштабируем
        view.findViewById<TextView>(R.id.overlay_title)    .textSize = s[2]
    }

    // ── Обновление данных ───────────────────────────────────────────────
    private fun updateOverlay() {
        val view = overlayView ?: return
        val vh   = vehicleHelper ?: return
        val db   = dbHelper ?: return

        val result = RangeCalculator.calculateWithHelper(this, WidgetPreferences.DEFAULT_WIDGET_ID, db, vh)

        // Собираем точку истории
        if (vh.isConnected) {
            val odo  = vh.getFloatProperty(PropertyConstants.VehicleInfo.ODOMETER, 0)
            val bat  = vh.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
            val fuel = vh.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()
            Log.i(TAG, "Collected: ODO=$odo BAT=$bat% FUEL=$fuel% → ${result.totalText}")
            if (odo > 0) db.insertLog(System.currentTimeMillis(), odo, bat, fuel)
        } else {
            Log.w(TAG, "Car API not connected")
        }

        view.findViewById<TextView>(R.id.overlay_range)    .text = result.totalText
        view.findViewById<TextView>(R.id.overlay_breakdown).text = result.breakdownText
        view.findViewById<TextView>(R.id.overlay_info)     .text = result.infoText
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
        .setContentText("Tap+hold to resize • Drag to move")
        .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel("overlay_channel", "Range Overlay", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}
