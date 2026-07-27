package com.starship7.kmwidget

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

class RangeDatabaseHelper(context: Context) : SQLiteOpenHelper(context, "RangeData.db", null, 2) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL("CREATE TABLE range_log (timestamp INTEGER, odometer REAL, ev_odo REAL, fuel_odo REAL, battery REAL, fuel REAL)")
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        db.execSQL("DROP TABLE IF EXISTS range_log")
        onCreate(db)
    }

    fun insertLog(timestamp: Long, odometer: Float, evOdo: Float, fuelOdo: Float, battery: Float, fuel: Float) {
        val db = writableDatabase
        val values = ContentValues().apply {
            put("timestamp", timestamp)
            put("odometer", odometer)
            put("ev_odo", evOdo)
            put("fuel_odo", fuelOdo)
            put("battery", battery)
            put("fuel", fuel)
        }
        db.insert("range_log", null, values)
        db.execSQL("DELETE FROM range_log WHERE timestamp < ?", arrayOf((System.currentTimeMillis() - 7 * 24 * 3600 * 1000L).toString()))
    }

    data class EfficiencyData(
        var evKm: Float = 0f,
        var evBatDrop: Float = 0f,
        var fuelKm: Float = 0f,
        var fuelDrop: Float = 0f
    ) {
        val evKmPerPct: Float get() = if (evBatDrop > 0) evKm / evBatDrop else 0f
        val fuelKmPerPct: Float get() = if (fuelDrop > 0) fuelKm / fuelDrop else 0f
        
        fun isValidEv(minBatDrop: Float = 1f) = evBatDrop >= minBatDrop
        fun isValidFuel(minFuelDrop: Float = 0.5f) = fuelDrop >= minFuelDrop
    }

    fun getEfficiencySinceTime(timestampThreshold: Long): EfficiencyData {
        val db = readableDatabase
        val cursor = db.rawQuery("SELECT timestamp, odometer, battery, fuel, ev_odo, fuel_odo FROM range_log WHERE timestamp >= ? ORDER BY timestamp ASC", arrayOf(timestampThreshold.toString()))
        return processSegments(cursor)
    }

    fun getEfficiencySinceKm(kmThreshold: Float): EfficiencyData {
        val db = readableDatabase
        val cursorMax = db.rawQuery("SELECT MAX(odometer) FROM range_log", null)
        var maxOdo = 0f
        if (cursorMax.moveToFirst()) maxOdo = cursorMax.getFloat(0)
        cursorMax.close()

        val odoThreshold = maxOdo - kmThreshold
        val cursor = db.rawQuery("SELECT timestamp, odometer, battery, fuel, ev_odo, fuel_odo FROM range_log WHERE odometer >= ? ORDER BY timestamp ASC", arrayOf(odoThreshold.toString()))
        return processSegments(cursor)
    }

    private fun processSegments(cursor: Cursor): EfficiencyData {
        val eff = EfficiencyData()
        if (!cursor.moveToFirst()) {
            cursor.close()
            return eff
        }

        var segStartEvOdo = cursor.getFloat(4)
        var segStartFuelOdo = cursor.getFloat(5)
        var segStartBat = cursor.getFloat(2)
        var segStartFuel = cursor.getFloat(3)

        var prevEvOdo = segStartEvOdo
        var prevFuelOdo = segStartFuelOdo
        var prevBat = segStartBat
        var prevFuel = segStartFuel

        while (cursor.moveToNext()) {
            val currEvOdo = cursor.getFloat(4)
            val currFuelOdo = cursor.getFloat(5)
            val currBat = cursor.getFloat(2)
            val currFuel = cursor.getFloat(3)

            // Если машина зарядилась или заправилась, либо сбросился одометр -> завершаем сегмент
            val batIncreased = currBat > prevBat + 0.5f
            val fuelIncreased = currFuel > prevFuel + 0.5f
            val odoReset = currEvOdo < prevEvOdo || currFuelOdo < prevFuelOdo

            if (batIncreased || fuelIncreased || odoReset) {
                // Фиксируем накопленный сегмент
                commitSegment(eff, segStartEvOdo, prevEvOdo, segStartBat, prevBat, segStartFuelOdo, prevFuelOdo, segStartFuel, prevFuel)
                
                // Начинаем новый сегмент
                segStartEvOdo = currEvOdo
                segStartFuelOdo = currFuelOdo
                segStartBat = currBat
                segStartFuel = currFuel
            }

            prevEvOdo = currEvOdo
            prevFuelOdo = currFuelOdo
            prevBat = currBat
            prevFuel = currFuel
        }

        // Фиксируем последний незаконченный сегмент
        commitSegment(eff, segStartEvOdo, prevEvOdo, segStartBat, prevBat, segStartFuelOdo, prevFuelOdo, segStartFuel, prevFuel)
        
        cursor.close()
        return eff
    }

    private fun commitSegment(eff: EfficiencyData, startEvOdo: Float, endEvOdo: Float, startBat: Float, endBat: Float, 
                              startFuelOdo: Float, endFuelOdo: Float, startFuel: Float, endFuel: Float) {
        val dEvOdo = endEvOdo - startEvOdo
        val dBat = startBat - endBat

        val dFuelOdo = endFuelOdo - startFuelOdo
        val dFuel = startFuel - endFuel

        if (dEvOdo > 0 && dEvOdo < 1000 && dBat > 0) {
            eff.evKm += dEvOdo
            eff.evBatDrop += dBat
        }
        
        if (dFuelOdo > 0 && dFuelOdo < 1000 && dFuel > 0) {
            eff.fuelKm += dFuelOdo
            eff.fuelDrop += dFuel
        }
    }
}
