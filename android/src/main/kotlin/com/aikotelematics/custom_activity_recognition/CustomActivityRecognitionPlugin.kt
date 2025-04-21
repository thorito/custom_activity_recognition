package com.aikotelematics.custom_activity_recognition

import android.app.Activity
import android.content.Context
import android.util.Log
import androidx.annotation.NonNull
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.DefaultLifecycleObserver
import io.flutter.embedding.engine.plugins.FlutterPlugin
import io.flutter.embedding.engine.plugins.activity.ActivityAware
import io.flutter.embedding.engine.plugins.activity.ActivityPluginBinding
import io.flutter.embedding.engine.plugins.lifecycle.HiddenLifecycleReference
import io.flutter.plugin.common.EventChannel
import io.flutter.plugin.common.MethodCall
import io.flutter.plugin.common.MethodChannel
import io.flutter.plugin.common.MethodChannel.MethodCallHandler
import io.flutter.plugin.common.MethodChannel.Result
import io.flutter.plugin.common.BinaryMessenger

/** CustomActivityRecognitionPlugin */
class CustomActivityRecognitionPlugin: FlutterPlugin, MethodCallHandler, ActivityAware {
  private lateinit var methodChannel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var context: Context
  private var activity: Activity? = null
  private lateinit var activityRecognitionManager: ActivityRecognitionManager
  private var binaryMessenger: BinaryMessenger? = null
  private var pendingResult: Result? = null

  companion object {
    private const val TAG = "CustomActivityRecognitionPlugin"
    const val DEFAULT_DETECTION_INTERVAL_MILLIS: Int = 10000
    const val DEFAULT_CONFIDENCE_THRESHOLD: Int = 50
  }

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    binaryMessenger = flutterPluginBinding.binaryMessenger
    setupChannels(flutterPluginBinding.binaryMessenger)
  }

  private fun setupChannels(messenger: BinaryMessenger) {
    methodChannel = MethodChannel(messenger, "com.aikotelematics.custom_activity_recognition/methods")
    eventChannel = EventChannel(messenger, "com.aikotelematics.custom_activity_recognition/events")

    activityRecognitionManager = ActivityRecognitionManager(context.applicationContext)
    methodChannel.setMethodCallHandler(this)
    eventChannel.setStreamHandler(activityRecognitionManager)
  }

  private fun teardownChannels() {
    cleanupResources()
    methodChannel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    binaryMessenger = null
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "checkPermissionStatus" -> {
        activity?.let {
          activityRecognitionManager.checkPermissionStatus(it) { status ->
            try {
              result.success(status)
            } catch (e: Exception) {
              Log.e(TAG, "Error en checkPermissionStatus callback: ${e.message}")
            }
          }
        } ?: result.error("NO_ACTIVITY", "Activity is not available", null)
      }
      "requestPermissions" -> {
        activity?.let {
          pendingResult = result
          activityRecognitionManager.requestPermissions(it) { granted ->
            try {
              pendingResult?.success(granted)
            } catch (e: Exception) {
              Log.e(TAG, "Error en requestPermissions callback: ${e.message}")
              try {
                pendingResult?.error("PERMISSION_ERROR", "Error processing permission result", e.message)
              } catch (e2: Exception) {
                Log.e(TAG, "Error al enviar error al pendingResult: ${e2.message}")
              }
            } finally {
              pendingResult = null
            }
          }
        } ?: result.error("NO_ACTIVITY", "Activity is not available", null)
      }
      "startTracking" -> {
        val showNotification = call.argument<Boolean>("showNotification") ?: true
        val useTransitionRecognition = call.argument<Boolean>("useTransitionRecognition") ?: true
        val useActivityRecognition = call.argument<Boolean>("useActivityRecognition") ?: false
        val detectionIntervalMillis = call.argument<Int>("detectionIntervalMillis") ?: DEFAULT_DETECTION_INTERVAL_MILLIS
        val confidenceThreshold = call.argument<Int>("confidenceThreshold") ?: DEFAULT_CONFIDENCE_THRESHOLD

        activityRecognitionManager.startTracking(
          showNotification = showNotification,
          useTransitionRecognition = useTransitionRecognition,
          useActivityRecognition = useActivityRecognition,
          detectionIntervalMillis = detectionIntervalMillis,
          confidenceThreshold = confidenceThreshold) { success ->

          try {
            result.success(success)
          } catch (e: Exception) {
            Log.e(TAG, "Error en startTracking callback: ${e.message}")
          }
        }
      }
      "stopTracking" -> {
        activityRecognitionManager.stopTracking { success ->
          try {
            result.success(success)
          } catch (e: Exception) {
            Log.e(TAG, "Error en stopTracking callback: ${e.message}")
          }
        }
      }
      "isAvailable" -> {
        result.success(activityRecognitionManager.isAvailable())
      }
      else -> result.notImplemented()
    }
  }

  override fun onDetachedFromEngine(@NonNull binding: FlutterPlugin.FlutterPluginBinding) {
    teardownChannels()
  }

  override fun onAttachedToActivity(binding: ActivityPluginBinding) {
    activity = binding.activity
    binding.addRequestPermissionsResultListener { requestCode, permissions, grantResults ->
      try {
        activityRecognitionManager.handlePermissionResult(requestCode, permissions, grantResults)
        true
      } catch (e: Exception) {
        Log.e(TAG, "Error en handlePermissionResult: ${e.message}")
        pendingResult?.let {
          try {
            it.error("PERMISSION_ERROR", "Error processing permission result", e.message)
          } catch (e2: Exception) {
            Log.e(TAG, "Error al enviar error al pendingResult: ${e2.message}")
          } finally {
            pendingResult = null
          }
        }
        true
      }
    }
  }

  override fun onDetachedFromActivityForConfigChanges() {
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
  }

  override fun onDetachedFromActivity() {
    activity = null
  }

  private fun cleanupResources() {
    try {
      activityRecognitionManager.stopTracking { _ -> }
    } catch (e: Exception) {
      // Not used
    }
  }
}
