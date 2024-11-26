package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.app.Activity
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build.VERSION.SDK_INT
import android.os.Build.VERSION_CODES
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.location.ActivityRecognition
import com.google.android.gms.location.ActivityTransition
import com.google.android.gms.location.ActivityTransitionRequest
import com.google.android.gms.location.DetectedActivity
import io.flutter.plugin.common.EventChannel

class ActivityRecognitionManager(private val context: Context) : EventChannel.StreamHandler {
    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
    }

    private var eventSink: EventChannel.EventSink? = null
    private val activityRecognitionClient = ActivityRecognition.getClient(context)
    private var pendingIntent: PendingIntent? = null

    private val transitions = listOf(
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.IN_VEHICLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.ON_BICYCLE)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.RUNNING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.ON_FOOT)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.WALKING)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
        ActivityTransition.Builder()
            .setActivityType(DetectedActivity.STILL)
            .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
            .build(),
    )

    fun isAvailable(): Boolean {
        return context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_COUNTER) &&
                context.packageManager.hasSystemFeature(PackageManager.FEATURE_SENSOR_STEP_DETECTOR)
    }

    fun requestPermissions(activity: Activity, callback: (Boolean) -> Unit) {

        if (SDK_INT >= VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(
                    context,
                    Manifest.permission.ACTIVITY_RECOGNITION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                ActivityCompat.requestPermissions(
                    activity,
                    arrayOf(Manifest.permission.ACTIVITY_RECOGNITION),
                    PERMISSION_REQUEST_CODE
                )
                callback(false)
            } else {
                callback(true)
            }
        } else {
            callback(true)
        }
    }

    fun startTracking(callback: (Boolean) -> Unit) {
        val intent = Intent(context, ActivityTransitionReceiver::class.java)
        pendingIntent = PendingIntent.getBroadcast(
            context,
            0,
            intent,
            if (SDK_INT >= VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val request = ActivityTransitionRequest(transitions)
        activityRecognitionClient
            .requestActivityTransitionUpdates(request, pendingIntent!!)
            .addOnSuccessListener {
                callback(true)
            }
            .addOnFailureListener {
                callback(false)
            }
    }

    fun stopTracking(callback: (Boolean) -> Unit) {
        pendingIntent?.let {
            activityRecognitionClient
                .removeActivityTransitionUpdates(it)
                .addOnSuccessListener {
                    pendingIntent = null
                    callback(true)
                }
                .addOnFailureListener {
                    callback(false)
                }
        } ?: callback(false)
    }

    override fun onListen(arguments: Any?, events: EventChannel.EventSink) {
        eventSink = events
        ActivityTransitionReceiver.eventSink = events
    }

    override fun onCancel(arguments: Any?) {
        eventSink = null
        ActivityTransitionReceiver.eventSink = null
    }
}