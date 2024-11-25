import Flutter
import CoreMotion
import UIKit

public class CustomActivityRecognitionPlugin: NSObject, FlutterPlugin {
  private let activityManager = CMMotionActivityManager()
  private var eventChannel: FlutterEventChannel?
  private var methodChannel: FlutterMethodChannel?
  private let streamHandler = ActivityStreamHandler()


  public static func register(with registrar: FlutterPluginRegistrar) {
    let instance = CustomActivityRecognitionPlugin()

    let methodChannel = FlutterMethodChannel(
        name: "com.aikotelematics.custom_activity_recognition/methods",
        binaryMessenger: registrar.messenger()
    )

    registrar.addMethodCallDelegate(instance, channel: methodChannel)
    instance.methodChannel = methodChannel

    let eventChannel = FlutterEventChannel(
        name: "com.aikotelematics.custom_activity_recognition/events",
        binaryMessenger: registrar.messenger()
    )
    eventChannel.setStreamHandler(instance.streamHandler)
    instance.eventChannel = eventChannel
  }

  public func handle(_ call: FlutterMethodCall, result: @escaping FlutterResult) {
      switch call.method {
      case "requestPermissions":
          requestPermissions(result: result)
      case "startTracking":
          startTracking(result: result)
      case "stopTracking":
          stopTracking(result: result)
      case "isAvailable":
          isActivityRecognitionAvailable(result: result)
      default:
          result(FlutterMethodNotImplemented)
      }
  }

  private func requestPermissions(result: @escaping FlutterResult) {
      if #available(iOS 11.0, *) {
              switch CMMotionActivityManager.authorizationStatus() {
              case .authorized:
                  result(true)
              case .denied, .restricted:
                  result(false)
              case .notDetermined:
                  result(true)
              @unknown default:
                  result(false)
              }
          } else {
              result(false)
          }
  }

  private func startTracking(result: @escaping FlutterResult) {
      guard CMMotionActivityManager.isActivityAvailable() else {
          result(false)
          return
      }

      streamHandler.startTracking(activityManager: activityManager)
      result(true)
  }

  private func stopTracking(result: @escaping FlutterResult) {
      streamHandler.stopTracking(activityManager: activityManager)
      result(true)
  }

  private func isActivityRecognitionAvailable(result: @escaping FlutterResult) {
      result(CMMotionActivityManager.isActivityAvailable())
  }
}
