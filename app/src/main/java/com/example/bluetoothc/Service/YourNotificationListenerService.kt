package com.example.bluetoothc.Service

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.example.bluetoothc.BLE.BLEManager
import com.example.bluetoothc.MyApp

class YourNotificationListenerService : NotificationListenerService() {
    private val bleManager: BLEManager by lazy {
        (application as MyApp).bleManager
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {

        sbn?.let {
            val notification = it.notification
            val extras = notification.extras

            val title = extras.getString("android.title")
            val text = extras.getCharSequence("android.text")

            Log.d("NotificationListener", "Title: $title")
            Log.d("NotificationListener", "Text: $text")

            val message = "$title: $text".toByteArray(Charsets.UTF_8)

            bleManager.sendNotificationData(message)

        }
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        // Handle notification removal if needed
    }
}