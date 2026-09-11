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
                        .foregroundStyle(.cyan)
                    Text("Salt Bubble")
                        .font(.largeTitle.bold())
                    Text("iOS cannot float over Camera. Hold to talk here, then send to Grok or Brand Studio.")
                        .foregroundStyle(.secondary)

                    GroupBox("BRIDGE") {
                        Text(settings.bridgeBaseUrl)
                        Text(settings.bridgeToken.isEmpty ? "Bridge token missing" : "Bridge token set")
                            .foregroundStyle(settings.bridgeToken.isEmpty ? .red : .secondary)
                        Text(settings.isPaired ? "Brand Studio paired" : "Brand Studio not paired")
                            .foregroundStyle(settings.isPaired ? .cyan : .secondary)
                        if !pairNote.isEmpty { Text(pairNote).foregroundStyle(.cyan) }
                        if !ping.isEmpty { Text(ping).foregroundStyle(.secondary) }
                        Button("Ping bridge") { Task { await pingBridge() } }
                    }

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
            }
        }
    }

    private var holdButton: some View {
        Text(recording ? "Listening… release" : "Hold to talk")
            .font(.headline)
            .frame(maxWidth: .infinity, minHeight: 56)
            .background(recording ? Color.red.opacity(0.85) : Color.cyan, in: RoundedRectangle(cornerRadius: 14))
            .foregroundStyle(.black)
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
        SendHelper.send(transcript, to: dest, captureUrl: settings.captureUrl)
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
                    ? "Bridge is up — paste the BRIDGE TOKEN in Settings"
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
