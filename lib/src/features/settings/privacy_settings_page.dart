import 'package:flutter/material.dart';
import 'package:m3e_core/m3e_core.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';

import '../../../l10n/app_localizations.dart';
import '../../core/platform_service.dart';
import '../../core/analytics_service.dart';
import '../auth/app_lock_controller.dart';
import 'settings_controller.dart';
import 'settings_card_list.dart';

class PrivacySettingsPage extends ConsumerWidget {
  const PrivacySettingsPage({super.key});

  String _analyticsTitle(BuildContext context) => Localizations.localeOf(context).languageCode == 'zh' ? '匿名使用统计' : 'Anonymous Usage Analytics';

  String _analyticsDescription(BuildContext context) => Localizations.localeOf(context).languageCode == 'zh'
      ? '帮助改进应用，仅记录启动和设置事件；不收集账号、内容、IP 地址或设备标识。'
      : 'Help improve the app with anonymous launch and setting events. No account, content, IP address, or device identifier is collected.';

  @override
  Widget build(BuildContext context, WidgetRef ref) {
    final settings = ref.watch(settingsProvider).valueOrNull;
    if (settings == null) return const Scaffold(body: Center(child: M3EContainedLoadingIndicator()));
    final controller = ref.read(settingsProvider.notifier);
    final l10n = AppLocalizations.of(context)!;
    return Scaffold(
      appBar: AppBar(title: Text(l10n.privacySettings)),
      body: ListView(children: [
         SettingsCardList(children: [
           SettingsCardItem(title: l10n.appLock, subtitle: l10n.appLockDescription, trailing: Switch(value: settings.appLockEnabled, onChanged: (value) async { if (!value || await PlatformService.authenticate()) { if (value) ref.read(appLockProvider.notifier).markUnlocked(); await controller.saveChanges((current) => current.copyWith(appLockEnabled: value)); } })),
           SettingsCardItem(title: l10n.emergencyExit, subtitle: l10n.emergencyExitDescription, trailing: Switch(value: settings.emergencyExitEnabled, onChanged: (value) async { await PlatformService.setEmergencyExit(value); await controller.saveChanges((current) => current.copyWith(emergencyExitEnabled: value)); })),
           SettingsCardItem(title: l10n.hideFromRecents, subtitle: l10n.hideFromRecentsDescription, trailing: Switch(value: settings.hideFromRecents, onChanged: (value) async { await PlatformService.setHideFromRecents(value); await controller.saveChanges((current) => current.copyWith(hideFromRecents: value)); })),
           SettingsCardItem(title: _analyticsTitle(context), subtitle: _analyticsDescription(context), trailing: Switch(value: settings.anonymousAnalyticsEnabled, onChanged: (value) async { await controller.saveChanges((current) => current.copyWith(anonymousAnalyticsEnabled: value)); if (value) unawaited(AnalyticsService().track('analytics_enabled')); })),
         ]),
      ]),
    );
  }
}
