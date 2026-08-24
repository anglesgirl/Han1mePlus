import 'package:flutter/material.dart';
import 'package:flutter_riverpod/flutter_riverpod.dart';
import 'package:go_router/go_router.dart';

import '../../../l10n/app_localizations.dart';


class LoginPage extends ConsumerStatefulWidget {
  const LoginPage({super.key});

  @override
  ConsumerState<LoginPage> createState() => _LoginPageState();
}

class _LoginPageState extends ConsumerState<LoginPage> {

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
        FilledButton.icon(
          onPressed: () => context.push('/login/web'),
          icon: const Icon(Icons.language),
          label: const Text('在应用内打开登录页面'),
        ),
      ]),
    );
  }
}
