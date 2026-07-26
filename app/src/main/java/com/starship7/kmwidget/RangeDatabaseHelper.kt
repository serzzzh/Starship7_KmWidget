package com.starship7.kmwidget

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RangeDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "RangeData.db", null, 1) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE range_log (timestamp INTEGER, odometer REAL, battery REAL, fuel REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS range_log")
        onCreate(db)
    }

    fun insertLog(timestamp: Long, odometer: Float, battery: Float, fuel: Float) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("odometer", odometer)
            put("battery", battery)
            put("fuel", fuel)
        }
        db.insert("range_log", null, values)
        // Храним данные за последние 7 дней для долгосрочной статистики
        db.execSQL("DELETE FROM range_log WHERE timestamp < ?", arrayOf((System.currentTimeMillis() - 7 * 24 * 3600 * 1000L).toString()))
    }

    data class EfficiencyData(
        var evKm: Float = 0f,
        var evBatDrop: Float = 0f,
        var fuelKm: Float = 0f,
        var fuelDrop: Float = 0f
    ) {
        // Сколько километров проезжаем на 1% батареи
        val evKmPerPct: Float get() = if (evBatDrop > 0) evKm / evBatDrop else 0f
        
        // Сколько километров проезжаем на 1% бака
        val fuelKmPerPct: Float get() = if (fuelDrop > 0) fuelKm / fuelDrop else 0f
        
        // Считаем данные валидными, если потратили хотя бы N% ресурса
        fun isValidEv(minBatDrop: Float = 1f) = evBatDrop >= minBatDrop
        fun isValidFuel(minFuelDrop: Float = 0.5f) = fuelDrop >= minFuelDrop
    }

    fun getEfficiencySinceTime(timestampThreshold: Long): EfficiencyData {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT timestamp, odometer, battery, fuel FROM range_log WHERE timestamp >= ? ORDER BY timestamp ASC", arrayOf(timestampThreshold.toString()))
        return processCursorToEfficiency(cursor)
    }

    fun getEfficiencySinceKm(kmThreshold: Float): EfficiencyData {
        val db = readableDatabase
        val cursorMax = db.rawQuery("SELECT MAX(odometer) FROM range_log", null)
        var maxOdo = 0f
        if (cursorMax.moveToFirst()) maxOdo = cursorMax.getFloat(0)
        cursorMax.close()

        val odoThreshold = maxOdo - kmThreshold
        val cursor = db.rawQuery("SELECT timestamp, odometer, battery, fuel FROM range_log WHERE odometer >= ? ORDER BY timestamp ASC", arrayOf(odoThreshold.toString()))
        return processCursorToEfficiency(cursor)
    }

    private fun processCursorToEfficiency(cursor: Cursor): EfficiencyData {
        val eff = EfficiencyData()
        if (!cursor.moveToFirst()) {
            cursor.close()
            return eff
        }

        var prevOdo = cursor.getFloat(1)
        var prevBat = cursor.getFloat(2)
        var prevFuel = cursor.getFloat(3)

        while (cursor.moveToNext()) {
            val currOdo = cursor.getFloat(1)
            val currBat = cursor.getFloat(2)
            val currFuel = cursor.getFloat(3)

            val dOdo = currOdo - prevOdo
            val dBat = prevBat - currBat // Положительно, если потратили батарею
            val dFuel = prevFuel - currFuel // Положительно, если потратили бензин

            // Защита от аномалий (перескоки одометра более 100 км между точками)
            if (dOdo > 0 && dOdo < 100) {
                // Если бензин падает -> работает ДВС (HEV режим)
                // Считаем этот пробег как гибридный (на бензине)
                if (dFuel > 0 && dFuel < 20) {
                    eff.fuelKm += dOdo
                    eff.fuelDrop += dFuel
                } 
                // Если батарея падает, а бензин не падает -> чистый EV режим
                else if (dBat > 0 && dBat < 20 && dFuel <= 0) {
                    eff.evKm += dOdo
                    eff.evBatDrop += dBat
                }
            }

            prevOdo = currOdo
            prevBat = currBat
            prevFuel = currFuel
        }
        cursor.close()
        return eff
    }
}
