//package com.example.demoble
//
//import android.annotation.SuppressLint
//import android.app.Service
//import android.bluetooth.BluetoothGatt
//import android.bluetooth.BluetoothGattCallback
//import android.bluetooth.BluetoothGattCharacteristic
//import android.bluetooth.BluetoothProfile
//import android.content.Intent
//import android.util.Log
//import java.util.*
//
//private val TAG = BluetoothLeService::class.java.simpleName
//private const val STATE_DISCONNECTED = 0
//private const val STATE_CONNECTING = 1
//private const val STATE_CONNECTED = 2
//const val ACTION_GATT_CONNECTED = "com.example.demoble.ACTION_GATT_CONNECTED"
//const val ACTION_GATT_DISCONNECTED = "com.example.demoble.ACTION_GATT_DISCONNECTED"
//const val ACTION_GATT_SERVICES_DISCOVERED =
//    "com.example.bluetooth.le.ACTION_GATT_SERVICES_DISCOVERED"
//const val ACTION_DATA_AVAILABLE = "com.example.demoble.ACTION_DATA_AVAILABLE"
//const val EXTRA_DATA = "com.example.demoble.EXTRA_DATA"
//val UUID_HEART_RATE_MEASUREMENT = UUID.fromString("")
//val SERVICE_DAtAUP_UUID = UUID.fromString("bd03fd36-3cb7-4e83-abdd-1debaf90c806")
//val CHARACTOR_DATAUP_UUID = UUID.fromString("cb100916-b274-47d4-b094-0372d72a3782")
//val SERVICE_NORMAL_UUID =  UUID.fromString("99a8e970-aa52-11ec-b909-0242ac120002")
//val CHARCTOR_NORMAL_UUID = UUID.fromString("f2b4d622-aa53-11ec-b909-0242ac120002")
//
//
//
//// A service that interacts with the BLE device via the Android BLE API.
//class BluetoothLeService(private var bluetoothGatt: BluetoothGatt?) : Service() {
//    private var connectionState = STATE_DISCONNECTED
//
//    // Various callback methods defined by the BLE API.
//    private val gattCallback = object : BluetoothGattCallback() {
//        @SuppressLint("MissingPermission")
//        override fun onConnectionStateChange(
//            gatt: BluetoothGatt,
//            status: Int,
//            newState: Int
//        ) {
//            val intentAction: String
//            when (newState) {
//                BluetoothProfile.STATE_CONNECTED -> {
//                    intentAction = ACTION_GATT_CONNECTED
//                    connectionState = STATE_CONNECTED
//                    broadcastUpdate(intentAction)
//                    Log.i(TAG, "Connected to GATT server.")
//                    Log.i(TAG, "Attempting to start service discovery: " +
//                            bluetoothGatt?.discoverServices())
//                }
//                BluetoothProfile.STATE_DISCONNECTED -> {
//                    intentAction = ACTION_GATT_DISCONNECTED
//                    connectionState = STATE_DISCONNECTED
//                    Log.i(TAG, "Disconnected from GATT server.")
//                    broadcastUpdate(intentAction)
//                }
//            }
//        }
//
//        // New services discovered
//        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
//            when (status) {
//                BluetoothGatt.GATT_SUCCESS -> broadcastUpdate(ACTION_GATT_SERVICES_DISCOVERED)
//                else -> Log.w(TAG, "onServicesDiscovered received: $status")
//            }
//        }
//
//        // Result of a characteristic read operation
//        override fun onCharacteristicRead(
//            gatt: BluetoothGatt,
//            characteristic: BluetoothGattCharacteristic,
//            status: Int
//        ) {
//            when (status) {
//                BluetoothGatt.GATT_SUCCESS -> {
//                    broadcastUpdate(ACTION_DATA_AVAILABLE, characteristic)
//                }
//            }
//        }
//    }
//
//
//    private fun broadcastUpdate(action: String) {
//        val intent = Intent(action)
//        sendBroadcast(intent)
//    }
//
//    private fun broadcastUpdate(action: String, characteristic: BluetoothGattCharacteristic) {
//        val intent = Intent(action)
//
//        // This is special handling for the Heart Rate Measurement profile. Data
//        // parsing is carried out as per profile specifications.
//        when (characteristic.uuid) {
//            UUID_HEART_RATE_MEASUREMENT -> {
//                val flag = characteristic.properties
//                val format = when (flag and 0x01) {
//                    0x01 -> {
//                        Log.d(TAG, "Heart rate format UINT16.")
//                        BluetoothGattCharacteristic.FORMAT_UINT16
//                    }
//                    else -> {
//                        Log.d(TAG, "Heart rate format UINT8.")
//                        BluetoothGattCharacteristic.FORMAT_UINT8
//                    }
//                }
//                val heartRate = characteristic.getIntValue(format, 1)
//                Log.d(TAG, String.format("Received heart rate: %d", heartRate))
//                intent.putExtra(EXTRA_DATA, (heartRate).toString())
//            }
//            else -> {
//                // For all other profiles, writes the data formatted in HEX.
//                val data: ByteArray? = characteristic.value
//                if (data?.isNotEmpty() == true) {
//                    val hexString: String = data.joinToString(separator = " ") {
//                        String.format("%02X", it)
//                    }
//                    intent.putExtra(EXTRA_DATA, "$data\n$hexString")
//                }
//            }
//
//        }
//        sendBroadcast(intent)
//    }
//
//}
