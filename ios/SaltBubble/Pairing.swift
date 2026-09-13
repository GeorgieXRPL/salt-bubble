import Foundation

struct StudioPair: Equatable {
    var hubUrl: String
    var hubToken: String
    var captureUrl: String
}

enum PairUrls {
    static let scheme = "saltbubble"
    static let defaultCapture = "https://app.mybrandstudio.club/capture"
    static let defaultHub = "https://brand-studio-hub.fly.dev"

    static func looksSafeHub(_ hub: String) -> Bool {
        let root = hub.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        return root.hasPrefix("https://")
            || root.hasPrefix("http://localhost")
            || root.hasPrefix("http://127.0.0.1")
    }

    static func parse(_ url: URL) -> StudioPair? {
        guard let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else {
            return nil
        }
        let hub = items.first(where: { $0.name == "hub" })?.value ?? ""
        let key = items.first(where: { $0.name == "key" })?.value
            ?? items.first(where: { $0.name == "token" })?.value
            ?? ""
        let capture = items.first(where: { $0.name == "capture" })?.value ?? defaultCapture
        let host = url.host?.lowercased() ?? ""
        let path = url.path.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let schemeOk = url.scheme?.lowercased() == scheme && host == "pair"
        let webOk = url.scheme?.lowercased() == "https"
            && host == "app.mybrandstudio.club"
            && path == "salt-bubble/pair"
        guard schemeOk || webOk || (url.scheme == "https" && path.hasSuffix("salt-bubble/pair")) else {
            if url.scheme?.lowercased() != scheme { return nil }
        }
        return requireValid(hub: hub, key: key, capture: capture)
    }

    static func requireValid(hub: String, key: String, capture: String) -> StudioPair? {
        let cleanedHub = hub.trimmingCharacters(in: .whitespacesAndNewlines)
            .trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        let token = key.trimmingCharacters(in: .whitespacesAndNewlines)
        let cap = capture.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleanedHub.isEmpty, !token.isEmpty, looksSafeHub(cleanedHub) else { return nil }
        return StudioPair(
            hubUrl: cleanedHub,
            hubToken: token,
            captureUrl: cap.isEmpty ? defaultCapture : cap
        )
    }
}

struct BridgePair: Equatable {
    var host: String
    var port: Int
    var https: Bool
    var token: String
}

enum BridgePairUrls {
    static func parse(_ url: URL) -> BridgePair? {
        guard url.scheme?.lowercased() == PairUrls.scheme,
              url.host?.lowercased() == "bridge",
              let items = URLComponents(url: url, resolvingAgainstBaseURL: false)?.queryItems else {
            return nil
        }
        if let expRaw = items.first(where: { $0.name == "exp" })?.value,
           let exp = Int(expRaw),
           exp > 0,
           exp < Int(Date().timeIntervalSince1970) {
            return nil
        }
        let host = items.first(where: { $0.name == "host" })?.value ?? ""
        let token = items.first(where: { $0.name == "token" })?.value ?? ""
        let port = Int(items.first(where: { $0.name == "port" })?.value ?? "") ?? 8791
        let https = ["1", "true", "yes"].contains((items.first(where: { $0.name == "https" })?.value ?? "").lowercased())
        var cleaned = host.trimmingCharacters(in: .whitespacesAndNewlines)
        cleaned = cleaned.replacingOccurrences(of: "https://", with: "")
        cleaned = cleaned.replacingOccurrences(of: "http://", with: "")
        cleaned = cleaned.split(separator: "/").first.map(String.init) ?? cleaned
        cleaned = cleaned.split(separator: ":").first.map(String.init) ?? cleaned
        let key = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !cleaned.isEmpty, !key.isEmpty else { return nil }
        return BridgePair(host: cleaned, port: min(max(port, 1), 65_535), https: https, token: key)
    }
}
