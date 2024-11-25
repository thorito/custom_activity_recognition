import 'activity_types.dart';
import 'custom_activity_recognition_platform_interface.dart';

class CustomActivityRecognition {
  CustomActivityRecognition._();

  static final CustomActivityRecognition _instance =
      CustomActivityRecognition._();

  static CustomActivityRecognition get instance => _instance;

  final _platform = CustomActivityRecognitionPlatform.instance;

  Future<bool> isAvailable() async =>
      await _platform.isActivityRecognitionAvailable();

  Future<bool> requestPermissions() async =>
      await _platform.requestPermissions();

  Future<bool> startTracking() async => await _platform.startTracking();

  Future<bool> stopTracking() async => await _platform.stopTracking();

  Stream<ActivityData> activityStream() => _platform.activityStream();
}
