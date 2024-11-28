import 'package:custom_activity_recognition/activity_types.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/services.dart';

import 'custom_activity_recognition_platform_interface.dart';

/// The MethodChannel that will the implementation of CustomActivityRecognition.
class MethodChannelCustomActivityRecognition
    extends CustomActivityRecognitionPlatform {
  static const MethodChannel _methodChannel =
      MethodChannel('com.aikotelematics.custom_activity_recognition/methods');
  static const EventChannel _eventChannel =
      EventChannel('com.aikotelematics.custom_activity_recognition/events');

  /// Checks if activity recognition is available
  @override
  Future<bool> requestPermissions() async {
    try {
      final bool? result =
          await _methodChannel.invokeMethod('requestPermissions');
      return result ?? false;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print('Error requesting permissions: ${e.message}');
      }
      return false;
    }
  }

  /// Starts tracking user activity
  @override
  Future<bool> startTracking() async {
    try {
      final bool? result = await _methodChannel.invokeMethod('startTracking');
      return result ?? false;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print('Error starting tracking: ${e.message}');
      }
      return false;
    }
  }

  /// Stops tracking user activity
  @override
  Future<bool> stopTracking() async {
    try {
      final bool? result = await _methodChannel.invokeMethod('stopTracking');
      return result ?? false;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print('Error stopping tracking: ${e.message}');
      }
      return false;
    }
  }

  /// Stream of activity data
  @override
  Stream<ActivityData> activityStream() =>
      _eventChannel.receiveBroadcastStream().map((event) {
        try {
          final map = Map<String, dynamic>.from(event);
          final data = ActivityData.fromMap(map);

          if (kDebugMode) {
            print('Received event: $data');
          }

          return data;
        } catch (e) {
          if (kDebugMode) {
            print('Error mapping event: ${e.toString()}');
          }
          return ActivityData(
              timestamp: DateTime.now(), activity: ActivityType.unknown.name);
        }
      });

  /// Checks if activity recognition is available
  @override
  Future<bool> isActivityRecognitionAvailable() async {
    try {
      final bool? result = await _methodChannel.invokeMethod('isAvailable');
      return result ?? false;
    } on PlatformException catch (e) {
      if (kDebugMode) {
        print('Error checking availability: ${e.message}');
      }
      return false;
    }
  }
}
