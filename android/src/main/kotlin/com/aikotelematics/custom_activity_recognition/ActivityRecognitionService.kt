package com.aikotelematics.custom_activity_recognition

import android.Manifest
import android.annotation.SuppressLint
import android.app.*
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import android.os.SystemClock
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.aikotelematics.custom_activity_recognition.Contants.ACTION_WAKEUP
import com.aikotelematics.custom_activity_recognition.Contants.DEFAULT_CONFIDENCE_THRESHOLD
import com.aikotelematics.custom_activity_recognition.Contants.DEFAULT_DETECTION_INTERVAL_MILLIS
import com.aikotelematics.custom_activity_recognition.Contants.TAG
import com.aikotelematics.custom_activity_recognition.Contants.UPDATE_ACTIVITY
import com.google.android.gms.location.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class ActivityRecognitionService : Service() {

    companion object {
        private const val ACTIVITY_REQUEST_CODE = 200
        private const val TRANSITION_REQUEST_CODE = 201
        private const val WAKEUP_REQUEST_CODE = 202
        private const val HEALTH_CHECK_REQUEST_CODE = 203

        private const val NOTIFICATION_ID = 7502
        private const val ACTION_HEALTH_CHECK = "com.aikotelematics.HEALTH_CHECK_ACTION"
        private const val CHANNEL_ID = "activity_recognition_channel"
        private const val SILENT_CHANNEL_ID = "activity_recognition_silent_channel"

        private const val WAKEUP_INTERVAL = 5 * 60 * 1000L // 5 minutes
        private const val WAKELOCK_TIMEOUT = 5 * 60 * 1000L // 5 minutes
        private const val HEALTH_CHECK_INTERVAL = 30 * 60 * 1000L // 30 minutes
        private var isServiceRunning = false

        private var isTransitionRecognitionConfigured = false
        private var healthCheckIntent: PendingIntent? = null
        private var isActivityRecognitionConfigured = false
        var detectionIntervalMillis: Int = DEFAULT_DETECTION_INTERVAL_MILLIS

        var confidenceThreshold: Int = DEFAULT_CONFIDENCE_THRESHOLD
        fun isRunning() = isServiceRunning

        fun scheduleNextHealthCheck(context: Context) {
            val alarmManager = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
            val intent = Intent(context, ActivityRecognitionHealthReceiver::class.java).apply {
                action = ACTION_HEALTH_CHECK
            }

            healthCheckIntent?.let { pending ->
                alarmManager.cancel(pending)
                Log.d(TAG, "Health check alarm cancelled")
            }

            healthCheckIntent = PendingIntent.getBroadcast(
                context,
                HEALTH_CHECK_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT
                }
            )

            healthCheckIntent?.let { pending ->

                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    try {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                            if (!alarmManager.canScheduleExactAlarms()) {
                                Log.w(
                                    TAG,
                                    "Cannot schedule exact alarms. Using inexact repeating for health check."
                                )
                                alarmManager.setInexactRepeating(
                                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                    SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL,
                                    HEALTH_CHECK_INTERVAL,
                                    pending
                                )
                                return@let
                            }
                        }
                        alarmManager.setExactAndAllowWhileIdle(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL,
                            pending
                        )
                        Log.d(TAG, "Next health check scheduled in $HEALTH_CHECK_INTERVAL ms")
                    } catch (e: SecurityException) {
                        Log.e(TAG, "SecurityException scheduling health check: ${e.message}")
                        alarmManager.setInexactRepeating(
                            AlarmManager.ELAPSED_REALTIME_WAKEUP,
                            SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL,
                            HEALTH_CHECK_INTERVAL,
                            pending
                        )
                    }
                } else {
                    alarmManager.setExact(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        SystemClock.elapsedRealtime() + HEALTH_CHECK_INTERVAL,
                        pending
                    )
                    Log.d(TAG, "Next health check scheduled in $HEALTH_CHECK_INTERVAL ms (pre-M)")
                }
            }
        }
    }

    private lateinit var alarmManager: AlarmManager
    private lateinit var activityRecognitionClient: ActivityRecognitionClient
    private lateinit var notificationManager: NotificationManager
    private lateinit var powerManager: PowerManager
    private lateinit var handler: android.os.Handler

    private var wakeLock: PowerManager.WakeLock? = null
    private var showNotification: Boolean = true
    private var useTransitionRecognition: Boolean = true
    private var useActivityRecognition: Boolean = true
    private var activityIntent: PendingIntent? = null
    private var transitionIntent: PendingIntent? = null
    private var wakeupIntent: PendingIntent? = null
    private var currentActivity: String = "UNKNOWN"
    private var timestamp: Long = 0

    @SuppressLint("InlinedApi")
    override fun onCreate() {
        super.onCreate()
        activityRecognitionClient = ActivityRecognition.getClient(this)
        isServiceRunning = true

        notificationManager =
            getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        alarmManager = getSystemService(Context.ALARM_SERVICE) as AlarmManager
        powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        handler = android.os.Handler(android.os.Looper.getMainLooper())

        createNotificationChannel()

        // Check if we have at least location or activity recognition permissions
        val hasLocation = hasLocationPermissions()
        val hasActivityRecognition = hasActivityRecognitionPermission()

        if (!hasLocation && !hasActivityRecognition) {
            Log.e(
                TAG,
                "Service cannot start - missing both location and activity recognition permissions"
            )
            stopSelf()
            return
        }

        // Log available permissions for debugging
        Log.d(
            TAG,
            "Starting service with permissions - Location: $hasLocation, ActivityRecognition: $hasActivityRecognition"
        )

        // Start foreground BEFORE setting up alarms and health checks
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val foregroundServiceType = determineForegroundServiceType()
                Log.d(TAG, "Starting foreground service with type: $foregroundServiceType")

                startForeground(
                    NOTIFICATION_ID,
                    createNotification(),
                    foregroundServiceType
                )
            } else {
                startForeground(NOTIFICATION_ID, createNotification())
            }
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException when starting foreground service: ${e.message}")
            Log.e(TAG, "Location permission granted: $hasLocation")
            Log.e(TAG, "Activity Recognition permission granted: $hasActivityRecognition")
            stopSelf()
            return
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected exception when starting foreground service: ${e.message}")
            stopSelf()
            return
        }

        // Setup alarms and health checks AFTER becoming foreground
        setupWakeupAlarm()
        setupHealthCheck()
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.d(TAG, "onStartCommand: ${intent?.action}")

        if (intent?.action == ACTION_WAKEUP) {
            // Validate that Flutter process is alive before processing wakeup
            if (!shouldProcessWakeup()) {
                Log.w(
                    TAG,
                    "onStartCommand: Flutter process is dead or tracker not registered, " +
                            "stopping service to prevent unwanted restart after force-stop"
                )
                stopSelf()
                return START_NOT_STICKY
            }

            Log.d(TAG, "onStartCommand: Wakeup validation passed, processing wakeup")
            handleWakeup()
            scheduleNextWakeup()
            scheduleNextHealthCheck(this)
            return START_STICKY
        }

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
            detectionIntervalMillis =
                intent.getIntExtra("detectionIntervalMillis", DEFAULT_DETECTION_INTERVAL_MILLIS)
        }

        if (intent != null && intent.hasExtra("confidenceThreshold")) {
            confidenceThreshold =
                intent.getIntExtra("confidenceThreshold", DEFAULT_CONFIDENCE_THRESHOLD)
        }

        if (!isActivityRecognitionConfigured) {
            Log.d(
                TAG, "onStartCommand: ${intent?.action}, " +
                        "showNotification: $showNotification, " +
                        "useTransitionRecognition: $useTransitionRecognition, " +
                        "useActivityRecognition: $useActivityRecognition, " +
                        "detectionIntervalMillis: $detectionIntervalMillis, " +
                        "confidenceThreshold: $confidenceThreshold"
            )
        }

        if (!isTransitionRecognitionConfigured && useTransitionRecognition) {
            setupTransitionRecognition()
            isTransitionRecognitionConfigured = true
        }

        if (!isActivityRecognitionConfigured && useActivityRecognition) {
            setupActivityRecognition()
            isActivityRecognitionConfigured = true
        }

        when (intent?.action) {
            UPDATE_ACTIVITY -> {
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
        Log.d(TAG, "Service onDestroy - cleaning up all resources")

        // Cancel all alarms first
        cancelCheckHealthAlarm()
        cancelWakeupAlarm()

        // Clean up activity recognition subscriptions
        cleanupRecognition()

        // Cancel notification
        notificationManager.cancel(NOTIFICATION_ID)

        // Release wake lock and clear handlers
        releaseWakeLock()
        handler.removeCallbacksAndMessages(null)

        // Update service state
        isServiceRunning = false
        isActivityRecognitionConfigured = false
        isTransitionRecognitionConfigured = false

        Log.d(TAG, "Service destroyed - all resources cleaned up")
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        Log.d(TAG, "onTaskRemoved - ensuring wakeup alarm continues")

        scheduleNextWakeup()
        scheduleNextHealthCheck(this)
    }

    private fun setupWakeupAlarm() {
        val intent = Intent(this, ActivityRecognitionService::class.java).apply {
            action = ACTION_WAKEUP
        }

        wakeupIntent = PendingIntent.getService(
            this,
            WAKEUP_REQUEST_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        scheduleNextWakeup()
    }

    private fun hasLocationPermissions(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.ACCESS_FINE_LOCATION
        ) == PackageManager.PERMISSION_GRANTED ||
                ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasActivityRecognitionPermission(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.ACTIVITY_RECOGNITION
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true // Not required before Android 10
        }
    }

    private fun determineForegroundServiceType(): Int {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            return 0 // No service type needed before Android 10
        }

        val hasLocation = hasLocationPermissions()
        val hasActivityRecognition = hasActivityRecognitionPermission()

        return when {
            hasLocation && hasActivityRecognition ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION or ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH

            hasLocation ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_LOCATION

            hasActivityRecognition ->
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH

            else -> {
                Log.w(TAG, "No required permissions available for foreground service")
                ServiceInfo.FOREGROUND_SERVICE_TYPE_HEALTH // Fallback to health type
            }
        }
    }

    private fun scheduleNextWakeup() {
        wakeupIntent?.let { pending ->
            val triggerTime = SystemClock.elapsedRealtime() + WAKEUP_INTERVAL

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                        val canSchedule = alarmManager.canScheduleExactAlarms()
                        if (!canSchedule) {
                            Log.e(TAG, "Exact alarms cannot be set")
                            alarmManager.setInexactRepeating(
                                AlarmManager.ELAPSED_REALTIME_WAKEUP,
                                triggerTime,
                                WAKEUP_INTERVAL,
                                pending
                            )
                            return
                        }
                    }

                    alarmManager.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        pending
                    )
                } catch (se: SecurityException) {
                    Log.e(TAG, "SecurityException: ${se.message}")

                    alarmManager.setInexactRepeating(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP,
                        triggerTime,
                        WAKEUP_INTERVAL,
                        pending
                    )
                }
            } else {
                alarmManager.setExact(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP,
                    triggerTime,
                    pending
                )
            }

            Log.d(TAG, "Next wakeup scheduled in $WAKEUP_INTERVAL ms")
        }
    }

    private fun cancelWakeupAlarm() {
        wakeupIntent?.let { pending ->
            try {
                alarmManager.cancel(pending)
                pending.cancel()
                Log.d(TAG, "Wakeup alarm cancelled and PendingIntent cancelled")
            } catch (e: Exception) {
                Log.e(TAG, "Error cancelling wakeup alarm: ${e.message}")
            }
        }
        wakeupIntent = null
    }

    private fun cancelCheckHealthAlarm() {
        try {
            // Cancel using companion object's healthCheckIntent
            healthCheckIntent?.let { pending ->
                try {
                    alarmManager.cancel(pending)
                    pending.cancel()
                    Log.d(TAG, "Health check alarm cancelled from companion object")
                } catch (e: Exception) {
                    Log.e(TAG, "Error cancelling health check alarm from companion: ${e.message}")
                }
            }

            // Also create and cancel a new PendingIntent with same parameters to ensure cleanup
            val intent = Intent(this, ActivityRecognitionHealthReceiver::class.java).apply {
                action = ACTION_HEALTH_CHECK
            }

            val pendingIntent = PendingIntent.getBroadcast(
                this,
                HEALTH_CHECK_REQUEST_CODE,
                intent,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_NO_CREATE
                } else {
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_NO_CREATE
                }
            )

            pendingIntent?.let { pending ->
                alarmManager.cancel(pending)
                pending.cancel()
                Log.d(TAG, "Health check alarm cancelled using FLAG_NO_CREATE")
            }

            // Set companion object's healthCheckIntent to null
            healthCheckIntent = null

            Log.d(TAG, "Health check alarm fully cancelled and cleaned up")
        } catch (e: Exception) {
            Log.e(TAG, "Error during health check alarm cancellation: ${e.message}")
        }
    }

    private fun handleWakeup() {
        Log.d(TAG, "Wakeup alarm triggered")

        acquireWakeLock()

        try {
            ensureRecognitionIsActive()

            updateNotification()

            logDeviceState()

        } catch (e: Exception) {
            Log.e(TAG, "Error during wakeup handling: ${e.message}")
        } finally {
            handler.postDelayed({
                releaseWakeLock()
            }, 5000)
        }
    }

    private fun ensureRecognitionIsActive() {
        if (useTransitionRecognition && !isTransitionRecognitionConfigured) {
            Log.d(TAG, "Re-configuring transition recognition")
            setupTransitionRecognition()
            isTransitionRecognitionConfigured = true
        }

        if (useActivityRecognition && !isActivityRecognitionConfigured) {
            Log.d(TAG, "Re-configuring activity recognition")
            setupActivityRecognition()
            isActivityRecognitionConfigured = true
        }
    }

    private fun logDeviceState() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Log.d(TAG, "Device idle mode: ${powerManager.isDeviceIdleMode}")
            Log.d(TAG, "Battery optimization ignored: ${isIgnoringBatteryOptimizations()}")
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            powerManager.isIgnoringBatteryOptimizations(packageName)
        } else {
            true
        }
    }

    private fun cleanupRecognition() {
        Log.d(TAG, "Cleaning up activity recognition subscriptions")

        // Remove activity updates
        activityIntent?.let { pending ->
            try {
                activityRecognitionClient.removeActivityUpdates(pending)
                    .addOnSuccessListener {
                        Log.d(TAG, "Activity updates removed successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove activity updates: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception removing activity updates: ${e.message}")
            }
        }

        // Remove transition updates
        transitionIntent?.let { pending ->
            try {
                activityRecognitionClient.removeActivityTransitionUpdates(pending)
                    .addOnSuccessListener {
                        Log.d(TAG, "Transition updates removed successfully")
                    }
                    .addOnFailureListener { e ->
                        Log.e(TAG, "Failed to remove transition updates: ${e.message}")
                    }
            } catch (e: Exception) {
                Log.e(TAG, "Exception removing transition updates: ${e.message}")
            }
        }

        // Nullify the pending intents to ensure they can't be used again
        activityIntent = null
        transitionIntent = null

        Log.d(TAG, "Activity recognition cleanup completed")
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

        if (transitionIntent == null) {
            Log.e(TAG, "initTransitionRequest: transitionIntent is null, cannot request updates")
            releaseWakeLock()
            return
        }

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

        if (activityIntent == null) {
            Log.e(TAG, "initActivityUpdates: activityIntent is null, cannot request updates")
            releaseWakeLock()
            return
        }

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
            wakeLock = powerManager.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK,
                "ActivityRecognition:WakeLock"
            )
            wakeLock?.setReferenceCounted(false)
        }

        if (wakeLock?.isHeld == false) {
            wakeLock?.acquire(WAKELOCK_TIMEOUT)
            Log.d(TAG, "WakeLock acquired")
        }
    }

    private fun releaseWakeLock() {
        try {
            wakeLock?.let {
                if (it.isHeld) {
                    it.release()
                    Log.d(TAG, "WakeLock released")
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error releasing WakeLock: ${e.message}")
        }
    }

    private fun setupHealthCheck() {
        val intent = Intent(this, ActivityRecognitionHealthReceiver::class.java)
        intent.action = ACTION_HEALTH_CHECK

        healthCheckIntent = PendingIntent.getBroadcast(
            this,
            HEALTH_CHECK_REQUEST_CODE,
            intent,
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE
            } else {
                PendingIntent.FLAG_UPDATE_CURRENT
            }
        )

        scheduleNextHealthCheck(this)
    }

    /**
     * Validates whether the service should process a wakeup action.
     * This prevents the service from running when the Flutter process is dead
     * (e.g., after adb stop-app or system force-stop).
     */
    private fun shouldProcessWakeup(): Boolean {
        // Check 1: Is tracker registered in SharedPreferences?
        if (!isTrackerRegistered()) {
            Log.d(TAG, "shouldProcessWakeup: Tracker not registered")
            return false
        }

        // Check 2: Is Flutter process alive?
        if (!isFlutterProcessAlive()) {
            Log.d(TAG, "shouldProcessWakeup: Flutter process is NOT alive")
            return false
        }

        // Check 3: Are internal Flutter services running?
        // This is important because only having external services running (like this one)
        // without internal Flutter services means the app was force-stopped
        if (!hasInternalForegroundServices()) {
            Log.d(TAG, "shouldProcessWakeup: No internal Flutter services running")
            return false
        }

        Log.d(TAG, "shouldProcessWakeup: All checks passed")
        return true
    }

    private fun isTrackerRegistered(): Boolean {
        return try {
            val prefs = getSharedPreferences("FlutterSharedPreferences", Context.MODE_PRIVATE)
            prefs.getBoolean("flutter.isTrackerRegistered", false)
        } catch (e: Exception) {
            Log.e(TAG, "shouldProcessWakeup: Error reading isTrackerRegistered", e)
            false
        }
    }

    private fun isFlutterProcessAlive(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val packageName = this.packageName
            val runningProcesses = activityManager.runningAppProcesses ?: return false

            for (processInfo in runningProcesses) {
                if (processInfo.processName == packageName) {
                    Log.d(TAG, "shouldProcessWakeup: Flutter process is alive")
                    return true
                }
            }

            Log.d(TAG, "shouldProcessWakeup: Flutter process is NOT alive")
            false
        } catch (e: Exception) {
            Log.e(TAG, "shouldProcessWakeup: Error checking Flutter process", e)
            false
        }
    }

    private fun hasInternalForegroundServices(): Boolean {
        return try {
            val activityManager = getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
            val runningServices = activityManager.getRunningServices(Integer.MAX_VALUE)

            val internalServices = listOf(
                "com.pravera.flutter_foreground_task.service.ForegroundService",
                "com.dexterous.flutterlocalnotifications.ForegroundService"
            )

            if (runningServices != null) {
                for (service in runningServices) {
                    val serviceClassName = service.service.className
                    if (service.foreground && internalServices.contains(serviceClassName)) {
                        Log.d(TAG, "shouldProcessWakeup: Found internal service: $serviceClassName")
                        return true
                    }
                }
            }

            Log.d(TAG, "shouldProcessWakeup: No internal Flutter services are active")
            false
        } catch (e: Exception) {
            Log.e(TAG, "shouldProcessWakeup: Error checking services", e)
            false
        }
    }
}