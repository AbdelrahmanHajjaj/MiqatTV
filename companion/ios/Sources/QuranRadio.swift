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
    private var endWatch: AnyCancellable?
    private var timeout: Task<Void, Never>?
    private var station: RadioStation?
    private var preferences: PhonePreferences?
    private var retries = 0
    private static let maxRetries = 4

    var wantsPlay: Bool { player != nil || status == "phone_radio_retrying" }
    var hasSession: Bool { status != nil }

    func play(station: RadioStation, preferences: PhonePreferences) {
        self.station = station
        self.preferences = preferences
        retries = 0
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
            PhoneRemotePlayback.shared.end(.radio)
        } else {
            metadata()
        }
    }

    func stop() {
        guard hasSession else { return }
        closeStream()
        PhoneRemotePlayback.shared.end(.radio)
        station = nil
        status = nil
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
        let center = NotificationCenter.default
        endWatch = center.publisher(for: AVPlayerItem.didPlayToEndTimeNotification, object: item)
            .merge(with: center.publisher(for: AVPlayerItem.failedToPlayToEndTimeNotification, object: item))
            .sink { [weak self, weak item] _ in
                Task { @MainActor in
                    guard let self, let item, self.player?.currentItem === item else { return }
                    self.retry()
                }
            }
        statusWatch = source.observe(\.timeControlStatus, options: [.new]) { [weak self] source, _ in
            Task { @MainActor in
                guard let self, self.player === source else { return }
                if source.timeControlStatus == .playing {
                    self.resetRetriesAfterStablePlayback()
                    self.status = "phone_radio_live"
                    self.metadata()
                } else if source.timeControlStatus == .waitingToPlayAtSpecifiedRate {
                    self.status = "phone_radio_buffering"
                    self.armTimeout()
                }
            }
        }
        PhoneRemotePlayback.shared.begin(.radio) { [weak self] action in
            guard let self else { return }
            switch action {
            case .play: self.resume()
            case .pause: self.pause()
            case .toggle: if self.wantsPlay { self.pause() } else { self.resume() }
            case .stop: self.stop()
            }
        }
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

    private func resetRetriesAfterStablePlayback() {
        timeout?.cancel()
        guard retries > 0 else { return }
        timeout = Task { [weak self] in
            try? await Task.sleep(for: .seconds(30))
            if !Task.isCancelled { self?.retries = 0 }
        }
    }

    private func retry() {
        closeStream()
        if retries < Self.maxRetries {
            retries += 1
            status = "phone_radio_retrying"
            metadata()
            let delay = Duration.milliseconds(1500 * retries)
            timeout = Task { [weak self] in
                try? await Task.sleep(for: delay)
                if !Task.isCancelled { self?.connect("phone_radio_connecting") }
            }
        } else {
            status = "phone_radio_failed"
            metadata()
        }
    }

    private func closeStream() {
        timeout?.cancel()
        statusWatch = nil
        itemWatch = nil
        endWatch = nil
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
        if let artwork = PhoneRemotePlayback.artwork(preferences) { info[MPMediaItemPropertyArtwork] = artwork }
        PhoneRemotePlayback.shared.publish(info, for: .radio)
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
