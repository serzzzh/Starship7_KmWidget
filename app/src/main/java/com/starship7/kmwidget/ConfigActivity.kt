package com.starship7.kmwidget

import android.app.Activity
import android.appwidget.AppWidgetManager
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.RadioButton
import android.widget.RadioGroup

class ConfigActivity : Activity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_config)

        setResult(RESULT_CANCELED)

        val intent = intent
        val extras = intent.extras
        if (extras != null) {
            appWidgetId = extras.getInt(
                AppWidgetManager.EXTRA_APPWIDGET_ID,
                AppWidgetManager.INVALID_APPWIDGET_ID
            )
        }

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        val radioGroup = findViewById<RadioGroup>(R.id.radioGroup)
        val editValue = findViewById<EditText>(R.id.editValue)
        val btnSave = findViewById<Button>(R.id.btnSave)

        btnSave.setOnClickListener {
            val isTimeBased = radioGroup.checkedRadioButtonId == R.id.radioTime
            val valueStr = editValue.text.toString()
            val value = valueStr.toFloatOrNull() ?: 30f

            val prefs = getSharedPreferences("widget_$appWidgetId", Context.MODE_PRIVATE).edit()
            prefs.putBoolean("isTimeBased", isTimeBased)
            if (isTimeBased) {
                prefs.putInt("timeValue", value.toInt())
            } else {
                prefs.putFloat("kmValue", value)
            }
            prefs.apply()

            val appWidgetManager = AppWidgetManager.getInstance(this)
            val dbHelper = RangeDatabaseHelper(this)
            val vehicleHelper = com.starship7.kmwidget.car.VehiclePropertyHelper(this)
            
            // Trigger update immediately
            val updateIntent = Intent(this, RangeWidgetProvider::class.java)
            updateIntent.action = AppWidgetManager.ACTION_APPWIDGET_UPDATE
            updateIntent.putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, intArrayOf(appWidgetId))
            sendBroadcast(updateIntent)

            val resultValue = Intent()
            resultValue.putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId)
            setResult(RESULT_OK, resultValue)
            finish()
        }
    }
}
