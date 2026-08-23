class EchNetworkSettings {
  const EchNetworkSettings({required this.enabled});

  const EchNetworkSettings.disabled() : enabled = false;

  static const gatewayDohUrl = 'https://tgxjjdszvu.cloudflare-gateway.com/dns-query';
  final bool enabled;

  String get dohUrl => gatewayDohUrl;
  String get dohResolve => '';
}
