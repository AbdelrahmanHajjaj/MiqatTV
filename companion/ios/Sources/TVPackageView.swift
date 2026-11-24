import SwiftUI
import UniformTypeIdentifiers

struct TVPackageView: View {
    private struct Platform: Decodable {
        let id: String
        let en: String
        let ar: String
    }

    private static let platforms: [Platform] = PhoneData.load("tv-platforms")
    let preferences: PhonePreferences
    @State private var platform = TVPackagePlatform.android
    @State private var channel = TVPackageChannel.production
    @State private var task: Task<Void, Never>?
    @State private var progress: TVPackageDownload.Progress?
    @State private var status: String?
    @State private var package: TVPackageDownload.Package?
    @State private var importing = false

    var body: some View {
        Form {
            Picker(preferences.text("tv_platform"), selection: $platform) {
                ForEach(TVPackagePlatform.allCases, id: \.self) { value in
                    if let name = Self.platforms.first(where: { $0.id == value.rawValue }) {
                        Text(preferences.arabic ? name.ar : name.en).tag(value)
                    }
                }
            }
            .disabled(task != nil)
            Picker(preferences.text("tv_public"), selection: $channel) {
                ForEach(TVPackageChannel.allCases, id: \.self) { value in
                    Text(preferences.text("tv_channel_" + value.rawValue)).tag(value)
                }
            }
            .disabled(task != nil)
            if let progress {
                switch progress {
                case .lookup: ProgressView(preferences.text("tv_public_lookup"))
                case .receiving(let fraction): ProgressView(preferences.text("tv_public_downloading"), value: fraction)
                case .verifying: ProgressView(preferences.text("tv_public_verifying"))
                }
            }
            if let status { Text(preferences.text(status)).foregroundStyle(.secondary) }
            if let package {
                LabeledContent(package.url.lastPathComponent, value: package.tag)
                ShareLink(item: package.url)
            }
            if let task {
                Button(preferences.text("cancel")) { task.cancel() }
            } else {
                Button(preferences.text("tv_public_download")) { start() }
                Button(preferences.text("tv_local")) { importing = true }
            }
        }
        .navigationTitle(preferences.text("tv_public"))
        .onChange(of: platform) { _, _ in clearPackage() }
        .onChange(of: channel) { _, _ in clearPackage() }
        .onDisappear { task?.cancel(); clearPackage() }
        .fileImporter(isPresented: $importing, allowedContentTypes: [.data]) { result in
            switch result {
            case .success(let url): importLocal(url)
            case .failure: status = "tv_local_invalid"
            }
        }
    }

    private func clearPackage() {
        package?.discard()
        package = nil
        status = nil
    }

    private func start() {
        guard task == nil else { return }
        clearPackage()
        progress = .lookup
        let selection = (platform, channel)
        task = Task {
            defer {
                task = nil
                progress = nil
            }
            do {
                let ready = try await TVPackageDownload.download(platform: selection.0, channel: selection.1) { update in
                    await MainActor.run { if !Task.isCancelled { progress = update } }
                }
                if Task.isCancelled { ready.discard(); return }
                package = ready
            } catch TVReleaseError.unavailable {
                status = "tv_public_unavailable"
            } catch {
                status = Task.isCancelled ? "tv_public_cancelled" : "tv_public_failed"
            }
        }
    }

    private func importLocal(_ url: URL) {
        guard task == nil else { return }
        clearPackage()
        progress = .verifying
        let selection = platform
        task = Task {
            defer { task = nil; progress = nil }
            do {
                let ready = try await TVPackageDownload.importLocal(url, platform: selection)
                if Task.isCancelled { ready.discard(); return }
                package = ready
            } catch {
                status = Task.isCancelled ? "tv_public_cancelled" : "tv_local_invalid"
            }
        }
    }
}
