package com.example.testingthings

import android.Manifest
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import androidx.activity.result.contract.ActivityResultContracts.RequestPermission
import androidx.activity.result.contract.ActivityResultContracts.StartActivityForResult
import androidx.compose.foundation.background
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.compose.ui.graphics.Color
import com.example.testingthings.ui.theme.TestingThingsTheme

class MainActivity : ComponentActivity() {

    private var isRecording by mutableStateOf(false)
    private val mediaProjectionManager by lazy { getSystemService(MediaProjectionManager::class.java) }
    private val requestPermissionLauncher by lazy {
        registerForActivityResult(RequestPermission()) {}
    }
    private val startMediaProjection = registerForActivityResult(StartActivityForResult()) { result ->
        if (result.resultCode == RESULT_OK) {
            val serviceIntent = Intent(this, ServerService::class.java)

            serviceIntent.putExtra("RESULT_CODE", result.resultCode)
            serviceIntent.putExtra("DATA", result.data)

            startForegroundService(serviceIntent)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        serviceInit()

        enableEdgeToEdge()
        setContent {
            TestingThingsTheme {
                Scaffold(modifier = Modifier.fillMaxSize()) { innerPadding ->
                    Box(
                        contentAlignment = Alignment.Center,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(innerPadding)
                    ) {
                        Button(
                            onClick = {
                                if (isRecording) stopService(Intent(this@MainActivity, ServerService::class.java))
                                else startMediaProjection.launch(mediaProjectionManager.createScreenCaptureIntent())

                                isRecording = !isRecording
                            }
                        ) {
                            Text(
                                text = if (isRecording) "Stop Recording" else "Start Recording"
                            )
                        }
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        stopService(Intent(this, ServerService::class.java))
        super.onDestroy()
    }

    private fun serviceInit() {
        if (checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
            != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }

        when {
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED -> println("Permission Granted")
            shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS) -> println("shouldShowRequestPermissionRationale")
            else -> requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RationalDialog(requestPermission: () -> Unit) {
    BasicAlertDialog(
        onDismissRequest = requestPermission,
        content = {
            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier.background(Color.Black)
            ) {
                Text(text = "Please give me permission", color = Color.White)
            }
        }
    )
}

//        val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
//
//        if (bluetoothAdapter != null && bluetoothAdapter.isEnabled) {
//            val pairedDevices: Set<BluetoothDevice> = if (
//                checkSelfPermission(Manifest.permission.BLUETOOTH_CONNECT)
//                != PackageManager.PERMISSION_GRANTED
//            ) {
//                // TODO: Consider calling
//                //    ActivityCompat#requestPermissions
//                // here to request the missing permissions, and then overriding
//                //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
//                //                                          int[] grantResults)
//                // to handle the case where the user grants the permission. See the documentation
//                // for ActivityCompat#requestPermissions for more details.
//                emptySet()
//            } else {
//                bluetoothAdapter.bondedDevices
//            }
//            if (pairedDevices.isNotEmpty()) {
//                // List paired devices
//                for (device in pairedDevices) {
//                    val deviceName = device.name
//                    val deviceAddress = device.address // MAC address
//                    println("Paired device: $deviceName, $deviceAddress")
//                }
//            } else {
//                println("No paired Bluetooth devices found")
//            }
//        } else {
//            println("Bluetooth is disabled or not available on this device")
//        }
//
//        val usbManager = getSystemService(USB_SERVICE) as UsbManager
//        val deviceList: HashMap<String, UsbDevice> = usbManager.deviceList
//
//        if (deviceList.isNotEmpty()) {
//            for (device in deviceList.values) {
//                val deviceName = device.deviceName
//                val vendorId = device.vendorId
//                val productId = device.productId
//                println("USB device: $deviceName, Vendor ID: $vendorId, Product ID: $productId")
//            }
//        } else {
//            println("No USB devices found")
//        }