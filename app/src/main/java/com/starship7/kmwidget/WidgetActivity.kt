package com.starship7.kmwidget

import android.app.Activity
import android.appwidget.AppWidgetHost
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

    private var pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
    private var shortcutMode = false
    private lateinit var appWidgetHost: AppWidgetHost

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_widget)

        shortcutMode = intent.action == Intent.ACTION_CREATE_SHORTCUT
        appWidgetHost = AppWidgetHost(this, APPWIDGET_HOST_ID)

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
            startAddWidgetFlow()
        }

        btnRefresh.setOnClickListener {
            saveDefaultConfig(radioGroup, editValue)
            refreshPreview()
            updateExistingWidgets()
        }

        refreshPreview()
        updateStatusText()
    }

    override fun onStart() {
        super.onStart()
        appWidgetHost.startListening()
    }

    override fun onStop() {
        super.onStop()
        appWidgetHost.stopListening()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == REQUEST_BIND_APPWIDGET) {
            if (resultCode == Activity.RESULT_OK && pendingWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
                finishAddWidget(pendingWidgetId)
            } else {
                appWidgetHost.deleteAppWidgetId(pendingWidgetId)
                pendingWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID
                if (!shortcutMode) {
                    Toast.makeText(this, R.string.widget_bind_failed, Toast.LENGTH_LONG).show()
                } else {
                    returnShortcutResult(AppWidgetManager.INVALID_APPWIDGET_ID)
                }
            }
        }
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

        val updateIntent = Intent(this, RangeWidgetProvider::class.java).apply {
            action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            putExtra(
                AppWidgetManager.EXTRA_APPWIDGET_IDS,
                AppWidgetManager.getInstance(this@WidgetActivity)
                    .getAppWidgetIds(ComponentName(this@WidgetActivity, RangeWidgetProvider::class.java))
            )
        }
        sendBroadcast(updateIntent)
        updateStatusText()
    }

    private fun startAddWidgetFlow() {
        val appWidgetManager = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, RangeWidgetProvider::class.java)

        if (!shortcutMode && appWidgetManager.isRequestPinAppWidgetSupported) {
            val successCallback = android.app.PendingIntent.getBroadcast(
                this,
                0,
                Intent(this, RangeWidgetProvider::class.java).apply {
                    action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                },
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or android.app.PendingIntent.FLAG_IMMUTABLE
            )
            appWidgetManager.requestPinAppWidget(provider, null, successCallback)
            return
        }

        pendingWidgetId = appWidgetHost.allocateAppWidgetId()
        if (appWidgetManager.bindAppWidgetIdIfAllowed(pendingWidgetId, provider)) {
            finishAddWidget(pendingWidgetId)
            return
        }

        val bindIntent = Intent(AppWidgetManager.ACTION_APPWIDGET_BIND).apply {
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, pendingWidgetId)
            putExtra(AppWidgetManager.EXTRA_APPWIDGET_PROVIDER, provider)
        }
        @Suppress("DEPRECATION")
        startActivityForResult(bindIntent, REQUEST_BIND_APPWIDGET)
    }

    private fun finishAddWidget(appWidgetId: Int) {
        copyDefaultConfigToWidget(appWidgetId)
        updateExistingWidgets()

        if (shortcutMode) {
            returnShortcutResult(appWidgetId)
            return
        }

        Toast.makeText(this, R.string.widget_shortcut_name, Toast.LENGTH_SHORT).show()
        updateStatusText()
    }

    private fun returnShortcutResult(appWidgetId: Int, launchIntent: Intent? = null) {
        val shortcutIntent = launchIntent ?: if (appWidgetId != AppWidgetManager.INVALID_APPWIDGET_ID) {
            Intent(AppWidgetManager.ACTION_APPWIDGET_CONFIGURE).apply {
                component = ComponentName(this@WidgetActivity, ConfigActivity::class.java)
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
        } else {
            Intent(this, WidgetActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            }
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

    private fun copyDefaultConfigToWidget(appWidgetId: Int) {
        val defaultConfig = WidgetPreferences.load(this, WidgetPreferences.DEFAULT_WIDGET_ID)
        WidgetPreferences.save(this, appWidgetId, defaultConfig)
    }

    companion object {
        private const val APPWIDGET_HOST_ID = 0x524B
        private const val REQUEST_BIND_APPWIDGET = 1001
    }
}
