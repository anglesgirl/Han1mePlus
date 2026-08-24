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
  final _email = TextEditingController();
  final _name = TextEditingController();
  final _password = TextEditingController();
  var _registerMode = false;
  var _busy = false;
  Object? _error;

  @override
  void dispose() {
    _email.dispose();
    _name.dispose();
    _password.dispose();
    super.dispose();
  }

  Future<void> _submit() async {
    if (_busy) return;
    final email = _email.text.trim();
    final name = _name.text.trim();
    final password = _password.text;
    if (!email.contains('@') || password.isEmpty || (_registerMode && name.isEmpty)) {
      setState(() => _error = StateError('请完整填写表单'));
      return;
    }
    setState(() { _busy = true; _error = null; });
    try {
      if (_registerMode) {
        await ref.read(accountProvider.notifier).register(email, name, password);
        if (!mounted) return;
        setState(() { _registerMode = false; _busy = false; _error = StateError('注册完成，请使用新账号登录'); });
      } else {
        await ref.read(accountProvider.notifier).login(email, password);
        if (mounted) Navigator.pop(context);
      }
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
        SegmentedButton<bool>(
          segments: const [ButtonSegment(value: false, label: Text('登录')), ButtonSegment(value: true, label: Text('注册'))],
          selected: {_registerMode},
          onSelectionChanged: _busy ? null : (value) => setState(() { _registerMode = value.first; _error = null; }),
        ),
        const SizedBox(height: 24),
        TextField(controller: _email, keyboardType: TextInputType.emailAddress, autofillHints: const [AutofillHints.email], decoration: const InputDecoration(labelText: '邮箱', border: OutlineInputBorder())),
        if (_registerMode) ...[
          const SizedBox(height: 16),
          TextField(controller: _name, textInputAction: TextInputAction.next, decoration: const InputDecoration(labelText: '用户名', border: OutlineInputBorder())),
        ],
        const SizedBox(height: 16),
        TextField(controller: _password, obscureText: true, onSubmitted: (_) => _submit(), decoration: const InputDecoration(labelText: '密码', border: OutlineInputBorder())),
        const SizedBox(height: 20),
        FilledButton.icon(onPressed: _busy ? null : _submit, icon: Icon(_registerMode ? Icons.person_add : Icons.login), label: Text(_registerMode ? '注册' : '登录')),
        if (_error != null) ...[const SizedBox(height: 16), Text('$_error', style: TextStyle(color: Colors.red))],
        if (_busy) ...[const SizedBox(height: 16), const LinearProgressIndicator()],
      ]),
    );
  }
}
