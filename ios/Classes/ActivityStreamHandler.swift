import Flutter
import CoreMotion

class ActivityStreamHandler: NSObject, FlutterStreamHandler {
    private var eventSink: FlutterEventSink?

    func onListen(withArguments arguments: Any?, eventSink events: @escaping FlutterEventSink) -> FlutterError? {
        self.eventSink = events
        return nil
    }

    func onCancel(withArguments arguments: Any?) -> FlutterError? {
        eventSink = nil
        return nil
    }

    func startTracking(activityManager: CMMotionActivityManager) {
        activityManager.startActivityUpdates(to: OperationQueue.main) { [weak self] (activity) in
            guard let activity = activity else { return }

            let activityType = self?.getActivityType(from: activity)

            let activityData: [String: Any] = [
                "timestamp": Int(Date().timeIntervalSince1970 * 1000),
                "activity": activityType ?? "UNKNOWN"
            ]

            self?.eventSink?(activityData)
        }
    }

    func stopTracking(activityManager: CMMotionActivityManager) {
        activityManager.stopActivityUpdates()
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

}