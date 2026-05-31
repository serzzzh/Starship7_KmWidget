package com.starship7.kmwidget

import android.content.ContentValues
import android.content.Context
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
        // Cleanup old data (keep last 7 days)
        db.execSQL("DELETE FROM range_log WHERE timestamp < ?", arrayOf((System.currentTimeMillis() - 7 * 24 * 3600 * 1000).toString()))
    }

    fun getStatsSinceMinutes(minutes: Int): Stats? {
        val db = readableDatabase
        val timeThreshold = System.currentTimeMillis() - minutes * 60 * 1000
        val cursor = db.rawQuery("SELECT MIN(odometer), MAX(odometer), MAX(battery), MIN(battery), MAX(fuel), MIN(fuel) FROM range_log WHERE timestamp >= ?", arrayOf(timeThreshold.toString()))
        
        if (cursor.moveToFirst()) {
            val minOdo = cursor.getFloat(0)
            val maxOdo = cursor.getFloat(1)
            val maxBat = cursor.getFloat(2)
            val minBat = cursor.getFloat(3)
            val maxFuel = cursor.getFloat(4)
            val minFuel = cursor.getFloat(5)
            cursor.close()
            
            if (maxOdo > minOdo) {
                return Stats(maxOdo - minOdo, maxBat - minBat, maxFuel - minFuel)
            }
        }
        cursor.close()
        return null
    }

    fun getStatsSinceKm(km: Float): Stats? {
        val db = readableDatabase
        val cursorMax = db.rawQuery("SELECT MAX(odometer) FROM range_log", null)
        var currentOdo = 0f
        if (cursorMax.moveToFirst()) currentOdo = cursorMax.getFloat(0)
        cursorMax.close()

        val odoThreshold = currentOdo - km
        val cursor = db.rawQuery("SELECT MIN(odometer), MAX(odometer), MAX(battery), MIN(battery), MAX(fuel), MIN(fuel) FROM range_log WHERE odometer >= ?", arrayOf(odoThreshold.toString()))
        
        if (cursor.moveToFirst()) {
            val minOdo = cursor.getFloat(0)
            val maxOdo = cursor.getFloat(1)
            val maxBat = cursor.getFloat(2)
            val minBat = cursor.getFloat(3)
            val maxFuel = cursor.getFloat(4)
            val minFuel = cursor.getFloat(5)
            cursor.close()
            
            if (maxOdo > minOdo) {
                return Stats(maxOdo - minOdo, maxBat - minBat, maxFuel - minFuel)
            }
        }
        cursor.close()
        return null
    }

    data class Stats(val deltaOdo: Float, val deltaBat: Float, val deltaFuel: Float)
}
