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
import android.widget.RadioGroup

class WidgetActivity : Activity() {

    private lateinit var linesContainer: LinearLayout
    private val entryViews = mutableListOf<View>()

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

        val config    = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val rangeSize = OverlaySettings.getRangeSize(this)
        val entries   = OverlaySettings.loadEntries(this)

        radioGroup.check(if (config.isTimeBased) R.id.radioTime else R.id.radioKm)
        editValue.setText(if (config.isTimeBased) config.timeValue.toString() else config.kmValue.toString())
        rgRange.check(rangeSizeToRadioId(rangeSize))

        for (entry in entries) addEntryRow(entry)

        updateToggleButton()
        btnToggleOverlay.setOnClickListener {
            if (OverlaySettings.isEnabled(this)) {
                startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
            } else {
                startForegroundService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
            }
            updateToggleButton()
        }

        radioGroup.setOnCheckedChangeListener { _, _ -> debouncedSave() }
        rgRange.setOnCheckedChangeListener    { _, _ -> debouncedSave() }
        editValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = debouncedSave()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        findViewById<Button>(R.id.btnAddLine).setOnClickListener {
            addEntryRow(OverlayEntry())
            debouncedSave()
        }

        if (OverlaySettings.isEnabled(this)) {
            startForegroundService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
        }
    }

    override fun onDestroy() {
        saveHandler.removeCallbacks(saveRunnable)
        super.onDestroy()
    }

    private fun updateToggleButton() {
        btnToggleOverlay.text = if (OverlaySettings.isEnabled(this)) "Выключить" else "Включить"
    }

    private fun addEntryRow(entry: OverlayEntry) {
        val row = LayoutInflater.from(this).inflate(R.layout.line_item, linesContainer, false)

        val cbText      = row.findViewById<CheckBox>(R.id.cbText)
        val cbSum       = row.findViewById<CheckBox>(R.id.cbSum)
        val cbBattery   = row.findViewById<CheckBox>(R.id.cbBattery)
        val cbFuel      = row.findViewById<CheckBox>(R.id.cbFuel)

        val layTextOpts = row.findViewById<LinearLayout>(R.id.layTextOptions)
        val laySumOpts  = row.findViewById<LinearLayout>(R.id.laySumOptions)
        val layBatOpts  = row.findViewById<LinearLayout>(R.id.layBatOptions)
        val layFuelOpts = row.findViewById<LinearLayout>(R.id.layFuelOptions)

        val etTextValue = row.findViewById<EditText>(R.id.etTextValue)

        cbText.isChecked    = entry.textEnabled
        cbSum.isChecked     = entry.sumEnabled
        cbBattery.isChecked = entry.batteryEnabled
        cbFuel.isChecked    = entry.fuelEnabled

        etTextValue.setText(entry.textValue)

        layTextOpts.visibility = if (entry.textEnabled)    View.VISIBLE else View.GONE
        laySumOpts.visibility  = if (entry.sumEnabled)     View.VISIBLE else View.GONE
        layBatOpts.visibility  = if (entry.batteryEnabled) View.VISIBLE else View.GONE
        layFuelOpts.visibility = if (entry.fuelEnabled)    View.VISIBLE else View.GONE

        row.findViewById<RadioGroup>(R.id.rgTextSize).check(textToRadioId(entry.textSizeSp))
        row.findViewById<RadioGroup>(R.id.rgSumSize).check(sumToRadioId(entry.sumSizeSp))
        row.findViewById<RadioGroup>(R.id.rgBatDisplay).check(batDisplayToRadioId(entry.batteryDisplay))
        row.findViewById<RadioGroup>(R.id.rgBatSize).check(batToRadioId(entry.batterySizeSp))
        row.findViewById<RadioGroup>(R.id.rgFuelDisplay).check(fuelDisplayToRadioId(entry.fuelDisplay))
        row.findViewById<RadioGroup>(R.id.rgFuelSize).check(fuelToRadioId(entry.fuelSizeSp))

        val toggleOpts = CompoundButton.OnCheckedChangeListener { btn, checked ->
            when (btn.id) {
                R.id.cbText    -> layTextOpts.visibility = if (checked) View.VISIBLE else View.GONE
                R.id.cbSum     -> laySumOpts.visibility  = if (checked) View.VISIBLE else View.GONE
                R.id.cbBattery -> layBatOpts.visibility  = if (checked) View.VISIBLE else View.GONE
                R.id.cbFuel    -> layFuelOpts.visibility = if (checked) View.VISIBLE else View.GONE
            }
            debouncedSave()
        }
        cbText.setOnCheckedChangeListener(toggleOpts)
        cbSum.setOnCheckedChangeListener(toggleOpts)
        cbBattery.setOnCheckedChangeListener(toggleOpts)
        cbFuel.setOnCheckedChangeListener(toggleOpts)

        etTextValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = debouncedSave()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        listOf(R.id.rgTextSize, R.id.rgSumSize, R.id.rgBatDisplay, R.id.rgBatSize, R.id.rgFuelDisplay, R.id.rgFuelSize).forEach { id ->
            row.findViewById<RadioGroup>(id).setOnCheckedChangeListener { _, _ -> debouncedSave() }
        }

        row.findViewById<Button>(R.id.btnDeleteEntry).setOnClickListener {
            entryViews.remove(row)
            linesContainer.removeView(row)
            debouncedSave()
        }

        entryViews.add(row)
        linesContainer.addView(row)
    }

    private fun readEntries(): List<OverlayEntry> = entryViews.map { row ->
        OverlayEntry(
            textEnabled    = row.findViewById<CheckBox>(R.id.cbText).isChecked,
            textValue      = row.findViewById<EditText>(R.id.etTextValue).text.toString(),
            textSizeSp     = radioToSize(row.findViewById<RadioGroup>(R.id.rgTextSize).checkedRadioButtonId),
            sumEnabled     = row.findViewById<CheckBox>(R.id.cbSum).isChecked,
            sumSizeSp      = radioToSize(row.findViewById<RadioGroup>(R.id.rgSumSize).checkedRadioButtonId),
            batteryEnabled = row.findViewById<CheckBox>(R.id.cbBattery).isChecked,
            batteryDisplay = readBatDisplay(row),
            batterySizeSp  = radioToSize(row.findViewById<RadioGroup>(R.id.rgBatSize).checkedRadioButtonId),
            fuelEnabled    = row.findViewById<CheckBox>(R.id.cbFuel).isChecked,
            fuelDisplay    = readFuelDisplay(row),
            fuelSizeSp     = radioToSize(row.findViewById<RadioGroup>(R.id.rgFuelSize).checkedRadioButtonId)
        )
    }

    private fun commitAndRestart() {
        val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
        val value = editValue.text.toString().toFloatOrNull() ?: 30f
        WidgetPreferences.save(this, WidgetPreferences.DEFAULT_WIDGET_ID, WidgetConfig(isTimeBased, value.toInt(), value))

        OverlaySettings.saveRangeSize(this, radioToRangeSize(rgRange.checkedRadioButtonId))
        OverlaySettings.saveEntries(this, readEntries())

        if (!OverlaySettings.isEnabled(this)) return

        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
        }, 400)
    }

    private fun rangeSizeToRadioId(sp: Int): Int = when (sp) {
        OverlayLine.SIZE_S  -> R.id.rgRangeS
        OverlayLine.SIZE_M  -> R.id.rgRangeM
        OverlayLine.SIZE_XL -> R.id.rgRangeXL
        else                -> R.id.rgRangeL
    }
    private fun radioToRangeSize(id: Int): Int = when (id) {
        R.id.rgRangeS  -> OverlayLine.SIZE_S
        R.id.rgRangeM  -> OverlayLine.SIZE_M
        R.id.rgRangeXL -> OverlayLine.SIZE_XL
        else           -> OverlayLine.SIZE_L
    }

    private fun textToRadioId(sp: Int): Int = when (sp) { OverlayLine.SIZE_S -> R.id.rbTextS; OverlayLine.SIZE_L -> R.id.rbTextL; OverlayLine.SIZE_XL -> R.id.rbTextXL; else -> R.id.rbTextM }
    private fun sumToRadioId(sp: Int): Int = when (sp) { OverlayLine.SIZE_S -> R.id.rbSumS; OverlayLine.SIZE_L -> R.id.rbSumL; OverlayLine.SIZE_XL -> R.id.rbSumXL; else -> R.id.rbSumM }
    private fun batToRadioId(sp: Int): Int = when (sp) { OverlayLine.SIZE_S -> R.id.rbBatS; OverlayLine.SIZE_L -> R.id.rbBatL; OverlayLine.SIZE_XL -> R.id.rbBatXL; else -> R.id.rbBatM }
    private fun fuelToRadioId(sp: Int): Int = when (sp) { OverlayLine.SIZE_S -> R.id.rbFuelS; OverlayLine.SIZE_L -> R.id.rbFuelL; OverlayLine.SIZE_XL -> R.id.rbFuelXL; else -> R.id.rbFuelM }

    private fun radioToSize(id: Int): Int = when (id) {
        R.id.rbTextS, R.id.rbSumS, R.id.rbBatS, R.id.rbFuelS -> OverlayLine.SIZE_S
        R.id.rbTextL, R.id.rbSumL, R.id.rbBatL, R.id.rbFuelL -> OverlayLine.SIZE_L
        R.id.rbTextXL, R.id.rbSumXL, R.id.rbBatXL, R.id.rbFuelXL -> OverlayLine.SIZE_XL
        else -> OverlayLine.SIZE_M
    }

    private fun batDisplayToRadioId(d: OverlayLineDisplay) = if (d == OverlayLineDisplay.KM) R.id.rbBatKm else R.id.rbBatPercent
    private fun readBatDisplay(row: View) = if (row.findViewById<RadioGroup>(R.id.rgBatDisplay).checkedRadioButtonId == R.id.rbBatKm) OverlayLineDisplay.KM else OverlayLineDisplay.PERCENT

    private fun fuelDisplayToRadioId(d: OverlayLineDisplay) = when (d) { OverlayLineDisplay.KM -> R.id.rbFuelKm; OverlayLineDisplay.LITERS -> R.id.rbFuelLiters; else -> R.id.rbFuelPercent }
    private fun readFuelDisplay(row: View) = when (row.findViewById<RadioGroup>(R.id.rgFuelDisplay).checkedRadioButtonId) { R.id.rbFuelKm -> OverlayLineDisplay.KM; R.id.rbFuelLiters -> OverlayLineDisplay.LITERS; else -> OverlayLineDisplay.PERCENT }
}