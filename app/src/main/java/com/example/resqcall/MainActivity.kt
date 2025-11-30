package com.example.resqcall

import android.Manifest
import android.animation.ValueAnimator
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothSocket
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.media.AudioManager
import android.media.MediaPlayer
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.provider.ContactsContract
import android.telephony.SmsManager
import android.util.Log
import android.view.View
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException
import java.io.InputStream
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var btnStop: Button
    private lateinit var txtTimer: TextView
    private lateinit var btnSelectContact: Button
    private lateinit var btnSelectDevice: Button
    private lateinit var waveView: View
    private var mediaPlayer: MediaPlayer? = null
    private var emergencyContact: String? = null
    private var countdownTimer: CountDownTimer? = null
    private var animator: ValueAnimator? = null

    // Bluetooth related variables
    private val TAG = "ResQCallBluetooth"
    private val SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB") // Standard SPP UUID
    private var bluetoothAdapter: BluetoothAdapter? = null
    private var bluetoothSocket: BluetoothSocket? = null
    private var connectedDevice: BluetoothDevice? = null
    private var isConnected = false
    private var bluetoothThread: Thread? = null
    private var inputStream: InputStream? = null

    private val contactPickerLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            val data = result.data
            data?.data?.let { uri ->
                val cursor = contentResolver.query(uri, arrayOf(ContactsContract.CommonDataKinds.Phone.NUMBER), null, null, null)
                cursor?.use {
                    if (it.moveToFirst()) {
                        emergencyContact = it.getString(0)
                        txtTimer.text = "Emergency Contact: $emergencyContact"
                    }
                }
            }
        }
    }

    private val bluetoothEnableLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == RESULT_OK) {
            showPairedDevices()
        } else {
            Toast.makeText(this, "Bluetooth is required for fall detection", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        btnStop = findViewById(R.id.btnStop)
        txtTimer = findViewById(R.id.txtTimer)
        waveView = findViewById(R.id.waveView)
        btnSelectContact = findViewById(R.id.btnSelectContact)

        btnSelectDevice = findViewById(R.id.btnSelectDevice)

        btnStop.setOnClickListener { stopSOS() }
        btnSelectContact.setOnClickListener { selectContact() }
        btnSelectDevice.setOnClickListener { selectBluetoothDevice() }

        requestPermissions()
        initBluetooth()
    }

    private fun requestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        // Check and add needed permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.CALL_PHONE)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.SEND_SMS)
        }

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        // Add specific Bluetooth permissions based on Android version
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            // Android 12 and above need BLUETOOTH_CONNECT and BLUETOOTH_SCAN
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_CONNECT)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_SCAN)
            }
        } else {
            // Older versions use these permissions
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH)
            }
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_ADMIN) != PackageManager.PERMISSION_GRANTED) {
                permissionsToRequest.add(Manifest.permission.BLUETOOTH_ADMIN)
            }
        }

        // Request permissions if needed
        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(this, permissionsToRequest.toTypedArray(), PERMISSION_REQUEST_CODE)
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            // Check if all permissions were granted
            val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

            if (allGranted) {
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Some permissions were denied. Location sharing may not work properly.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun initBluetooth() {
        val bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        bluetoothAdapter = bluetoothManager.adapter

        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show()
            return
        }
    }

    private fun selectBluetoothDevice() {
        if (bluetoothAdapter == null) {
            Toast.makeText(this, "Bluetooth is not available on this device", Toast.LENGTH_LONG).show()
            return
        }

        // Check if Bluetooth is enabled
        if (!bluetoothAdapter!!.isEnabled) {
            try {
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                bluetoothEnableLauncher.launch(enableBtIntent)
            } catch (e: Exception) {
                Log.e(TAG, "Error enabling Bluetooth", e)
                Toast.makeText(this, "Failed to enable Bluetooth", Toast.LENGTH_SHORT).show()
            }
        } else {
            showPairedDevices()
        }
    }

    private fun showPairedDevices() {
        try {
            // Check for Bluetooth permissions
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                    return
                }
            }

            val pairedDevices = bluetoothAdapter?.bondedDevices?.toList() ?: emptyList()

            if (pairedDevices.isEmpty()) {
                Toast.makeText(this, "No paired devices found. Please pair your ESP32 device first.", Toast.LENGTH_LONG).show()
                return
            }

            val deviceNames = pairedDevices.map { it.name ?: "Unknown Device" }.toTypedArray()

            AlertDialog.Builder(this)
                .setTitle("Select Fall Detection Device")
                .setItems(deviceNames) { _, which ->
                    val selectedDevice = pairedDevices[which]
                    connectToDevice(selectedDevice)
                }
                .setNegativeButton("Cancel", null)
                .show()
        } catch (e: Exception) {
            Log.e(TAG, "Error showing paired devices", e)
            Toast.makeText(this, "Error: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun connectToDevice(device: BluetoothDevice) {
        // Disconnect from current device if connected
        disconnectFromDevice()

        Toast.makeText(this, "Connecting to ${device.name}...", Toast.LENGTH_SHORT).show()

        // Start connection in a background thread
        bluetoothThread = Thread {
            try {
                // Check for Bluetooth permissions
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    if (ActivityCompat.checkSelfPermission(this, Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                        runOnUiThread {
                            Toast.makeText(this, "Bluetooth permission required", Toast.LENGTH_SHORT).show()
                        }
                        return@Thread
                    }
                }

                // Connect to the device
                bluetoothSocket = device.createRfcommSocketToServiceRecord(SPP_UUID)
                bluetoothSocket?.connect()

                if (bluetoothSocket?.isConnected == true) {
                    connectedDevice = device
                    isConnected = true

                    runOnUiThread {
                        Toast.makeText(this, "Connected to ${device.name}", Toast.LENGTH_SHORT).show()
                        txtTimer.text = "Connected to ${device.name}"
                    }

                    // Start listening for data
                    listenForData()
                }
            } catch (e: IOException) {
                Log.e(TAG, "Error connecting to device", e)
                runOnUiThread {
                    Toast.makeText(this, "Failed to connect: ${e.message}", Toast.LENGTH_SHORT).show()
                }

                try {
                    bluetoothSocket?.close()
                } catch (ce: IOException) {
                    Log.e(TAG, "Error closing socket", ce)
                }

                isConnected = false
                connectedDevice = null
            }
        }

        bluetoothThread?.start()
    }

    private fun disconnectFromDevice() {
        isConnected = false

        try {
            inputStream?.close()
            bluetoothSocket?.close()
        } catch (e: IOException) {
            Log.e(TAG, "Error closing Bluetooth connection", e)
        }

        bluetoothThread?.interrupt()
        bluetoothThread = null
        connectedDevice = null
    }

    private fun listenForData() {
        try {
            inputStream = bluetoothSocket?.inputStream
            val buffer = ByteArray(1024)
            var bytes: Int

            // Keep listening to the InputStream
            while (isConnected) {
                try {
                    bytes = inputStream?.read(buffer) ?: -1

                    if (bytes > 0) {
                        val message = String(buffer, 0, bytes)
                        Log.d(TAG, "Received: $message")

                        // Check if impact is detected
                        if (message.contains("IMPACT_DETECTED", ignoreCase = true)) {
                            runOnUiThread {
                                Toast.makeText(this, "Fall detected! Starting emergency protocol.", Toast.LENGTH_LONG).show()
                                startSOS() // This will now be triggered only by ESP32
                            }
                        }
                    }
                } catch (e: IOException) {
                    Log.e(TAG, "Input stream was disconnected", e)
                    isConnected = false

                    runOnUiThread {
                        Toast.makeText(this, "Connection lost with the device", Toast.LENGTH_SHORT).show()
                        txtTimer.text = "Waiting for fall detection..."
                    }

                    break
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in listenForData", e)
        }
    }

    private fun startSOS() {
        if (emergencyContact.isNullOrEmpty()) {
            txtTimer.text = "Select an emergency contact first!"
            return
        }

        waveView.visibility = View.VISIBLE
        animateWave()
        increaseVolume()
        playAlarmSound()

        // Hide the device and contact selection buttons during emergency
        btnSelectDevice.visibility = View.GONE
        btnSelectContact.visibility = View.GONE

        countdownTimer = object : CountDownTimer(30000, 1000) {
            override fun onTick(millisUntilFinished: Long) {
                txtTimer.text = "Time left: ${millisUntilFinished / 1000}s"
            }

            override fun onFinish() {
                txtTimer.text = "Calling Help!"
                stopAlarmOnly()

                sendLocationSharingMessage(emergencyContact!!)

                // Small delay before making the call
                txtTimer.postDelayed({
                    callEmergencyContact()
                }, 1500)
            }
        }.start()

        // Only show the STOP button
        btnStop.visibility = View.VISIBLE
    }

    private fun stopAlarmOnly() {
        // Stop just the alarm and animation but keep the emergency process going
        mediaPlayer?.stop()
        mediaPlayer?.release()
        mediaPlayer = null

        animator?.cancel()
    }

    private fun stopSOS() {
        countdownTimer?.cancel()
        countdownTimer = null

        stopAlarmOnly()

        waveView.visibility = View.GONE

        // Restore device and contact selection buttons
        btnSelectDevice.visibility = View.VISIBLE
        btnSelectContact.visibility = View.VISIBLE

        if (connectedDevice != null) {
            txtTimer.text = "Connected to ${connectedDevice?.name}"
        } else {
            txtTimer.text = "Waiting for fall detection..."
        }

        btnStop.visibility = View.GONE
    }

    private fun animateWave() {
        animator = ValueAnimator.ofInt(100, 1000).apply {
            duration = 30000
            addUpdateListener {
                val value = it.animatedValue as Int
                waveView.layoutParams.width = value
                waveView.layoutParams.height = value
                waveView.requestLayout()
            }
            start()
        }
    }

    private fun playAlarmSound() {
        mediaPlayer = MediaPlayer.create(this, R.raw.alarm).apply {
            isLooping = true
            start()
        }
    }

    private fun increaseVolume() {
        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC), 0)
    }

    private fun callEmergencyContact() {
        emergencyContact?.let { number ->
            // Call intent
            val callIntent = Intent(Intent.ACTION_CALL, Uri.parse("tel:$number"))
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.CALL_PHONE) == PackageManager.PERMISSION_GRANTED) {
                startActivity(callIntent)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CALL_PHONE), 1)
            }
        }
    }

    private fun sendLocationSharingMessage(contactNumber: String) {
        try {
            // Check SMS permission first
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
                Log.e(TAG, "SMS permission not granted")
                Toast.makeText(this, "SMS permission not granted", Toast.LENGTH_SHORT).show()
                return
            }

            // Initialize SMS manager
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                this.getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }

            // Define the emergency message with emoji
            val emergencyMessage = "🚨 SOS ALERT! I need help immediately! A fall has been detected. Emergency services may be required."

            // Send the emergency message using multipart to handle emoji
            try {
                val emergencyParts = smsManager.divideMessage(emergencyMessage)
                smsManager.sendMultipartTextMessage(contactNumber, null, emergencyParts, null, null)
                Log.d(TAG, "Initial emergency SMS sent: $emergencyMessage")

                // Add a confirmation toast for the first message
                runOnUiThread {
                    Toast.makeText(this, "Emergency alert sent", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                Log.e(TAG, "Failed to send multipart message with emoji, trying plain text", e)
                // Fallback to plain text if emoji fails
                val plainEmergencyMessage = "SOS ALERT! I need help immediately! A fall has been detected at my location. Emergency services may be required."
                smsManager.sendTextMessage(contactNumber, null, plainEmergencyMessage, null, null)
            }

            // Then get and send location in a separate SMS
            getCurrentLocation { location ->
                if (location != null) {
                    // Create a Google Maps link with the coordinates
                    val mapLink = "https://maps.google.com/?q=${location.latitude},${location.longitude}"
                    val locationMessage = "🚨 My exact location : $mapLink"

                    try {
                        // Send the location with emoji using multipart message
                        val locationParts = smsManager.divideMessage(locationMessage)
                        smsManager.sendMultipartTextMessage(contactNumber, null, locationParts, null, null)
                        Log.d(TAG, "Location SMS with emoji sent: $locationMessage")
                    } catch (e: Exception) {
                        Log.e(TAG, "Failed to send location SMS with emoji, trying plain text", e)
                        // Fallback to plain text if emoji fails
                        val plainLocationMessage = "SOS ALERT! I need help immediately! My exact location (fall detected): $mapLink"
                        smsManager.sendTextMessage(contactNumber, null, plainLocationMessage, null, null)
                        Log.d(TAG, "Plain location SMS sent as fallback")
                    }
                } else {
                    // Location not available, send basic SMS
                    try {
                        val fallbackMessage = "🚨 Fall detected! Location unavailable. Please call me immediately!"
                        val fallbackParts = smsManager.divideMessage(fallbackMessage)
                        smsManager.sendMultipartTextMessage(contactNumber, null, fallbackParts, null, null)
                    } catch (e: Exception) {
                        // Try without emoji if that fails
                        val plainFallbackMessage = "Fall detected! Location unavailable. Please call me immediately!"
                        smsManager.sendTextMessage(contactNumber, null, plainFallbackMessage, null, null)
                    }
                    Log.d(TAG, "Location unavailable - sent basic SMS")
                }

                // Toast on the main thread to confirm messages are sent
                runOnUiThread {
                    Toast.makeText(this, "Location information sent", Toast.LENGTH_SHORT).show()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in sending emergency messages", e)
            Toast.makeText(this, "Failed to send messages: ${e.message}", Toast.LENGTH_SHORT).show()

            // Fallback to simple SMS with no special characters
            try {
                val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    this.getSystemService(SmsManager::class.java)
                } else {
                    SmsManager.getDefault()
                }
                smsManager.sendTextMessage(contactNumber, null,
                    "EMERGENCY! Fall detected! Please call me immediately!", null, null)
                Log.d(TAG, "Fallback emergency SMS sent")
            } catch (ex: Exception) {
                Log.e(TAG, "Even fallback SMS failed", ex)
            }
        }
    }

    // Location retrieval with callback
    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            Log.e(TAG, "Location permission not granted")
            callback(null)
            return
        }

        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager

        try {
            // Check if location providers are enabled
            val gpsEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER)
            val networkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)

            if (!gpsEnabled && !networkEnabled) {
                Log.e(TAG, "All location providers are disabled")
                callback(null)
                return
            }

            // Try to get location from available providers
            var location: Location? = null

            if (networkEnabled) {
                location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
                Log.d(TAG, "Trying network provider: ${location != null}")
            }

            if (location == null && gpsEnabled) {
                location = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
                Log.d(TAG, "Trying GPS provider: ${location != null}")
            }

            callback(location)
        } catch (e: Exception) {
            Log.e(TAG, "Error getting location", e)
            callback(null)
        }
    }

    private fun selectContact() {
        val intent = Intent(Intent.ACTION_PICK, ContactsContract.CommonDataKinds.Phone.CONTENT_URI)
        contactPickerLauncher.launch(intent)
    }

    override fun onDestroy() {
        super.onDestroy()

        // Clean up Bluetooth connections
        disconnectFromDevice()

        // Clean up media player
        mediaPlayer?.release()
        mediaPlayer = null

        // Cancel any running timer
        countdownTimer?.cancel()
        animator?.cancel()
    }

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }
}