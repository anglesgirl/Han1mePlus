import 'dart:io';

class EchNetworkSettings {
  const EchNetworkSettings({
    required this.enabled,
    required this.dohPreset,
    required this.dohCustomUrl,
    required this.dohBootstrapIps,
  });

  const EchNetworkSettings.disabled()
      : enabled = false,
        dohPreset = 'cloudflare',
        dohCustomUrl = '',
        dohBootstrapIps = '';

  final bool enabled;
  final String dohPreset;
  final String dohCustomUrl;
  final String dohBootstrapIps;

  String get dohUrl => switch (dohPreset) {
        // 纯数字 IP DoH：移动网络下 DoH 域名（dns.alidns.com / cloudflare-dns.com 等）
        // 会被污染或屏蔽，而 IP 直连 223.5.5.5（阿里）可稳定拉取 CF 共享 ECH 配置（RFC8484）
        'alidns' => 'https://223.5.5.5/dns-query',
        'dnspod' => 'https://119.29.29.29/dns-query',
        'custom' when dohCustomUrl.trim().isNotEmpty => dohCustomUrl.trim(),
        _ => 'https://223.5.5.5/dns-query',
      };

  String get dohResolve {
    final uri = Uri.parse(dohUrl);
    final addresses = dohBootstrapIps
        .split(RegExp(r'[,;\s]+'))
        .map((value) => value.trim())
        .where((value) => InternetAddress.tryParse(value) != null)
        .map((value) => value.contains(':') ? '[$value]' : value)
        .toList();
    if (addresses.isEmpty) return '';
    return '${uri.host}:${uri.hasPort ? uri.port : 443}:${addresses.join(',')}';
  }
}
