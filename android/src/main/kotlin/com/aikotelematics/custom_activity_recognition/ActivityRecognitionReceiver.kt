package com.aikotelematics.custom_activity_recognition

import android.annotation.SuppressLint
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.aikotelematics.custom_activity_recognition.Constants.ACTION_WAKEUP
import com.aikotelematics.custom_activity_recognition.Constants.TAG
import com.aikotelematics.custom_activity_recognition.Constants.UPDATE_ACTIVITY
import com.google.android.gms.location.ActivityRecognition
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

        @SuppressLint("ConstantLocale")
        private val timeFormat = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
    }

    override fun onReceive(context: Context, intent: Intent) {
        try {
            when {
                ActivityTransitionResult.hasResult(intent) -> {
                    handleTransitionResult(context, ActivityTransitionResult.extractResult(intent))
                }

                ActivityRecognitionResult.hasResult(intent) -> {
                    val isInitialState = intent.getBooleanExtra("isInitialState", false)
                    val result = ActivityRecognitionResult.extractResult(intent)
                    if (result != null) {
                        if (isInitialState) {
                            // Handle initial state request
                            handleInitialState(context, result)
                            // Create a new pending intent for removal
                            val removeIntent =
                                Intent(context, ActivityRecognitionReceiver::class.java)
                            val pendingIntent = PendingIntent.getBroadcast(
                                context,
                                0,
                                removeIntent,
                                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                            )
                            // Remove updates after getting the initial state
                            ActivityRecognition.getClient(context)
                                .removeActivityUpdates(pendingIntent)
                        } else {
                            handleActivityResult(context, result)
                        }
                    } else {
                        Log.e(TAG, "Received null ActivityRecognitionResult")
                    }
                }

                intent.action == "GET_INITIAL_STATE" -> {
                    // This is a request to get the initial state
                    // The actual handling will be done when we receive the activity result
                    val requestIntent =
                        Intent(context, ActivityRecognitionReceiver::class.java).apply {
                            putExtra("isInitialState", true)
                        }
                    val pendingIntent = PendingIntent.getBroadcast(
                        context,
                        0,
                        requestIntent,
                        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
                    )
                    ActivityRecognition.getClient(context).requestActivityUpdates(0, pendingIntent)
                }

                else -> {
                    // Nothing
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error in onReceive: ${e.message}", e)
        }

        checkAndWakeupService(context)
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

                if (activityType != lastActivityType) {
                    sendActivityUpdate(context, activityType, timestamp)
                    lastActivityType = activityType;
                }
            }
        }
    }

    private fun handleInitialState(context: Context, result: ActivityRecognitionResult?) {
        if (result == null) {
            Log.e(TAG, "Received null result in handleInitialState")
            return
        }
        val activity = result.mostProbableActivity
        val activityType = getActivityType(activity.type)
        val timestamp = result.time

        Log.d(TAG, "Initial activity state: $activityType (${activity.confidence}%)")

        // Always send initial state, even if it's the same as last activity
        sendActivityUpdate(context, activityType, timestamp, true)
        lastActivityType = activityType
    }
    
    private fun handleActivityResult(context: Context, result: ActivityRecognitionResult?) {
        result?.let {
            val activity = it.mostProbableActivity
            val activityType = getActivityType(activity.type)
            val confidence = activity.confidence
            val hasSufficientConfidence =
                confidence >= ActivityRecognitionService.confidenceThreshold
            val time = timeFormat.format(Date(it.time))
            val icon = if (hasSufficientConfidence) "🟢" else "🔴"

            Log.d(
                TAG, "$icon Activity detected [$time]: $activityType, " +
                        "confidence: $confidence"
            )

            // Only update if:
            // 1. We have sufficient confidence
            // 2. It's not a TILTING activity (false positive)
            // 3. The activity type has changed
            if (hasSufficientConfidence &&
                activity.type != DetectedActivity.TILTING &&
                activityType != lastActivityType
            ) {
                val timestamp = it.time
                lastActivityType = activityType

                // Only send update if we have a valid activity type
                if (activityType != "UNKNOWN") {
                    sendActivityUpdate(context, activityType, timestamp, false)
                    // Update last known activity in manager
                    if (context is android.content.Context) {
                        val plugin = context.applicationContext as? CustomActivityRecognitionPlugin
                        val manager = plugin?.getActivityRecognitionManager()
                        manager?.updateLastKnownActivity(activityType)
                    }
                }
            }
        }
    }

    private fun sendActivityUpdate(
        context: Context,
        activityType: String,
        timestamp: Long,
        isInitialState: Boolean = false
    ) {
        try {
            val data = mapOf<String, Any?>(
                "timestamp" to timestamp,
                "activity" to activityType,
                "systemTime" to System.currentTimeMillis(),
                "isInitialState" to isInitialState
            )

            Log.d(TAG, "Sending activity update: $activityType at ${java.util.Date(timestamp)}")

            // Intentar enviar a través del eventSink
            eventSink?.let { sink ->
                try {
                    sink.success(data)
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending event through eventSink: ${e.message}")
                }
            } ?: run {
                Log.w(TAG, "eventSink is null, cannot send activity update")
                // Si el eventSink es nulo, intentar enviar a través de un broadcast
                try {
                    val intent = Intent("ACTIVITY_UPDATE").apply {
                        putExtra("activity", activityType)
                        putExtra("timestamp", timestamp)
                    }
                    context.sendBroadcast(intent)
                    Log.d(TAG, "Broadcast sent as fallback")
                } catch (e: Exception) {
                    Log.e(TAG, "Error sending broadcast: ${e.message}")
                }
            }

            updateNotification(context, activityType, timestamp)
        } catch (e: Exception) {
            Log.e(TAG, "Error in sendActivityUpdate: ${e.message}", e)
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