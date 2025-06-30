import Flutter
import CoreMotion
import UIKit

public class CustomActivityRecognitionPlugin: NSObject, FlutterPlugin {
  private let activityManager = CMMotionActivityManager()
  private var eventChannel: FlutterEventChannel?
  private var methodChannel: FlutterMethodChannel?
  private let streamHandler = ActivityStreamHandler()

  private var isRunningOnSimulator: Bool {
    #if targetEnvironment(simulator)
      return true
    #else
      return false
    #endif
  }

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
      case "checkPermissionStatus":
          checkPermissionStatus(result: result)
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

  private func checkPermissionStatus(result: @escaping FlutterResult) {
    guard isRunningOnSimulator || CMMotionActivityManager.isActivityAvailable() else {
        result(false)
        return
    }

    if #available(iOS 11.0, *) {
        let status = CMMotionActivityManager.authorizationStatus()

        switch status {
        case .authorized:
            result("AUTHORIZED")
        case .denied:
            result("PERMANENTLY_DENIED")
        case .restricted:
            result("RESTRICTED")
        case .notDetermined:
            result("NOT_DETERMINED")
        @unknown default:
            result("NOT_DETERMINED")
        }
    } else {
        let endDate = Date()
        let startDate = endDate.addingTimeInterval(-1000)

        activityManager.queryActivityStarting(from: startDate, to: endDate, to: OperationQueue.main) { [weak self] (activities, error) in
            DispatchQueue.main.async {
                if let error = error as NSError? {
                    if error.domain == CMErrorDomain && error.code == 105 {
                        result("DENIED")
                    } else {
                        result("RESTRICTED")
                    }
                } else {
                    result("AUTHORIZED")
                }
            }
        }
    }
  }

  private func requestPermissions(result: @escaping FlutterResult) {
      guard isRunningOnSimulator || CMMotionActivityManager.isActivityAvailable() else {
          result(false)
          return
      }

      if #available(iOS 11.0, *) {
          let status = CMMotionActivityManager.authorizationStatus()

          switch status {
          case .authorized:
              result(true)
          case .denied, .restricted:
              result(false)
          case .notDetermined:
              let endDate = Date()
              let startDate = endDate.addingTimeInterval(-1000)

              activityManager.queryActivityStarting(from: startDate, to: endDate, to: OperationQueue.main) { (activities, error) in
                  DispatchQueue.main.async {
                      if let error = error as NSError? {
                          if error.domain == CMErrorDomain && error.code == 105 {
                              result(false)
                          } else {
                              result(false)
                          }
                      } else {
                          if CMMotionActivityManager.authorizationStatus() == .authorized {
                              result(true)
                          } else {
                              result(false)
                          }
                      }
                  }
              }
          @unknown default:
              result(false)
          }
      } else {
          result(false)
      }
  }

  private func startTracking(result: @escaping FlutterResult) {

      guard isRunningOnSimulator || CMMotionActivityManager.isActivityAvailable() else {
          result(false)
          return
      }

      if #available(iOS 11.0, *) {
          switch CMMotionActivityManager.authorizationStatus() {
          case .authorized:
              streamHandler.startTracking(activityManager: activityManager)
              result(true)
          case .notDetermined:

              requestPermissions { permissionResult in
                  if let granted = permissionResult as? Bool, granted {
                      self.streamHandler.startTracking(activityManager: self.activityManager)
                  }
                  result(permissionResult)
              }
          case .denied, .restricted:
              result(false)
          @unknown default:
              result(false)
          }
      } else {

          streamHandler.startTracking(activityManager: activityManager)
          result(true)
      }
  }

  private func stopTracking(result: @escaping FlutterResult) {
      if isRunningOnSimulator {
        result(true)
        return
      }

      streamHandler.stopTracking(activityManager: activityManager)
      result(true)
  }

  private func isActivityRecognitionAvailable(result: @escaping FlutterResult) {
      if isRunningOnSimulator {
        result(true)
        return
      }

      result(CMMotionActivityManager.isActivityAvailable())
  }
}
