import AVFoundation
import Combine
import Foundation
import MiqatKit

@MainActor
final class PhoneAdhan: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var name: String?
    @Published private(set) var prayer: Int?
    @Published private(set) var paused = false
    private var player: AVAudioPlayer?
    private var cutoff: TimeInterval = 0
    private var stopTask: Task<Void, Never>?
    var onPlayback: (() -> Void)?

    func play(url: URL, name: String, selection: AdhanSelection, prayer: Int? = nil) -> Bool {
        guard selection.level > 0, self.prayer == nil || prayer != nil else { return false }
        stop()
        do {
            let next = try AVAudioPlayer(contentsOf: url)
            guard next.duration >= 0.001, next.duration <= 600 else { return false }
            onPlayback?()
            let session = AVAudioSession.sharedInstance()
            try session.setCategory(.playback)
            try session.setActive(true)
            next.delegate = self
            next.volume = Float(selection.level) / 100
            cutoff = Double(PhonePrayerRules.shared.cutoffMillis(recordingMillis: Int32(next.duration * 1000),
                percent: Int32(selection.length), legacySeconds: 0)) / 1000
            player = next
            guard next.play() else { stop(); return false }
            self.name = name
            self.prayer = prayer
            armCutoff()
            return true
        } catch { stop(); return false }
    }

    var progress: Double {
        guard let player, cutoff > 0 else { return 0 }
        return min(1, player.currentTime / cutoff)
    }

    func togglePause() {
        guard let player else { return }
        if paused {
            guard player.play() else { stop(); return }
            paused = false
            armCutoff()
        } else {
            player.pause()
            stopTask?.cancel()
            paused = true
        }
    }

    func stop(previewOnly: Bool = false) {
        guard let player, !previewOnly || prayer == nil else { return }
        stopTask?.cancel()
        stopTask = nil
        player.stop()
        self.player = nil
        name = nil
        prayer = nil
        paused = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func armCutoff() {
        stopTask?.cancel()
        if let player {
            let remaining = max(0, cutoff - player.currentTime)
            stopTask = Task { [weak self] in
                try? await Task.sleep(for: .seconds(remaining))
                if !Task.isCancelled { self?.stop() }
            }
        }
    }

    nonisolated func audioPlayerDidFinishPlaying(_ player: AVAudioPlayer, successfully flag: Bool) {
        Task { @MainActor [weak self] in
            if self?.player === player { self?.stop() }
        }
    }
}
