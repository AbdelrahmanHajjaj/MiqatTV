import AVFoundation
import Combine
import MediaPlayer
import SwiftUI

struct RadioStation: Decodable, Identifiable {
    let id: String
    let ar: String
    let en: String
    let whereAr: String
    let whereEn: String
    let url: URL

    static let catalog: [Self] = PhoneData.load("stations")
    func title(_ preferences: PhonePreferences) -> String { preferences.arabic ? ar : en }
    func place(_ preferences: PhonePreferences) -> String { preferences.arabic ? whereAr : whereEn }
}

@MainActor
final class QuranRadio: ObservableObject {
    @Published private(set) var status: String?
    private var player: AVPlayer?
    private var itemWatch: NSKeyValueObservation?
    private var statusWatch: NSKeyValueObservation?
    private var timeout: Task<Void, Never>?
    private var station: RadioStation?
    private var preferences: PhonePreferences?
    private var retries = 0
    private var commandTargets: [(MPRemoteCommand, Any)] = []

    var wantsPlay: Bool { player != nil }
    var hasSession: Bool { status != nil }

    func play(station: RadioStation, preferences: PhonePreferences) {
        self.station = station
        self.preferences = preferences
        retries = 2
        connect("phone_radio_connecting")
    }

    func refreshMetadata(_ preferences: PhonePreferences) {
        self.preferences = preferences
        if station != nil { metadata() }
    }

    func pause(forAdhan: Bool = false) {
        guard hasSession else { return }
        closeStream()
        status = forAdhan ? "phone_radio_adhan" : "phone_radio_paused"
        if forAdhan {
            removeCommands()
            MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
        } else {
            metadata()
        }
    }

    func stop() {
        guard hasSession else { return }
        closeStream()
        removeCommands()
        station = nil
        status = nil
        MPNowPlayingInfoCenter.default().nowPlayingInfo = nil
    }

    private func connect(_ message: String) {
        closeStream()
        guard let station, station.url.scheme == "https" else { stop(); return }
        status = message
        let session = AVAudioSession.sharedInstance()
        do {
            try session.setCategory(.playback)
            try session.setActive(true)
        } catch {
            status = "phone_radio_failed"
            return
        }
        let item = AVPlayerItem(url: station.url)
        let source = AVPlayer(playerItem: item)
        player = source
        itemWatch = item.observe(\.status, options: [.new]) { [weak self] item, _ in
            guard item.status == .failed else { return }
            Task { @MainActor in
                if self?.player?.currentItem === item { self?.retry() }
            }
        }
        statusWatch = source.observe(\.timeControlStatus, options: [.new]) { [weak self] source, _ in
            Task { @MainActor in
                guard let self, self.player === source else { return }
                if source.timeControlStatus == .playing {
                    self.timeout?.cancel()
                    self.status = "phone_radio_live"
                    self.metadata()
                } else if source.timeControlStatus == .waitingToPlayAtSpecifiedRate {
                    self.status = "phone_radio_buffering"
                    self.armTimeout()
                }
            }
        }
        useCommands()
        metadata()
        source.play()
        armTimeout()
    }

    private func armTimeout() {
        timeout?.cancel()
        timeout = Task { [weak self] in
            try? await Task.sleep(for: .seconds(16))
            if !Task.isCancelled { self?.retry() }
        }
    }

    private func retry() {
        if retries > 0 {
            retries -= 1
            connect("phone_radio_retrying")
        } else {
            closeStream()
            status = "phone_radio_failed"
            metadata()
        }
    }

    private func closeStream() {
        timeout?.cancel()
        statusWatch = nil
        itemWatch = nil
        guard let player else { return }
        self.player = nil
        player.pause()
        player.replaceCurrentItem(with: nil)
        try? AVAudioSession.sharedInstance().setActive(false, options: .notifyOthersOnDeactivation)
    }

