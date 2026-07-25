package com.starship7.kmwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView

class WidgetActivity : Activity() {

    private lateinit var linesContainer: LinearLayout
    private val lineViews = mutableListOf<android.view.View>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        linesContainer = findViewById(R.id.linesContainer)

        // ── Загружаем настройки ──────────────────────────────────────────
        val config     = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val rangeSize  = OverlaySettings.getRangeSize(this)
        val savedLines = OverlaySettings.loadLines(this)

        // Окно расчёта
        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val editValue  = findViewById<EditText>(R.id.editValue)
        radioGroup.check(if (config.isTimeBased) R.id.radioTime else R.id.radioKm)
        editValue.setText(if (config.isTimeBased) config.timeValue.toString() else config.kmValue.toString())

        // Размер строки "Запас хода"
        val rgRange = findViewById<RadioGroup>(R.id.rgRangeSize)
        rgRange.check(rangeSizeToRadioId(rangeSize))

        // Загрузить сохранённые доп. строки
        for (line in savedLines) addLineRow(line)

        // ── Кнопка + Добавить ────────────────────────────────────────────
        findViewById<Button>(R.id.btnAddLine).setOnClickListener {
            addLineRow(OverlayLine())   // новая строка с дефолтными значениями
        }

        // ── Сохранить и применить ────────────────────────────────────────
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveAll(radioGroup, editValue, rgRange)
            refreshPreview()
        }

        // ── Авто-запуск оверлея ──────────────────────────────────────────
        startForegroundService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        })

        refreshPreview()
    }

    override fun onResume() {
        super.onResume()
        refreshPreview()
    }

    // ── Добавить строку в список ─────────────────────────────────────────
    private fun addLineRow(line: OverlayLine) {
        val rowView = LayoutInflater.from(this).inflate(R.layout.line_item, linesContainer, false)

        // Установить начальные значения
        rowView.findViewById<RadioGroup>(R.id.rgType).check(
            if (line.type == OverlayLineType.BATTERY) R.id.rbBattery else R.id.rbFuel
        )
        rowView.findViewById<RadioGroup>(R.id.rgDisplay).check(
            if (line.display == OverlayLineDisplay.KM) R.id.rbKm else R.id.rbPct
        )
        rowView.findViewById<RadioGroup>(R.id.rgSize).check(lineSizeToRadioId(line.sizeSp))

        // Удалить строку
        rowView.findViewById<Button>(R.id.btnDeleteLine).setOnClickListener {
            lineViews.remove(rowView)
            linesContainer.removeView(rowView)
        }

        lineViews.add(rowView)
        linesContainer.addView(rowView)
    }

    // ── Читать состояние всех строк ──────────────────────────────────────
    private fun readLines(): List<OverlayLine> = lineViews.map { row ->
        val type = if (row.findViewById<RadioButton>(R.id.rbBattery).isChecked)
            OverlayLineType.BATTERY else OverlayLineType.FUEL

        val display = if (row.findViewById<RadioButton>(R.id.rbKm).isChecked)
            OverlayLineDisplay.KM else OverlayLineDisplay.PERCENT

        val sizeSp = when {
            row.findViewById<RadioButton>(R.id.rbS ).isChecked -> OverlayLine.LINE_SIZE_S
            row.findViewById<RadioButton>(R.id.rbL ).isChecked -> OverlayLine.LINE_SIZE_L
            row.findViewById<RadioButton>(R.id.rbXL).isChecked -> OverlayLine.LINE_SIZE_XL
            else                                               -> OverlayLine.LINE_SIZE_M
        }
        OverlayLine(type, display, sizeSp)
    }

    // ── Сохранить всё и перезапустить оверлей ───────────────────────────
    private fun saveAll(radioGroup: RadioGroup, editValue: EditText, rgRange: RadioGroup) {
        // Окно расчёта
        val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
        val value = editValue.text.toString().toFloatOrNull() ?: 30f
        WidgetPreferences.save(this, WidgetPreferences.DEFAULT_WIDGET_ID,
            WidgetConfig(isTimeBased, value.toInt(), value))

        // Размер "Запас хода"
        OverlaySettings.saveRangeSize(this, radioToRangeSize(rgRange.checkedRadioButtonId))

        // Дополнительные строки
        OverlaySettings.saveLines(this, readLines())

        // Перезапустить оверлей
        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
        }, 600)
    }

    private fun refreshPreview() {
        val r = RangeCalculator.calculate(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        findViewById<TextView>(R.id.textRange).text = r.totalText
        findViewById<TextView>(R.id.textInfo) .text = r.breakdownText
    }

    // ── Маппинг ID ↔ размер ───────────────────────────────────────────────

    private fun rangeSizeToRadioId(sp: Int): Int = when (sp) {
        OverlayLine.RANGE_SIZE_S  -> R.id.rgRangeS
        OverlayLine.RANGE_SIZE_L  -> R.id.rgRangeL
        OverlayLine.RANGE_SIZE_XL -> R.id.rgRangeXL
        else                      -> R.id.rgRangeM
    }

    private fun radioToRangeSize(id: Int): Int = when (id) {
        R.id.rgRangeS  -> OverlayLine.RANGE_SIZE_S
        R.id.rgRangeL  -> OverlayLine.RANGE_SIZE_L
        R.id.rgRangeXL -> OverlayLine.RANGE_SIZE_XL
        else            -> OverlayLine.RANGE_SIZE_M
    }

    private fun lineSizeToRadioId(sp: Int): Int = when (sp) {
        OverlayLine.LINE_SIZE_S  -> R.id.rbS
        OverlayLine.LINE_SIZE_L  -> R.id.rbL
        OverlayLine.LINE_SIZE_XL -> R.id.rbXL
        else                     -> R.id.rbM
    }
}
