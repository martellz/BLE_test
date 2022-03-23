package com.example.demoble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Column
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import java.lang.Exception
import java.util.*

class BleActivity : ComponentActivity() {
    companion object {
        private const val STATE_DISCONNECTED = 0
        private const val STATE_CONNECTING = 1
        private const val STATE_CONNECTED = 2
        private const val TAG = "BleDemo"
        private const val GATT_MAX_MTU_SIZE = 23
        private const val PERMISSION_REQUEST_COARSE_LOCATION = 1
        const val SERVICE_NORMAL_UUID =
            "99a8e970-aa52-11ec-b909-0242ac120002"  // "0000ff00-0000-1000-8000-00805f9b34fb"
        const val CHARACTER_NORMAL_UUID = "f2b4d622-aa53-11ec-b909-0242ac120002"
        const val CHARACTER_DATA_UP_UUID =
            "cb100916-b274-47d4-b094-0372d72a3782" // "0000ff01-0000-1000-8000-00805f9b34fb"

        const val BLUETOOTH_ADDRESS: String = "DeviceAddress"
    }

    private val bluetoothAddress =
        if (intent != null) intent.getStringExtra(BLUETOOTH_ADDRESS)!! else "EC:94:CB:7A:36:3A" // "A4:4B:D5:7C:76:D7" //
    private lateinit var bluetoothManage: BluetoothManager
    private lateinit var device: BluetoothDevice
    private var ourBluetoothGatt: BluetoothGatt? = null
    private var ourBluetoothGattService: BluetoothGattService? = null
    private var ourNormalCharacteristic: BluetoothGattCharacteristic? = null
    private var ourDataUpCharacteristic: BluetoothGattCharacteristic? = null
    private val permissionHelper = PermissionHelper(this)

    data class BleResult(val value: ByteArray?, val status: Int)

    private val channel = Channel<BleResult>()
    private val channelForRepeating = Channel<ByteArray>()
    private var previousPosition = byteArrayOf(0x00.toByte())

    private val gattCallback = object : BluetoothGattCallback() {
        @SuppressLint("MissingPermission")
        override fun onConnectionStateChange(
            gatt: BluetoothGatt?,
            status: Int,
            newState: Int
        ) {
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                // successfully connected to the GATT Server
                Log.d(TAG, "Connected to device")
                gatt?.discoverServices()
                ourBluetoothGatt = gatt

                // Call site
//                gatt?.requestMtu(GATT_MAX_MTU_SIZE)

            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                // disconnected from the GATT Server
                Log.d(TAG, "Disconnected from device")
                ourBluetoothGatt = null
            }
        }

