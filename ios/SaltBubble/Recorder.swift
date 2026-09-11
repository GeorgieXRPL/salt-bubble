import AVFoundation
import Foundation

enum RecorderError: LocalizedError {
    case permission
    case start
    var errorDescription: String? {
        switch self {
        case .permission: return "Microphone permission denied"
        case .start: return "Could not start the microphone"
        }
    }
}

final class WavRecorder {
    private var recorder: AVAudioRecorder?
    private var fileURL: URL?

    func requestPermission() async -> Bool {
        await withCheckedContinuation { cont in
            AVAudioSession.sharedInstance().requestRecordPermission { allowed in
                cont.resume(returning: allowed)
            }
        }
    }

    func start() throws -> URL {
        let session = AVAudioSession.sharedInstance()
        try session.setCategory(.playAndRecord, mode: .default, options: [.defaultToSpeaker])
        try session.setActive(true)
        let url = FileManager.default.temporaryDirectory
            .appendingPathComponent("salt-\(UUID().uuidString).wav")
        let settings: [String: Any] = [
            AVFormatIDKey: Int(kAudioFormatLinearPCM),
            AVSampleRateKey: 16_000,
            AVNumberOfChannelsKey: 1,
            AVLinearPCMBitDepthKey: 16,
            AVLinearPCMIsFloatKey: false,
            AVLinearPCMIsBigEndianKey: false,
        ]
        let rec = try AVAudioRecorder(url: url, settings: settings)
        guard rec.record() else { throw RecorderError.start }
        recorder = rec
        fileURL = url
        return url
    }

    func stop() -> URL? {
        recorder?.stop()
        recorder = nil
        return fileURL
    }
}
