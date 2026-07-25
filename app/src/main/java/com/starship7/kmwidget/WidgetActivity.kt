package com.starship7.kmwidget

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.LayoutInflater
import android.view.View
import android.widget.Button
import android.widget.CheckBox
import android.widget.CompoundButton
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup

class WidgetActivity : Activity() {

    private lateinit var linesContainer: LinearLayout
    private val entryViews = mutableListOf<View>()

    // Дебаунс авто-сохранения: 800 мс после последнего изменения
    private val saveHandler = Handler(Looper.getMainLooper())
    private val saveRunnable = Runnable { commitAndRestart() }
    private fun debouncedSave() {
        saveHandler.removeCallbacks(saveRunnable)
        saveHandler.postDelayed(saveRunnable, 800)
    }

    private lateinit var radioGroup: RadioGroup
    private lateinit var editValue: EditText
    private lateinit var rgRange: RadioGroup

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        linesContainer = findViewById(R.id.linesContainer)
        radioGroup     = findViewById(R.id.radioGroup)
        editValue      = findViewById(R.id.editValue)
        rgRange        = findViewById(R.id.rgRangeSize)

        // ── Загружаем сохранённые настройки ─────────────────────────────
        val config    = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val rangeSize = OverlaySettings.getRangeSize(this)
        val entries   = OverlaySettings.loadEntries(this)

        radioGroup.check(if (config.isTimeBased) R.id.radioTime else R.id.radioKm)
        editValue.setText(if (config.isTimeBased) config.timeValue.toString()
                          else config.kmValue.toString())
        rgRange.check(rangeSizeToRadioId(rangeSize))

        for (entry in entries) addEntryRow(entry)

        // ── Слушатели авто-сохранения ────────────────────────────────────
        radioGroup.setOnCheckedChangeListener { _, _ -> debouncedSave() }
        rgRange.setOnCheckedChangeListener    { _, _ -> debouncedSave() }
        editValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = debouncedSave()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ── Кнопка «+ Добавить» ──────────────────────────────────────────
        findViewById<Button>(R.id.btnAddLine).setOnClickListener {
            addEntryRow(OverlayEntry())
            debouncedSave()
        }

        // ── Кнопка «Сохранить» (мгновенное применение) ──────────────────
        findViewById<Button>(R.id.btnSave).setOnClickListener {
            saveHandler.removeCallbacks(saveRunnable)
            commitAndRestart()
        }

