package com.starship7.kmwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioGroup

class ConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val editValue = findViewById<EditText>(R.id.editValue)
        val btnSave = findViewById<Button>(R.id.btnSave)

        val config = WidgetPreferences.load(this, appWidgetId)
        if (config.isTimeBased) {
            radioGroup.check(R.id.radioTime)
            editValue.setText(config.timeValue.toString())
        } else {
            radioGroup.check(R.id.radioKm)
            editValue.setText(config.kmValue.toString())
        }

        btnSave.setOnClickListener {
            val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
            val value = editValue.text.toString().toFloatOrNull() ?: 30f

            WidgetPreferences.save(
                this,
                appWidgetId,
                WidgetConfig(
                    isTimeBased = isTimeBased,
                    timeValue = value.toInt(),
                    kmValue = value
                )
            )

            val updateIntent = Intent(this, RangeWidgetProvider::class.java).apply {
                action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
                putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            }
            sendBroadcast(updateIntent)

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}
