package com.aikotelematics.custom_activity_recognition

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import com.aikotelematics.custom_activity_recognition.Constants.DEFAULT_CONFIDENCE_THRESHOLD
import com.aikotelematics.custom_activity_recognition.Constants.DEFAULT_DETECTION_INTERVAL_MILLIS
import com.aikotelematics.custom_activity_recognition.Constants.TAG
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.plugin.common.BinaryMessenger
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler

/** CustomActivityRecognitionPlugin */
class CustomActivityRecognitionPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var methodChannel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private var context: Context? = null
  private var activity: Activity? = null
  private var activityRecognitionManager: ActivityRecognitionManager? = null
    private set

  // Getter for ActivityRecognitionManager that can be accessed from other classes
  fun getActivityRecognitionManager(): ActivityRecognitionManager? = activityRecognitionManager
  private var binaryMessenger: BinaryMessenger? = null
  private var pendingResult: MethodChannel.Result? = null
  private var isAttached = false

  override fun onAttachedToEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    context = binding.applicationContext
    binaryMessenger = binding.binaryMessenger
    isAttached = true
    setupChannels(binding.binaryMessenger)
  }

  private fun setupChannels(messenger: BinaryMessenger) {
    context?.let { ctx ->
      methodChannel =
        MethodChannel(messenger, "com.aikotelematics.custom_activity_recognition/methods")
      eventChannel =
        EventChannel(messenger, "com.aikotelematics.custom_activity_recognition/events")

      activityRecognitionManager = ActivityRecognitionManager(ctx.applicationContext)
      methodChannel.setMethodCallHandler(this)
      activityRecognitionManager?.let { manager ->
        eventChannel.setStreamHandler(manager)
      } ?: Log.e(TAG, "ActivityRecognitionManager is not initialized")
    } ?: Log.e(TAG, "Context is not available for setting up channels")
  }

  private fun teardownChannels() {
    try {
      cleanupResources()
      methodChannel.setMethodCallHandler(null)
      eventChannel.setStreamHandler(null)
      binaryMessenger = null
      isAttached = false
    } catch (e: Exception) {
      Log.e(TAG, "Error tearing down channels: ${e.message}")
    }
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: MethodChannel.Result) {
    when (call.method) {
      "checkPermissionStatus" -> {
        val currentActivity = activity
        val manager = activityRecognitionManager
        if (currentActivity != null && manager != null) {
          manager.checkPermissionStatus(currentActivity) { status ->
            try {
              result.success(status)
            } catch (e: Exception) {
              Log.e(TAG, "Error on checkPermissionStatus callback: ${e.message}, status: $status")
              try {
                result.error("CALLBACK_ERROR", "Error in checkPermissionStatus callback", e.message)
              } catch (e: Exception) {
                Log.e(TAG, "Error sending error to result: ${e.message}")
              }
            }
          }
        } else {
          result.error(
            "UNAVAILABLE",
            "Activity or ActivityRecognitionManager is not available",
            null
          )
        }
      }
      "requestPermissions" -> {
        val currentActivity = activity
        val manager = activityRecognitionManager
        if (currentActivity != null && manager != null) {
          pendingResult = result
          manager.requestPermissions(currentActivity) { granted ->
            val currentPendingResult = pendingResult
            pendingResult = null
            
            try {
              currentPendingResult?.success(granted)
            } catch (e: Exception) {
              Log.e(TAG, "Error in requestPermissions callback: ${e.message}")
              try {
                currentPendingResult?.error(
                  "PERMISSION_ERROR",
                  "Error processing permission result",
                  e.message
                )
              } catch (e2: Exception) {
                Log.e(TAG, "Error sending error to pendingResult: ${e2.message}")
              }
            }
          }
        } else {
          result.error(
            "UNAVAILABLE",
            "Activity or ActivityRecognitionManager is not available",
            null
          )
        }
      }
      "startTracking" -> {
        val manager = activityRecognitionManager
        if (manager != null) {
          val showNotification = call.argument<Boolean>("showNotification") ?: true
          val useTransitionRecognition = call.argument<Boolean>("useTransitionRecognition") ?: false
          val useActivityRecognition = call.argument<Boolean>("useActivityRecognition") ?: true
          val detectionIntervalMillis =
            call.argument<Int>("detectionIntervalMillis") ?: DEFAULT_DETECTION_INTERVAL_MILLIS
          val confidenceThreshold =
            call.argument<Int>("confidenceThreshold") ?: DEFAULT_CONFIDENCE_THRESHOLD

          manager.startTracking(
            showNotification = showNotification,
            useTransitionRecognition = useTransitionRecognition,
            useActivityRecognition = useActivityRecognition,
            detectionIntervalMillis = detectionIntervalMillis,
            confidenceThreshold = confidenceThreshold
          ) { success ->
            try {
              result.success(success)
            } catch (e: Exception) {
              Log.e(TAG, "Error in startTracking callback: ${e.message}")
              try {
                result.error("CALLBACK_ERROR", "Error in startTracking callback", e.message)
              } catch (e2: Exception) {
                Log.e(TAG, "Error sending error to result: ${e2.message}")
              }
            }
          }
        } else {
          result.error("UNAVAILABLE", "ActivityRecognitionManager is not available", null)
        }
      }
      "stopTracking" -> {
        val manager = activityRecognitionManager
        if (manager != null) {
          manager.stopTracking { success ->
            try {
              result.success(success)
            } catch (e: Exception) {
              Log.e(TAG, "Error in stopTracking callback: ${e.message}")
              try {
                result.error("CALLBACK_ERROR", "Error in stopTracking callback", e.message)
              } catch (e2: Exception) {
                Log.e(TAG, "Error sending error to result: ${e2.message}")
              }
            }
          }
        } else {
          result.error("UNAVAILABLE", "ActivityRecognitionManager is not available", null)
        }
      }
      "isAvailable" -> {
        try {
          val manager = activityRecognitionManager
          result.success(manager?.isAvailable() ?: false)
        } catch (e: Exception) {
          Log.e(TAG, "Error checking availability: ${e.message}")
          result.success(false)
        }
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(binding: FlutterPlugin.FlutterPluginBinding) {
    teardownChannels()
    context = null
    activityRecognitionManager = null
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
      try {
        activityRecognitionManager?.let { manager ->
          manager.handlePermissionResult(requestCode, permissions, grantResults)
        } ?: Log.e(TAG, "ActivityRecognitionManager is null in permission result handler")
        true
      } catch (e: Exception) {
        Log.e(TAG, "Error in handlePermissionResult: ${e.message}")
        pendingResult?.let { result ->
          try {
            result.error("PERMISSION_ERROR", "Error processing permission result", e.message)
          } catch (e2: Exception) {
            Log.e(TAG, "Error sending error to pendingResult: ${e2.message}")
          } finally {
            pendingResult = null
          }
        }
        true
      }
    }
  }

  override fun onDetachedFromActivityForConfigChanges() {
    // No es necesario limpiar la actividad aquí ya que volverá a adjuntarse
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    onAttachedToActivity(binding)
  }

  override fun onDetachedFromActivity() {
    cleanupResources()
    activity = null
  }

  private fun cleanupResources() {
    try {
      activityRecognitionManager?.stopTracking { /* Ignore result */ }
    } catch (e: Exception) {
      Log.e(TAG, "Error cleaning up resources: ${e.message}")
    }
  }
}
