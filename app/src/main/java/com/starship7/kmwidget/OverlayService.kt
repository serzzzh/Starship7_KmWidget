package com.starship7.kmwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.graphics.Rect
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

        fun isEnabled(ctx: Context) = OverlaySettings.isEnabled(ctx)
        fun setEnabled(ctx: Context, v: Boolean) = OverlaySettings.setEnabled(ctx, v)
    }

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

    // Drag state
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
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                OverlaySettings.setEnabled(this, false)
                removeOverlay()
                stopSelf()
                return START_NOT_STICKY
            }
            else -> {
                OverlaySettings.setEnabled(this, true)
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

        val (savedX, savedY) = OverlaySettings.getPosition(this)

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

        // Кнопка выключения (top-right)
        view.findViewById<TextView>(R.id.overlay_power_btn).setOnClickListener {
            Log.i(TAG, "Power button pressed — stopping overlay")
            OverlaySettings.setEnabled(this, false)
            removeOverlay()
            stopSelf()
        }

        setupDrag(view)
        applyDisplayConfig(view)

        overlayView = view
        windowManager.addView(view, params)
        handler.post(updateRunnable)
    }

    // ── Drag (игнорируем зону power-кнопки) ────────────────────────────
    private fun setupDrag(view: View) {
        view.setOnTouchListener { v, event ->
            // Если касание попало на power-кнопку — не начинаем drag
            val powerBtn = view.findViewById<TextView>(R.id.overlay_power_btn)
            val btnBounds = Rect()
            powerBtn.getHitRect(btnBounds)
            if (event.action == MotionEvent.ACTION_DOWN &&
                btnBounds.contains(event.x.toInt(), event.y.toInt())) {
                return@setOnTouchListener false
            }

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
                        OverlaySettings.savePosition(this, params.x, params.y)
                    }
                    isDragging = false
                    true
                }
                else -> false
            }
        }
    }

    // ── Применить настройки отображения ────────────────────────────────
    private fun applyDisplayConfig(view: View) {
        val cfg = OverlaySettings.loadDisplay(this)

        fun applyLine(id: Int, sizeSp: Int) {
            val tv = view.findViewById<TextView>(id)
            if (sizeSp == OverlayDisplayConfig.SIZE_OFF) {
                tv.visibility = View.GONE
            } else {
                tv.visibility = View.VISIBLE
                tv.textSize = sizeSp.toFloat()
            }
        }

        applyLine(R.id.overlay_range,     cfg.totalSizeSp)
        applyLine(R.id.overlay_breakdown, cfg.breakdownSizeSp)
        applyLine(R.id.overlay_info,      cfg.infoSizeSp)

        // Заголовок — показываем только если хоть одна строка видна
        val titleVisible = cfg.totalSizeSp != OverlayDisplayConfig.SIZE_OFF ||
                cfg.breakdownSizeSp != OverlayDisplayConfig.SIZE_OFF ||
                cfg.infoSizeSp != OverlayDisplayConfig.SIZE_OFF
        view.findViewById<TextView>(R.id.overlay_title).visibility =
            if (titleVisible) View.VISIBLE else View.GONE
    }

    // ── Обновление данных ───────────────────────────────────────────────
    private fun updateOverlay() {
        val view = overlayView ?: return
        val vh   = vehicleHelper ?: return
        val db   = dbHelper ?: return

        val result = RangeCalculator.calculateWithHelper(this, WidgetPreferences.DEFAULT_WIDGET_ID, db, vh)

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

    // Вызывается из WidgetActivity после изменения настроек дисплея
    fun refreshDisplayConfig() {
        overlayView?.let { applyDisplayConfig(it) }
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
        .setContentText("Running • tap ⏻ on overlay to stop")
        .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel("overlay_channel", "Range Overlay", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}
