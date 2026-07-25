package com.starship7.kmwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView

class WidgetActivity : Activity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        // ── Загружаем сохранённые настройки ─────────────────────────────
        val config = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val display = OverlaySettings.loadDisplay(this)

        // Окно расчёта
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val editValue  = findViewById<EditText>(R.id.editValue)
        if (config.isTimeBased) {
            radioGroup.check(R.id.radioTime)
            editValue.setText(config.timeValue.toString())
        } else {
            radioGroup.check(R.id.radioKm)
            editValue.setText(config.kmValue.toString())
        }

        // Настройки отображения: 3 строки
        val rgTotal     = findViewById<RadioGroup>(R.id.rgTotal)
        val rgBreakdown = findViewById<RadioGroup>(R.id.rgBreakdown)
        val rgInfo      = findViewById<RadioGroup>(R.id.rgInfo)

        rgTotal.check(spToRadioId(rgTotal, display.totalSizeSp))
        rgBreakdown.check(spToRadioId(rgBreakdown, display.breakdownSizeSp))
        rgInfo.check(spToRadioId(rgInfo, display.infoSizeSp))

        // ── Сохранить ───────────────────────────────────────────────────
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
            val value = editValue.text.toString().toFloatOrNull() ?: 30f
            WidgetPreferences.save(
                this, WidgetPreferences.DEFAULT_WIDGET_ID,
                WidgetConfig(isTimeBased, value.toInt(), value)
            )

            val newDisplay = OverlayDisplayConfig(
                totalSizeSp     = radioToSp(rgTotal.checkedRadioButtonId),
                breakdownSizeSp = radioToSp(rgBreakdown.checkedRadioButtonId),
                infoSizeSp      = radioToSp(rgInfo.checkedRadioButtonId)
            )
            OverlaySettings.saveDisplay(this, newDisplay)

            // Перезапустить оверлей чтобы применить новые настройки
            restartOverlay()
            refreshPreview()
        }

        // ── Авто-запуск оверлея при открытии приложения ─────────────────
        ensureOverlayRunning()

        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    /** Запустить оверлей если ещё не запущен */
    private fun ensureOverlayRunning() {
        startForegroundService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        })
    }

    /** Остановить и перезапустить оверлей (после изменения настроек) */
    private fun restartOverlay() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_STOP
        })
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
        }, 500)
    }

    private fun refreshPreview() {
        val result = RangeCalculator.calculate(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        findViewById<TextView>(R.id.textRange).text = result.totalText
        findViewById<TextView>(R.id.textInfo).text  = result.breakdownText + "  " + result.infoText
    }

    // ── Маппинг RadioGroup ID ↔ sp ────────────────────────────────────

    private fun radioToSp(radioId: Int): Int = when (radioId) {
        R.id.rgTotalOff,  R.id.rgBdOff,  R.id.rgInfoOff  -> OverlayDisplayConfig.SIZE_OFF
        R.id.rgTotalS,    R.id.rgBdS,    R.id.rgInfoS    -> OverlayDisplayConfig.SIZE_S
        R.id.rgTotalM,    R.id.rgBdM,    R.id.rgInfoM    -> OverlayDisplayConfig.SIZE_M
        R.id.rgTotalL,    R.id.rgBdL,    R.id.rgInfoL    -> OverlayDisplayConfig.SIZE_L
        R.id.rgTotalXL,   R.id.rgBdXL,  R.id.rgInfoXL   -> OverlayDisplayConfig.SIZE_XL
        else -> OverlayDisplayConfig.SIZE_M
    }

    /** Выбрать RadioButton в группе, соответствующий заданному размеру sp. */
    private fun spToRadioId(group: RadioGroup, sp: Int): Int {
        val offId = when (group.id) {
            R.id.rgTotal     -> R.id.rgTotalOff
            R.id.rgBreakdown -> R.id.rgBdOff
            else             -> R.id.rgInfoOff
        }
        val sId = when (group.id) {
            R.id.rgTotal     -> R.id.rgTotalS
            R.id.rgBreakdown -> R.id.rgBdS
            else             -> R.id.rgInfoS
        }
        val mId = when (group.id) {
            R.id.rgTotal     -> R.id.rgTotalM
            R.id.rgBreakdown -> R.id.rgBdM
            else             -> R.id.rgInfoM
        }
        val lId = when (group.id) {
            R.id.rgTotal     -> R.id.rgTotalL
            R.id.rgBreakdown -> R.id.rgBdL
            else             -> R.id.rgInfoL
        }
        val xlId = when (group.id) {
            R.id.rgTotal     -> R.id.rgTotalXL
            R.id.rgBreakdown -> R.id.rgBdXL
            else             -> R.id.rgInfoXL
        }
        return when (sp) {
            OverlayDisplayConfig.SIZE_OFF -> offId
            OverlayDisplayConfig.SIZE_S   -> sId
            OverlayDisplayConfig.SIZE_M   -> mId
            OverlayDisplayConfig.SIZE_L   -> lId
            OverlayDisplayConfig.SIZE_XL  -> xlId
            else -> mId
        }
    }
}
