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
