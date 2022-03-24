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
import android.view.Choreographer
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.snapshots.SnapshotStateList
import androidx.core.app.ActivityCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import java.lang.NullPointerException

class DeviceSelectActivity : ComponentActivity() {
    private val deviceInfoList = mutableStateListOf<DeviceInfo>()
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.StartActivityForResult()) {
            // do not need to get the result for launch bluetooth
        }   // lifecycle owners must call register before they are started.

    private val permissionHelper = PermissionHelper(this)

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

    @SuppressLint("MissingPermission")
    private suspend fun refreshBondedDeviceList(
        permissionHelper: PermissionHelper,
        bluetoothAdapter: BluetoothAdapter,
        deviceInfoList: SnapshotStateList<DeviceInfo>
    ) {
        // If support bluetooth but bluetooth is closed, then request opening it.
        if (!bluetoothAdapter.isEnabled) {
            val enableBluetoothIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            activityResultLauncher.launch(enableBluetoothIntent)
        }
        if (permissionHelper.request(Manifest.permission.BLUETOOTH_CONNECT,
                { _, onUserResponse ->
                    showRequestPermissionRationale(onUserResponse)
                },
            this@DeviceSelectActivity) ) {
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
    }

    private fun onRefreshDevice() {
        val bluetoothAdapter = getBluetoothAdapter()
        lifecycleScope.launch {
            refreshBondedDeviceList(permissionHelper, bluetoothAdapter, deviceInfoList)
        }
    }

    private fun onClickDevice(deviceInfo: DeviceInfo) {
        val intent = Intent(this, BleActivity::class.java) //对象获取
        intent.putExtra(BleActivity.BLUETOOTH_ADDRESS, deviceInfo.address)
        this.startActivity(intent)
    }

    private fun showRequestPermissionRationale(onUserResponse: (Boolean) -> Unit) {
        Choreographer.getInstance().postFrameCallback { onUserResponse(true) }
    }
}
