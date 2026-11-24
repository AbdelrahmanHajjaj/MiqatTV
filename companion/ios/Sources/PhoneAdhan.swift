import AVFoundation
import Combine
import Foundation
import MediaPlayer
import MiqatKit

@MainActor
final class PhoneAdhan: NSObject, ObservableObject, AVAudioPlayerDelegate {
    @Published private(set) var name: String?
    @Published private(set) var prayer: Int?
    @Published private(set) var paused = false
    private var player: AVAudioPlayer?
    private var cutoff: TimeInterval = 0
    private var mediaTitle: String?
    private var artwork: MPMediaItemArtwork?
    private var stopTask: Task<Void, Never>?
    var onPlayback: (() -> Void)?

    func play(url: URL, name: String, selection: AdhanSelection, prayer: Int? = nil,
              mediaTitle: String? = nil, artwork: MPMediaItemArtwork? = nil) -> Bool {
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
            self.mediaTitle = mediaTitle
            self.artwork = artwork
            if mediaTitle != nil {
                PhoneRemotePlayback.shared.begin(.adhan) { [weak self] action in
                    guard let self else { return }
                    switch action {
                    case .play: self.setPaused(false)
                    case .pause: self.setPaused(true)
                    case .toggle: self.togglePause()
                    case .stop: self.stop()
                    }
                }
                publishPlayback()
            }
            armCutoff()
            return true
        } catch { stop(); return false }
    }

    var progress: Double {
        guard let player, cutoff > 0 else { return 0 }
        return min(1, player.currentTime / cutoff)
    }

    func togglePause() { setPaused(!paused) }

    func pauseForRouteChange() {
        if prayer == nil { stop(previewOnly: true) }
        else { setPaused(true) }
    }

    private func setPaused(_ value: Bool) {
        guard let player, paused != value else { return }
        if value {
            player.pause()
            stopTask?.cancel()
            paused = true
        } else {
            do { try AVAudioSession.sharedInstance().setActive(true) }
            catch { stop(); return }
            guard player.play() else { stop(); return }
            paused = false
            armCutoff()
        }
        publishPlayback()
    }

    func stop(previewOnly: Bool = false) {
        guard let player, !previewOnly || prayer == nil else { return }
        stopTask?.cancel()
        stopTask = nil
        player.stop()
        self.player = nil
        mediaTitle = nil
        artwork = nil
        PhoneRemotePlayback.shared.end(.adhan)
        name = nil
        prayer = nil
        paused = false
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func publishPlayback() {
        guard let player, let name, let mediaTitle else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: mediaTitle,
            MPMediaItemPropertyArtist: name,
            MPMediaItemPropertyAlbumTitle: "Miqat",
            MPMediaItemPropertyPlaybackDuration: cutoff,
            MPNowPlayingInfoPropertyElapsedPlaybackTime: min(player.currentTime, cutoff),
            MPNowPlayingInfoPropertyPlaybackRate: paused ? 0 : 1,
        ]
        if let artwork { info[MPMediaItemPropertyArtwork] = artwork }
        PhoneRemotePlayback.shared.publish(info, for: .adhan)
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
