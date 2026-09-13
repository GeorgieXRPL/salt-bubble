import SwiftUI

struct ContentView: View {
    @EnvironmentObject private var settings: SettingsStore
    @State private var destination: Destination = .both
    @State private var transcript = ""
    @State private var status = "Hold to talk. Whisper runs on your PC."
    @State private var recording = false
    @State private var busy = false
    @State private var ping = ""
    @State private var pairNote = ""
    private let recorder = WavRecorder()

    var body: some View {
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 16) {
                    Text("EASTON · HUD")
                        .font(.caption.weight(.semibold))
                        .foregroundStyle(Color(red: 0.18, green: 0.90, blue: 0.84))
                    Text("Salt Bubble")
                        .font(.largeTitle.bold())
                    Text("iOS cannot float over Camera. Hold to talk here, then send to Grok or Brand Studio. Session wake lives on Android.")
                        .foregroundStyle(.secondary)

                    GroupBox("READY") {
                        Text(settings.bridgeToken.isEmpty ? "Bridge not paired" : "Bridge paired · private tailnet")
                            .foregroundStyle(settings.bridgeToken.isEmpty ? .red : .secondary)
                        Text(settings.isPaired ? "Studio paired" : "Studio not paired")
                            .foregroundStyle(settings.isPaired ? Color(red: 0.18, green: 0.90, blue: 0.84) : .secondary)
                        Text("Grok on \(settings.grokLand.lowercased())")
                            .foregroundStyle(.secondary)
                        if !pairNote.isEmpty { Text(pairNote).foregroundStyle(Color(red: 0.18, green: 0.90, blue: 0.84)) }
                        if !ping.isEmpty { Text(ping).foregroundStyle(.secondary) }
                        Button("Ping bridge") { Task { await pingBridge() } }
                    }

                    Picker("Grok lands", selection: $settings.grokLand) {
                        Text("Phone").tag("Phone")
                        Text("PC").tag("PC")
                        Text("Both").tag("Both")
                    }
                    .pickerStyle(.segmented)

                    Picker("Send", selection: $destination) {
                        ForEach(Destination.allCases) { Text($0.rawValue).tag($0) }
                    }
                    .pickerStyle(.segmented)

                    Text(status)
                        .font(.callout)
                    Text(transcript.isEmpty ? "—" : transcript)
                        .frame(maxWidth: .infinity, alignment: .leading)
                        .padding()
                        .background(.thinMaterial, in: RoundedRectangle(cornerRadius: 12))

                    holdButton

                    HStack {
                        Button("Grok") { send(.grok) }
                        Button("Studio") { send(.studio) }
                        Button("Both") { send(.both) }
                    }
                    .buttonStyle(.bordered)
                    .disabled(transcript.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty)
                }
                .padding()
            }
            .navigationTitle("Salt Bubble")
            .toolbar {
                NavigationLink("Settings") { SettingsView() }
            }
            .onOpenURL { url in
                if let pair = PairUrls.parse(url) {
                    settings.apply(pair)
                    pairNote = "Paired with Brand Studio"
                }
                if let bridge = BridgePairUrls.parse(url) {
                    settings.apply(bridge)
                    pairNote = "Paired with this PC over Tailscale"
                    Task {
                        do {
                            try await BridgeClient.pair(baseUrl: settings.bridgeBaseUrl, token: settings.bridgeToken)
                        } catch {
                            pairNote = "Bridge saved — pin failed"
                        }
                    }
                }
            }
        }
    }

    private var holdButton: some View {
        Text(busy ? "Transcribing" : recording ? "Release" : "Hold to talk")
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(
                busy ? Color.gray.opacity(0.35) : recording ? Color(red: 0.18, green: 0.90, blue: 0.84) : Color(red: 0.05, green: 0.43, blue: 0.41),
                in: RoundedRectangle(cornerRadius: 14)
            )
            .foregroundStyle(recording ? .black : Color(red: 0.49, green: 0.96, blue: 0.93))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { _ in
                        if !recording && !busy { startTalk() }
                    }
                    .onEnded { _ in
                        if recording { stopTalk() }
                    }
            )
    }

    private func startTalk() {
        Task {
            guard await recorder.requestPermission() else {
                status = "Grant the microphone to talk."
                return
            }
            do {
                _ = try recorder.start()
                recording = true
                status = "Listening… release to transcribe"
            } catch {
                status = error.localizedDescription
            }
        }
    }

    private func stopTalk() {
        recording = false
        guard let file = recorder.stop() else {
            status = "That take was too short."
            return
        }
        busy = true
        status = "Transcribing on your PC…"
        Task {
            defer { busy = false }
            do {
                let text = try await BridgeClient.transcribe(
                    baseUrl: settings.bridgeBaseUrl,
                    token: settings.bridgeToken,
                    file: file
                )
                try? FileManager.default.removeItem(at: file)
                if text.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty {
                    status = "Heard silence. Hold a little longer."
                } else {
                    transcript = text
                    status = "Ready"
                }
            } catch {
                try? FileManager.default.removeItem(at: file)
                status = error.localizedDescription
            }
        }
    }

    private func send(_ dest: Destination) {
        let land = settings.grokLand
        let wantsGrok = dest == .grok || dest == .both
        if dest == .studio || dest == .both {
            SendHelper.send(transcript, to: .studio, captureUrl: settings.captureUrl)
        }
        if wantsGrok {
            if land == "PC" || land == "Both" {
                Task {
                    do {
                        try await BridgeClient.sendToGrok(
                            baseUrl: settings.bridgeBaseUrl,
                            token: settings.bridgeToken,
                            text: transcript
                        )
                        status = "Pasted into Grok Bot on MyPC"
                    } catch {
                        if land != "PC" {
                            SendHelper.send(transcript, to: .grok, captureUrl: settings.captureUrl)
                            status = "Bridge paste failed. Shared on this phone."
                        } else {
                            status = "Could not paste into Grok on MyPC"
                        }
                    }
                }
            } else {
                SendHelper.send(transcript, to: .grok, captureUrl: settings.captureUrl)
            }
        }
        if dest.includesStudio, settings.postIdeaOnSend, settings.isPaired {
            Task {
                do {
                    try await BridgeClient.addIdea(hubUrl: settings.hubUrl, token: settings.hubToken, text: transcript)
                    status = "Saved to Brand Studio ideas"
                } catch {
                    status = "Could not POST idea: \(error.localizedDescription)"
                }
            }
        }
    }

    private func pingBridge() async {
        ping = "Pinging…"
        do {
            let health = try await BridgeClient.health(baseUrl: settings.bridgeBaseUrl)
            if health.ok {
                ping = health.authRequired && settings.bridgeToken.isEmpty
                    ? "Bridge is up — open PAIR PHONE from the PC"
                    : "Bridge is up"
            } else {
                ping = "Bridge answered but is not healthy"
            }
        } catch {
            ping = "Could not reach \(settings.bridgeBaseUrl)"
        }
    }
}

struct SettingsView: View {
    @EnvironmentObject private var settings: SettingsStore

    var body: some View {
        Form {
            Section("saltvoice-local bridge") {
                TextField("Host", text: $settings.bridgeHost)
                    .textInputAutocapitalization(.never)
                    .autocorrectionDisabled()
                TextField("Port", value: $settings.bridgePort, format: .number)
                    .keyboardType(.numberPad)
                SecureField("Bridge token", text: $settings.bridgeToken)
                Text(settings.bridgeBaseUrl).font(.caption).foregroundStyle(.secondary)
            }
            Section("Brand Studio") {
                TextField("Capture URL", text: $settings.captureUrl)
                    .textInputAutocapitalization(.never)
                TextField("Hub URL", text: $settings.hubUrl)
                    .textInputAutocapitalization(.never)
                SecureField("Hub token (ak_…)", text: $settings.hubToken)
                Toggle("POST idea when sending to Brand Studio", isOn: $settings.postIdeaOnSend)
                Text("Pair from Agents → Open Salt Bubble, or paste a key.")
                    .font(.caption)
                    .foregroundStyle(.secondary)
            }
        }
        .navigationTitle("Settings")
    }
}
