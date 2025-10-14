package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import androidx.core.content.ContextCompat
import android.content.pm.PackageManager
import com.aikotelematics.custom_activity_recognition.Contants.ACTION_WAKEUP
import com.aikotelematics.custom_activity_recognition.Contants.TAG
import com.aikotelematics.custom_activity_recognition.Contants.UPDATE_ACTIVITY
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.flutter.plugin.common.EventChannel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityRecognitionReceiver : BroadcastReceiver() {
    companion object {
        var eventSink: EventChannel.EventSink? = null
        var lastActivityType: String? = null
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    override fun onReceive(context: Context, intent: Intent) {
        Log.d(TAG, "📡 BroadcastReceiver.onReceive - action: ${intent.action}")

        if (!hasLocationPermissions(context)) {
            Log.e(TAG, "Cannot start service - missing location permissions")
            return
        }

        when {
            ActivityTransitionResult.hasResult(intent) -> {
                Log.d(TAG, "📍 Has ActivityTransitionResult")
                handleTransitionResult(context, ActivityTransitionResult.extractResult(intent))
            }
            ActivityRecognitionResult.hasResult(intent) -> {
                Log.d(TAG, "🏃 Has ActivityRecognitionResult")
                handleActivityResult(context, ActivityRecognitionResult.extractResult(intent))
            }
            else -> {
                Log.d(TAG, "⚠️ Intent received with no recognizable result - action: ${intent.action}")
            }
        }

        checkAndWakeupService(context)
    }

    private fun hasLocationPermissions(context: Context): Boolean {
        return ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun handleTransitionResult(context: Context, result: ActivityTransitionResult?) {
        result?.let {
            for (event in result.transitionEvents) {
                val activityType = getActivityType(event.activityType)
                val transitionType = getTransitionType(event.transitionType)
                val currentTimeMillis = System.currentTimeMillis()
                val bootTimeMillis = currentTimeMillis - SystemClock.elapsedRealtime()
                val timestamp = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)
                val time = timeFormat.format(Date(timestamp))
                Log.d(
                    TAG, "🟠 Activity transition detected [$time]: " +
                            "$activityType ($transitionType)"
                )

                if (activityType != lastActivityType || lastActivityType == null) {
                    sendActivityUpdate(context, activityType, timestamp)
                    lastActivityType = activityType;
                }
            }
        }
    }

    private fun handleActivityResult(context: Context, result: ActivityRecognitionResult?) {
        result?.let {
            val time = timeFormat.format(Date(it.time))
            val activity = it.mostProbableActivity
            val activityType = getActivityType(activity.type)
            val hasConfidence =
                activity.confidence >= ActivityRecognitionService.confidenceThreshold
            val icon = if (hasConfidence) "🟢" else "🔴"

            Log.d(
                TAG, "$icon Activity Loop detected [$time]: $activityType, " +
                        "confidence: ${activity.confidence}"
            )
            if ((activityType != lastActivityType || lastActivityType == null) &&
                activity.type != DetectedActivity.TILTING && hasConfidence
            ) {
                val timestamp = it.time
                sendActivityUpdate(context, activityType, timestamp)
                lastActivityType = activityType
            }
        }
    }

    private fun sendActivityUpdate(context: Context, activityType: String, timestamp: Long) {

        val data = mapOf<String, Any?>(
            "timestamp" to System.currentTimeMillis(),
            "activity" to activityType,
        )

        eventSink?.success(data)

        updateNotification(context, activityType, timestamp)
    }

    private fun getActivityType(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.STILL -> "STILL"
            DetectedActivity.UNKNOWN -> "UNKNOWN"
            else -> "UNKNOWN"
        }
    }

    private  fun getTransitionType(type: Int): String {
        return when (type) {
            ActivityTransition.ACTIVITY_TRANSITION_ENTER -> "ENTER"
            ActivityTransition.ACTIVITY_TRANSITION_EXIT -> "EXIT"
            else -> "?"
        }
    }

    private fun updateNotification(context: Context, activityType: String, timestamp: Long) {
        val serviceIntent = Intent(context, ActivityRecognitionService::class.java).apply {
            action = UPDATE_ACTIVITY
            putExtra("activity", activityType)
            putExtra("timestamp", timestamp)
        }

        if (ActivityRecognitionService.isRunning()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        } else {
            checkAndWakeupService(context)
        }
    }

    private fun checkAndWakeupService(context: Context) {
        if (!ActivityRecognitionService.isRunning()) {
            Log.d(TAG, "📢 The service is not running, waking it up...")

            val serviceIntent = Intent(context, ActivityRecognitionService::class.java).apply {
                action = ACTION_WAKEUP
            }

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
    }
}