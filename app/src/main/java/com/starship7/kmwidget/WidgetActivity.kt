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
    private lateinit var btnToggleOverlay: Button

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        linesContainer   = findViewById(R.id.linesContainer)
        radioGroup       = findViewById(R.id.radioGroup)
        editValue        = findViewById(R.id.editValue)
        btnToggleOverlay = findViewById(R.id.btnToggleOverlay)

        val config    = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        val entries   = OverlaySettings.loadEntries(this)

        radioGroup.check(if (config.isTimeBased) R.id.radioTime else R.id.radioKm)
        editValue.setText(if (config.isTimeBased) config.timeValue.toString() else config.kmValue.toString())

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

        val checkboxesContainer = row.findViewById<LinearLayout>(R.id.checkboxesContainer)
        val cbText      = row.findViewById<CheckBox>(R.id.cbText)
        val cbSum       = row.findViewById<CheckBox>(R.id.cbSum)
        val cbBattery   = row.findViewById<CheckBox>(R.id.cbBattery)
        val cbFuel      = row.findViewById<CheckBox>(R.id.cbFuel)
        val cbMode      = row.findViewById<CheckBox>(R.id.cbMode)

        val optionsContainer = row.findViewById<LinearLayout>(R.id.optionsContainer)
        val layTextOpts = row.findViewById<LinearLayout>(R.id.layTextOptions)
        val laySumOpts  = row.findViewById<LinearLayout>(R.id.laySumOptions)
        val layBatOpts  = row.findViewById<LinearLayout>(R.id.layBatOptions)
        val layFuelOpts = row.findViewById<LinearLayout>(R.id.layFuelOptions)
        val layModeOpts = row.findViewById<LinearLayout>(R.id.layModeOptions)

        val etTextValue = row.findViewById<EditText>(R.id.etTextValue)

        // Setup Spinners
        val spinTextSize = row.findViewById<android.widget.Spinner>(R.id.spinTextSize)
        val spinSumSize  = row.findViewById<android.widget.Spinner>(R.id.spinSumSize)
        val spinBatSize  = row.findViewById<android.widget.Spinner>(R.id.spinBatSize)
        val spinFuelSize = row.findViewById<android.widget.Spinner>(R.id.spinFuelSize)
        val spinModeSize = row.findViewById<android.widget.Spinner>(R.id.spinModeSize)

        val sizeAdapter = android.widget.ArrayAdapter(this, android.R.layout.simple_spinner_dropdown_item, arrayOf("S", "M", "L", "XL"))
        spinTextSize.adapter = sizeAdapter; spinSumSize.adapter = sizeAdapter
        spinBatSize.adapter = sizeAdapter; spinFuelSize.adapter = sizeAdapter; spinModeSize.adapter = sizeAdapter

        fun sizeToIndex(sp: Int) = when(sp) { OverlayLine.SIZE_S->0; OverlayLine.SIZE_M->1; OverlayLine.SIZE_XL->3; else->2 }
        fun indexToSize(idx: Int) = when(idx) { 0->OverlayLine.SIZE_S; 1->OverlayLine.SIZE_M; 3->OverlayLine.SIZE_XL; else->OverlayLine.SIZE_L }

        // Track current order
        val currentOrder = entry.renderOrder.toMutableList()

        fun updateContainers() {
            // Reorder Checkboxes to match order of selection, placing unselected at the end
            checkboxesContainer.removeAllViews()
            optionsContainer.removeAllViews()

            val allKeys = listOf("TEXT", "SUM", "BATTERY", "FUEL", "MODE")
            val orderedKeys = currentOrder + allKeys.filter { it !in currentOrder }

            orderedKeys.forEach { key ->
                when (key) {
                    "TEXT" -> { checkboxesContainer.addView(cbText); optionsContainer.addView(layTextOpts) }
                    "SUM" -> { checkboxesContainer.addView(cbSum); optionsContainer.addView(laySumOpts) }
                    "BATTERY" -> { checkboxesContainer.addView(cbBattery); optionsContainer.addView(layBatOpts) }
                    "FUEL" -> { checkboxesContainer.addView(cbFuel); optionsContainer.addView(layFuelOpts) }
                    "MODE" -> { checkboxesContainer.addView(cbMode); optionsContainer.addView(layModeOpts) }
                }
            }

            layTextOpts.visibility = if ("TEXT" in currentOrder) View.VISIBLE else View.GONE
            laySumOpts.visibility  = if ("SUM" in currentOrder) View.VISIBLE else View.GONE
            layBatOpts.visibility  = if ("BATTERY" in currentOrder) View.VISIBLE else View.GONE
            layFuelOpts.visibility = if ("FUEL" in currentOrder) View.VISIBLE else View.GONE
            layModeOpts.visibility = if ("MODE" in currentOrder) View.VISIBLE else View.GONE
        }

        // Apply initial values
        cbText.isChecked    = entry.textEnabled
        cbSum.isChecked     = entry.sumEnabled
        cbBattery.isChecked = entry.batteryEnabled
        cbFuel.isChecked    = entry.fuelEnabled
        cbMode.isChecked    = entry.modeEnabled

        etTextValue.setText(entry.textValue)

        spinTextSize.setSelection(sizeToIndex(entry.textSizeSp))
        spinSumSize.setSelection(sizeToIndex(entry.sumSizeSp))
        spinBatSize.setSelection(sizeToIndex(entry.batterySizeSp))
        spinFuelSize.setSelection(sizeToIndex(entry.fuelSizeSp))
        spinModeSize.setSelection(sizeToIndex(entry.modeSizeSp))

        row.findViewById<RadioGroup>(R.id.rgBatDisplay).check(batDisplayToRadioId(entry.batteryDisplay))
        row.findViewById<RadioGroup>(R.id.rgFuelDisplay).check(fuelDisplayToRadioId(entry.fuelDisplay))

        updateContainers()

        // Listeners
        val toggleOpts = CompoundButton.OnCheckedChangeListener { btn, checked ->
            val key = when (btn.id) { R.id.cbText->"TEXT"; R.id.cbSum->"SUM"; R.id.cbBattery->"BATTERY"; R.id.cbMode->"MODE"; else->"FUEL" }
            if (checked && key !in currentOrder) {
                currentOrder.add(key)
            } else if (!checked) {
                currentOrder.remove(key)
            }
            updateContainers()
            debouncedSave()
        }
        cbText.setOnCheckedChangeListener(toggleOpts)
        cbSum.setOnCheckedChangeListener(toggleOpts)
        cbBattery.setOnCheckedChangeListener(toggleOpts)
        cbFuel.setOnCheckedChangeListener(toggleOpts)
        cbMode.setOnCheckedChangeListener(toggleOpts)

        etTextValue.addTextChangedListener(object : android.text.TextWatcher {
            override fun afterTextChanged(s: android.text.Editable?) = debouncedSave()
            override fun beforeTextChanged(s: CharSequence?, st: Int, c: Int, a: Int) {}
            override fun onTextChanged(s: CharSequence?, st: Int, b: Int, c: Int) {}
        })

        // OnItemSelectedListener for Spinners
        val spinnerListener = object : android.widget.AdapterView.OnItemSelectedListener {
            override fun onItemSelected(parent: android.widget.AdapterView<*>?, view: View?, position: Int, id: Long) = debouncedSave()
            override fun onNothingSelected(parent: android.widget.AdapterView<*>?) {}
        }
        spinTextSize.onItemSelectedListener = spinnerListener
        spinSumSize.onItemSelectedListener = spinnerListener
        spinBatSize.onItemSelectedListener = spinnerListener
        spinFuelSize.onItemSelectedListener = spinnerListener
        spinModeSize.onItemSelectedListener = spinnerListener

        listOf(R.id.rgBatDisplay, R.id.rgFuelDisplay).forEach { id ->
            row.findViewById<RadioGroup>(id).setOnCheckedChangeListener { _, _ -> debouncedSave() }
        }

        row.findViewById<Button>(R.id.btnDeleteEntry).setOnClickListener {
            entryViews.remove(row)
            linesContainer.removeView(row)
            debouncedSave()
        }

        row.tag = currentOrder // save reference to order list
        entryViews.add(row)
        linesContainer.addView(row)
    }

    private fun readEntries(): List<OverlayEntry> = entryViews.map { row ->
        val currentOrder = row.tag as MutableList<String>
        fun indexToSize(idx: Int) = when(idx) { 0->OverlayLine.SIZE_S; 1->OverlayLine.SIZE_M; 3->OverlayLine.SIZE_XL; else->OverlayLine.SIZE_L }
        
        OverlayEntry(
            renderOrder    = currentOrder.toList(),
            textEnabled    = row.findViewById<CheckBox>(R.id.cbText).isChecked,
            textValue      = row.findViewById<EditText>(R.id.etTextValue).text.toString(),
            textSizeSp     = indexToSize(row.findViewById<android.widget.Spinner>(R.id.spinTextSize).selectedItemPosition),

            sumEnabled     = row.findViewById<CheckBox>(R.id.cbSum).isChecked,
            sumSizeSp      = indexToSize(row.findViewById<android.widget.Spinner>(R.id.spinSumSize).selectedItemPosition),

            batteryEnabled = row.findViewById<CheckBox>(R.id.cbBattery).isChecked,
            batteryDisplay = readBatDisplay(row),
            batterySizeSp  = indexToSize(row.findViewById<android.widget.Spinner>(R.id.spinBatSize).selectedItemPosition),

            fuelEnabled    = row.findViewById<CheckBox>(R.id.cbFuel).isChecked,
            fuelDisplay    = readFuelDisplay(row),
            fuelSizeSp     = indexToSize(row.findViewById<android.widget.Spinner>(R.id.spinFuelSize).selectedItemPosition),

            modeEnabled    = row.findViewById<CheckBox>(R.id.cbMode).isChecked,
            modeSizeSp     = indexToSize(row.findViewById<android.widget.Spinner>(R.id.spinModeSize).selectedItemPosition)
        )
    }

    private fun commitAndRestart() {
        val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
        val value = editValue.text.toString().toFloatOrNull() ?: 30f
        WidgetPreferences.save(this, WidgetPreferences.DEFAULT_WIDGET_ID, WidgetConfig(isTimeBased, value.toInt(), value))

        OverlaySettings.saveEntries(this, readEntries())

        if (!OverlaySettings.isEnabled(this)) return

        startService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_STOP })
        Handler(Looper.getMainLooper()).postDelayed({
            startForegroundService(Intent(this, OverlayService::class.java).apply { action = OverlayService.ACTION_START })
        }, 400)
    }

    private fun batDisplayToRadioId(d: OverlayLineDisplay) = if (d == OverlayLineDisplay.KM) R.id.rbBatKm else R.id.rbBatPercent
    private fun readBatDisplay(row: View) = if (row.findViewById<RadioGroup>(R.id.rgBatDisplay).checkedRadioButtonId == R.id.rbBatKm) OverlayLineDisplay.KM else OverlayLineDisplay.PERCENT

    private fun fuelDisplayToRadioId(d: OverlayLineDisplay) = when (d) { OverlayLineDisplay.KM -> R.id.rbFuelKm; OverlayLineDisplay.LITERS -> R.id.rbFuelLiters; else -> R.id.rbFuelPercent }
    private fun readFuelDisplay(row: View) = when (row.findViewById<RadioGroup>(R.id.rgFuelDisplay).checkedRadioButtonId) { R.id.rbFuelKm -> OverlayLineDisplay.KM; R.id.rbFuelLiters -> OverlayLineDisplay.LITERS; else -> OverlayLineDisplay.PERCENT }
}