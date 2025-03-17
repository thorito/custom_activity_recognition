package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.app.Activity
import android.content.ContentValues.TAG
import android.content.Context
import android.content.Intent
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

    fun requestPermissions(activity: Activity, callback: (Boolean) -> Unit) {
        activityReference = WeakReference(activity)
        permissionCallback = callback

        val basicPermissionsToRequest = mutableListOf<String>()

        if (SDK_INT >= VERSION_CODES.Q) {
            if (!hasPermission(Manifest.permission.ACTIVITY_RECOGNITION)) {
                basicPermissionsToRequest.add(Manifest.permission.ACTIVITY_RECOGNITION)
            }
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            if (!hasPermission(Manifest.permission.POST_NOTIFICATIONS)) {
                basicPermissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            basicPermissionsToRequest.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }

        if (!hasPermission(Manifest.permission.ACCESS_COARSE_LOCATION)) {
            basicPermissionsToRequest.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }

        if (basicPermissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                activity,
                basicPermissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
        else if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
            )
        }
        else {
            callback(true)
        }
    }

    fun handlePermissionResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        when (requestCode) {
            PERMISSION_REQUEST_CODE -> {
                val allGranted = grantResults.all { it == PackageManager.PERMISSION_GRANTED }

                Log.d(TAG, "handlePermissionResult allGranted: $allGranted")
                if (allGranted) {
                    if (SDK_INT >= VERSION_CODES.Q && !hasPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION)) {
                        Log.d(TAG, "handlePermissionResult ACCESS_BACKGROUND_LOCATION not granted")
                        activityReference?.get()?.apply {
                            Log.d(TAG, "handlePermissionResult ACCESS_BACKGROUND_LOCATION request")
                            ActivityCompat.requestPermissions(
                                this,
                                arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION),
                                BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE
                            )
                        } ?: run {
                            Log.e(TAG, "No activity reference available to request background location permission")
                            permissionCallback?.invoke(false)
                            permissionCallback = null
                        }

                    } else {
                        permissionCallback?.invoke(true)
                        permissionCallback = null
                    }
                } else {
                    permissionCallback?.invoke(false)
                    permissionCallback = null
                }
            }
            BACKGROUND_LOCATION_PERMISSION_REQUEST_CODE -> {
                val granted = grantResults.isNotEmpty() &&
                        grantResults[0] == PackageManager.PERMISSION_GRANTED
                permissionCallback?.invoke(granted)
                permissionCallback = null
            }
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
        }

        if (SDK_INT >= VERSION_CODES.TIRAMISU) {
            hasPermissions = hasPermissions && hasPermission(Manifest.permission.POST_NOTIFICATIONS)
        }

        return hasPermissions
    }
}