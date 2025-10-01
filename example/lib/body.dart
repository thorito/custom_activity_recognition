import 'package:app_settings/app_settings.dart';
import 'package:custom_activity_recognition/activity_types.dart';
import 'package:custom_activity_recognition/custom_activity_permission_status.dart';
import 'package:custom_activity_recognition/custom_activity_recognition.dart';
import 'package:flutter/material.dart';

class ActivityRecognitionPage extends StatefulWidget {
  const ActivityRecognitionPage({super.key});

  @override
  State<ActivityRecognitionPage> createState() =>
      _ActivityRecognitionPageState();
}

class _ActivityRecognitionPageState extends State<ActivityRecognitionPage>
    with WidgetsBindingObserver {
  final CustomActivityRecognition _activityRecognition =
      CustomActivityRecognition.instance;
  bool _isAvailable = false;
  CustomActivityPermissionStatus _permissionStatus =
      CustomActivityPermissionStatus.notDetermined;
  bool _isRequestingPermissions = false;
  bool _isTracking = false;
  String _currentActivity = "UNKNOWN";
  DateTime? _lastUpdate;

  @override
  void initState() {
    super.initState();
    WidgetsBinding.instance.addObserver(this);
    _checkAvailability();
  }

  @override
  Future<void> dispose() async {
    WidgetsBinding.instance.removeObserver(this);
    if (_isTracking) {
      await _activityRecognition.stopTracking();
    }
    super.dispose();
  }

  @override
  void didChangeAppLifecycleState(AppLifecycleState state) {
    if (state == AppLifecycleState.resumed) {
      _checkAvailability();
    }
  }

  @override
  Widget build(BuildContext context) => Scaffold(
        appBar: AppBar(
          title: Text('Activity Recognition'),
        ),
        body: Padding(
          padding: const EdgeInsets.all(16),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.center,
            children: [
              Card(
                child: Padding(
                  padding: const EdgeInsets.all(16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text('Status:',
                          style: TextStyle(fontWeight: FontWeight.bold)),
                      SizedBox(height: 8),
                      _buildStatusItem('Available', _isAvailable),
                      _buildPermissionStatusItem(
                          'Permission', _permissionStatus),
                      _buildStatusItem('Tracking', _isTracking),
                    ],
                  ),
                ),
              ),
              SizedBox(height: 16),
              SizedBox(
                width: double.infinity,
                child: Card(
                  child: Padding(
                    padding: const EdgeInsets.all(16),
                    child: Column(
                      crossAxisAlignment: CrossAxisAlignment.start,
                      children: [
                        Text('Current activity:',
                            style: TextStyle(fontWeight: FontWeight.bold)),
                        SizedBox(height: 8),
                        Text(_currentActivity, style: TextStyle(fontSize: 24)),
                        if (_lastUpdate != null)
                          Text(
                            'Last update: ${_formatDateTime(_lastUpdate!)}',
                            style: TextStyle(color: Colors.grey),
                          ),
                      ],
                    ),
                  ),
                ),
              ),
              SizedBox(height: 16),
              if (_permissionStatus !=
                  CustomActivityPermissionStatus.authorized)
                SizedBox(
                  width: double.infinity,
                  child: ElevatedButton(
                    onPressed:
                        _isRequestingPermissions ? null : _requestPermissions,
                    child: _isRequestingPermissions
                        ? Row(
                            mainAxisAlignment: MainAxisAlignment.center,
                            children: [
                              SizedBox(
                                width: 20,
                                height: 20,
                                child: CircularProgressIndicator(
                                  strokeWidth: 2,
                                ),
                              ),
                              SizedBox(width: 10),
                              Text(
                                'Requesting permissions...',
                                style: TextStyle(fontSize: 16),
                              ),
                            ],
                          )
                        : Text(
                            'Request Permissions',
                            style: TextStyle(fontSize: 16),
                          ),
                  ),
                ),
              SizedBox(height: 8),
              SizedBox(
                width: double.infinity,
                child: ElevatedButton(
                  onPressed: _permissionStatus ==
                          CustomActivityPermissionStatus.authorized
                      ? (_isTracking ? _stopTracking : _startTracking)
                      : null,
                  style: ElevatedButton.styleFrom(
                    backgroundColor: _isTracking ? Colors.red : Colors.green,
                  ),
                  child: Text(
                    _isTracking ? 'Stop Tracking' : 'Start Tracking',
                    style: TextStyle(fontSize: 16, color: Colors.white),
                  ),
                ),
              ),
            ],
          ),
        ),
      );

  Future<void> _checkAvailability() async {
    final isAvailable = await _activityRecognition.isAvailable();

    if (isAvailable) {
      final status = await _activityRecognition.checkPermissionStatus();

      setState(() {
        _isAvailable = isAvailable;
        _permissionStatus = status;
      });

      if (status.isGranted) {
        _setupActivityStream();
      }
    } else {
      setState(() {
        _isAvailable = isAvailable;
      });
    }
  }

  Future<void> _requestPermissions() async {
    if (_isRequestingPermissions) return;

    try {
      setState(() {
        _isRequestingPermissions = true;
      });

      CustomActivityPermissionStatus status =
          await _activityRecognition.checkPermissionStatus();

      if (status.isRestricted || status.isPermanentlyDenied) {
        _showOpenSettingsDialog();
      } else {
        final isGranted = await _activityRecognition.requestPermissions();
        debugPrint('Permission granted: $isGranted');

        status = await _activityRecognition.checkPermissionStatus();

        if (mounted) {
          setState(() {
            _permissionStatus = status;
            _isRequestingPermissions = false;
          });
        }
      }

      if (status.isGranted) {
        _setupActivityStream();
      }
    } catch (e) {
      debugPrint('Error requesting permissions: $e');
      if (mounted) {
        setState(() {
          _isRequestingPermissions = false;
        });

        ScaffoldMessenger.of(context).showSnackBar(
          SnackBar(
            content: Text('Error requesting permissions: $e'),
            backgroundColor: Colors.red,
          ),
        );
      }
    } finally {
      if (mounted) {
        setState(() {
          _isRequestingPermissions = false;
        });
      }
    }
  }

  void _setupActivityStream() {
    _activityRecognition.activityStream().listen((ActivityData data) {
      setState(() {
        _currentActivity = data.activity;
        _lastUpdate = data.timestamp;
      });
    });
  }

  Future<void> _startTracking() async {
    if (!_permissionStatus.isGranted) {
      if (_permissionStatus.isPermanentlyDenied) {
        _showOpenSettingsDialog();
        return;
      }
      await _requestPermissions();
      if (!_permissionStatus.isGranted) {
        return;
      }
    }

    final success = await _activityRecognition.startTracking(
      showNotification: true,
      useTransitionRecognition: true,
      useActivityRecognition: true,
      detectionIntervalMillis: 5000,
      confidenceThreshold: 70,
    );

    setState(() {
      _isTracking = success;
    });
  }

  Future<void> _stopTracking() async {
    final success = await _activityRecognition.stopTracking();

    setState(() {
      _isTracking = !success;
    });
  }

  void _showOpenSettingsDialog() {
    showDialog(
      context: context,
      builder: (context) => AlertDialog(
        title: Text('Permission Permanently Denied'),
        content: Text(
            'You have permanently denied the activity recognition permission. '
            'Please open the app settings to enable it manually.'),
        actions: [
          TextButton(
            onPressed: () => Navigator.of(context).pop(),
            child: Text('Cancel'),
          ),
          TextButton(
            onPressed: () async {
              Navigator.of(context).pop();
              await AppSettings.openAppSettings();
            },
            child: Text('Open Settings'),
          ),
        ],
      ),
    );
  }

  Widget _buildStatusItem(String label, bool value) => Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Icon(
              value ? Icons.check_circle : Icons.cancel,
              color: value ? Colors.green : Colors.red,
            ),
            SizedBox(width: 8),
            Text(label),
          ],
        ),
      );

  Widget _buildPermissionStatusItem(
    String label,
    CustomActivityPermissionStatus status,
  ) =>
      Padding(
        padding: const EdgeInsets.symmetric(vertical: 4),
        child: Row(
          children: [
            Icon(
              _getIconForPermissionStatus(status),
              color: _getColorForPermissionStatus(status),
            ),
            SizedBox(width: 8),
            Text('$label: ${_getTextForPermissionStatus(status)}'),
          ],
        ),
      );

  Color _getColorForPermissionStatus(CustomActivityPermissionStatus status) {
    switch (status) {
      case CustomActivityPermissionStatus.authorized:
        return Colors.green;
      case CustomActivityPermissionStatus.denied:
        return Colors.orange;
      case CustomActivityPermissionStatus.restricted:
        return Colors.red;
      case CustomActivityPermissionStatus.permanentlyDenied:
        return Colors.red;
      case CustomActivityPermissionStatus.notDetermined:
        return Colors.blue;
    }
  }

  String _getTextForPermissionStatus(CustomActivityPermissionStatus status) {
    switch (status) {
      case CustomActivityPermissionStatus.authorized:
        return 'Authorized';
      case CustomActivityPermissionStatus.denied:
        return 'Denied';
      case CustomActivityPermissionStatus.restricted:
        return 'Restricted';
      case CustomActivityPermissionStatus.permanentlyDenied:
        return 'Permanently Denied';
      case CustomActivityPermissionStatus.notDetermined:
        return 'Not Determined';
    }
  }

  IconData _getIconForPermissionStatus(CustomActivityPermissionStatus status) {
    switch (status) {
      case CustomActivityPermissionStatus.authorized:
        return Icons.check_circle;
      case CustomActivityPermissionStatus.denied:
        return Icons.cancel;
      case CustomActivityPermissionStatus.restricted:
        return Icons.lock;
      case CustomActivityPermissionStatus.permanentlyDenied:
        return Icons.block;
      case CustomActivityPermissionStatus.notDetermined:
        return Icons.help_outline;
    }
  }

  String _formatDateTime(DateTime dateTime) =>
      '${dateTime.hour.toString().padLeft(2, '0')}:'
      '${dateTime.minute.toString().padLeft(2, '0')}:'
      '${dateTime.second.toString().padLeft(2, '0')}';
}
