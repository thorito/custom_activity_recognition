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
import android.graphics.Color

class ActivityRecognitionService : Service() {

    companion object {
        private var isServiceRunning = false
        private var isActivityRecognitionConfigured = false
        private var isTransitionRecognitionConfigured = false
        private const val ACTIVITY_REQUEST_CODE = 100
        private const val TRANSITION_REQUEST_CODE = 200
        private const val TAG = "ActivityService"
        private const val NOTIFICATION_ID = 4321
        private const val CHANNEL_ID = "activity_recognition_channel"
        private const val SILENT_CHANNEL_ID = "activity_recognition_silent_channel"

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

        createNotificationChannel()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, createNotification(), ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION)
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error starting foreground service: ${e.message}")
        }

        handler.postDelayed(healthCheckRunnable, 5 * 60 * 1000) // Check every 5 minutes
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        showNotification = intent?.getBooleanExtra("showNotification", true) ?: true

        if (!isTransitionRecognitionConfigured) {
            useTransitionRecognition = intent?.getBooleanExtra("useTransitionRecognition", false) ?: false
            if (useTransitionRecognition) {
                acquireWakeLock()
                setupTransitionRecognition()
                isTransitionRecognitionConfigured = true
            }
        }

        if (!isActivityRecognitionConfigured) {
            useActivityRecognition =
                intent?.getBooleanExtra("useActivityRecognition", true) ?: true
            detectionIntervalMillis =
                intent?.getIntExtra("detectionIntervalMillis", 0) ?: 0
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

                if (changes) {
                    updateNotification()
                }
            }
        }

        updateNotification()

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

        updateNotification()
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
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val normalChannel = NotificationChannel(
                CHANNEL_ID,
                "Activity Recognition",
                NotificationManager.IMPORTANCE_LOW
            ).apply {
                description = "Tracking user activity"
            }

            val silentChannel = NotificationChannel(
                SILENT_CHANNEL_ID,
                "Silent Activity Recognition",
                NotificationManager.IMPORTANCE_MIN
            ).apply {
                description = "Silent tracking"
                setShowBadge(false)
                enableLights(false)
                enableVibration(false)
                lockscreenVisibility = Notification.VISIBILITY_SECRET
            }

            notificationManager.createNotificationChannel(normalChannel)
            notificationManager.createNotificationChannel(silentChannel)
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

        val channelId = if (showNotification) CHANNEL_ID else SILENT_CHANNEL_ID

        val timestampFormatted = if (timestamp > 0 && showNotification) {
            " (${formatTimestamp(timestamp)})"
        } else {
            ""
        }

        val builder = NotificationCompat.Builder(this, channelId)
            .setContentIntent(pendingIntent)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)

        if (showNotification) {
            builder.setContentTitle("Activity Recognition")
                .setContentText("$currentActivity$timestampFormatted")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .setCategory(NotificationCompat.CATEGORY_SERVICE)
        } else {
            builder.setContentTitle("")
                .setContentText("")
                .setSmallIcon(android.R.drawable.ic_menu_compass)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setColor(Color.TRANSPARENT)
                .setOngoing(true)
                .setSilent(true)
        }

        return builder.build()
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