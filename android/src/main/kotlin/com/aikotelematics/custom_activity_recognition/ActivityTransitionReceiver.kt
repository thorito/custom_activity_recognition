package com.aikotelematics.custom_activity_recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.SystemClock
import android.util.Log
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.flutter.plugin.common.EventChannel
import java.util.Date

class ActivityTransitionReceiver : BroadcastReceiver() {
    companion object {
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)

            result?.let {

                for (event in result.transitionEvents) {
                    val currentTimeMillis = System.currentTimeMillis()
                    val bootTimeMillis = currentTimeMillis - SystemClock.elapsedRealtime()
                    val eventTimeMillis = bootTimeMillis + (event.elapsedRealTimeNanos / 1_000_000)

                    val activityType = getActivityType(event.activityType)
                    val data = mapOf<String, Any?>(
                        "timestamp" to eventTimeMillis,
                        "activity" to activityType,
                    )

                    eventSink?.success(data)
                }
            }
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