import 'dart:async';

import 'package:custom_activity_recognition/activity_types.dart';
import 'package:custom_activity_recognition/custom_activity_recognition.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';

void main() {
  runApp(MaterialApp(home: const ActivityRecognitionApp()));
}

class ActivityRecognitionApp extends StatefulWidget {
  const ActivityRecognitionApp({super.key});

  @override
  State<ActivityRecognitionApp> createState() => _ActivityRecognitionAppState();
}

class _ActivityRecognitionAppState extends State<ActivityRecognitionApp> {
  final _activityRecognition = CustomActivityRecognition.instance;
  ActivityData? _lastActivity;
  bool _isTracking = false;

  @override
  void initState() {
    super.initState();
    _checkPermissionsAndAvailability();
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: const Text('Activity Recognition'),
        ),
        body: Center(
          child: Column(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Text(
                'Current Activity:',
                style: TextStyle(fontSize: 20),
              ),
              SizedBox(height: 10),
              Text(
                _lastActivity?.activity ?? 'Not detected',
                style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
              ),
              if (_lastActivity != null) ...[
                SizedBox(height: 10),
                Text(
                  'Last update: '
                  '${_lastActivity!.timestamp.toLocal().toString().split('.')[0]}',
                  style: TextStyle(fontSize: 14, color: Colors.grey),
                ),
              ],
              SizedBox(height: 20),
              ElevatedButton(
                onPressed: _toggleTracking,
                child: Text(_isTracking ? 'Stop Tracking' : 'Start Tracking'),
              ),
            ],
          ),
        ),
      );

  Future<void> _checkPermissionsAndAvailability() async {
    final isAvailable = await _activityRecognition.isAvailable();
    if (isAvailable) {
      final hasPermissions = await _activityRecognition.requestPermissions();
      if (hasPermissions) {
        _setupActivityStream();
      } else {
        _showError('Activity recognition permissions are required');
      }
    } else {
      _showError('Activity recognition is not available on this device');
    }
  }

  void _setupActivityStream() {
    _activityRecognition.activityStream().listen(
      (activity) {
        setState(() {
          _lastActivity = activity;
        });
      },
      onError: (error) {
        if (kDebugMode) {
          print('Error ActivityStream: $error');
        }
      },
    );
  }

  Future<void> _toggleTracking() async {
    try {
      bool success;
      if (_isTracking) {
        success = await _activityRecognition.stopTracking();
      } else {
        success = await _activityRecognition.startTracking();
      }

      if (success) {
        setState(() {
          _isTracking = !_isTracking;
        });
      }
    } catch (e) {
      _showError('Error changing tracking status');
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(content: Text(message)),
    );
  }
}
