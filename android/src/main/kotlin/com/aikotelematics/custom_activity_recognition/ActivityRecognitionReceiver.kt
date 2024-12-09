package com.aikotelematics.custom_activity_recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.flutter.plugin.common.EventChannel

class ActivityRecognitionReceiver : BroadcastReceiver() {
    companion object {
        var eventSink: EventChannel.EventSink? = null
        private const val TAG = "ActivityReceiver"
    }

    override fun onReceive(context: Context, intent: Intent) {

        when {
            ActivityRecognitionResult.hasResult(intent) -> {
                handleActivityResult(context, ActivityRecognitionResult.extractResult(intent))
            }
            ActivityTransitionResult.hasResult(intent) -> {
                handleTransitionResult(context, ActivityTransitionResult.extractResult(intent))
            }
            else -> {
                Log.d(TAG, "Intent recibido sin resultado reconocible")
            }
        }
    }

    private fun handleActivityResult(context: Context, result: ActivityRecognitionResult?) {
        result?.let {
            val activity = it.mostProbableActivity
            val activityType = getActivityType(activity.type)
            Log.d(TAG, "⭕️ Activity detected: $activityType, " +
                    "confidence: ${activity.confidence}")
            if (activity.confidence > ActivityRecognitionService.confidenceThreshold) {
                val timestamp = it.time
                sendActivityUpdate(context, activityType, timestamp)
            }
        }
    }

    private fun handleTransitionResult(context: Context, result: ActivityTransitionResult?) {
        result?.let {
            for (event in result.transitionEvents) {
                val activityType = getActivityType(event.activityType)
                val transitionType = getTransitionType(event.transitionType)
                val currentTimeMillis = System.currentTimeMillis()
                val bootTimeMillis = currentTimeMillis - SystemClock.elapsedRealtime()
                val timestamp = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)
                Log.d(TAG, "⭕️ Activity transition detected: $activityType ($transitionType)")
                sendActivityUpdate(context, activityType, timestamp)
            }
        }
    }

    private fun sendActivityUpdate(context: Context, activityType: String, timestamp: Long) {

        val data = mapOf<String, Any?>(
            "timestamp" to System.currentTimeMillis(),
            "activity" to activityType,
        )

        eventSink?.success(data)

        val serviceIntent = Intent(context, ActivityRecognitionService::class.java).apply {
            action = "UPDATE_ACTIVITY"
            putExtra("activity", activityType)
            putExtra("timestamp", timestamp)
        }

        if (ActivityRecognitionService.isRunning()) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
                context.startForegroundService(serviceIntent)
            } else {
                context.startService(serviceIntent)
            }
        }
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
}