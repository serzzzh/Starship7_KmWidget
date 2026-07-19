package com.starship7.kmwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup
import android.widget.TextView
import android.widget.Toast

class WidgetActivity : Activity() {

    private var shortcutMode = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        shortcutMode = intent.action == Intent.ACTION_CREATE_SHORTCUT

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val editValue = findViewById<EditText>(R.id.editValue)
        val btnAddWidget = findViewById<Button>(R.id.btnAddWidget)
        val btnRefresh = findViewById<Button>(R.id.btnRefresh)

        val config = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        if (config.isTimeBased) {
            radioGroup.check(R.id.radioTime)
            editValue.setText(config.timeValue.toString())
        } else {
            radioGroup.check(R.id.radioKm)
            editValue.setText(config.kmValue.toString())
        }

        btnAddWidget.setOnClickListener {
            saveDefaultConfig(radioGroup, editValue)
            if (shortcutMode) {
                returnShortcutResult(AppWidgetManager.INVALID_APPWIDGET_ID)
            } else {
                openFlymeWidgetPicker()
            }
        }

        btnRefresh.setOnClickListener {
            saveDefaultConfig(radioGroup, editValue)
            refreshPreview()
            updateExistingWidgets()
        }

        refreshPreview()
        updateStatusText()
    }

    override fun onResume() {
        super.onResume()
        updateStatusText()
    }

    private fun saveDefaultConfig(radioGroup: RadioGroup, editValue: EditText) {
        val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
        val value = editValue.text.toString().toFloatOrNull() ?: 30f
        WidgetPreferences.save(
            this,
            WidgetPreferences.DEFAULT_WIDGET_ID,
            WidgetConfig(
                isTimeBased = isTimeBased,
                timeValue = value.toInt(),
                kmValue = value
            )
        )
    }

    private fun refreshPreview() {
        val result = RangeCalculator.calculate(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        findViewById<TextView>(R.id.textRange).text = result.rangeText
        findViewById<TextView>(R.id.textInfo).text = result.infoText
    }

    private fun updateStatusText() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, RangeWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        val statusView = findViewById<TextView>(R.id.textStatus)
        statusView.text = if (widgetIds.isEmpty()) {
            getString(R.string.widget_status_none)
        } else {
            getString(R.string.widget_status_count, widgetIds.size)
        }
    }

    private fun updateExistingWidgets() {
        val intent = Intent(this, RangeUpdateService::class.java)
        startForegroundService(intent)

        val appWidgetManager = AppWidgetManager.getInstance(this)
        val componentName = ComponentName(this, RangeWidgetProvider::class.java)
        val widgetIds = appWidgetManager.getAppWidgetIds(componentName)
        if (widgetIds.isEmpty()) return

        val updateIntent = Intent(this, RangeWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, widgetIds)
        }
        sendBroadcast(updateIntent)
        updateStatusText()
    }

    private fun openFlymeWidgetPicker() {
        val opened = FlymeWidgetHelper.openWidgetPicker(this)
        FlymeWidgetHelper.installHomeShortcut(this)
        Toast.makeText(
            this,
            if (opened) R.string.widget_picker_opened else R.string.widget_bind_failed,
            Toast.LENGTH_LONG
        ).show()
    }

    private fun returnShortcutResult(appWidgetId: Int) {
        val shortcutIntent = Intent(this, WidgetActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val result = Intent().apply {
            putExtra(Intent.EXTRA_SHORTCUT_INTENT, shortcutIntent)
            putExtra(Intent.EXTRA_SHORTCUT_NAME, getString(R.string.widget_shortcut_name))
            putExtra(
                Intent.EXTRA_SHORTCUT_ICON_RESOURCE,
                Intent.ShortcutIconResource.fromContext(this@WidgetActivity, R.mipmap.ic_launcher)
            )
        }
        setResult(RESULT_OK, result)
        finish()
    }
}
