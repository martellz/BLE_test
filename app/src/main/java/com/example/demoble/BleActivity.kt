package com.example.demoble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.os.Bundle
import android.util.Log
import android.view.Choreographer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material.Button
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.beepiz.bluetooth.gattcoroutines.BGC
import com.beepiz.bluetooth.gattcoroutines.GattConnection
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import java.lang.Exception
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*

class BleActivity : ComponentActivity() {
    companion object {
        private const val TAG = "BleDemo"
        private const val GATT_MAX_MTU_SIZE = 23
        const val SERVICE_UUID =
            "99a8e970-aa52-11ec-b909-0242ac120002"

        //            "0000ff00-0000-1000-8000-00805f9b34fb"
        const val CHARACTER_NORMAL_UUID =
//            "0000ff01-0000-1000-8000-00805f9b34fb"
            "f2b4d622-aa53-11ec-b909-0242ac120002"

        //            "00000009-09da-4bed-9652-f507366fcfc5"
        const val CHARACTER_DATA_UP_UUID =
//            "0000ff01-0000-1000-8000-00805f9b34fb"
//        "00000007-09da-4bed-9652-f507366fcfc5"
            "cb100916-b274-47d4-b094-0372d72a3782"
        const val CCC_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        const val BLUETOOTH_ADDRESS: String = "DeviceAddress"
    }

    private val bluetoothAddress =
        if (intent != null)
            intent.getStringExtra("BLUETOOTH_ADDRESS")!! else "7C:9E:BD:72:30:9E" //"A4:4B:D5:7C:76:D7" // "EC:94:CB:7A:36:3A" //
    private var bluetoothManage: BluetoothManager? = null
    private var device: BluetoothDevice? = null
    private var ourBluetoothGatt: BluetoothGatt? = null
    private var ourBluetoothGattService: BluetoothGattService? = null
    private var ourNormalCharacteristic: BluetoothGattCharacteristic? = null
    private var ourDataUpCharacteristic: BluetoothGattCharacteristic? = null
    private val permissionHelper = PermissionHelper(this)
    private var bleGattConnect: GattConnection? = null

    private val dataUpMessage = mutableStateListOf("data up message")
    private val normalMessage = mutableStateListOf("normal message\n")

    private var dataUpFlow: Flow<BGC>? = null

    private var dataUpJob: Job? = null
    private var normalJob: Job? = null

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
//        device.connectGatt(this@BleActivity, false, leScanCallback)
        super.onCreate(savedInstanceState)

        bluetoothManage = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        device = bluetoothManage!!.adapter.getRemoteDevice(bluetoothAddress)



