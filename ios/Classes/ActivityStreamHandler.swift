import Flutter
import CoreMotion
import UIKit

class ActivityStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?
    private var backgroundTaskIdentifier: UIBackgroundTaskIdentifier = .invalid
    private var backgroundTimer: Timer?
    private var lastActivityType: String = "UNKNOWN"

    deinit {
        stopTracking(activityManager: CMMotionActivityManager())
    }

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.eventSink = events
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }

    func startTracking(activityManager: CMMotionActivityManager) {
        endBackgroundTask()

        backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask { [weak self] in
            self?.endBackgroundTask()
        }

        backgroundTimer = Timer.scheduledTimer(withTimeInterval: 25.0, repeats: true) { [weak self] _ in
            self?.refreshBackgroundTask()
        }

        activityManager.startActivityUpdates(to: OperationQueue.main) { [weak self] (activity) in
            guard let self = self,
                  let activity = activity else {

                return
            }

            let activityType = self.getActivityType(from: activity)

            if activityType != self.lastActivityType {
                self.lastActivityType = activityType

                let activityData: [String: Any] = [
                    "timestamp": Int(Date().timeIntervalSince1970 * 1000),
                    "activity": activityType
                ]

                self.eventSink?(activityData)
            }
        }
    }

    func stopTracking(activityManager: CMMotionActivityManager) {
        activityManager.stopActivityUpdates()
        endBackgroundTask()
        backgroundTimer?.invalidate()
        backgroundTimer = nil
    }

    private func getActivityType(from activity: CMMotionActivity) -> String {
        if activity.stationary {
            return "STILL"
        } else if activity.walking {
            return "WALKING"
        } else if activity.running {
            return "RUNNING"
        } else if activity.automotive {
            return "IN_VEHICLE"
        } else if activity.cycling {
            return "ON_BICYCLE"
        }
        return "UNKNOWN"
    }


    private func refreshBackgroundTask() {
        endBackgroundTask()

        backgroundTaskIdentifier = UIApplication.shared.beginBackgroundTask { [weak self] in
            self?.endBackgroundTask()
        }
    }

    private func endBackgroundTask() {
        if backgroundTaskIdentifier != .invalid {
            UIApplication.shared.endBackgroundTask(backgroundTaskIdentifier)
            backgroundTaskIdentifier = .invalid
        }
    }
}