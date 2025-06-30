import 'package:equatable/equatable.dart';

enum ActivityType {
  inVehicle,
  onBicycle,
  running,
  onFoot,
  walking,
  tilting,
  still,
  unknown,
}

extension ActivityTypeExtension on ActivityType {
  String get name => switch (this) {
        ActivityType.inVehicle => "IN_VEHICLE",
        ActivityType.onBicycle => "ON_BICYCLE",
        ActivityType.running => "RUNNING",
        ActivityType.onFoot => "ON_FOOT",
        ActivityType.walking => "WALKING",
        ActivityType.tilting => "TILTING",
        ActivityType.still => "STILL",
        ActivityType.unknown => "UNKNOWN",
      };
}

/// Data class for activity data
class ActivityData extends Equatable {
  final DateTime timestamp;
  final String activity;

  const ActivityData({
    required this.timestamp,
    required this.activity,
  });

  @override
  List<Object?> get props => [timestamp, activity];

  ActivityData copyWith({
    DateTime? timestamp,
    String? activity,
  }) =>
      ActivityData(
        timestamp: timestamp ?? this.timestamp,
        activity: activity ?? this.activity,
      );

  factory ActivityData.fromMap(Map<String, dynamic> map) => ActivityData(
        timestamp: DateTime.fromMillisecondsSinceEpoch(map['timestamp'] ?? 0),
        activity: map['activity'],
      );

  Map<String, dynamic> toMap() => {
        'timestamp': timestamp,
        'activity': activity,
      };
}
