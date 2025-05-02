# custom_activity_recognition

Activity recognition plugin for Android (**Activity Recognition Transition API**) and iOS (**CoreMotion**)

|         | Android |  iOS  |
| :------ | :-----: | :---: |
| Support | SDK 21+ | 12.0+ |

## Getting started

To use this plugin, add flutter_activity_recognition as a dependency in your pubspec.yaml file. 

## Demo

[Show video](https://youtube.com/shorts/ue3rZyVhpw0)

For example:
```yaml
  dependencies:
    custom_activity_recognition: ^0.0.20
```

* Android
  Open the AndroidManifest.xml file and add the following permissions between the <manifest> and <application> tags.

```
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_LOCATION" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_HEALTH" />
    <uses-permission android:name="android.permission.ACCESS_FINE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_COARSE_LOCATION" />
    <uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="android.permission.WAKE_LOCK" />
```

Allow to enable or disable the hybrid model (only for Android).

If you need to detect activity switching in both the foreground and background, I recommend:
`useTransitionRecognition = true, useActivityRecognition = true`

If you need to detect activity switching only in the foreground, I recommend:
`useTransitionRecognition = true, useActivityRecognition = false`

```dart
  // CustomActivityRecognition.instance.startTracking() 
  // useTransitionRecognition = true, useActivityRecognition = true

  CustomActivityRecognition.instance.startTracking(
    showNotification: true,
    useTransitionRecognition: true,
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