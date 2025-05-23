import 'package:custom_activity_recognition/constants.dart';
import 'package:custom_activity_recognition/custom_activity_permission_status.dart';

import 'activity_types.dart';
import 'custom_activity_recognition_platform_interface.dart';

/// Class for handling custom activity recognition
class CustomActivityRecognition {
  CustomActivityRecognition._();

  static final CustomActivityRecognition _instance =
      CustomActivityRecognition._();

  /// The default instance of [CustomActivityRecognition] to use
  static CustomActivityRecognition get instance => _instance;

  /// Checks if activity recognition is available
  Future<bool> isAvailable() async =>
      await CustomActivityRecognitionPlatform.instance
          .isActivityRecognitionAvailable();

  /// Checks the current status of activity recognition permission
  Future<CustomActivityPermissionStatus> checkPermissionStatus() async =>
      await CustomActivityRecognitionPlatform.instance.checkPermissionStatus();

  /// Requests permissions for activity recognition
  Future<bool> requestPermissions() async =>
      await CustomActivityRecognitionPlatform.instance.requestPermissions();

  /// Starts tracking user activity
  /// [useTransitionRecognition] and [useActivityRecognition] are optional
  /// useTransitionRecognition is true by default
  /// useActivityRecognition is true by default
  /// detectionIntervalMillis is 10000 by default
  /// confidenceThreshold is 60 by default
  Future<bool> startTracking({
    bool showNotification = true,
    bool useTransitionRecognition = true,
    bool useActivityRecognition = true,
    int detectionIntervalMillis = defaultDetectionIntervalMillis,
    int confidenceThreshold = defaultConfidenceThreshold,
  }) async =>
      await CustomActivityRecognitionPlatform.instance.startTracking(
        showNotification: showNotification,
        useTransitionRecognition: useTransitionRecognition,
        useActivityRecognition: useActivityRecognition,
        detectionIntervalMillis: detectionIntervalMillis,
        confidenceThreshold: confidenceThreshold,
      );

  /// Stops tracking user activity
  Future<bool> stopTracking() async =>
      await CustomActivityRecognitionPlatform.instance.stopTracking();

  /// Stream of activity data
  Stream<ActivityData> activityStream() =>
      CustomActivityRecognitionPlatform.instance.activityStream();
}
