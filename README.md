# custom_activity_recognition

Activity recognition plugin for Android and iOS

## Notes

* On Android use Activity Recognition Transition API
* On iOS use CoreMotion

## Getting started

* Android
  Open the AndroidManifest.xml file and add the following permissions between the <manifest> and <application> tags.

```
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
    <uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
    <uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
    <uses-permission android:name="android.permission.ACTIVITY_RECOGNITION" />
    <uses-permission android:name="com.google.android.gms.permission.ACTIVITY_RECOGNITION" />
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