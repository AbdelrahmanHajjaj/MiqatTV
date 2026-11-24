import Foundation
import MediaPlayer
import UIKit

@MainActor
final class PhoneRemotePlayback {
    enum Source: Equatable { case adhan, radio }
    enum Action: Sendable { case play, pause, toggle, stop }

    static let shared = PhoneRemotePlayback()
    private var owner: Source?
    private var registration: UUID?
    private var targets: [(MPRemoteCommand, Any)] = []

    func begin(_ source: Source, handle: @escaping @MainActor (Action) -> Void) {
        if let owner { end(owner) }
        owner = source
        let token = UUID()
        registration = token
        let commands = MPRemoteCommandCenter.shared()
        let actions: [(MPRemoteCommand, Action)] = [
            (commands.playCommand, .play), (commands.pauseCommand, .pause),
            (commands.togglePlayPauseCommand, .toggle), (commands.stopCommand, .stop),
        ]
        targets = actions.map { command, action in
            let target = command.addTarget { [weak self] _ in
                Task { @MainActor in
                    guard self?.registration == token else { return }
                    handle(action)
                }
                return .success
            }
            return (command, target)
        }
    }

    func publish(_ information: [String: Any], for source: Source) {
        guard owner == source else { return }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = information
    }

    func end(_ source: Source) {
        guard owner == source else { return }
        owner = nil
        registration = nil
        for (command, target) in targets { command.removeTarget(target) }
        targets.removeAll()
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    static func artwork(_ preferences: PhonePreferences) -> MPMediaItemArtwork? {
        guard preferences.showArtwork, let url = Bundle.main.url(forResource: preferences.background,
            withExtension: preferences.background == "miqat_signature" ? "png" : "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return MPMediaItemArtwork(boundsSize: image.size) { _ in image }
    }
}
