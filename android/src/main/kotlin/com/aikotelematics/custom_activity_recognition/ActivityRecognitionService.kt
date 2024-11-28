package com.aikotelematics.custom_activity_recognition

import android.app.*
import android.content.Intent
import android.os.IBinder
import androidx.core.app.NotificationCompat
import android.os.Build
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityRecognitionService : Service() {

    companion object {
        private var isServiceRunning = false
        private const val ACTIVITY_REQUEST_CODE = 100
        private const val TRANSITION_REQUEST_CODE = 200
        private const val TAG = "ActivityService"
        private const val NOTIFICATION_ID = 4321
        private const val CHANNEL_ID = "activity_recognition_channel"

        fun isRunning() = isServiceRunning
    }

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var activityIntent: PendingIntent? = null
    private var transitionIntent: PendingIntent? = null
    private var currentActivity: String = "UNKNOWN"
    private var timestamp: Long = 0

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        createNotificationChannel()
        setupActivityRecognition()
        setupTransitionRecognition()
        isServiceRunning = true
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        startForeground(NOTIFICATION_ID, createNotification())

        Log.d(TAG, "onStartCommand: ${intent?.action}")
        when (intent?.action) {
            "UPDATE_ACTIVITY" -> {
                var changes = false
                val activityExtra = intent.getStringExtra("activity") ?: "UNKNOWN"
                val timestampExtra = intent.getLongExtra("timestamp", 0)
                if (timestampExtra > 0 && timestampExtra != timestamp) {
                    timestamp = timestampExtra
                    changes = true
                }

                if (activityExtra != currentActivity) {
                    changes = true
                    currentActivity = activityExtra
                }
                if (changes) {
                    updateNotification()
                }
            }
        }

        return START_STICKY
    }

    private fun setupActivityRecognition() {
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        activityIntent = PendingIntent.getBroadcast(
            this,
            ACTIVITY_REQUEST_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        activityIntent?.let {
            activityRecognitionClient.removeActivityUpdates(it)
                .addOnCompleteListener {
                    Log.d(TAG, "removeActivityUpdates success")
                    initActivityUpdates()
                }

        }
    }

    private fun initActivityUpdates() {
        activityRecognitionClient.requestActivityUpdates(15000, activityIntent!!)
            .addOnSuccessListener {
                Log.d(TAG, "requestActivityUpdates success")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error requestActivityUpdates: ${e.message}")
            }
    }

    private fun setupTransitionRecognition() {
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        transitionIntent = PendingIntent.getBroadcast(
            this,
            TRANSITION_REQUEST_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        transitionIntent?.let {
            activityRecognitionClient.removeActivityTransitionUpdates(it)
                .addOnCompleteListener {
                    Log.d(TAG, "removeActivityTransitionUpdates success")
                    initTransitionRequest()
                }

        }
    }

    private fun initTransitionRequest() {
        val transitions = mutableListOf<ActivityTransition>()

        val activityTypes = listOf(
            DetectedActivity.IN_VEHICLE,
            DetectedActivity.ON_BICYCLE,
            DetectedActivity.RUNNING,
            DetectedActivity.ON_FOOT,
            DetectedActivity.WALKING,
            DetectedActivity.STILL
        )

        for (activityType in activityTypes) {
            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_ENTER)
                    .build()
            )
        }

        val request = ActivityTransitionRequest(transitions)
        activityRecognitionClient.requestActivityTransitionUpdates(request, transitionIntent!!)
            .addOnSuccessListener {
                Log.d(TAG, "requestActivityTransitionUpdates success")
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error requestActivityTransitionUpdates: ${e.message}")
            }
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Activity Recognition"
            val descriptionText = "Tracking user activity"
            val importance = NotificationManager.IMPORTANCE_LOW
            val channel = NotificationChannel(CHANNEL_ID, name, importance).apply {
                description = descriptionText
            }
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    private fun createNotification(): Notification {
        val intent = packageManager.getLaunchIntentForPackage(packageName)?.apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }

        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        val timestampFormatted = if (timestamp > 0) { " (${formatTimestamp(timestamp)})" } else { "" }

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Recognition")
            .setContentText("$currentActivity$timestampFormatted")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .build()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    override fun onDestroy() {
        super.onDestroy()
        activityIntent?.let { pending ->
            activityRecognitionClient.removeActivityUpdates(pending)
        }
        transitionIntent?.let { pending ->
            activityRecognitionClient.removeActivityTransitionUpdates(pending)
        }
        isServiceRunning = false
        Log.d(TAG, "Service destroyed")
    }

}