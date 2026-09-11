import Foundation

struct BridgeHealth {
    var ok: Bool
    var authRequired: Bool
}

enum BridgeClient {
    static func health(baseUrl: String) async throws -> BridgeHealth {
        guard let url = URL(string: baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/")) + "/health") else {
            throw URLError(.badURL)
        }
        let (data, response) = try await URLSession.shared.data(from: url)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard (200...299).contains(code) else { throw URLError(.badServerResponse) }
        let raw = String(data: data, encoding: .utf8) ?? ""
        return BridgeHealth(ok: raw.contains("\"ok\""), authRequired: raw.contains("\"auth\":true") || raw.contains("\"auth\": true"))
    }

    static func transcribe(baseUrl: String, token: String, file: URL) async throws -> String {
        let root = baseUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        if let text = try? await post(url: URL(string: root + "/transcribe")!, token: token, file: file, acceptJSON: true),
           !text.isEmpty {
            return text
        }
        return try await post(url: URL(string: root + "/transcribe.txt")!, token: token, file: file, acceptJSON: false)
    }

    static func addIdea(hubUrl: String, token: String, text: String) async throws {
        let key = token.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !key.isEmpty else { return }
        let hub = hubUrl.trimmingCharacters(in: CharacterSet(charactersIn: "/"))
        guard PairUrls.looksSafeHub(hub) else { throw URLError(.unsupportedURL) }
        var request = URLRequest(url: URL(string: hub + "/api/ideas")!)
        request.httpMethod = "POST"
        request.setValue("Bearer \(key)", forHTTPHeaderField: "Authorization")
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        request.httpBody = try JSONSerialization.data(withJSONObject: ["text": text])
        let (_, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        guard code == 200 || code == 201 else { throw URLError(.badServerResponse) }
    }

    private static func post(url: URL, token: String, file: URL, acceptJSON: Bool) async throws -> String {
        let boundary = "SaltBoundary\(UUID().uuidString)"
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue(acceptJSON ? "application/json, text/plain" : "text/plain, application/json", forHTTPHeaderField: "Accept")
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        if !token.isEmpty {
            request.setValue(token, forHTTPHeaderField: "X-Salt-Token")
        }
        let wav = try Data(contentsOf: file)
        var body = Data()
        for name in ["audio", "file"] {
            body.append("--\(boundary)\r\n".data(using: .utf8)!)
            body.append("Content-Disposition: form-data; name=\"\(name)\"; filename=\"clip.wav\"\r\n".data(using: .utf8)!)
            body.append("Content-Type: audio/wav\r\n\r\n".data(using: .utf8)!)
            body.append(wav)
            body.append("\r\n".data(using: .utf8)!)
        }
        body.append("--\(boundary)--\r\n".data(using: .utf8)!)
        request.httpBody = body
        let (data, response) = try await URLSession.shared.data(for: request)
        let code = (response as? HTTPURLResponse)?.statusCode ?? 0
        let raw = String(data: data, encoding: .utf8) ?? ""
        if code == 401 { throw NSError(domain: "SaltBubble", code: 401, userInfo: [NSLocalizedDescriptionKey: "Bridge rejected the token."]) }
        if code == 429 { throw NSError(domain: "SaltBubble", code: 429, userInfo: [NSLocalizedDescriptionKey: "Bridge rate-limited the phone."]) }
        guard (200...299).contains(code) else {
            throw NSError(domain: "SaltBubble", code: code, userInfo: [NSLocalizedDescriptionKey: "Bridge returned HTTP \(code)"])
        }
        return parseTranscript(raw)
    }

    static func parseTranscript(_ body: String) -> String {
        let trimmed = body.trimmingCharacters(in: .whitespacesAndNewlines)
        if trimmed.hasPrefix("{"), let data = trimmed.data(using: .utf8),
           let obj = try? JSONSerialization.jsonObject(with: data) as? [String: Any] {
            for key in ["text", "transcription", "transcript"] {
                if let value = obj[key] as? String, !value.isEmpty { return value }
            }
        }
        return trimmed
    }
}
