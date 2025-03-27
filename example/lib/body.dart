import 'dart:async';

import 'package:custom_activity_recognition/activity_types.dart';
import 'package:custom_activity_recognition/custom_activity_recognition.dart';
import 'package:flutter/foundation.dart';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:permission_handler/permission_handler.dart';

class Body extends StatefulWidget {
  const Body({super.key});

  @override
  State<Body> createState() => _BodyState();
}

class _BodyState extends State<Body> {
  final _activityRecognition = CustomActivityRecognition.instance;
  bool _isTracking = false;
  ActivityData? _lastActivity;
  StreamSubscription<ActivityData>? _subscription;

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
        body: _buildBody(),
      );

  Widget _buildBody() => PopScope(
        canPop: false,
        onPopInvokedWithResult: (didPop, _) async => _handlePopInvoked(didPop),
        child: Center(
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
      final isPermanentlyDenied =
          await Permission.activityRecognition.isPermanentlyDenied;

      if (isPermanentlyDenied) {
        await openAppSettings();
      } else {
        final isGranted = await _activityRecognition.requestPermissions();
        if (isGranted) {
          await _initActivityListener();
        }
      }
    } else {
      _showError('Activity recognition is not available on this device');
    }
  }

  Future<void> _initActivityListener() async {
    _subscription?.cancel();
    _subscription = _activityRecognition.activityStream().listen(
      (activity) {
        if (mounted) {
          setState(() {
            _lastActivity = activity;
          });
        }
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
        success = await _activityRecognition.startTracking(
          showNotification: true,
          useTransitionRecognition: true,
          useActivityRecognition: true,
          detectionIntervalMillis: 10000,
          confidenceThreshold: 50,
        );
        if (success) {
          await _initActivityListener();
        }
      }

      if (success) {
        setState(() {
          _isTracking = !_isTracking;
        });
      }
    } catch (e) {
      if (mounted) {
        _showError('Error changing tracking status');
      }
    }
  }

  void _showError(String message) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(message),
        action: SnackBarAction(
            label: 'Retry',
            onPressed: () async {
              await _checkPermissionsAndAvailability();
            }),
      ),
    );
  }

  Future<void> _handlePopInvoked(bool didPop) async {
    if (didPop) {
      return;
    }

    if (_isTracking) {
      await _activityRecognition.stopTracking();
    }
    await _subscription?.cancel();

    if (mounted) {
      SystemNavigator.pop();
    }
  }
}
