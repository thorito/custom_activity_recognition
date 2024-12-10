# custom_activity_recognition

Activity recognition plugin for Android (**Activity Recognition Transition API**) and iOS (**CoreMotion**)

|         | Android |  iOS  |
| :------ | :-----: | :---: |
| Support | SDK 21+ | 12.0+ |

## Getting started

To use this plugin, add flutter_activity_recognition as a dependency in your pubspec.yaml file. 
[Show video](https://youtube.com/shorts/vkVThDpTyk8?feature=share)

For example:
```yaml
  dependencies:
    custom_activity_recognition: ^0.0.10
```

* Android
  Open the AndroidManifest.xml file and add the following permissions between the <manifest> and <application> tags.

```
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
```

Allow to enable or disable the hybrid model (only for Android).

```dart
  // CustomActivityRecognition.instance.startTracking() 
  // useTransitionRecognition = true, useActivityRecognition = false

  CustomActivityRecognition.instance.startTracking(
    useTransitionRecognition: false,
    useActivityRecognition: true,
    detectionIntervalMillis: 10000,
    confidenceThreshold: 50,
  );
```

* iOS
  Open the ios/Runner/Info.plist file and add the following permission inside the <dict> tag.

```
	<key>NSMotionUsageDescription</key>
    <string>Detects human activity</string>
    <key>UIBackgroundModes</key>
    <array>
        <string>fetch</string>
        <string>motion</string>
    </array>
```

## ActivityData

```dart
class ActivityData {
  final DateTime timestamp;
  final String activity;    
}

```

## Types of activities

> **Android**:
>
>    * IN_VEHICLE
>    * ON_BICYCLE
>    * RUNNING
>    * ON_FOOT
>    * WALKING
>    * STILL
>    * UNKNOWN

---

> **iOS**:
>
>    * IN_VEHICLE
>    * ON_BICYCLE
>    * RUNNING
>    * WALKING
>    * STILL
>    * UNKNOWN

## Support

If you find any bugs or issues while using the plugin, please register an issues on [GitHub](https://github.com/thorito/custom_activity_recognition/issues). 