        setContent {
            Column {
                Greeting2("Android")

                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    lifecycleScope.launch {
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
                                val connection = GattConnection(device!!)
                                bleGattConnect = connection
                                withTimeout(2000) {
                                    connection.connect()
                                    Log.d(TAG, "is connected: ${bleGattConnect?.isConnected}")
                                }


                                val services = connection.discoverServices()
                                services.forEach { service ->
                                    Log.d(TAG, "service: ${service.uuid}")
                                    service.characteristics.forEach {
                                        Log.d(TAG, "chara: ${it.uuid}")
                                    }
                                    Log.d(TAG, "-----------------------------")
                                }

                                val gattService =
                                    connection.getService(UUID.fromString(SERVICE_UUID))
                                if (gattService == null) {
                                    Log.d(TAG, "did not find our service!")
                                    return@launch
                                }

                                val normalCharacteristic = gattService.getCharacteristic(
                                    UUID.fromString(CHARACTER_NORMAL_UUID)
                                )
                                val dataUpCharacteristic = gattService.getCharacteristic(
                                    UUID.fromString(CHARACTER_DATA_UP_UUID)
                                )


                                normalJob = launch {
                                    repeat(100) {
                                        normalCharacteristic.value =
                                            byteArrayOf((it % 256).toByte())
                                        val calendar1 = Calendar.getInstance()
                                        connection.writeCharacteristic(normalCharacteristic)
                                        val calendar2 = Calendar.getInstance()
                                        val readChara =
                                            connection.readCharacteristic(normalCharacteristic)
                                        val calendar3 = Calendar.getInstance()
                                        normalMessage.add(
                                            "cost:${
                                                calendar2.get(Calendar.MILLISECOND) - calendar1.get(
                                                    Calendar.MILLISECOND
                                                )
                                            }:${
                                                calendar3.get(Calendar.MILLISECOND) - calendar2.get(
                                                    Calendar.MILLISECOND
                                                )
                                            }: " + readChara.value.toHexString()
                                        )
                                    }
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, e.toString())
                            e.printStackTrace()
                        } finally {

                        }
                    }
                }) {
                    Text(text = "start connection")
                }

                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    lifecycleScope.launch {
                        val connection = bleGattConnect ?: return@launch
                        val gattService =
                            connection.getService(UUID.fromString(SERVICE_UUID))
                        if (gattService == null) {
                            Log.d(TAG, "did not find our service!")
                            return@launch
                        }

                        val dataUpCharacteristic = gattService.getCharacteristic(
                            UUID.fromString(CHARACTER_DATA_UP_UUID)
                        )

                        connection.setCharacteristicNotificationsEnabled(
                            dataUpCharacteristic,
                            true
                        )
                        connection.setCharacteristicNotificationsEnabledOnRemoteDevice(
                            dataUpCharacteristic,
                            true
                        )
                        dataUpFlow = connection.notifications(dataUpCharacteristic, true)

                        dataUpJob = launch {
                            dataUpFlow?.collect {
                                val calendar = Calendar.getInstance()
                                dataUpMessage.add(
                                    "${calendar.get(Calendar.MINUTE)}:${
                                        calendar.get(
                                            Calendar.SECOND
                                        )
                                    }:${calendar.get(Calendar.MILLISECOND)}>" + it.value.toHexString()
                                )
                            }
                        }
                        Log.d(TAG, "is connected: ${bleGattConnect?.isConnected}")
                    }
                }) {
                    Text(text = "open data up")
                }

                Button(modifier = Modifier.fillMaxWidth(), onClick = {
                    normalJob?.cancel()
                    dataUpJob?.cancel()
                    bleGattConnect?.close()

                    Log.d(TAG, "is connected: ${bleGattConnect?.isConnected}")
                }) {
                    Text(text = "close connection")
                }

                Row {
                    LazyColumn {
                        items(normalMessage) { item ->
                            Text(text = item, modifier = Modifier.fillMaxWidth(0.5f))
                        }
                    }

                    LazyColumn() {
                        items(dataUpMessage) { item ->
                            Text(text = item)
                        }

                    }
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    override fun onDestroy() {
        ourBluetoothGatt?.disconnect()
        ourBluetoothGatt?.close()
        ourBluetoothGatt = null
        ourDataUpCharacteristic = null
        ourNormalCharacteristic = null
        ourBluetoothGattService = null

        super.onDestroy()
    }

//    @OptIn(ExperimentalCoroutinesApi::class)
//    private suspend fun receivingRepeatingPosition(): ByteArray? {
//        return withTimeoutOrNull(1000) {
//            channelForRepeating.receive()
//        }

//        if (channelForRepeating.isEmpty)
//        previousPosition
//        else

//    }


    fun BluetoothGatt.findCharacteristic(uuid: UUID): BluetoothGattCharacteristic? {
        services?.forEach { service ->
            service.characteristics?.firstOrNull { characteristic ->
                characteristic.uuid == uuid
            }?.let { matchingCharacteristic ->
                return matchingCharacteristic
            }
        }
        return null
    }

    @SuppressLint("MissingPermission")
    fun writeDescriptor(descriptor: BluetoothGattDescriptor, payload: ByteArray) {
        ourBluetoothGatt?.let { gatt ->
            descriptor.value = payload
            gatt.writeDescriptor(descriptor)
        } ?: error("Not connected to a BLE device!")
    }

    @SuppressLint("MissingPermission")
    fun enableNotifications(characteristic: BluetoothGattCharacteristic) {
        val cccdUuid = UUID.fromString(CCC_DESCRIPTOR_UUID)
        val payload = when {
            characteristic.isIndicatable() -> BluetoothGattDescriptor.ENABLE_INDICATION_VALUE
            characteristic.isNotifiable() -> BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            else -> {
                Log.e(
                    "ConnectionManager",
                    "${characteristic.uuid} doesn't support notifications/indications"
                )
                return
            }
        }

        characteristic.getDescriptor(cccdUuid)?.let { cccDescriptor ->
            if (ourBluetoothGatt?.setCharacteristicNotification(characteristic, true) == false) {
                Log.e(
                    "ConnectionManager",
                    "setCharacteristicNotification failed for ${characteristic.uuid}"
                )
                return
            }
            writeDescriptor(cccDescriptor, payload)
        } ?: Log.e(
            "ConnectionManager",
            "${characteristic.uuid} doesn't contain the CCC descriptor!"
        )
    }

    fun BluetoothGattCharacteristic.isReadable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_READ)

    private fun BluetoothGattCharacteristic.isWritable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE)

    private fun BluetoothGattCharacteristic.isWritableWithoutResponse(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_WRITE_NO_RESPONSE)

    fun BluetoothGattCharacteristic.isIndicatable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_INDICATE)

    fun BluetoothGattCharacteristic.isNotifiable(): Boolean =
        containsProperty(BluetoothGattCharacteristic.PROPERTY_NOTIFY)

    fun BluetoothGattCharacteristic.containsProperty(property: Int): Boolean {
        return properties and property != 0
    }

    fun ByteArray.toHexString(): String =
        joinToString(separator = " ", prefix = "0x") { String.format("%02X", it) }

    private fun showRequestPermissionRationale(onUserResponse: (Boolean) -> Unit) {
        Choreographer.getInstance().postFrameCallback { onUserResponse(true) }
    }
}



