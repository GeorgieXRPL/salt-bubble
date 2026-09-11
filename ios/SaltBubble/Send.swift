import UIKit

enum Destination: String, CaseIterable, Identifiable {
    case grok = "Grok Bot"
    case studio = "Brand Studio"
    case both = "Both"
    var id: String { rawValue }
    var includesStudio: Bool { self == .studio || self == .both }
}

enum SendHelper {
    static func copy(_ text: String) {
        UIPasteboard.general.string = text
    }

    static func share(_ text: String) {
        let boxed = text
        DispatchQueue.main.async {
            let vc = UIActivityViewController(activityItems: [boxed], applicationActivities: nil)
            guard let scene = UIApplication.shared.connectedScenes.first as? UIWindowScene,
                  let root = scene.windows.first(where: { $0.isKeyWindow })?.rootViewController else { return }
            root.present(vc, animated: true)
        }
    }

    static func openCapture(url: String, text: String) {
        var target = url.trimmingCharacters(in: .whitespacesAndNewlines)
        if target.isEmpty { target = PairUrls.defaultCapture }
        let encoded = text.addingPercentEncoding(withAllowedCharacters: .urlQueryAllowed) ?? text
        let sep = target.contains("?") ? "&" : "?"
        guard let uri = URL(string: "\(target)\(sep)text=\(encoded)") else { return }
        DispatchQueue.main.async {
            UIApplication.shared.open(uri)
        }
    }

    static func send(_ text: String, to destination: Destination, captureUrl: String) {
        let trimmed = text.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !trimmed.isEmpty else { return }
        copy(trimmed)
        switch destination {
        case .grok:
            share(trimmed)
        case .studio:
            openCapture(url: captureUrl, text: trimmed)
        case .both:
            openCapture(url: captureUrl, text: trimmed)
            share(trimmed)
        }
    }
}
