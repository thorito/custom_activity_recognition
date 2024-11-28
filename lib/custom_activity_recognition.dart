import 'activity_types.dart';
import 'custom_activity_recognition_platform_interface.dart';

/// Class for handling custom activity recognition
class CustomActivityRecognition {
  CustomActivityRecognition._();

  static final CustomActivityRecognition _instance =
      CustomActivityRecognition._();

  /// The default instance of [CustomActivityRecognition] to use
  static CustomActivityRecognition get instance => _instance;

  final _platform = CustomActivityRecognitionPlatform.instance;

  /// Checks if activity recognition is available
  Future<bool> isAvailable() async =>
      await _platform.isActivityRecognitionAvailable();

  /// Requests permissions for activity recognition
  Future<bool> requestPermissions() async =>
      await _platform.requestPermissions();

  /// Starts tracking user activity
  Future<bool> startTracking() async => await _platform.startTracking();

  /// Stops tracking user activity
  Future<bool> stopTracking() async => await _platform.stopTracking();

  /// Stream of activity data
  Stream<ActivityData> activityStream() => _platform.activityStream();
}
