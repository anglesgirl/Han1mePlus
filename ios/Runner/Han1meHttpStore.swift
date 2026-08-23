import Foundation

enum Han1meHttpStore {
    static let preferencesName = "han1meplus_http"
    static let cookieKey = "cookies"
    static let gatewayDohUrl = "https://tgxjjdszvu.cloudflare-gateway.com/dns-query"

    static let hanimeHosts: Set<String> = [
        "hanime1.me", "hanime1.com", "hanimeone.me", "javchu.com",
    ]

    private static var defaults: UserDefaults { .standard }

    static func cookieHosts(for host: String) -> Set<String> {
        hanimeHosts.contains(host) ? hanimeHosts : [host]
    }

    static func mergeCookies(_ current: String, _ next: String) -> String {
        var map: [String: String] = [:]
        for raw in (current + ";" + next).split(separator: ";") {
            let pair = raw.trimmingCharacters(in: .whitespaces)
            guard let index = pair.firstIndex(of: "=") else { continue }
            let name = String(pair[..<index]).trimmingCharacters(in: .whitespaces)
            let value = String(pair[pair.index(after: index)...]).trimmingCharacters(in: .whitespaces)
            if !name.isEmpty { map[name] = value }
        }
        return map.map { "\($0.key)=\($0.value)" }.joined(separator: "; ")
    }

    static func saveCookies(_ cookies: String, url: String) {
        guard let host = URL(string: url)?.host else { return }
        for target in cookieHosts(for: host) {
            let key = "\(cookieKey):\(target)"
            let current = defaults.string(forKey: key) ?? ""
            defaults.set(mergeCookies(current, cookies), forKey: key)
        }
    }

    static func readCookies(host: String) -> String {
        defaults.string(forKey: "\(cookieKey):\(host)") ?? ""
    }

    static func clearCookies(url: String) {
        guard let host = URL(string: url)?.host else { return }
        for target in cookieHosts(for: host) {
            defaults.removeObject(forKey: "\(cookieKey):\(target)")
        }
    }

    static func hasCookie(url: String, name: String) -> Bool {
        guard let host = URL(string: url)?.host else { return false }
        let cookies = readCookies(host: host)
        return cookies.split(separator: ";").contains { part in
            part.trimmingCharacters(in: .whitespaces)
                .split(separator: "=", maxSplits: 1)
                .first?
                .trimmingCharacters(in: .whitespaces)
                .caseInsensitiveCompare(name) == .orderedSame
        }
    }
}
