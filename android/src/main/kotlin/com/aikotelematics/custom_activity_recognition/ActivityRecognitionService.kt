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
import android.content.pm.ServiceInfo
import android.os.PowerManager

class ActivityRecognitionService : Service() {

    companion object {
        private var isServiceRunning = false
        private var isNotificationChannelCreated = false
        private var isActivityRecognitionConfigured = false
        private var isTransitionRecognitionConfigured = false
        private const val ACTIVITY_REQUEST_CODE = 100
        private const val TRANSITION_REQUEST_CODE = 200
        private const val TAG = "ActivityService"
        private const val NOTIFICATION_ID = 4321
        private const val CHANNEL_ID = "activity_recognition_channel"

        var confidenceThreshold: Int = 50
        fun isRunning() = isServiceRunning
    }

    private var wakeLock: PowerManager.WakeLock? = null
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private var showNotification: Boolean = true
    private var useTransitionRecognition: Boolean = true
    private var useActivityRecognition: Boolean = false
    private var detectionIntervalMillis: Int = 10000
    private var activityIntent: PendingIntent? = null
    private var transitionIntent: PendingIntent? = null
    private var currentActivity: String = "UNKNOWN"
    private var timestamp: Long = 0
    private val handler = Handler(Looper.getMainLooper())
    private val healthCheckRunnable = object : Runnable {
        override fun run() {
            scheduleServiceHealthCheck()
            handler.postDelayed(this, 15 * 60 * 1000) // Check every 15 minutes
        }
    }

    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        isServiceRunning = true

        handler.postDelayed(healthCheckRunnable, 5 * 60 * 1000) // Check every 5 minutes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (!isNotificationChannelCreated) {
            showNotification = intent?.getBooleanExtra("showNotification", true) ?: true
            createNotificationChannel()
            isNotificationChannelCreated = true
        }

        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
            if (!showNotification) {
//                removeForegroundNotification()
            }
        }

        if (!isTransitionRecognitionConfigured) {
            useTransitionRecognition = intent?.getBooleanExtra("useTransitionRecognition", true) ?: true
            if (useTransitionRecognition) {
                acquireWakeLock()
                setupTransitionRecognition()
                isTransitionRecognitionConfigured = true
            }
        }

        if (!isActivityRecognitionConfigured) {
            useActivityRecognition =
                intent?.getBooleanExtra("useActivityRecognition", false) ?: false
            detectionIntervalMillis =
                intent?.getIntExtra("detectionIntervalMillis", 10000) ?: 10000
            confidenceThreshold = intent?.getIntExtra("confidenceThreshold", 50) ?: 50


            if (useActivityRecognition) {
                acquireWakeLock()
                setupActivityRecognition()
                isActivityRecognitionConfigured = true
            }
        }

        Log.d(TAG, "onStartCommand: ${intent?.action}, " +
                "useTransitionRecognition: $useTransitionRecognition, " +
                "useActivityRecognition: $useActivityRecognition, " +
                "detectionIntervalMillis: $detectionIntervalMillis, " +
                "confidenceThreshold: $confidenceThreshold")

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

                if (changes && showNotification) {
                    updateNotification()
                }
            }
        }

        if (!showNotification) {
            removeForegroundNotification()
        }

        return START_STICKY
    }

    private fun scheduleServiceHealthCheck() {
        if (!isServiceRunning) {
            return
        }

        Log.d(TAG, "Performing service health check")

        // Verificar estado de las configuraciones
        if (useTransitionRecognition && !isTransitionRecognitionConfigured) {
            Log.d(TAG, "Reinitializing transition recognition")
            setupTransitionRecognition()
        }

        if (useActivityRecognition && !isActivityRecognitionConfigured) {
            Log.d(TAG, "Reinitializing activity recognition")
            setupActivityRecognition()
        }

        // Verificar que la notificación sea visible si debe serlo
        if (showNotification) {
            updateNotification()
        }
    }

    private fun removeForegroundNotification() {
        Handler(Looper.getMainLooper()).postDelayed({
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                stopForeground(STOP_FOREGROUND_REMOVE)
            } else {
                @Suppress("DEPRECATION")
                stopForeground(true)
            }
        }, 1000)
    }

    override fun onDestroy() {

        handler.removeCallbacks(healthCheckRunnable)

        releaseWakeLock()

        activityIntent?.let { pending ->
            activityRecognitionClient.removeActivityUpdates(pending)
                .addOnCompleteListener {
                    Log.d(TAG, "Activity updates removed")
                }
        }
        transitionIntent?.let { pending ->
            activityRecognitionClient.removeActivityTransitionUpdates(pending)
                .addOnCompleteListener {
                    Log.d(TAG, "Transition updates removed")
                }
        }

        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(NOTIFICATION_ID)

        isServiceRunning = false
        isActivityRecognitionConfigured = false
        isTransitionRecognitionConfigured = false
        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val name = "Activity Recognition"
            val channel = if (showNotification) {
                val descriptionText = "Tracking user activity"
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_LOW).apply {
                    description = descriptionText
                }
            } else {
                NotificationChannel(CHANNEL_ID, name, NotificationManager.IMPORTANCE_MIN).apply {
                    setShowBadge(false)
                }
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

        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Activity Recognition")
            .setContentText("$currentActivity$timestampFormatted")
            .setSmallIcon(android.R.drawable.ic_menu_compass)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (!showNotification) {
            notification.setContentTitle("")
            notification.setContentText("")
            notification.setPriority(NotificationCompat.PRIORITY_MIN)
            notification.setVisibility(NotificationCompat.VISIBILITY_SECRET)
        }

        return notification.build()
    }

    private fun formatTimestamp(timestamp: Long): String {
        val sdf = SimpleDateFormat("HH:mm:ss", Locale.getDefault())
        return sdf.format(Date(timestamp))
    }

    private fun updateNotification() {
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun setupTransitionRecognition() {
        Log.d(TAG, "setupTransitionRecognition")
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
                releaseWakeLock()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error requestActivityTransitionUpdates: ${e.message}")
                releaseWakeLock()
            }
    }

    private fun setupActivityRecognition() {
        Log.d(TAG, "setupActivityRecognition")
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
        activityRecognitionClient.requestActivityUpdates(detectionIntervalMillis.toLong(), activityIntent!!)
            .addOnSuccessListener {
                Log.d(TAG, "requestActivityUpdates success")
                releaseWakeLock()
            }
            .addOnFailureListener { e ->
                Log.e(TAG, "Error requestActivityUpdates: ${e.message}")
                releaseWakeLock()
            }
    }

    private fun acquireWakeLock() {
        if (wakeLock == null) {
            val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ActivityRecognition:WakeLock"
            )
            wakeLock?.setReferenceCounted(false)
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(3 * 60 * 1000L) // 3 minutes
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

}