        @SuppressLint("MissingPermission")
        override fun onServicesDiscovered(gatt: BluetoothGatt?, status: Int) {
            //super.onServicesDiscovered(gatt, status)
            if (status == BluetoothGatt.GATT_SUCCESS) {
                Log.d(TAG, "Found # services: " + gatt?.services?.size)
                gatt?.printGattTable()
                gatt?.services?.forEach {
                    Log.d(TAG, "" + it.uuid)
                    if (SERVICE_NORMAL_UUID == it.uuid.toString()) {
                        Log.d(TAG, "we found our service!")

                        val service = gatt.getService(it.uuid)
                        if (service == null) {
                            Log.e(TAG, "Did not get our service")
                        }
                        Log.d(TAG, "we got our service!")
                        ourBluetoothGattService = service

                        if (service != null) {
                            val charaNormal = service.getCharacteristic(
                                UUID.fromString(CHARACTER_NORMAL_UUID)
                            )

                            val charaDataUp = service.getCharacteristic(
                                UUID.fromString(CHARACTER_DATA_UP_UUID)
                            )
                            if (charaNormal != null) {
                                ourNormalCharacteristic = charaNormal
                                Log.d(TAG, "we got our normal chara: " + charaNormal.uuid)
                                Log.d(
                                    TAG,
                                    "isNotifiable: ${
                                        charaNormal.containsProperty(
                                            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                        )
                                    }"
                                )
                            } else {
                                Log.e(
                                    TAG,
                                    "Did not find our normal characteristic"
                                )
                            }

                            if (charaDataUp != null) {
                                ourDataUpCharacteristic = charaDataUp
                                gatt.setCharacteristicNotification(charaNormal, true)
                                Log.d(TAG, "we got our data up chara: " + charaNormal.uuid)
                                Log.d(
                                    TAG,
                                    "isNotifiable: ${
                                        charaNormal.containsProperty(
                                            BluetoothGattCharacteristic.PROPERTY_NOTIFY
                                        )
                                    }"
                                )
                            } else {
                                Log.e(
                                    TAG,
                                    "Did not find our data up characteristic"
                                )
                            }
                        } else {
                            Log.e(TAG, "Did not find our service")
                        }
                    }
                }
            } else {
                Log.e(TAG, "Failure while scan for services")
            }

        }

        override fun onCharacteristicRead(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            when (status) {
                BluetoothGatt.GATT_SUCCESS -> {
                    Log.d(
                        TAG,
                        "CharacteristicRead " + characteristic.uuid
                    )
                    val value = characteristic.value    // byte[]
                    Log.d(TAG, "Value=${value.toHexString()}")
                    if (characteristic.uuid == UUID.fromString(CHARACTER_NORMAL_UUID))
                        channel.trySend(BleResult(characteristic.value, status))
                }
                BluetoothGatt.GATT_READ_NOT_PERMITTED -> {
                    Log.e(
                        TAG,
                        "Read not permitted for $CHARACTER_NORMAL_UUID!"
                    )
                    if (characteristic.uuid == UUID.fromString(CHARACTER_NORMAL_UUID))
                        channel.trySend(BleResult(null, status))
                }
                else -> {
                    Log.e(
                        TAG,
                        "Characteristic read failed for $CHARACTER_NORMAL_UUID, error: $status"
                    )
                    if (characteristic.uuid == UUID.fromString(CHARACTER_NORMAL_UUID))
                        channel.trySend(BleResult(characteristic.value, status))
                }
            }
        }

        override fun onMtuChanged(gatt: BluetoothGatt, mtu: Int, status: Int) {
            super.onMtuChanged(gatt, mtu, status)
            Log.d(TAG, "MTU changed to $mtu")
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int
        ) {
            super.onCharacteristicWrite(gatt, characteristic, status)
            Log.d(TAG, "write characteristic")
            if (characteristic.uuid == UUID.fromString(CHARACTER_NORMAL_UUID))
                channel.trySend(BleResult(null, status))
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic
        ) {
            super.onCharacteristicChanged(gatt, characteristic)
            if (characteristic.uuid == UUID.fromString(CHARACTER_DATA_UP_UUID))
                channelForRepeating.trySend(characteristic.value)
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
//        device.connectGatt(this@BleActivity, false, leScanCallback)
        super.onCreate(savedInstanceState)

        Log.d(TAG, "Start Scanning");
        bluetoothManage = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        device = bluetoothManage.adapter.getRemoteDevice(bluetoothAddress)
//        val bluetoothLeScanner = bluetoothManage.adapter.bluetoothLeScanner
//        val scanFilter =
//            ScanFilter.Builder().setServiceUuid(ParcelUuid.fromString(SERVICE_UUID)).build()
//        val scanSettings =
//            ScanSettings.Builder().setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY).build()

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                if (permissionHelper.request(
                        Manifest.permission.BLUETOOTH_SCAN,
                        { _, onUserResponse ->
                            showRequestPermissionRationale(onUserResponse)
                        },
                        this@BleActivity
                    )
                    && permissionHelper.request(
                        Manifest.permission.BLUETOOTH_CONNECT,
                        { _, onUserResponse ->
                            showRequestPermissionRationale(onUserResponse)
                        },
                        this@BleActivity
                    )
                ) {
                    device.connectGatt(this@BleActivity, false, gattCallback)

                    delay(2000)

                    launch {
                        while (isActive) {
                            delay(20)
                            val position = receivingRepeatingPosition()
                            Log.d(
                                "BLEDemoCoroutine",
                                "position by notification: ${position.toHexString()}"
                            )
                        }
                    }

                    repeat(5) {
                        delay(10)
                        val readResult = ourBluetoothGatt?.readCharacteristic()
                        Log.d(
                            "BLEDemoCoroutine",
                            "value= ${readResult?.value?.toHexString()}, status=${readResult?.status}"
                        )
                        delay(10)
                        val writeResult = ourBluetoothGatt?.writeCharacteristic(
                            byteArrayOf(
                                0x11.toByte(),
                                0x12.toByte(),
                                0x13.toByte(),
                                0x14.toByte(),
                                0x15.toByte(),
                                0x16.toByte()
                            )
                        )

                        Log.d(
                            "BLEDemoCoroutine",
                            "write value= ${writeResult?.value}, status=${writeResult?.status}"
                        )
                    }
//                    delay(5000)

//                    delay(1000)

                    Log.d(TAG, "ending...")
                    ourBluetoothGatt?.disconnect()
                    ourBluetoothGatt?.close()
                    ourBluetoothGatt = null
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
            } finally {
                ourBluetoothGatt?.disconnect()
                ourBluetoothGatt?.close()
                ourBluetoothGatt = null
            }
        }


        setContent {
            Column() {
                Greeting2("Android")
            }
        }
    }

    private suspend fun waitForResult(): BleResult {
        return withTimeout(5000) {
            channel.receive()
        }
    }

    @OptIn(ExperimentalCoroutinesApi::class)
    private suspend fun receivingRepeatingPosition(): ByteArray {
        return if (channelForRepeating.isEmpty)
            previousPosition
        else
            withTimeout(5000) {
                channelForRepeating.receive()
            }
    }

    @SuppressLint("MissingPermission")
    private suspend fun BluetoothGatt.readCharacteristic(): BleResult? {
        val gatt = ourBluetoothGatt ?: return null
        val chara = ourNormalCharacteristic ?: return null

        readCharacteristic(chara)
        return waitForResult()
    }

    @SuppressLint("MissingPermission")
    private suspend fun BluetoothGatt.writeCharacteristic(payload: ByteArray): BleResult? {
        val gatt = ourBluetoothGatt ?: return null
        val chara = ourNormalCharacteristic ?: return null

        val writeType = when {
            chara.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            chara.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> error("not writable BLE device")
        }

        chara.writeType = writeType
        chara.value = payload
        gatt.writeCharacteristic(chara)

        return waitForResult()
    }

    @SuppressLint("MissingPermission")
    private fun readChara() {
        val gatt = ourBluetoothGatt ?: return
        val chara = ourNormalCharacteristic ?: return
        gatt.readCharacteristic(chara)
    }


    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                "printGattTable",
                "No service and characteristic available, call discoverServices() first?"
            )
            return
        }
        services.forEach { service ->
            val characteristicsTable = service.characteristics.joinToString(
                separator = "\n|--",
                prefix = "|--"
            ) { it.uuid.toString() }
            Log.d(
                TAG, "\nService ${service.uuid}\nCharacteristics:\n$characteristicsTable"
            )
        }
    }

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    private fun showRequestPermissionRationale(onUserResponse: (Boolean) -> Unit) {
        Choreographer.getInstance().postFrameCallback { onUserResponse(true) }
    }
}



