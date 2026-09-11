import Foundation
import SwiftUI

@MainActor
final class SettingsStore: ObservableObject {
    @AppStorage("bridgeHost") var bridgeHost: String = "mypc.tailc2bc11.ts.net"
    @AppStorage("bridgePort") var bridgePort: Int = 8791
    @AppStorage("bridgeToken") var bridgeToken: String = ""
    @AppStorage("captureUrl") var captureUrl: String = PairUrls.defaultCapture
    @AppStorage("hubUrl") var hubUrl: String = PairUrls.defaultHub
    @AppStorage("hubToken") var hubToken: String = ""
    @AppStorage("postIdeaOnSend") var postIdeaOnSend: Bool = true

    var bridgeBaseUrl: String {
        let host = bridgeHost
            .replacingOccurrences(of: "https://", with: "")
            .replacingOccurrences(of: "http://", with: "")
            .split(separator: "/").first
            .map(String.init) ?? bridgeHost
        let port = min(max(bridgePort, 1), 65_535)
        return "http://\(host):\(port)"
    }

    var isPaired: Bool { !hubToken.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty }

    func apply(_ pair: StudioPair) {
        hubUrl = pair.hubUrl
        hubToken = pair.hubToken
        if !pair.captureUrl.isEmpty { captureUrl = pair.captureUrl }
        postIdeaOnSend = true
    }
}
