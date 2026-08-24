import 'dart:async';
import 'dart:io' show Platform;

import 'package:dio/dio.dart';
import 'package:package_info_plus/package_info_plus.dart';

class AnalyticsService {
  AnalyticsService({Dio? client}) : _client = client ?? Dio();

  static const _endpoint = 'https://analytics.anglesgirl.eu.org/api/app-event';
  final Dio _client;

  Future<void> track(String event) async {
    try {
      final info = await PackageInfo.fromPlatform();
      await _client.post<void>(
        _endpoint,
        data: {'event': event, 'appVersion': '${info.version}+${info.buildNumber}', 'platform': _platform},
        options: Options(
          contentType: Headers.jsonContentType,
          sendTimeout: const Duration(seconds: 3),
          receiveTimeout: const Duration(seconds: 3),
          validateStatus: (status) => status != null && status < 500,
        ),
      );
    } catch (_) {
      // Analytics must never affect app startup or user workflows.
    }
  }

  String get _platform {
    if (Platform.isAndroid) return 'android';
    if (Platform.isIOS) return 'ios';
    if (Platform.isWindows) return 'windows';
    if (Platform.isMacOS) return 'macos';
    return 'linux';
  }
}