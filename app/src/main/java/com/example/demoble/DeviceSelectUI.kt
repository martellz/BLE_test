package com.example.demoble

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp

data class DeviceInfo(val name: String, val address: String)  // device name and MAC address

@Composable
fun DeviceList(deviceInfos: List<DeviceInfo>, onClickDevice: (DeviceInfo) -> Unit = {}) {
    // use to show all (bonded or all) devices
    LazyColumn {
        items(deviceInfos) { deviceInfo ->
            Button(
                onClick = { onClickDevice(deviceInfo) },
                modifier = Modifier
                    .height(80.dp)
                    .fillMaxWidth()
                    .padding(5.dp, 5.dp),
                shape = RoundedCornerShape(10.dp, 10.dp, 10.dp, 10.dp),
                colors = ButtonDefaults.buttonColors(
                    backgroundColor = Color.White,
                    contentColor = Color.White
                )

            ) {
                Column {
                    Text(
                        text = deviceInfo.name,
                        fontSize = MaterialTheme.typography.button.fontSize,
                        textAlign = TextAlign.Left,
                        color = Color.Black
                    )
                    Text(
                        text = deviceInfo.address,
                        fontSize = MaterialTheme.typography.button.fontSize,
                        textAlign = TextAlign.Left,
                        color = Color.Black
                    )
                }
            }
        }
    }
}

@Composable
fun RefreshDevices(onRefresh: () -> Unit = {}) {
    Button(
        onClick = onRefresh,
        modifier = Modifier
            .height(50.dp)
            .fillMaxWidth()
            .padding(5.dp)
            .offset(0.dp, 0.dp),
        shape = RoundedCornerShape(10.dp, 10.dp, 10.dp, 10.dp)
    ) {
        Text(
            text = "Refresh"
        )
    }
}

@Composable
fun ShowDeviceList(
    deviceList: List<DeviceInfo>,
    onRefresh: () -> Unit = {},
    onClickDevice: (DeviceInfo) -> Unit = {}
) {

    Scaffold(bottomBar = { RefreshDevices(onRefresh = onRefresh) }) {
        Column {
            Text(text = "device counts : ${deviceList.size}")
            DeviceList(deviceInfos = deviceList, onClickDevice)
        }
    }
}


/***
 ***************************
 *       Preview Part      *
 ***************************
 ***/

@Preview
@Composable
private fun DeviceListPreview() {
    val deviceInfos = listOf(
        DeviceInfo("MotorController", "3C:61:05:14:E4:8E"),
        DeviceInfo("K30 5g", "A4:4B:D5:7C:76:D7")
    )
    DeviceList(deviceInfos = deviceInfos)
}

@Preview
@Composable
private fun RefreshDevicesPreview() {
    RefreshDevices()
}

@Preview
@Composable
private fun DeviceSelectListPreview() {
    ShowDeviceList(listOf(DeviceInfo("name1", "addr1")))
}