package com.aikotelematics.custom_activity_recognition

import android.annotation.SuppressLint
import android.app.*
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.util.Log
import androidx.core.app.NotificationCompat
import com.aikotelematics.custom_activity_recognition.CustomActivityRecognitionPlugin.Companion.DEFAULT_CONFIDENCE_THRESHOLD
import com.aikotelematics.custom_activity_recognition.CustomActivityRecognitionPlugin.Companion.DEFAULT_DETECTION_INTERVAL_MILLIS
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityRecognitionService : Service() {

    companion object {
        private const val TAG = "ActivityRecognitionService"
        private const val ACTIVITY_REQUEST_CODE = 200
        private const val TRANSITION_REQUEST_CODE = 201

        private const val NOTIFICATION_ID = 7502
        private const val CHANNEL_ID = "activity_recognition_channel"
        private const val SILENT_CHANNEL_ID = "activity_recognition_silent_channel"
        private var isServiceRunning = false
        private var isTransitionRecognitionConfigured = false
        private var isActivityRecognitionConfigured = false

        var detectionIntervalMillis: Int = DEFAULT_DETECTION_INTERVAL_MILLIS
        var confidenceThreshold: Int = DEFAULT_CONFIDENCE_THRESHOLD

        fun isRunning() = isServiceRunning
    }

    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var notificationManager: NotificationManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var showNotification: Boolean = true
    private var useTransitionRecognition: Boolean = true
    private var useActivityRecognition: Boolean = true
    private var activityIntent: PendingIntent? = null
    private var transitionIntent: PendingIntent? = null
    private var currentActivity: String = "UNKNOWN"
    private var timestamp: Long = 0

    // Heartbeat mechanism for monitoring activity recognition
    private var initHeatbeat = false
    private val heartbeatHandler = Handler(Looper.getMainLooper())

    private val heartbeatRunnable = object : Runnable {
        override fun run() {
            // Check if we've been in STILL or UNKNOWN state for a long time
            if ((currentActivity == "STILL" || currentActivity == "UNKNOWN") &&
                System.currentTimeMillis() - timestamp > 30 * 60 * 1000
            ) {
                // Only restart if we've been in STILL for 30 minutes or more
                Log.d(TAG, "Long period in STILL or UNKNOWN state, verifying recognition system")

                // Verify and restart only if necessary
                if (isRunning() && useTransitionRecognition) {
                    Log.d(TAG, "Transition recognition not configured, setting up...")
                    setupTransitionRecognition()
                }

                if (isRunning() && useActivityRecognition) {
                    Log.d(TAG, "Activity recognition not configured, setting up...")
                    setupActivityRecognition()
                }

                // Schedule next check in 30 minutes if still in STILL state
                heartbeatHandler.postDelayed(this, 30 * 60 * 1000L)
            } else {
                // If not in STILL state or only for a short time, check again in 2 hours
                Log.d(TAG, "Normal activity state, scheduling next check in 2 hours")
                heartbeatHandler.postDelayed(this, 2 * 60 * 60 * 1000L)
            }
        }
    }

    // Start the heartbeat monitoring
    private fun startHeartbeat() {
        Log.d(TAG, "Starting heartbeat monitoring")
        heartbeatHandler.postDelayed(heartbeatRunnable, 60 * 60 * 1000L) // First check in 1 hour
    }

    // Stop the heartbeat monitoring
    private fun stopHeartbeat() {
        Log.d(TAG, "Stopping heartbeat monitoring")
        heartbeatHandler.removeCallbacks(heartbeatRunnable)
    }

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        isServiceRunning = true

        notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        createNotificationChannel()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                NOTIFICATION_ID,
                createNotification(),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH or
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION
            )
        } else {
            startForeground(NOTIFICATION_ID, createNotification())
        }

        // Start heartbeat monitoring
        if (!initHeatbeat) {
            initHeatbeat = true
            startHeartbeat()
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        if (intent != null && intent.hasExtra("showNotification")) {
            showNotification = intent.getBooleanExtra("showNotification", true)
        }

        if (intent != null && intent.hasExtra("useTransitionRecognition")) {
            useTransitionRecognition = intent.getBooleanExtra("useTransitionRecognition", true)
        }

        if (intent != null && intent.hasExtra("useActivityRecognition")) {
            useActivityRecognition = intent.getBooleanExtra("useActivityRecognition", true)
        }

        if (intent != null && intent.hasExtra("detectionIntervalMillis")) {
            detectionIntervalMillis = intent.getIntExtra("detectionIntervalMillis", DEFAULT_DETECTION_INTERVAL_MILLIS)
        }

        if (intent != null && intent.hasExtra("confidenceThreshold")) {
            confidenceThreshold = intent.getIntExtra("confidenceThreshold", DEFAULT_CONFIDENCE_THRESHOLD)
        }

        if (!isTransitionRecognitionConfigured && useTransitionRecognition) {
            acquireWakeLock()
            setupTransitionRecognition()
            isTransitionRecognitionConfigured = true
        }

        if (!isActivityRecognitionConfigured && useActivityRecognition) {
            acquireWakeLock()
            setupActivityRecognition()
            isActivityRecognitionConfigured = true
        }


        Log.d(
            TAG, "onStartCommand: ${intent?.action}, " +
                    "showNotification: $showNotification, " +
                    "useTransitionRecognition: $useTransitionRecognition, " +
                    "useActivityRecognition: $useActivityRecognition, " +
                    "detectionIntervalMillis: $detectionIntervalMillis, " +
                    "confidenceThreshold: $confidenceThreshold"
        )

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

    override fun onDestroy() {
        stopHeartbeat()

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

        notificationManager.cancel(NOTIFICATION_ID)

        releaseWakeLock()
        initHeatbeat = false
        isServiceRunning = false
        isActivityRecognitionConfigured = false
        isTransitionRecognitionConfigured = false

        Log.d(TAG, "Service destroyed")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved")

        activityIntent?.let { pending ->
            activityRecognitionClient.removeActivityUpdates(pending)
                .addOnCompleteListener {
                    Log.d(TAG, "Activity updates removed due to task removal")
                }
        }
        transitionIntent?.let { pending ->
            activityRecognitionClient.removeActivityTransitionUpdates(pending)
                .addOnCompleteListener {
                    Log.d(TAG, "Transition updates removed due to task removal")
                }
        }

        stopHeartbeat()
        releaseWakeLock()
        stopSelf()
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
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
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setOngoing(true)
                .setAutoCancel(false)
        } else {
            builder.setContentTitle("")
                .setContentText("")
                .setSmallIcon(R.drawable.ic_notification)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
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
        notificationManager.notify(NOTIFICATION_ID, createNotification())
    }

    private fun setupTransitionRecognition() {
        Log.d(TAG, "setupTransitionRecognition")
        val intent = Intent(this, ActivityRecognitionReceiver::class.java)
        intent.action = "com.aikotelematics.activity_transition"

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
        acquireWakeLock()
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

            transitions.add(
                ActivityTransition.Builder()
                    .setActivityType(activityType)
                    .setActivityTransition(ActivityTransition.ACTIVITY_TRANSITION_EXIT)
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
        intent.action = "com.aikotelematics.activity_recognition"
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
        acquireWakeLock()
        activityRecognitionClient.requestActivityUpdates(
            detectionIntervalMillis.toLong(),
            activityIntent!!
        )
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
            val powerManager = getSystemService(POWER_SERVICE) as PowerManager
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ActivityRecognition:WakeLock"
            )
            wakeLock?.setReferenceCounted(false)
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(30000L) // 30 seconds
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