package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.aikotelematics.custom_activity_recognition.ActivityRecognitionService.Companion
import io.flutter.plugin.common.EventChannel
import android.util.Log
import java.lang.ref.WeakReference

class ActivityRecognitionManager(private val context: Context) : EventChannel.StreamHandler {
    companion object {
        private const val TAG = "ActivityRecognitionManager"
        private const val PERMISSION_REQUEST_CODE = 100
        private const val LOCATION_PERMISSION_REQUEST_CODE = 101
        private const val BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE = 102
        private var eventSinkInstance: EventChannel.EventSink? = null
        private var permissionCallback: ((Boolean) -> Unit)? = null
        private var permissionStatusCallback: ((String) -> Unit)? = null
    }

    private var activityReference: WeakReference<Activity>? = null

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSinkInstance = events
        ActivityRecognitionReceiver.eventSink = events
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
            false
        }
    }

    fun checkPermissionStatus(activity: Activity, callback: (String) -> Unit) {
        activityReference = WeakReference(activity)
        permissionStatusCallback = callback

        if (SDK_INT < VERSION_CODES.Q) {
            if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
                !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
                if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                    callback("DENIED")
                } else {
                    val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                    val hasRequestedBefore = sharedPrefs.getBoolean("has_requested_location", false)

                    if (hasRequestedBefore) {
                        callback("PERMANENTLY_DENIED")
                    } else {
                        callback("NOT_DETERMINED")
                    }
                }
                return
            }
            callback("AUTHORIZED")
            return
        }

        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACTIVITY_RECOGNITION)) {
                callback("DENIED")
                return
            } else {
                val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                val hasRequestedActivityRecognition = sharedPrefs.getBoolean("has_requested_activity_recognition", false)

                if (hasRequestedActivityRecognition) {
                    callback("PERMANENTLY_DENIED")
                    return
                } else {
                    callback("NOT_DETERMINED")
                    return
                }
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION) ||
            !hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_FINE_LOCATION)) {
                callback("DENIED")
                return
            } else {
                val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                val hasRequestedLocation = sharedPrefs.getBoolean("has_requested_location", false)

                if (hasRequestedLocation) {
                    callback("PERMANENTLY_DENIED")
                    return
                } else {
                    callback("NOT_DETERMINED")
                    return
                }
            }
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.POST_NOTIFICATIONS)) {
                callback("DENIED")
                return
            } else {
                val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                val hasRequestedNotifications = sharedPrefs.getBoolean("has_requested_notifications", false)

                if (hasRequestedNotifications) {
                    callback("PERMANENTLY_DENIED")
                    return
                } else {
                    callback("NOT_DETERMINED")
                    return
                }
            }
        }

        if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                callback("DENIED")
                return
            } else {
                val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                val hasRequestedBackgroundLocation = sharedPrefs.getBoolean("has_requested_background_location", false)

                if (hasRequestedBackgroundLocation) {
                    callback("PERMANENTLY_DENIED")
                    return
                } else {
                    callback("NOT_DETERMINED")
                    return
                }
            }
        }

        callback("AUTHORIZED")
    }

    fun getMissingPermissions(activity: Activity, callback: (List<String>) -> Unit) {
        activityReference = WeakReference(activity)

        val missingPermissions = mutableListOf<String>()

        if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
            missingPermissions.add("activity_recognition")
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            missingPermissions.add("fine_location")
        }

        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            missingPermissions.add("coarse_location")
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU && !hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
            missingPermissions.add("notifications")
        }

        if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            missingPermissions.add("background_location")
        }

        callback(missingPermissions)
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
        else if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            editor.putBoolean("has_requested_background_location", true)
            editor.apply()
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
        }
        else {
            callback(true)
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
                        try {
                            permissionCallback?.invoke(false)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error invoking permission callback: ${e.message}")
                        }
                        permissionCallback = null

                        try {
                            permissionStatusCallback?.let { callback ->
                                activityReference?.get()?.let { activity ->
                                    checkPermissionStatus(activity, callback)
                                }
                            }
                        } catch (e: Exception) {
                            Log.e(TAG, "Error updating permission status: ${e.message}")
                        }
                        permissionStatusCallback = null
                        return true
                    }

                    val allGranted = grantResults.isNotEmpty() && grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                    if (allGranted) {
                        if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                            Log.d(TAG, "All basic permissions granted, requesting background location")
                            val activity = activityReference?.get()
                            if (activity != null) {
                                try {
                                    val sharedPrefs = context.getSharedPreferences("activity_recognition_prefs", Context.MODE_PRIVATE)
                                    ActivityCompat.requestPermissions(
                                        activity,
                                        arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                        BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                                    )

                                    sharedPrefs.edit()
                                        .putBoolean("has_requested_background_location", true)
                                        .apply()
                                } catch (e: Exception) {
                                    Log.e(TAG, "Error requesting background location: ${e.message}")
                                    permissionCallback?.invoke(false)
                                    permissionCallback = null
                                }
                            } else {
                                Log.e(TAG, "No activity reference available")
                                permissionCallback?.invoke(false)
                                permissionCallback = null
                            }
                        } else {
                            try {
                                permissionCallback?.invoke(true)
                            } catch (e: Exception) {
                                Log.e(TAG, "Error invoking permission callback: ${e.message}")
                            }
                            permissionCallback = null
                        }
                    } else {
                        try {
                            permissionCallback?.invoke(true)
                        } catch (e: Exception) {
                            Log.e(TAG, "Error invoking permission callback: ${e.message}")
                        }
                        permissionCallback = null
                    }

                    try {
                        permissionStatusCallback?.let { callback ->
                            activityReference?.get()?.let { activity ->
                                checkPermissionStatus(activity, callback)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating permission status: ${e.message}")
                    }
                    permissionStatusCallback = null
                }
                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                    val granted = grantResults.isNotEmpty() &&
                            grantResults[0] == PackageManager.PERMISSION_GRANTED

                    Log.d(TAG, "Background location permission: ${if (granted) "GRANTED" else "DENIED"}")

                    try {
                        permissionCallback?.invoke(granted)
                    } catch (e: Exception) {
                        Log.e(TAG, "Error invoking permission callback: ${e.message}")
                    }
                    permissionCallback = null

                    try {
                        permissionStatusCallback?.let { callback ->
                            activityReference?.get()?.let { activity ->
                                checkPermissionStatus(activity, callback)
                            }
                        }
                    } catch (e: Exception) {
                        Log.e(TAG, "Error updating permission status: ${e.message}")
                    }
                    permissionStatusCallback = null
                }
            }
            return true
        } catch (e: Exception) {
            Log.e(TAG, "Error handling permission result: ${e.message}")
            try {
                permissionCallback?.invoke(false)
            } catch (e: Exception) {
                Log.e(TAG, "Error invoking permission callback: ${e.message}")
            }
            permissionCallback = null

            try {
                permissionStatusCallback?.let { callback ->
                    activityReference?.get()?.let { activity ->
                        checkPermissionStatus(activity, callback)
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error updating permission status: ${e.message}")
            }
            permissionStatusCallback = null
            return false
        }
    }

    fun startTracking(showNotification: Boolean = true,
                      useTransitionRecognition: Boolean = true,
                      useActivityRecognition: Boolean = false,
                      detectionIntervalMillis: Int = 10000,
                      confidenceThreshold: Int = 50,
                      callback: (Boolean) -> Unit) {

        if (!hasRequiredPermissions()) {
            callback(false)
            return
        }

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
}