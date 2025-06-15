package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.edit
import androidx.localbroadcastmanager.content.LocalBroadcastManager
import com.aikotelematics.custom_activity_recognition.ActivityRecognitionReceiver.Companion.eventSink
import com.aikotelematics.custom_activity_recognition.Constants.DEFAULT_CONFIDENCE_THRESHOLD
import com.aikotelematics.custom_activity_recognition.Constants.DEFAULT_DETECTION_INTERVAL_MILLIS
import com.aikotelematics.custom_activity_recognition.Constants.TAG
import com.google.android.gms.location.ActivityRecognition
import io.flutter.plugin.common.EventChannel
import java.lang.ref.WeakReference

class ActivityRecognitionManager(private val context: Context) : EventChannel.StreamHandler {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
        private var eventSinkInstance: EventChannel.EventSink? = null
        private var permissionCallback: ((Boolean) -> Unit)? = null
        private var permissionStatusCallback: ((String) -> Unit)? = null
    }

    private var activityReference: WeakReference<Activity>? = null

    private val localBroadcastManager: LocalBroadcastManager by lazy {
        LocalBroadcastManager.getInstance(context)
    }

    private val activityUpdateReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "ACTIVITY_UPDATE") {
                val activityType = intent.getStringExtra("activity") ?: return
                val timestamp = intent.getLongExtra("timestamp", 0)

                eventSink?.success(
                    mapOf(
                        "activity" to activityType,
                        "timestamp" to timestamp
                    )
                ) ?: Log.d(TAG, "eventSink is null, queuing update")
            }
        }
    }

    private var isTracking = false
    private var lastKnownActivity: String? = null

    /**
     * Updates the last known activity type
     * @param activityType The new activity type to set as last known
     */
    fun updateLastKnownActivity(activityType: String) {
        if (activityType != "UNKNOWN") {
            lastKnownActivity = activityType
            Log.d(TAG, "Updated last known activity to: $activityType")
        }
    }

    fun getCurrentActivity(callback: (String) -> Unit) {
        if (!isTracking) {
            Log.d(TAG, "getCurrentActivity called but tracking is not active")
            lastKnownActivity?.let { callback(it) } ?: callback("UNKNOWN")
            return
        }

        try {
            val requestCode = System.currentTimeMillis().toInt()
            val intent = Intent(context, ActivityRecognitionReceiver::class.java).apply {
                action = "GET_INITIAL_STATE"
                putExtra("isInitialState", true)
            }

            val pendingIntent = PendingIntent.getBroadcast(
                context,
                requestCode,
                intent,
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
            )

            // Request a single update with a short delay to get the most recent state
            val task = ActivityRecognition.getClient(context).requestActivityUpdates(
                1000, // 1 second delay to get the most recent state
                pendingIntent
            )

            task.addOnSuccessListener {
                Log.d(TAG, "Successfully requested initial activity update")
            }

            task.addOnFailureListener { e ->
                Log.e(TAG, "Error getting current activity: ${e.localizedMessage}")
                lastKnownActivity?.let { callback(it) } ?: callback("UNKNOWN")
            }

        } catch (e: Exception) {
            Log.e(TAG, "Exception in getCurrentActivity: ${e.message}", e)
            callback("UNKNOWN")
        }
    }

    fun startListening() {
        try {
            if (isTracking) {
                Log.d(TAG, "Already listening for activity updates")
                return
            }

            isTracking = true
            val filter = IntentFilter("ACTIVITY_UPDATE")
            // Ensure the receiver is not already registered
            try {
                localBroadcastManager.unregisterReceiver(activityUpdateReceiver)
            } catch (e: Exception) {
                // Receiver was not registered, ignore
            }
            localBroadcastManager.registerReceiver(activityUpdateReceiver, filter)
            Log.d(TAG, "Started listening for activity updates")
        } catch (e: Exception) {
            isTracking = false
            Log.e(TAG, "Error starting to listen for activity updates", e)
        }
    }

    fun stopListening() {
        try {
            localBroadcastManager.unregisterReceiver(activityUpdateReceiver)
        } catch (e: Exception) {
            Log.e(TAG, "Error unregistering receiver", e)
        }
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        Log.d(TAG, "onListen called")
        eventSinkInstance = events
        ActivityRecognitionReceiver.eventSink = events

        // Get current activity state when starting to listen
        getCurrentActivity { currentActivity ->
            events.success(
                mapOf(
                    "activity" to currentActivity,
                    "timestamp" to System.currentTimeMillis(),
                    "isInitialState" to true
                )
            )
        }

        startListening()
    }

    override fun onCancel(arguments: Any?) {
        eventSinkInstance = null
        ActivityRecognitionReceiver.eventSink = null
    }

    fun isAvailable(): Boolean {
        return try {
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
                    context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)
        } catch (e: Exception) {
            Log.e(TAG, "Error isAvailable: ${e.message}")
            false
        }
    }

    fun checkPermissionStatus(activity: Activity, callback: (String) -> Unit) {
        activityReference = WeakReference(activity)
        permissionStatusCallback = callback

        Log.d(TAG, "Checking permission status")
        val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)

        // Check location permissions for Android < Q (API 29)
        if (SDK_INT < VERSION_CODES.Q) {
            val hasLocationPerms = hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) &&
                    hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

            if (!hasLocationPerms) {
                val hasRequestedLocation = sharedPrefs.getBoolean("has_requested_location", false)
                val shouldShowRationale = ActivityCompat.shouldShowRequestPermissionRationale(
                    activity, Manifest.permission.ACCESS_FINE_LOCATION)

                Log.d(TAG, "Location permissions: hasRequested=$hasRequestedLocation, shouldShowRationale=$shouldShowRationale")

                // Fix: Only consider "PERMANENTLY_DENIED" if we've requested before
                if (!shouldShowRationale && hasRequestedLocation) {
                    callback("PERMANENTLY_DENIED")
                } else if (shouldShowRationale) {
                    callback("DENIED")
                } else {
                    callback("NOT_DETERMINED")
                }
                return
            }
            callback("AUTHORIZED")
            return
        }

        // Check ACTIVITY_RECOGNITION permission for Android Q+ (API 29+)
        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            val hasRequestedAR = sharedPrefs.getBoolean("has_requested_activity_recognition", false)
            val shouldShowRationaleAR = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACTIVITY_RECOGNITION)

            Log.d(TAG, "Activity recognition: hasRequested=$hasRequestedAR, shouldShowRationale=$shouldShowRationaleAR")

            // Fix: Only consider "PERMANENTLY_DENIED" if we've requested before
            if (!shouldShowRationaleAR && hasRequestedAR) {
                callback("PERMANENTLY_DENIED")
                return
            } else if (shouldShowRationaleAR) {
                callback("DENIED")
                return
            } else {
                callback("NOT_DETERMINED")
                return
            }
        }

        // Check location permissions for Android Q+ (API 29+)
        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {

            val hasRequestedLocation = sharedPrefs.getBoolean("has_requested_location", false)
            val shouldShowRationaleLocation = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_FINE_LOCATION)

            Log.d(TAG, "Location permissions: hasRequested=$hasRequestedLocation, shouldShowRationale=$shouldShowRationaleLocation")

            if (!shouldShowRationaleLocation && hasRequestedLocation) {
                callback("PERMANENTLY_DENIED")
                return
            } else if (shouldShowRationaleLocation) {
                callback("DENIED")
                return
            } else {
                callback("NOT_DETERMINED")
                return
            }
        }

        // Check notification permission for Android Tiramisu+ (API 33+)
        if (SDK_INT >= VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            val hasRequestedNotifications = sharedPrefs.getBoolean("has_requested_notifications", false)
            val shouldShowRationaleNotifications = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.POST_NOTIFICATIONS)

            Log.d(TAG, "Notifications: hasRequested=$hasRequestedNotifications, shouldShowRationale=$shouldShowRationaleNotifications")

            if (!shouldShowRationaleNotifications && hasRequestedNotifications) {
                callback("PERMANENTLY_DENIED")
                return
            } else if (shouldShowRationaleNotifications) {
                callback("DENIED")
                return
            } else {
                callback("NOT_DETERMINED")
                return
            }
        }

        // Check background location permission for Android Q+ (API 29+)
        if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            val hasRequestedBackgroundLocation = sharedPrefs.getBoolean("has_requested_background_location", false)
            val shouldShowRationaleBackgroundLocation = ActivityCompat.shouldShowRequestPermissionRationale(
                activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)

            Log.d(TAG, "Background location: hasRequested=$hasRequestedBackgroundLocation, shouldShowRationale=$shouldShowRationaleBackgroundLocation")

            if (!shouldShowRationaleBackgroundLocation && hasRequestedBackgroundLocation) {
                callback("PERMANENTLY_DENIED")
                return
            } else if (shouldShowRationaleBackgroundLocation) {
                callback("DENIED")
                return
            } else {
                callback("NOT_DETERMINED")
                return
            }
        }

        callback("AUTHORIZED")
    }

    fun requestPermissions(activity: Activity, callback: (Boolean) -> Unit) {
        activityReference = WeakReference(activity)
        permissionCallback = callback
        val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
        val editor = sharedPrefs.edit()

        val basicPermissionsToRequest = mutableListOf<String>()

        if (SDK_INT >= VERSION_CODES.Q) {
            if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
                basicPermissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
                editor.putBoolean("has_requested_activity_recognition", true)
            }
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                basicPermissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
                editor.putBoolean("has_requested_notifications", true)
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            basicPermissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
            editor.putBoolean("has_requested_location", true)
        }

        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            basicPermissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
            editor.putBoolean("has_requested_location", true)
        }

        editor.apply()

        if (basicPermissionsToRequest.isNotEmpty()) {
            try {
                ActivityCompat.requestPermissions(
                    activity,
                    basicPermissionsToRequest.toTypedArray(),
                    PERMISSION_REQUEST_CODE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting permissions: ${e.message}")
                callback(false)
            }
        }
        else {
            continueWithBackgroundLocation(activity, callback)
        }
    }

    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray): Boolean {
        try {
            when (requestCode) {
                PERMISSION_REQUEST_CODE -> {
                    var hasCriticalDenied = false

                    for (i in permissions.indices) {
                        val permission = permissions[i]
                        val isGranted = i < grantResults.size && grantResults[i] == PackageManager.PERMISSION_GRANTED

                        Log.d(TAG, "Permission $permission: ${if (isGranted) "GRANTED" else "DENIED"}")

                        if (!isGranted) {
                            if (permission == Manifest.permission.ACTIVITY_RECOGNITION ||
                                permission == Manifest.permission.ACCESS_FINE_LOCATION ||
                                permission == Manifest.permission.ACCESS_COARSE_LOCATION) {
                                Log.d(TAG, "Critical permission denied: $permission")
                                hasCriticalDenied = true
                            }
                        }
                    }

                    if (hasCriticalDenied) {
                        permissionCallback?.invoke(false)
                        permissionCallback = null
                    } else {
                        val allGranted =
                            grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                        if (allGranted) {
                            activityReference?.get()?.let { activity ->
                                continueWithBackgroundLocation(activity) { finalResult ->
                                    permissionCallback?.invoke(finalResult)
                                    permissionCallback = null
                                }
                            } ?: run {
                                permissionCallback?.invoke(false)
                                permissionCallback = null
                            }
                        } else {
                            permissionCallback?.invoke(true)
                            permissionCallback = null
                        }
                    }

                    permissionStatusCallback?.let { callback ->
                        activityReference?.get()?.let { activity ->
                            checkPermissionStatus(activity, callback)
                        }
                    }
                    permissionStatusCallback = null
                }
                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                    val granted = grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED

                    Log.d(TAG, "Background location permission: ${if (granted) "GRANTED" else "DENIED"}")

                    permissionCallback?.invoke(granted)
                    permissionCallback = null

                    permissionStatusCallback?.let { callback ->
                        activityReference?.get()?.let { activity ->
                            checkPermissionStatus(activity, callback)
                        }
                    }
                    permissionStatusCallback = null
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result: ${e.message}")
            permissionCallback?.invoke(false)
            permissionCallback = null
            return false
        }
    }

    fun startTracking(showNotification: Boolean = true,
                      useTransitionRecognition: Boolean = true,
                      useActivityRecognition: Boolean = true,
                      detectionIntervalMillis: Int = DEFAULT_DETECTION_INTERVAL_MILLIS,
                      confidenceThreshold: Int = DEFAULT_CONFIDENCE_THRESHOLD,
                      callback: (Boolean) -> Unit) {

        if (!hasRequiredPermissions()) {
            Log.d(TAG, "Missing required permissions")
            callback(false)
            return
        }

        Log.d(TAG, "Starting tracking")

        eventSinkInstance?.let {
            ActivityRecognitionReceiver.eventSink = it
        }

        val intent = Intent(context, ActivityRecognitionService::class.java).apply {
            putExtra("showNotification", showNotification)
            putExtra("useTransitionRecognition", useTransitionRecognition)
            putExtra("useActivityRecognition", useActivityRecognition)
            putExtra("detectionIntervalMillis", detectionIntervalMillis)
            putExtra("confidenceThreshold", confidenceThreshold)
        }

        if (SDK_INT >= VERSION_CODES.O) {
            context.startForegroundService(intent)
        } else {
            context.startService(intent)
        }
        callback(true)
    }

    @SuppressLint("ImplicitSamInstance")
    fun stopTracking(callback: (Boolean) -> Unit) {
        context.stopService(Intent(context, ActivityRecognitionService::class.java))
        callback(true)
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasRequiredPermissions(): Boolean {

        var hasPermissions = true

        if (SDK_INT >= VERSION_CODES.Q) {
            hasPermissions = hasPermissions && hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)
            hasPermissions = hasPermissions && hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            hasPermissions = hasPermissions && hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }

        hasPermissions = hasPermissions && hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)
        hasPermissions = hasPermissions && hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)

        return hasPermissions
    }

    private fun continueWithBackgroundLocation(activity: Activity, callback: (Boolean) -> Unit) {
        if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            val sharedPrefs =
                context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
            sharedPrefs.edit() {
                putBoolean("has_requested_background_location", true)
            }
            try {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                    BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                )
            } catch (e: Exception) {
                Log.e(TAG, "Error requesting background location: ${e.message}")
                callback(false)
            }
        } else {
            callback(true)
        }
    }
}