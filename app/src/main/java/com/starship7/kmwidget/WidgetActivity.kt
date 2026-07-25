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
    private lateinit var btnToggleOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        linesContainer   = findViewById(R.id.linesContainer)
        radioGroup       = findViewById(R.id.radioGroup)
        editValue        = findViewById(R.id.editValue)
        rgRange          = findViewById(R.id.rgRangeSize)
        btnToggleOverlay = findViewById(R.id.btnToggleOverlay)

        // ── Загружаем сохранённые настройки ─────────────────────────────
        val config    = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val rangeSize = OverlaySettings.getRangeSize(this)
        val entries   = OverlaySettings.loadEntries(this)

        radioGroup.check(if (config.isTimeBased) R.id.radioTime else R.id.radioKm)
        editValue.setText(if (config.isTimeBased) config.timeValue.toString()
                          else config.kmValue.toString())
        rgRange.check(rangeSizeToRadioId(rangeSize))

        for (entry in entries) addEntryRow(entry)

        // ── Кнопка Включить / Выключить ──────────────────────────────────
        updateToggleButton()
        btnToggleOverlay.setOnClickListener {
            val nowEnabled = OverlaySettings.isEnabled(this)
            if (nowEnabled) {
                startService(Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_STOP
                })
            } else {
                startForegroundService(Intent(this, OverlayService::class.java).apply {
                    action = OverlayService.ACTION_START
                })
            }
            updateToggleButton()
        }

        // ── Слушатели авто-сохранения ────────────────────────────────────
        radioGroup.setOnCheckedChangeListener { _, _ -> debouncedSave() }
        rgRange.setOnCheckedChangeListener    { _, _ -> debouncedSave() }
        editValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = debouncedSave()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // ── Кнопка «+» ──────────────────────────────────────────────────
        findViewById<Button>(R.id.btnAddLine).setOnClickListener {
            addEntryRow(OverlayEntry())
            debouncedSave()
        }

        // ── Авто-запуск оверлея (если был включён) ───────────────────────
        if (OverlaySettings.isEnabled(this)) {
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
        }
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        super.onDestroy()
    }

    private fun updateToggleButton() {
        val isOn = OverlaySettings.isEnabled(this)
        btnToggleOverlay.text = if (isOn) "Выключить" else "Включить"
    }

    // ── Добавить строку записи ────────────────────────────────────────────
    private fun addEntryRow(entry: OverlayEntry) {
        val row = LayoutInflater.from(this).inflate(R.layout.line_item, linesContainer, false)

        val cbBattery   = row.findViewById<CheckBox>(R.id.cbBattery)
        val cbFuel      = row.findViewById<CheckBox>(R.id.cbFuel)
        val cbCombine   = row.findViewById<CheckBox>(R.id.cbCombine)
        val layBatOpts  = row.findViewById<LinearLayout>(R.id.layBatOptions)
        val layFuelOpts = row.findViewById<LinearLayout>(R.id.layFuelOptions)

        // Начальные значения
        cbBattery.isChecked = entry.batteryEnabled
        cbFuel.isChecked    = entry.fuelEnabled
        cbCombine.isChecked = entry.combineLine
        layBatOpts.visibility  = if (entry.batteryEnabled) View.VISIBLE else View.GONE
        layFuelOpts.visibility = if (entry.fuelEnabled)   View.VISIBLE else View.GONE

        row.findViewById<RadioGroup>(R.id.rgBatDisplay).check(batDisplayToRadioId(entry.batteryDisplay))
        row.findViewById<RadioGroup>(R.id.rgBatSize).check(sizeToRadioId(entry.batterySizeSp, bat = true))

        row.findViewById<RadioGroup>(R.id.rgFuelDisplay).check(fuelDisplayToRadioId(entry.fuelDisplay))
        row.findViewById<RadioGroup>(R.id.rgFuelSize).check(sizeToRadioId(entry.fuelSizeSp, bat = false))

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
        cbCombine.setOnCheckedChangeListener { _, _ -> debouncedSave() }

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
            batteryDisplay = readBatDisplay(row),
            batterySizeSp  = readSize(row, bat = true),
            fuelEnabled    = row.findViewById<CheckBox>(R.id.cbFuel).isChecked,
            fuelDisplay    = readFuelDisplay(row),
            fuelSizeSp     = readSize(row, bat = false),
            combineLine    = row.findViewById<CheckBox>(R.id.cbCombine).isChecked
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

        // Перезапуск только если оверлей включён
        if (!OverlaySettings.isEnabled(this)) return

        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_START
            })
        }, 400)
    }

    // ── Маппинг ID ↔ размер ────────────────────────────────────────────────

    private fun rangeSizeToRadioId(sp: Int): Int = when (sp) {
        OverlayLine.SIZE_S  -> R.id.rgRangeS
        OverlayLine.SIZE_M  -> R.id.rgRangeM
        OverlayLine.SIZE_XL -> R.id.rgRangeXL
        else                -> R.id.rgRangeL   // SIZE_L — дефолт
    }

    private fun radioToRangeSize(id: Int): Int = when (id) {
        R.id.rgRangeS  -> OverlayLine.SIZE_S
        R.id.rgRangeM  -> OverlayLine.SIZE_M
        R.id.rgRangeXL -> OverlayLine.SIZE_XL
        else            -> OverlayLine.SIZE_L
    }

    private fun sizeToRadioId(sp: Int, bat: Boolean): Int = when (sp) {
        OverlayLine.SIZE_S  -> if (bat) R.id.rbBatS  else R.id.rbFuelS
        OverlayLine.SIZE_M  -> if (bat) R.id.rbBatM  else R.id.rbFuelM
        OverlayLine.SIZE_XL -> if (bat) R.id.rbBatXL else R.id.rbFuelXL
        else                -> if (bat) R.id.rbBatL  else R.id.rbFuelL   // SIZE_L
    }

    private fun readSize(row: View, bat: Boolean): Int {
        val (sId, mId, lId, xlId) = if (bat)
            listOf(R.id.rbBatS, R.id.rbBatM, R.id.rbBatL, R.id.rbBatXL)
        else
            listOf(R.id.rbFuelS, R.id.rbFuelM, R.id.rbFuelL, R.id.rbFuelXL)
        return when {
            row.findViewById<RadioButton>(sId) .isChecked -> OverlayLine.SIZE_S
            row.findViewById<RadioButton>(mId) .isChecked -> OverlayLine.SIZE_M
            row.findViewById<RadioButton>(xlId).isChecked -> OverlayLine.SIZE_XL
            else                                          -> OverlayLine.SIZE_L
        }
    }

    // Батарея: км | %
    private fun batDisplayToRadioId(d: OverlayLineDisplay): Int = when (d) {
        OverlayLineDisplay.KM      -> R.id.rbBatKm
        else                       -> R.id.rbBatPercent
    }
    private fun readBatDisplay(row: View): OverlayLineDisplay =
        if (row.findViewById<RadioButton>(R.id.rbBatKm).isChecked) OverlayLineDisplay.KM
        else OverlayLineDisplay.PERCENT

    // Топливо: км | л | %
    private fun fuelDisplayToRadioId(d: OverlayLineDisplay): Int = when (d) {
        OverlayLineDisplay.KM     -> R.id.rbFuelKm
        OverlayLineDisplay.LITERS -> R.id.rbFuelLiters
        else                      -> R.id.rbFuelPercent
    }
    private fun readFuelDisplay(row: View): OverlayLineDisplay = when {
        row.findViewById<RadioButton>(R.id.rbFuelKm)    .isChecked -> OverlayLineDisplay.KM
        row.findViewById<RadioButton>(R.id.rbFuelLiters).isChecked -> OverlayLineDisplay.LITERS
        else                                                        -> OverlayLineDisplay.PERCENT
    }
}
