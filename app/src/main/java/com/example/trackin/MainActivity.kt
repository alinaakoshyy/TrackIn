package com.example.trackin

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.Bundle
import android.telephony.SmsManager
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import com.google.android.gms.tasks.CancellationTokenSource
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var etStudentName: EditText
    private lateinit var etRollNumber: EditText
    private lateinit var etTeacherPhone: EditText
    private lateinit var tvLocationStatus: TextView
    private lateinit var tvLatitude: TextView
    private lateinit var tvLongitude: TextView
    private lateinit var tvSmsStatus: TextView
    private lateinit var btnGetLocation: Button
    private lateinit var btnSendAlert: Button

    private lateinit var fusedLocationClient: FusedLocationProviderClient
    private lateinit var database: AppDatabase
    private lateinit var locationPermissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var smsPermissionLauncher: ActivityResultLauncher<String>

    private var currentLatitude: Double? = null
    private var currentLongitude: Double? = null
    private var sendAfterLocation = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        fusedLocationClient = LocationServices.getFusedLocationProviderClient(this)
        database = AppDatabase.getDatabase(this)

        bindViews()
        setupPermissionLaunchers()

        btnGetLocation.setOnClickListener {
            fetchCurrentLocation()
        }

        btnSendAlert.setOnClickListener {
            handleSendAlertClicked()
        }
    }

    private fun bindViews() {
        etStudentName = findViewById(R.id.etStudentName)
        etRollNumber = findViewById(R.id.etRollNumber)
        etTeacherPhone = findViewById(R.id.etTeacherPhone)
        tvLocationStatus = findViewById(R.id.tvLocationStatus)
        tvLatitude = findViewById(R.id.tvLatitude)
        tvLongitude = findViewById(R.id.tvLongitude)
        tvSmsStatus = findViewById(R.id.tvSmsStatus)
        btnGetLocation = findViewById(R.id.btnGetLocation)
        btnSendAlert = findViewById(R.id.btnSendAlert)
    }

    private fun setupPermissionLaunchers() {
        locationPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestMultiplePermissions()
        ) { permissions ->
            val fineGranted = permissions[Manifest.permission.ACCESS_FINE_LOCATION] == true
            val coarseGranted = permissions[Manifest.permission.ACCESS_COARSE_LOCATION] == true

            if (fineGranted || coarseGranted) {
                fetchCurrentLocation {
                    if (sendAfterLocation) {
                        sendAfterLocation = false
                        sendAttendanceAlert()
                    }
                }
            } else {
                sendAfterLocation = false
                tvLocationStatus.text = "Location Status: Permission denied"
                Toast.makeText(this, "Location permission denied", Toast.LENGTH_SHORT).show()
            }
        }

        smsPermissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            if (granted) {
                handleSendAlertClicked()
            } else {
                tvSmsStatus.text = "SMS Status: Permission denied"
                Toast.makeText(this, "SMS permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun handleSendAlertClicked() {
        if (!validateInputs()) return

        if (!hasSmsPermission()) {
            smsPermissionLauncher.launch(Manifest.permission.SEND_SMS)
            return
        }

        if (currentLatitude == null || currentLongitude == null) {
            sendAfterLocation = true
            fetchCurrentLocation {
                if (sendAfterLocation) {
                    sendAfterLocation = false
                    sendAttendanceAlert()
                }
            }
            return
        }

        sendAttendanceAlert()
    }

    private fun validateInputs(): Boolean {
        val studentName = etStudentName.text.toString().trim()
        val rollNumber = etRollNumber.text.toString().trim()
        val teacherPhone = etTeacherPhone.text.toString().trim()

        return when {
            studentName.isEmpty() -> {
                etStudentName.error = "Enter student name"
                false
            }
            rollNumber.isEmpty() -> {
                etRollNumber.error = "Enter roll number"
                false
            }
            teacherPhone.isEmpty() -> {
                etTeacherPhone.error = "Enter teacher/admin phone number"
                false
            }
            else -> true
        }
    }

    private fun hasLocationPermission(): Boolean {
        val finePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        val coarsePermission = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_COARSE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED

        return finePermission || coarsePermission
    }

    private fun hasSmsPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.SEND_SMS
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestLocationPermissions() {
        locationPermissionLauncher.launch(
            arrayOf(
                Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION
            )
        )
    }

    private fun isLocationServiceEnabled(): Boolean {
        val locationManager = getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
            locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    @SuppressLint("MissingPermission")
    private fun fetchCurrentLocation(onLocationReady: (() -> Unit)? = null) {
        if (!hasLocationPermission()) {
            requestLocationPermissions()
            return
        }

        if (!isLocationServiceEnabled()) {
            sendAfterLocation = false
            tvLocationStatus.text = "Location Status: Please enable location services"
            Toast.makeText(this, "Please enable GPS/location services", Toast.LENGTH_LONG).show()
            return
        }

        tvLocationStatus.text = "Location Status: Fetching current location..."

        val cancellationTokenSource = CancellationTokenSource()
        fusedLocationClient.getCurrentLocation(
            Priority.PRIORITY_HIGH_ACCURACY,
            cancellationTokenSource.token
        ).addOnSuccessListener { location ->
            if (location != null) {
                updateLocation(location, "Current location fetched")
                onLocationReady?.invoke()
            } else {
                fetchLastKnownLocation(onLocationReady)
            }
        }.addOnFailureListener {
            fetchLastKnownLocation(onLocationReady)
        }
    }

    @SuppressLint("MissingPermission")
    private fun fetchLastKnownLocation(onLocationReady: (() -> Unit)? = null) {
        tvLocationStatus.text = "Location Status: Trying last known location..."

        fusedLocationClient.lastLocation
            .addOnSuccessListener { location ->
                if (location != null) {
                    updateLocation(location, "Last known location used")
                    onLocationReady?.invoke()
                } else {
                    sendAfterLocation = false
                    tvLocationStatus.text = "Location Status: Location unavailable"
                    Toast.makeText(this, "Location unavailable. Try again.", Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener {
                sendAfterLocation = false
                tvLocationStatus.text = "Location Status: Failed to fetch location"
                Toast.makeText(this, "Failed to fetch location", Toast.LENGTH_SHORT).show()
            }
    }

    private fun updateLocation(location: Location, status: String) {
        currentLatitude = location.latitude
        currentLongitude = location.longitude
        tvLocationStatus.text = "Location Status: $status"
        tvLatitude.text = "Latitude: ${location.latitude}"
        tvLongitude.text = "Longitude: ${location.longitude}"
    }

    @Suppress("DEPRECATION")
    private fun sendAttendanceAlert() {
        val latitude = currentLatitude
        val longitude = currentLongitude

        if (latitude == null || longitude == null) {
            tvSmsStatus.text = "SMS Status: Location unavailable"
            Toast.makeText(this, "Fetch location before sending alert", Toast.LENGTH_SHORT).show()
            return
        }

        val studentName = etStudentName.text.toString().trim()
        val rollNumber = etRollNumber.text.toString().trim()
        val teacherPhone = etTeacherPhone.text.toString().trim()
        val timestamp = SimpleDateFormat("dd-MM-yyyy hh:mm a", Locale.getDefault()).format(Date())
        val mapsLink = "https://maps.google.com/?q=$latitude,$longitude"

        val smsMessage = """
            Attendance Alert

            Name: $studentName
            Roll No: $rollNumber
            Status: Present
            Location: $mapsLink
            Latitude: $latitude
            Longitude: $longitude
            Time: $timestamp
        """.trimIndent()

        val smsStatus = try {
            val smsManager = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                getSystemService(SmsManager::class.java)
            } else {
                SmsManager.getDefault()
            }
            val messageParts = smsManager.divideMessage(smsMessage)
            smsManager.sendMultipartTextMessage(teacherPhone, null, messageParts, null, null)
            "SMS sent successfully"
        } catch (exception: Exception) {
            "SMS failed: ${exception.localizedMessage ?: "Unknown error"}"
        }

        tvSmsStatus.text = "SMS Status: $smsStatus"
        Toast.makeText(this, smsStatus, Toast.LENGTH_SHORT).show()
        saveAttendance(studentName, rollNumber, teacherPhone, latitude, longitude, timestamp, smsStatus)
    }

    private fun saveAttendance(
        studentName: String,
        rollNumber: String,
        teacherPhone: String,
        latitude: Double,
        longitude: Double,
        timestamp: String,
        smsStatus: String
    ) {
        val attendance = AttendanceEntity(
            studentName = studentName,
            rollNumber = rollNumber,
            teacherPhone = teacherPhone,
            latitude = latitude,
            longitude = longitude,
            timestamp = timestamp,
            smsStatus = smsStatus
        )

        lifecycleScope.launch {
            database.attendanceDao().insertAttendance(attendance)
        }
    }
}

