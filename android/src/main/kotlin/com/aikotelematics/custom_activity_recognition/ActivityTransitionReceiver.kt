package com.aikotelematics.custom_activity_recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import com.google.android.gms.location.ActivityTransitionResult
import com.google.android.gms.location.DetectedActivity
import io.flutter.plugin.common.EventChannel

class ActivityTransitionReceiver : BroadcastReceiver() {
    companion object {
        private val TAG = ActivityTransitionReceiver::class.java.simpleName
        var eventSink: EventChannel.EventSink? = null
    }

    override fun onReceive(context: Context, intent: Intent) {

        if (ActivityTransitionResult.hasResult(intent)) {
            val result = ActivityTransitionResult.extractResult(intent)

            result?.let {

                for (event in result.transitionEvents) {
                    val activityType = getActivityType(event.activityType)
                    val data = mapOf<String, Any?>(
                        "activity" to activityType,
                        "timestamp" to System.currentTimeMillis()
                    )

                    eventSink?.success(data)
                }
            }
        }
    }

    private fun getActivityType(type: Int): String {
        when (type) {
            DetectedActivity.IN_VEHICLE -> return "IN_VEHICLE"
            DetectedActivity.ON_BICYCLE -> return "ON_BICYCLE"
            DetectedActivity.RUNNING -> return "RUNNING"
            DetectedActivity.ON_FOOT -> return "ON_FOOT"
            DetectedActivity.WALKING -> return "WALKING"
            DetectedActivity.TILTING -> return "TILTING"
            DetectedActivity.STILL -> return "STILL"
            else -> return "UNKNOWN"
        }
    }
}