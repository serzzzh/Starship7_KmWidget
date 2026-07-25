package com.starship7.kmwidget

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
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
import android.widget.LinearLayout
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

    private var dragInitialX = 0; private var dragInitialY = 0
    private var dragTouchX   = 0f; private var dragTouchY   = 0f
    private var isDragging   = false

    // Длинный тап → открыть настройки
    private val longPressHandler = Handler(Looper.getMainLooper())
    private val longPressRunnable = Runnable {
        if (!isDragging) openSettings()
    }
    private fun openSettings() {
        val intent = Intent(this, WidgetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP
        }
        startActivity(intent)
    }

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()
        startForeground(2, buildNotification())
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        dbHelper      = RangeDatabaseHelper(this)
        vehicleHelper = VehiclePropertyHelper(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return when (intent?.action) {
            ACTION_STOP -> {
                OverlaySettings.setEnabled(this, false)
                removeOverlay(); stopSelf()
                START_NOT_STICKY
            }
            else -> {
                OverlaySettings.setEnabled(this, true)
                showOverlay()
                START_STICKY
            }
        }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onDestroy() {
        handler.removeCallbacks(updateRunnable)
        longPressHandler.removeCallbacks(longPressRunnable)
        removeOverlay()
        super.onDestroy()
    }

    // ── Показать оверлей ────────────────────────────────────────────────
    private fun showOverlay() {
        if (overlayView != null) return
        val (x, y) = OverlaySettings.getPosition(this)

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
            this.x = x; this.y = y
        }

        val view = LayoutInflater.from(this).inflate(R.layout.overlay_range, null)
        setupDrag(view)
        overlayView = view
        windowManager.addView(view, params)
        handler.post(updateRunnable)
    }

    // ── Drag + длинный тап → настройки ──────────────────────────────────
    private fun setupDrag(view: View) {
        view.setOnTouchListener { v, event ->
            when (event.action) {
                MotionEvent.ACTION_DOWN -> {
                    dragInitialX = params.x; dragInitialY = params.y
                    dragTouchX = event.rawX; dragTouchY = event.rawY
                    isDragging = false
                    longPressHandler.postDelayed(longPressRunnable, 600)
                    true
                }
                MotionEvent.ACTION_MOVE -> {
                    val dx = (event.rawX - dragTouchX).toInt()
                    val dy = (event.rawY - dragTouchY).toInt()
                    if (!isDragging && (Math.abs(dx) > 10 || Math.abs(dy) > 10)) {
                        isDragging = true
                        longPressHandler.removeCallbacks(longPressRunnable)
                    }
                    if (isDragging) {
                        params.x = dragInitialX + dx; params.y = dragInitialY + dy
                        try { windowManager.updateViewLayout(v, params) } catch (_: Exception) {}
                    }
                    true
                }
                MotionEvent.ACTION_UP -> {
                    longPressHandler.removeCallbacks(longPressRunnable)
                    if (isDragging) OverlaySettings.savePosition(this, params.x, params.y)
                    isDragging = false; true
                }
                else -> false
            }
        }
    }

    // ── Обновление данных ────────────────────────────────────────────────
    private fun updateOverlay() {
        val view = overlayView ?: return
        val vh   = vehicleHelper ?: return
        val db   = dbHelper ?: return

        val result = RangeCalculator.calculateWithHelper(this, WidgetPreferences.DEFAULT_WIDGET_ID, db, vh)

        if (vh.isConnected) {
            val odo  = vh.getFloatProperty(PropertyConstants.VehicleInfo.ODOMETER, 0)
            val bat  = vh.getFloatProperty(PropertyConstants.VehicleInfo.EV_BATTERY_PERCENTAGE, 0)
            val fuel = vh.getIntProperty(PropertyConstants.VehicleInfo.FUEL_PERCENTAGE, 0).toFloat()
            Log.i(TAG, "ODO=$odo BAT=$bat% FUEL=$fuel% → ${result.totalText}")
            if (odo > 0) db.insertLog(System.currentTimeMillis(), odo, bat, fuel)
        }

        // Главная строка
        val rangeView = view.findViewById<TextView>(R.id.overlay_range)
        rangeView.text     = result.totalText
        rangeView.textSize = OverlaySettings.getRangeSize(this).toFloat()

        // Дополнительные строки
        val container = view.findViewById<LinearLayout>(R.id.overlay_lines)
        renderLines(container, result.snapshot)
    }

    // ── Рендер доп. строк ────────────────────────────────────────────────
    private fun renderLines(container: LinearLayout, s: CarSnapshot) {
        container.removeAllViews()
        if (!s.isConnected) return

        for (entry in OverlaySettings.loadEntries(this)) {
            if (!entry.batteryEnabled && !entry.fuelEnabled) continue

            // Комбинированная строка: батарея + бензин на одной
            if (entry.combineLine && entry.batteryEnabled && entry.fuelEnabled) {
                val batPart  = formatPart(OverlayLineType.BATTERY, entry.batteryDisplay, s)
                val fuelPart = formatPart(OverlayLineType.FUEL,    entry.fuelDisplay,    s)
                val combined = listOfNotNull(batPart, fuelPart).joinToString("  |  ")
                if (combined.isNotEmpty()) {
                    addTextView(container, combined,
                        maxOf(entry.batterySizeSp, entry.fuelSizeSp))
                }
            } else {
                // Раздельные строки
                if (entry.batteryEnabled) {
                    val t = formatPart(OverlayLineType.BATTERY, entry.batteryDisplay, s)
                    if (t != null) addTextView(container, t, entry.batterySizeSp)
                }
                if (entry.fuelEnabled) {
                    val t = formatPart(OverlayLineType.FUEL, entry.fuelDisplay, s)
                    if (t != null) addTextView(container, t, entry.fuelSizeSp)
                }
            }
        }
    }

    private fun formatPart(type: OverlayLineType, display: OverlayLineDisplay, s: CarSnapshot): String? =
        when {
            type == OverlayLineType.BATTERY && display == OverlayLineDisplay.KM      ->
                if (s.evRangeKm   > 0)  "🔋${s.evRangeKm.toInt()} км"    else null
            type == OverlayLineType.BATTERY && display == OverlayLineDisplay.PERCENT ->
                if (s.batteryPct  >= 0) "🔋${s.batteryPct.toInt()}%"      else null
            type == OverlayLineType.FUEL    && display == OverlayLineDisplay.KM      ->
                if (s.fuelRangeKm > 0)  "⛽${s.fuelRangeKm.toInt()} км"  else null
            type == OverlayLineType.FUEL    && display == OverlayLineDisplay.LITERS  ->
                if (s.fuelLiters  >= 0) "⛽${s.fuelLiters.toInt()} л"     else null
            type == OverlayLineType.FUEL    && display == OverlayLineDisplay.PERCENT ->
                if (s.fuelPct     >= 0) "⛽${s.fuelPct.toInt()}%"         else null
            else -> null
        }

    private fun addTextView(container: LinearLayout, text: String, sizeSp: Int) {
        container.addView(TextView(this).apply {
            this.text = text
            textSize  = sizeSp.toFloat()
            setTextColor(Color.parseColor("#CCCCCC"))
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { topMargin = 2 }
        })
    }

    private fun removeOverlay() {
        handler.removeCallbacks(updateRunnable)
        overlayView?.let {
            try { windowManager.removeView(it) } catch (_: Exception) {}
            overlayView = null
        }
    }

    private fun buildNotification() = Notification.Builder(this, "overlay_channel")
        .setContentTitle("Запас хода")
        .setContentText("Оверлей активен — управляйте через настройки приложения")
        .build()

    private fun createNotificationChannel() {
        val ch = NotificationChannel("overlay_channel", "Запас хода", NotificationManager.IMPORTANCE_LOW)
        getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }
}
