package com.example.communication

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.*
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.*
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.*
import com.example.communication.ui.theme.CommunicationTheme

private var connectedSocket: BluetoothSocket? = null

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CommunicationTheme {
                BluetoothScreen()
            }
        }
    }
}

@SuppressLint("MissingPermission")
@Composable
fun BluetoothScreen() {
    val context = LocalContext.current
    val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    val discoveredDevices = remember { mutableStateListOf<BluetoothDevice>() }
    val selectedDevice = remember { mutableStateOf<BluetoothDevice?>(null) }
    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    val messageToSend = remember { mutableStateOf(TextFieldValue("")) }
    val receivedMessage = remember { mutableStateOf("") }

    // Function to send message via Bluetooth
    fun sendMessage(message: String) {
        val socket = connectedSocket
        if (socket != null && socket.isConnected) {
            try {
                val outputStream: OutputStream = socket.outputStream
                outputStream.write(message.toByteArray())
                outputStream.flush()

                scope.launch {
                    snackbarHostState.showSnackbar("Message Sent: $message")
                }
            } catch (e: IOException) {
                scope.launch {
                    snackbarHostState.showSnackbar("Failed to send message: ${e.message}")
                }
            }
        } else {
            scope.launch {
                snackbarHostState.showSnackbar("Not connected to any device!")
            }
        }
    }

    // Function to listen for incoming messages
    fun startListeningForMessages(socket: BluetoothSocket) {
        scope.launch(Dispatchers.IO) {
            try {
                val inputStream: InputStream = socket.inputStream
                val buffer = ByteArray(1024)

                while (socket.isConnected) {
                    val bytesRead = inputStream.read(buffer)
                    val message = String(buffer, 0, bytesRead)

                    scope.launch(Dispatchers.Main) {
                        receivedMessage.value = message
                    }
                }
            } catch (e: IOException) {
                connectedSocket = null
                scope.launch {
                    snackbarHostState.showSnackbar("Connection lost: ${e.message}")
                }
            }
        }
    }

    @SuppressLint("MissingPermission")
    fun connectToDevice(device: BluetoothDevice) {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        if (connectedSocket?.isConnected == true) {
            return
        }

        val socket = device.createRfcommSocketToServiceRecord(uuid)

        CoroutineScope(Dispatchers.IO).launch {
            try {
                socket.connect()
                connectedSocket = socket

                withContext(Dispatchers.Main) {
                    selectedDevice.value = device
                    snackbarHostState.showSnackbar("Connected to ${device.name}")
                }

                startListeningForMessages(socket)

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Log.e("Bluetooth", "Connection failed: ${e.message}")
                }
            }
        }
    }

    // Function to start Bluetooth server (For Phone B)
    fun startBluetoothServer() {
        val uuid = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")

        CoroutineScope(Dispatchers.IO).launch {
            val bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
            val serverSocket: BluetoothServerSocket? =
                bluetoothAdapter?.listenUsingRfcommWithServiceRecord("BTServer", uuid)

            try {
                val socket = serverSocket?.accept()
                connectedSocket = socket

                withContext(Dispatchers.Main) {
                    Toast.makeText(
                        context,
                        "Connected as Server!",
                        Toast.LENGTH_SHORT
                    ).show()
                    selectedDevice.value = socket?.remoteDevice
                }

                if (socket != null) {
                    startListeningForMessages(socket)
                }

            } catch (e: IOException) {
                withContext(Dispatchers.Main) {
                    Toast.makeText(context, "Server error: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    Column(modifier = Modifier.fillMaxSize(), horizontalAlignment = Alignment.CenterHorizontally) {
        SnackbarHost(hostState = snackbarHostState)

        if (selectedDevice.value == null) {
            Button(onClick = {
                if (bluetoothAdapter == null) {
                    scope.launch { snackbarHostState.showSnackbar("Bluetooth not supported") }
                    return@Button
                }
                if (!bluetoothAdapter.isEnabled) {
                    scope.launch { snackbarHostState.showSnackbar("Turn on Bluetooth first") }
                    return@Button
                }
                discoveredDevices.clear()

                val pairedDevices: Set<BluetoothDevice>? = bluetoothAdapter.bondedDevices
                pairedDevices?.forEach { device ->
                    if (!discoveredDevices.contains(device)) {
                        discoveredDevices.add(device)
                    }
                }

                bluetoothAdapter.startDiscovery()
            }) {
                Text("Scan Bluetooth Devices")
            }

            LazyColumn {
                items(discoveredDevices) { device ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { connectToDevice(device) }
                            .padding(16.dp)
                    ) {
                        Text(text = "${device.name ?: "Unknown"} - ${device.address}")
                    }
                }
            }

            // Button to start Bluetooth Server (for Phone B)
            Button(onClick = { startBluetoothServer() }) {
                Text("Start Listening")
            }
        } else {
            Column {
                Text("Connected to: ${selectedDevice.value?.name ?: "Unknown"}")

                TextField(
                    value = messageToSend.value,
                    onValueChange = { messageToSend.value = it },
                    label = { Text("Enter Message") }
                )

                Button(onClick = { sendMessage(messageToSend.value.text) }) {
                    Text("Send Message")
                }

                Text("Received Message: ${receivedMessage.value}")
            }
        }
    }
}
