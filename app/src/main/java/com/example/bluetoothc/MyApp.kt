package com.example.bluetoothc

import android.app.Application
import com.example.bluetoothc.BLE.BLEManager

class MyApp : Application() {
    lateinit var bleManager: BLEManager

    override fun onCreate() {
        super.onCreate()
        bleManager = BLEManager(this)
    }
}