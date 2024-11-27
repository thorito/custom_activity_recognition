package com.aikotelematics.custom_activity_recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityRecognitionResult
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
            ActivityTransitionResult.hasResult(intent) -> {
                handleTransitionResult(context, ActivityTransitionResult.extractResult(intent))
            }
            ActivityRecognitionResult.hasResult(intent) -> {
                handleActivityResult(context, ActivityRecognitionResult.extractResult(intent))
            }
            else -> {
                Log.d(TAG, "Intent recibido sin resultado reconocible")
            }
        }
    }

    private fun handleTransitionResult(context: Context, result: ActivityTransitionResult?) {
        result?.let {
            for (event in result.transitionEvents) {
                val activityType = getActivityType(event.activityType)
                val currentTimeMillis = System.currentTimeMillis()
                val bootTimeMillis = currentTimeMillis - SystemClock.elapsedRealtime()
                val timestamp = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)

                sendActivityUpdate(context, activityType, timestamp)
            }
        }
    }

    private fun handleActivityResult(context: Context, result: ActivityRecognitionResult?) {
        result?.let {
            val activity = it.mostProbableActivity
            if (activity.confidence >= 50) {
                val activityType = getActivityType(activity.type)
                val timestamp = it.time
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

        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            context.startForegroundService(serviceIntent)
        } else {
            context.startService(serviceIntent)
        }
    }

    private fun getActivityType(type: Int): String {
        return when (type) {
            DetectedActivity.IN_VEHICLE -> "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> "ON_BICYCLE"
            DetectedActivity.RUNNING -> "RUNNING"
            DetectedActivity.ON_FOOT -> "ON_FOOT"
            DetectedActivity.WALKING -> "WALKING"
            DetectedActivity.TILTING -> "TILTING"
            DetectedActivity.STILL -> "STILL"
            else -> "UNKNOWN"
        }
    }
}