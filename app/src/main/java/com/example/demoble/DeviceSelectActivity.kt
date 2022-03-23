package com.example.demoble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

class DeviceSelectActivity : ComponentActivity() {
    private val deviceInfoList = mutableStateListOf<DeviceInfo>()
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // do not need to get the result for launch bluetooth
        }   // lifecycle owners must call register before they are started.

    override fun onResume() {
        super.onResume()
        onRefreshDevice()
    }

    override fun onCreate(savedInstanceState: Bundle?) {

        super.onCreate(savedInstanceState)
        setContent {
                // is there a better way to put the refresh button at bottom???????????
                ShowDeviceList(
                    deviceList = deviceInfoList,
                    onRefresh = { onRefreshDevice() },
                    onClickDevice = { deviceInfo: DeviceInfo -> onClickDevice(deviceInfo) }
                )
        }
    }

    private fun getBluetoothAdapter(): BluetoothAdapter {
        val bluetoothManage = this.getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        return bluetoothManage.adapter
    }

    private fun refreshBondedDeviceList(
        bluetoothAdapter: BluetoothAdapter,
        deviceInfoList: SnapshotStateList<DeviceInfo>
    ) {
        // If support bluetooth but bluetooth is closed, then request opening it.
        if (!bluetoothAdapter.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityResultLauncher.launch(enableBluetoothIntent)
        }
        if (ActivityCompat.checkSelfPermission(
                this,
                Manifest.permission.BLUETOOTH_CONNECT
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            // TODO: Consider calling
            //    ActivityCompat#requestPermissions
            // here to request the missing permissions, and then overriding
            //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
            //                                          int[] grantResults)
            // to handle the case where the user grants the permission. See the documentation
            // for ActivityCompat#requestPermissions for more details.
            return
        }
        val pairedDevices = bluetoothAdapter.bondedDevices
        deviceInfoList.clear()
        if (pairedDevices.isNotEmpty()) {
            for (device: BluetoothDevice in pairedDevices) {
                deviceInfoList.add(DeviceInfo(device.name, device.address))
            }
        } else {
            Toast.makeText(this, "没有已配对的蓝牙设备，请先配对!", Toast.LENGTH_SHORT).show()
        }
    }

    private fun onRefreshDevice() {
        val bluetoothAdapter = getBluetoothAdapter()
        refreshBondedDeviceList(bluetoothAdapter, deviceInfoList)
    }

    private fun onClickDevice(deviceInfo: DeviceInfo) {
        val intent = Intent(this, BleActivity::class.java) //对象获取
        intent.putExtra(BleActivity.BLUETOOTH_ADDRESS, deviceInfo.address)
        this.startActivity(intent)
    }
}
