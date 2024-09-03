package com.example.bluetoothc.BLE

import android.Manifest
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothProfile
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import java.util.UUID
import android.app.Activity
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothGattService
import android.bluetooth.BluetoothManager
import android.bluetooth.le.BluetoothLeScanner
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.bluetooth.le.ScanSettings
import android.location.Address
import com.example.bluetoothc.AppUtils.Constants
import java.io.Serializable


class BLEManager(private val context: Context) {

    private var bluetoothGatt: BluetoothGatt? = null
    private var bluetoothAdapter: BluetoothAdapter
    private var bluetoothLeScanner: BluetoothLeScanner
    private var characteristicToWrite: BluetoothGattCharacteristic? = null
    private var mBuffer: ByteArray = ByteArray(0)
    private var mBufferPointer = 0
    private var RX_SERVICE_UUID: UUID? = null
    private var RX_CHAR_UUID: UUID? = null

    private val bleManager: BluetoothManager = context.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager

    init {
        bluetoothAdapter = bleManager.adapter
        bluetoothLeScanner = bluetoothAdapter.bluetoothLeScanner
    }

    private val bluetoothGattCallback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt?, status: Int, newState: Int) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                gatt?.discoverServices()
                gatt?.requestMtu(100)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // Handle disconnection
                broadcastUpdate(Constants.BLUETOOTH_EVENT_DISCONNECTED)
            } else {
                println("BLE profile $newState")
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {

            gatt?.services?.forEach { service ->
                Log.d("BLE", "Service UUID: ${service.uuid}")

                if (RX_SERVICE_UUID == null) {
                    // Assuming you want to set the first service's UUID
                    RX_SERVICE_UUID = service.uuid
                }

                service.characteristics.forEach { characteristic ->
                    Log.d("BLE", "Characteristic UUID: ${characteristic.uuid}")

                    if (RX_CHAR_UUID == null) {
                        // Assuming you want to set the first characteristic's UUID
                        RX_CHAR_UUID = characteristic.uuid
                    }
                }
            }

            characteristicToWrite = gatt?.getService(RX_SERVICE_UUID)?.getCharacteristic(RX_CHAR_UUID)
            broadcastUpdate(Constants.BLUETOOTH_EVENT_SERVICES_DISCOVERED)
        }

        override fun onCharacteristicWrite(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                continueWritingData(characteristic, gatt)
            } else {
                Log.e("BLEManager", "Characteristic write failed: $status")
            }
        }

        override fun onCharacteristicRead(gatt: BluetoothGatt?, characteristic: BluetoothGattCharacteristic?, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                val data = characteristic?.value
                data?.let { processReceivedData(it) }
            }
        }
    }

    private val scanCallback = object : ScanCallback() {
        override fun onScanResult(callbackType: Int, result: ScanResult?) {
            result?.device?.let {

                //if (it.name == null) return

                val device = it
                val deviceName = device.name ?: "Unnamed Device"
                val deviceAddress = device.address

                Log.d("BLE", "Device Name: $deviceName")
                Log.d("BLE", "Device Address: $deviceAddress")
                //if (it.name == "BSW004") {
                    //connectAddress(it.address)
                    connectToDevice(it)
                    //broadcastUpdate(Constants.BLUETOOTH_EVENT_DEVICE_CONNECTED, it.name)
                    bluetoothLeScanner.stopScan(this)
                //}
            }
        }
    }

    fun startScanning() {
        val scanSettings = ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()
        bluetoothLeScanner.startScan(null, scanSettings, scanCallback)
    }

    fun connectToDevice(device: BluetoothDevice) {
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(context as Activity, REQUIRED_PERMISSIONS, REQUEST_CODE_PERMISSIONS)
            return
        }
        try {
            bluetoothGatt = device.connectGatt(context, false, bluetoothGattCallback)
            stopScanning()
        } catch (exception: Exception) {
            // Log or handle the exception
            Log.e("BLE", "Failed to connect to device: ${device.address}", exception)
            // You can also show a user-friendly message or take other actions as needed
        }
    }

    fun connectAddress(address: String) {
        bluetoothAdapter.let { adapter ->
            try {
                val bluetoothDevice = adapter.getRemoteDevice(address)
                bluetoothGatt = bluetoothDevice.connectGatt(context,false,bluetoothGattCallback)
                stopScanning()
            } catch (e :Exception) {
                Log.d("deviceexc",e.message.toString())
            }
        }
    }

    fun disconnectDevice() {
        bluetoothGatt?.disconnect()
        bluetoothGatt?.close()
    }

    fun stopScanning() {
        scanCallback.let {
            bluetoothLeScanner.stopScan(it)
            //scanCallback = null // Clear the reference
        }
    }

    fun sendNotificationData(data: ByteArray) {
        mBuffer = data
        characteristicToWrite?.let {
            bluetoothGatt?.let { gatt -> writeDataToCharacteristic(it, gatt) }
        }
    }

    private fun continueWritingData(characteristic: BluetoothGattCharacteristic?, gatt: BluetoothGatt?) {
        if (mBuffer.size > mBufferPointer) {
            val chunk = mBuffer.copyOfRange(mBufferPointer, minOf(mBufferPointer + 20, mBuffer.size))
            characteristic?.value = chunk
            gatt?.writeCharacteristic(characteristic)
            mBufferPointer += chunk.size
        } else {
            broadcastUpdate(Constants.BLUETOOTH_EVENT_SENDING_COMPLETE)
            mBufferPointer = 0
        }
    }

    private fun writeDataToCharacteristic(characteristic: BluetoothGattCharacteristic, gatt: BluetoothGatt) {
        continueWritingData(characteristic, gatt)
    }

    private fun processReceivedData(data: ByteArray) {
        val dataString = String(data, Charsets.UTF_8)
        Log.d("BLEManager", "Received data: $dataString")
        broadcastUpdate(Constants.BLUETOOTH_EVENT_DATA_RECEIVED, dataString)
    }

    private fun broadcastUpdate(action: String) {
        val intent = Intent(action)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    private fun broadcastUpdate(action: String, data: String) {
        val intent = Intent(action)
        intent.putExtra(Constants.BLUETOOTH_EXTRA_DATA, data)
        LocalBroadcastManager.getInstance(context).sendBroadcast(intent)
    }

    companion object {
        private const val REQUEST_CODE_PERMISSIONS = 1
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH,
            Manifest.permission.BLUETOOTH_ADMIN,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.BLUETOOTH_SCAN,
            Manifest.permission.BLUETOOTH_CONNECT
        )
    }
}
