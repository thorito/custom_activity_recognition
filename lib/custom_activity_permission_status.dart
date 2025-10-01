enum CustomActivityPermissionStatus {
  authorized,
  denied,
  restricted,
  permanentlyDenied,
  notDetermined,
}

extension CustomActivityPermissionStatusExtension
    on CustomActivityPermissionStatus {
  /// If the user granted access to the requested feature.
  bool get isGranted => this == CustomActivityPermissionStatus.authorized;

  /// If the user denied access to the requested feature.
  bool get isDenied => this == CustomActivityPermissionStatus.denied;

  /// If the OS denied access to the requested feature.
  /// *Only supported on iOS.*
  bool get isRestricted => this == CustomActivityPermissionStatus.restricted;

  /// If the permission to the requested feature is permanently denied, the
  /// permission dialog will not be shown when requesting this permission.
  bool get isPermanentlyDenied =>
      this == CustomActivityPermissionStatus.permanentlyDenied;

  bool get isNotDetermined =>
      this == CustomActivityPermissionStatus.notDetermined;
}

extension PermissionStatusExtension on CustomActivityPermissionStatus {
  String get name => switch (this) {
        CustomActivityPermissionStatus.authorized => "AUTHORIZED",
        CustomActivityPermissionStatus.denied => "DENIED",
        CustomActivityPermissionStatus.restricted => "RESTRICTED",
        CustomActivityPermissionStatus.permanentlyDenied =>
          "PERMANENTLY_DENIED",
        CustomActivityPermissionStatus.notDetermined => "NOT_DETERMINED",
      };
}
