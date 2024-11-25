package com.aikotelematics.custom_activity_recognition

import android.app.Activity
import android.content.Context
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
class CustomActivityRecognitionPlugin: FlutterPlugin, MethodCallHandler, ActivityAware, DefaultLifecycleObserver {
  private lateinit var methodChannel: MethodChannel
  private lateinit var eventChannel: EventChannel
  private lateinit var context: Context
  private var activity: Activity? = null
  private lateinit var activityRecognitionManager: ActivityRecognitionManager
  private var binaryMessenger: BinaryMessenger? = null
  private var lifecycle: Lifecycle? = null

  override fun onAttachedToEngine(@NonNull flutterPluginBinding: FlutterPlugin.FlutterPluginBinding) {
    context = flutterPluginBinding.applicationContext
    binaryMessenger = flutterPluginBinding.binaryMessenger
    setupChannels(flutterPluginBinding.binaryMessenger)
  }

  private fun setupChannels(messenger: BinaryMessenger) {
    methodChannel = MethodChannel(messenger, "com.aikotelematics.custom_activity_recognition/methods")
    eventChannel = EventChannel(messenger, "com.aikotelematics.custom_activity_recognition/events")

    activityRecognitionManager = ActivityRecognitionManager(context)
    methodChannel.setMethodCallHandler(this)
    eventChannel.setStreamHandler(activityRecognitionManager)
  }

  private fun teardownChannels() {
    methodChannel.setMethodCallHandler(null)
    eventChannel.setStreamHandler(null)
    binaryMessenger = null
  }

  override fun onMethodCall(@NonNull call: MethodCall, @NonNull result: Result) {
    when (call.method) {
      "requestPermissions" -> {
        activity?.let {
          activityRecognitionManager.requestPermissions(it) { granted ->
            result.success(granted)
          }
        } ?: result.error("NO_ACTIVITY", "Activity is not available", null)
      }
      "startTracking" -> {
        activityRecognitionManager.startTracking { success ->
          result.success(success)
        }
      }
      "stopTracking" -> {
        activityRecognitionManager.stopTracking { success ->
          result.success(success)
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
    lifecycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
    lifecycle?.addObserver(this)
  }

  override fun onDetachedFromActivityForConfigChanges() {
    lifecycle?.removeObserver(this)
    lifecycle = null
    activity = null
  }

  override fun onReattachedToActivityForConfigChanges(binding: ActivityPluginBinding) {
    activity = binding.activity
    lifecycle = (binding.lifecycle as HiddenLifecycleReference).lifecycle
    lifecycle?.addObserver(this)
  }

  override fun onDetachedFromActivity() {
    lifecycle?.removeObserver(this)
    lifecycle = null
    activity = null
  }

  override fun onCreate(owner: LifecycleOwner) {
    // Inicialización cuando se crea la actividad
  }

  override fun onStart(owner: LifecycleOwner) {
    // La actividad se hace visible
  }

  override fun onResume(owner: LifecycleOwner) {
    // La actividad está lista para interactuar con el usuario
  }

  override fun onPause(owner: LifecycleOwner) {
    // La actividad está parcialmente visible pero no interactiva
  }

  override fun onStop(owner: LifecycleOwner) {
    // La actividad ya no es visible
  }

  override fun onDestroy(owner: LifecycleOwner) {
    cleanupResources()
  }

  private fun cleanupResources() {
    try {
      activityRecognitionManager.stopTracking { _ -> }
    } catch (e: Exception) {
      // Manejar cualquier error durante la limpieza
    }
  }
}
