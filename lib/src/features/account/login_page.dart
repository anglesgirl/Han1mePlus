import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../l10n/app_localizations.dart';
import 'account_controller.dart';

class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {
  var _busy = false;
  Object? _error;

  @override
  void dispose() {
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    setState(() { _busy = true; _error = null; });
    try {
      await ref.read(accountProvider.notifier).loginInInternalWebView();
      if (mounted) Navigator.pop(context);
    } catch (error) {
      if (!mounted) return;
      setState(() { _busy = false; _error = error; });
    }
  }

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      resizeToAvoidBottomInset: true,
      appBar: AppBar(title: Text(AppLocalizations.of(context)!.login)),
      floatingActionButton: FloatingActionButton(
        tooltip: AppLocalizations.of(context)!.manualCookieLogin,
        onPressed: () => context.push('/login/cookies'),
        child: const Icon(Icons.cookie_outlined),
      ),
      body: ListView(padding: const EdgeInsets.all(24), children: [
        FilledButton.icon(onPressed: _busy ? null : _submit, icon: const Icon(Icons.language), label: const Text('在应用内打开登录页面')),
        if (_error != null) ...[const SizedBox(height: 16), Text('$_error', style: TextStyle(color: Colors.red))],
        if (_busy) ...[const SizedBox(height: 16), const LinearProgressIndicator()],
      ]),
    );
  }
}
