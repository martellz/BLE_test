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
import androidx.compose.material.Text
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.consume
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.consumeAsFlow
import java.lang.Exception
import java.lang.Integer.max
import java.lang.Integer.min
import java.util.*

class BleActivity : ComponentActivity() {
    companion object {
        private const val TAG = "BleDemo"
        private const val GATT_MAX_MTU_SIZE = 23
        const val SERVICE_NORMAL_UUID =
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
    private lateinit var bluetoothManage: BluetoothManager
    private lateinit var device: BluetoothDevice
    private var ourBluetoothGatt: BluetoothGatt? = null
    private var ourBluetoothGattService: BluetoothGattService? = null
    private var ourNormalCharacteristic: BluetoothGattCharacteristic? = null
    private var ourDataUpCharacteristic: BluetoothGattCharacteristic? = null
    private val permissionHelper = PermissionHelper(this)

    data class BleResult(val value: ByteArray?, val status: Int)

    private val channel = Channel<BleResult>()
//    private val channelForRepeating = Channel<ByteArray>()
    private val characteristicChangedFlow = MutableSharedFlow<BluetoothGattCharacteristic>(extraBufferCapacity = 1)

    private val dataUpMessage = mutableStateListOf("data up message")
    private val normalMessage = mutableStateOf("normal message\n")

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

                            if (charaNormal != null) {
                                ourNormalCharacteristic = charaNormal
                                lifecycleScope.launch(Dispatchers.IO) {
                                    repeat(1000) { iter->
                                        delay(500)
                                        val payload = byteArrayOf(
                                            (iter%256).toByte()
                                        )
                                        val writeResult = ourBluetoothGatt?.writeCharacteristic(payload)
                                        normalMessage.value += "write: ${payload.toHexString()}\n"

                                        Log.d(
                                            "BleDemoCoroutine",
                                            "write value= ${writeResult?.value}, status=${writeResult?.status}"
                                        )

                                        delay(500)
                                        val readResult = ourBluetoothGatt?.readCharacteristic()
                                        normalMessage.value += "receive: ${readResult?.value?.toHexString()}\n"
                                        Log.d(
                                            "BleDemoCoroutine",
                                            "value= ${readResult?.value?.toHexString()}, status=${readResult?.status}"
                                        )
                                    }

                                    withContext(Dispatchers.Main) {
                                        Log.d(TAG, "${ourDataUpCharacteristic?.isNotifiable()}...")
                                    }
                                }
                            }

                            val charaDataUp = service.getCharacteristic(
                                UUID.fromString(CHARACTER_DATA_UP_UUID)
                            )

                            if (charaDataUp != null) {
                                ourDataUpCharacteristic = charaDataUp
                                enableNotifications(charaDataUp)
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
                }
                else -> {
                    Log.e(
                        TAG,
                        "Characteristic read failed for $CHARACTER_NORMAL_UUID, error: $status"
                    )
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
//            if (characteristic.uuid == UUID.fromString(CHARACTER_DATA_UP_UUID))
//                Log.d("BleDemoCoroutine", "send ${characteristic.value} to channel")
//            this@BleActivity.lifecycleScope.launch {
//                channelForRepeating.trySend(characteristic.value)
//            }
            if (dataUpMessage.size > 256) dataUpMessage.remove(dataUpMessage[0])
            dataUpMessage.add(characteristic.value.toHexString())
        }
    }

    @SuppressLint("MissingPermission")
    override fun onCreate(savedInstanceState: Bundle?) {
//        device.connectGatt(this@BleActivity, false, leScanCallback)
        super.onCreate(savedInstanceState)

        bluetoothManage = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        device = bluetoothManage.adapter.getRemoteDevice(bluetoothAddress)

        Toast.makeText(this, bluetoothAddress, Toast.LENGTH_SHORT).show()

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
                    device.connectGatt(this@BleActivity, true, gattCallback)

//                    ourBluetoothGatt?.disconnect()
//                    ourBluetoothGatt?.close()
//                    ourBluetoothGatt = null
                }
            } catch (e: Exception) {
                Log.e(TAG, e.toString())
                e.printStackTrace()
            } finally {
//                ourBluetoothGatt?.disconnect()
//                ourBluetoothGatt?.close()
//                ourBluetoothGatt = null
            }
        }

        setContent {
            Column {
                Greeting2("Android")

                Row{
                    LazyColumn{
                        items(listOf(normalMessage.value)){item ->
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

    private suspend fun waitForResult(): BleResult {
        return withTimeout(1000) {
            channel.receive()
        }
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

    @SuppressLint("MissingPermission")
    private suspend fun BluetoothGatt.readCharacteristic(): BleResult? {
        val chara = ourNormalCharacteristic ?: return null

        readCharacteristic(chara)
        return waitForResult()
    }

    @SuppressLint("MissingPermission")
    private suspend fun BluetoothGatt.writeCharacteristic(payload: ByteArray): BleResult? {
        val chara = ourNormalCharacteristic ?: return null

        val writeType = when {
            chara.isWritable() -> BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            chara.isWritableWithoutResponse() -> BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE
            else -> error("not writable BLE device")
        }

        chara.writeType = writeType
        chara.value = payload
        this.writeCharacteristic(chara)

        return waitForResult()
    }

    private fun BluetoothGatt.printGattTable() {
        if (services.isEmpty()) {
            Log.i(
                TAG,
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