        // ── Авто-запуск оверлея ──────────────────────────────────────────
        startForegroundService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_START
        })
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        super.onDestroy()
    }

    // ── Добавить строку записи ────────────────────────────────────────────
    private fun addEntryRow(entry: OverlayEntry) {
        val row = LayoutInflater.from(this).inflate(R.layout.line_item, linesContainer, false)

        val cbBattery    = row.findViewById<CheckBox>(R.id.cbBattery)
        val cbFuel       = row.findViewById<CheckBox>(R.id.cbFuel)
        val layBatOpts   = row.findViewById<LinearLayout>(R.id.layBatOptions)
        val layFuelOpts  = row.findViewById<LinearLayout>(R.id.layFuelOptions)

        // Установить начальные значения
        cbBattery.isChecked = entry.batteryEnabled
        cbFuel.isChecked    = entry.fuelEnabled
        layBatOpts.visibility  = if (entry.batteryEnabled) View.VISIBLE else View.GONE
        layFuelOpts.visibility = if (entry.fuelEnabled)   View.VISIBLE else View.GONE

        row.findViewById<RadioGroup>(R.id.rgBatDisplay).check(
            if (entry.batteryDisplay == OverlayLineDisplay.KM) R.id.rbBatKm else R.id.rbBatPct)
        row.findViewById<RadioGroup>(R.id.rgBatSize).check(lineSizeToRadioId(entry.batterySizeSp, bat = true))

        row.findViewById<RadioGroup>(R.id.rgFuelDisplay).check(
            if (entry.fuelDisplay == OverlayLineDisplay.KM) R.id.rbFuelKm else R.id.rbFuelPct)
        row.findViewById<RadioGroup>(R.id.rgFuelSize).check(lineSizeToRadioId(entry.fuelSizeSp, bat = false))

        // Показать/скрыть опции при смене галочек
        val toggleOpts = CompoundButton.OnCheckedChangeListener { btn, checked ->
            when (btn.id) {
                R.id.cbBattery -> layBatOpts.visibility  = if (checked) View.VISIBLE else View.GONE
                R.id.cbFuel    -> layFuelOpts.visibility = if (checked) View.VISIBLE else View.GONE
            }
            debouncedSave()
        }
        cbBattery.setOnCheckedChangeListener(toggleOpts)
        cbFuel.setOnCheckedChangeListener(toggleOpts)

        // Любое изменение RadioGroup → авто-сохранение
        listOf(R.id.rgBatDisplay, R.id.rgBatSize, R.id.rgFuelDisplay, R.id.rgFuelSize).forEach { id ->
            row.findViewById<RadioGroup>(id).setOnCheckedChangeListener { _, _ -> debouncedSave() }
        }

        // Удалить запись
        row.findViewById<Button>(R.id.btnDeleteEntry).setOnClickListener {
            entryViews.remove(row)
            linesContainer.removeView(row)
            debouncedSave()
        }

        entryViews.add(row)
        linesContainer.addView(row)
    }

    // ── Читать текущие настройки из UI ────────────────────────────────────
    private fun readEntries(): List<OverlayEntry> = entryViews.map { row ->
        OverlayEntry(
            batteryEnabled = row.findViewById<CheckBox>(R.id.cbBattery).isChecked,
            batteryDisplay = if (row.findViewById<RadioButton>(R.id.rbBatKm).isChecked)
                OverlayLineDisplay.KM else OverlayLineDisplay.PERCENT,
            batterySizeSp  = readLineSize(row, bat = true),
            fuelEnabled    = row.findViewById<CheckBox>(R.id.cbFuel).isChecked,
            fuelDisplay    = if (row.findViewById<RadioButton>(R.id.rbFuelKm).isChecked)
                OverlayLineDisplay.KM else OverlayLineDisplay.PERCENT,
            fuelSizeSp     = readLineSize(row, bat = false)
        )
    }

    // ── Сохранить и перезапустить оверлей ────────────────────────────────
    private fun commitAndRestart() {
        val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
        val value = editValue.text.toString().toFloatOrNull() ?: 30f
        WidgetPreferences.save(this, WidgetPreferences.DEFAULT_WIDGET_ID,
            WidgetConfig(isTimeBased, value.toInt(), value))

        OverlaySettings.saveRangeSize(this, radioToRangeSize(rgRange.checkedRadioButtonId))
        OverlaySettings.saveEntries(this, readEntries())

        // Стоп → небольшая пауза → старт
        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
        }, 400)
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

    private fun lineSizeToRadioId(sp: Int, bat: Boolean): Int = when (sp) {
        OverlayLine.LINE_SIZE_S  -> if (bat) R.id.rbBatS  else R.id.rbFuelS
        OverlayLine.LINE_SIZE_L  -> if (bat) R.id.rbBatL  else R.id.rbFuelL
        OverlayLine.LINE_SIZE_XL -> if (bat) R.id.rbBatXL else R.id.rbFuelXL
        else                     -> if (bat) R.id.rbBatM  else R.id.rbFuelM
    }

    private fun readLineSize(row: View, bat: Boolean): Int {
        val (sId, mId, lId, xlId) = if (bat)
            listOf(R.id.rbBatS, R.id.rbBatM, R.id.rbBatL, R.id.rbBatXL)
        else
            listOf(R.id.rbFuelS, R.id.rbFuelM, R.id.rbFuelL, R.id.rbFuelXL)
        return when {
            row.findViewById<RadioButton>(sId) .isChecked -> OverlayLine.LINE_SIZE_S
            row.findViewById<RadioButton>(lId) .isChecked -> OverlayLine.LINE_SIZE_L
            row.findViewById<RadioButton>(xlId).isChecked -> OverlayLine.LINE_SIZE_XL
            else                                          -> OverlayLine.LINE_SIZE_M
        }
    }
}