    private func metadata() {
        guard let station, let preferences, status != "phone_radio_adhan" else { return }
        var info: [String: Any] = [
            MPMediaItemPropertyTitle: station.title(preferences),
            MPMediaItemPropertyArtist: station.place(preferences),
            MPNowPlayingInfoPropertyIsLiveStream: true,
            MPNowPlayingInfoPropertyPlaybackRate: status == "phone_radio_live" ? 1 : 0,
        ]
        if preferences.showArtwork, let url = Bundle.main.url(forResource: preferences.background,
            withExtension: preferences.background == "miqat_signature" ? "png" : "jpg"),
           let image = UIImage(contentsOfFile: url.path) {
            info[MPMediaItemPropertyArtwork] = MPMediaItemArtwork(boundsSize: image.size) { _ in image }
        }
        MPNowPlayingInfoCenter.default().nowPlayingInfo = info
    }

    private func useCommands() {
        guard commandTargets.isEmpty else { return }
        let commands = MPRemoteCommandCenter.shared()
        commandTargets = [
            (commands.playCommand, commands.playCommand.addTarget { [weak self] _ in
                Task { @MainActor in self?.resume() }
                return .success
            }),
            (commands.pauseCommand, commands.pauseCommand.addTarget { [weak self] _ in
                Task { @MainActor in self?.pause() }
                return .success
            }),
            (commands.togglePlayPauseCommand, commands.togglePlayPauseCommand.addTarget { [weak self] _ in
                Task { @MainActor in
                    guard let self else { return }
                    if self.wantsPlay { self.pause() } else { self.resume() }
                }
                return .success
            }),
            (commands.stopCommand, commands.stopCommand.addTarget { [weak self] _ in
                Task { @MainActor in self?.stop() }
                return .success
            }),
        ]
    }

    private func removeCommands() {
        for (command, target) in commandTargets { command.removeTarget(target) }
        commandTargets = []
    }

    private func resume() {
        guard !wantsPlay, let station, let preferences else { return }
        play(station: station, preferences: preferences)
    }
}

struct RadioSettings: View {
    @EnvironmentObject private var radio: QuranRadio
    @EnvironmentObject private var audio: PhoneAdhan
    @Binding var preferences: PhonePreferences
    @State private var query = ""

    var body: some View {
        let station = RadioStation.catalog.first { $0.id == preferences.radioStation } ?? RadioStation.catalog[0]
        Form {
            Section {
                LabeledContent(station.title(preferences), value: station.place(preferences))
                if let status = radio.status { Text(preferences.text(status)).foregroundStyle(.secondary) }
                Button(preferences.text(radio.wantsPlay ? "phone_pause" : "phone_resume")) {
                    if radio.wantsPlay { radio.pause() }
                    else {
                        audio.stop(previewOnly: true)
                        radio.play(station: station, preferences: preferences)
                    }
                }
                .disabled(audio.prayer != nil)
                if radio.hasSession { Button(preferences.text("phone_radio_stop")) { radio.stop() } }
            }
            Section(preferences.text("phone_radio_station")) {
                ForEach(RadioStation.catalog.filter(matches)) { option in
                    Button {
                        let playing = radio.wantsPlay
                        preferences.radioStation = option.id
                        if playing && audio.prayer == nil { radio.play(station: option, preferences: preferences) }
                        else if radio.hasSession && option.id != station.id { radio.stop() }
                    } label: {
                        HStack {
                            VStack(alignment: .leading) {
                                Text(option.title(preferences))
                                Text(option.place(preferences)).font(.caption).foregroundStyle(.secondary)
                            }
                            Spacer()
                            if option.id == station.id { Image(systemName: "checkmark") }
                        }
                    }
                }
            }
        }
        .navigationTitle(preferences.text("phone_radio"))
        .searchable(text: $query, prompt: preferences.text("phone_search"))
    }

    private func matches(_ station: RadioStation) -> Bool {
        query.isEmpty || [station.en, station.ar, station.whereEn, station.whereAr].contains { $0.localizedStandardContains(query) }
    }
}
