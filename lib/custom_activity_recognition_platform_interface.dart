import 'package:custom_activity_recognition/activity_types.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'custom_activity_recognition_method_channel.dart';

/// The interface that implementations of custom_activity_recognition must implement.
abstract class CustomActivityRecognitionPlatform extends PlatformInterface {
  CustomActivityRecognitionPlatform() : super(token: _token);

  static final Object _token = Object();

  static CustomActivityRecognitionPlatform _instance =
      MethodChannelCustomActivityRecognition();

  /// The default instance of [CustomActivityRecognitionPlatform] to use.
  static CustomActivityRecognitionPlatform get instance => _instance;

  /// Provides a way to change instances of this class for tests.
  static set instance(CustomActivityRecognitionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  /// Requests permissions for activity recognition
  Future<bool> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  /// Starts tracking user activity
  Future<bool> startTracking({
    bool useTransitionRecognition = true,
    bool useActivityRecognition = false,
    int detectionIntervalMillis = 10000,
    int confidenceThreshold = 50,
  }) {
    throw UnimplementedError('startTracking() has not been implemented.');
  }

  /// Stops tracking user activity
  Future<bool> stopTracking() {
    throw UnimplementedError('stopTracking() has not been implemented.');
  }

  /// Stream of activity data
  Stream<ActivityData> activityStream() {
    throw UnimplementedError('activityStream() has not been implemented.');
  }

  Future<bool> isActivityRecognitionAvailable() {
    throw UnimplementedError(
        'isActivityRecognitionAvailable() has not been implemented.');
  }
}
