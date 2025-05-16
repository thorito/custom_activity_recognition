package com.aikotelematics.custom_activity_recognition

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import com.aikotelematics.custom_activity_recognition.ActivityRecognitionService.Companion.scheduleNextHealthCheck

class ActivityRecognitionHealthReceiver : BroadcastReceiver() {
    companion object {
        private const val TAG = "ActivityRecognitionHealthReceiver"
        private const val ACTION_HEALTH_CHECK = "com.aikotelematics.HEALTH_CHECK_ACTION"
        private const val WAKELOCK_TIMEOUT = 30 * 1000L
    }

    override fun onReceive(context: Context, intent: Intent) {
        val action = intent.action
        Log.d(TAG, "Received intent with action: $action")

        if (action != ACTION_HEALTH_CHECK) {
            Log.d(TAG, "Ignoring intent with unexpected action: $action")
            return
        }

        val powerManager = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val wakeLock = powerManager.newWakeLock(
            PowerManager.PARTIAL_WAKE_LOCK,
            "ActivityRecognition:HealthCheckWakeLock"
        )
        wakeLock.acquire(WAKELOCK_TIMEOUT)

        try {
            if (!ActivityRecognitionService.isRunning()) {
                Log.d(TAG, "Service not running, restarting")

                val serviceIntent = Intent(context, ActivityRecognitionService::class.java)
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                    context.startForegroundService(serviceIntent)
                } else {
                    context.startService(serviceIntent)
                }
            } else {
                Log.d(TAG, "Service running correctly")
            }

            scheduleNextHealthCheck(context)
        } catch (e: Exception) {
            Log.e(TAG, "Error during health check: ${e.message}")
            try {
                scheduleNextHealthCheck(context)
            } catch (e2: Exception) {
                Log.e(TAG, "Error scheduling next health check: ${e2.message}")
            }
        } finally {
            if (wakeLock.isHeld) {
                try {
                    wakeLock.release()
                    Log.d(TAG, "WakeLock released")
                } catch (e: Exception) {
                    Log.e(TAG, "Error releasing WakeLock: ${e.message}")
                }
            }
        }
    }
}