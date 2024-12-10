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

  /// Requests permissions for activity recognition
  Future<bool> requestPermissions() async =>
      await CustomActivityRecognitionPlatform.instance.requestPermissions();

  /// Starts tracking user activity
  /// [useTransitionRecognition] and [useActivityRecognition] are optional
  /// useTransitionRecognition is true by default
  /// useActivityRecognition is false by default
  /// detectionIntervalMillis is 10000 by default
  /// confidenceThreshold is 50 by default
  Future<bool> startTracking({
    bool useTransitionRecognition = true,
    bool useActivityRecognition = false,
    int detectionIntervalMillis = 10000,
    int confidenceThreshold = 50,
  }) async =>
      await CustomActivityRecognitionPlatform.instance.startTracking(
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
