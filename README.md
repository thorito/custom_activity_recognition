# custom_activity_recognition

Activity recognition plugin for Android and iOS

## Notes

* On Android use Activity Recognition Transition API
* On iOS use CoreMotion

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
>    * TILTING
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