import 'package:custom_activity_recognition/activity_types.dart';
import 'package:plugin_platform_interface/plugin_platform_interface.dart';

import 'custom_activity_recognition_method_channel.dart';

abstract class CustomActivityRecognitionPlatform extends PlatformInterface {
  CustomActivityRecognitionPlatform() : super(token: _token);

  static final Object _token = Object();

  static CustomActivityRecognitionPlatform _instance =
      MethodChannelCustomActivityRecognition();

  static CustomActivityRecognitionPlatform get instance => _instance;

  static set instance(CustomActivityRecognitionPlatform instance) {
    PlatformInterface.verifyToken(instance, _token);
    _instance = instance;
  }

  Future<bool> requestPermissions() {
    throw UnimplementedError('requestPermissions() has not been implemented.');
  }

  Future<bool> startTracking() {
    throw UnimplementedError('startTracking() has not been implemented.');
  }

  Future<bool> stopTracking() {
    throw UnimplementedError('stopTracking() has not been implemented.');
  }

  Stream<ActivityData> activityStream() {
    throw UnimplementedError('activityStream() has not been implemented.');
  }

  Future<bool> isActivityRecognitionAvailable() {
    throw UnimplementedError(
        'isActivityRecognitionAvailable() has not been implemented.');
  }
}
