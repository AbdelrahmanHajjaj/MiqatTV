import SwiftUI
import MiqatKit
import UniformTypeIdentifiers

struct AdhanSettings: View {
    @Binding var preferences: PhonePreferences

    var body: some View {
        Form {
            AdhanSelectionRows(preferences: preferences, selection: $preferences.adhan.selection)
            NavigationLink(preferences.prayerNames()[0]) {
                AdhanVoicePicker(preferences: preferences, voice: $preferences.adhan.fajrVoice,
                                 fallbackVoice: PhoneData.defaultFajrVoice)
            }
            Toggle(preferences.text("phone_full_screen"), isOn: $preferences.adhan.showScreen)
        }
        .navigationTitle(preferences.text("phone_adhan"))
        .onDisappear { audio.stop(previewOnly: true) }
    }
    @EnvironmentObject private var audio: PhoneAdhan
}

struct AdhanSelectionRows: View {
    @EnvironmentObject private var library: AdhanLibrary
    @EnvironmentObject private var audio: PhoneAdhan
    let preferences: PhonePreferences
    @Binding var selection: AdhanSelection
    var inherit: String?
    var defaultLevelAndLength: AdhanSelection?
    @State private var failed = false

    var body: some View {
        NavigationLink {
            AdhanVoicePicker(preferences: preferences, voice: $selection.voice, inheritedVoice: inherit)
        } label: {
            LabeledContent(preferences.text("phone_voice"), value: library.name(selection.voice, preferences: preferences))
        }
        if defaultLevelAndLength == nil {
            Stepper(value: $selection.level, in: 0...100, step: 5) {
                LabeledContent(preferences.text("phone_volume"), value: preferences.displayDigits("\(selection.level)%"))
            }
            Stepper(value: $selection.length, in: 5...100, step: 5) {
                LabeledContent(preferences.text("phone_length"), value: preferences.displayDigits("\(selection.length)%"))
            }
        }
        Button(preferences.text("phone_audio_preview_length")) {
            let id = selection.voice == "inherit" ? inherit ?? "" : selection.voice
            guard let url = library.url(id) else { failed = true; return }
            failed = !audio.play(url: url, name: library.name(id, preferences: preferences),
                                 selection: defaultLevelAndLength ?? selection)
        }
        .disabled(audio.prayer != nil || (defaultLevelAndLength ?? selection).level == 0)
        if audio.name != nil && audio.prayer == nil {
            Button(preferences.text("phone_stop")) { audio.stop(previewOnly: true) }
        }
        if failed { Text(preferences.text("phone_audio_failed")).foregroundStyle(.secondary) }
    }
}

struct AdhanVoicePicker: View {
    private enum Naming {
        case importing(URL)
        case renaming(String)
    }

    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var library: AdhanLibrary
    @EnvironmentObject private var audio: PhoneAdhan
    let preferences: PhonePreferences
    @Binding var voice: String
    var inheritedVoice: String?
    var fallbackVoice = AdhanPreferences.defaultVoice
    @State private var draft = ""
    @State private var query = ""
    @State private var filePicker = false
    @State private var importTask: Task<Void, Never>?
    @State private var naming: Naming?
    @State private var editName = ""
    @State private var removing: AdhanLibrary.Recording?
    @State private var failed = false

    var body: some View {
        List {
            if inheritedVoice != nil { voiceRow("inherit") }
            ForEach(PhoneData.voices.filter { matches($0.id) }) { voice in voiceRow(voice.id) }
            Section(preferences.text("phone_custom")) {
                ForEach(library.records.filter { matches($0.id) }) { record in
                    voiceRow(record.id)
                        .contextMenu {
                            Button(preferences.text("phone_audio_import_name")) {
                                editName = record.name
                                naming = .renaming(record.id)
                            }
                            Button(preferences.text("remove"), role: .destructive) { removing = record }
                        }
                }
                Button(preferences.text("phone_import_audio")) { filePicker = true }.disabled(importTask != nil)
                if importTask != nil { ProgressView() }
            }
            if audio.name != nil && audio.prayer == nil {
                Button(preferences.text("phone_stop")) { audio.stop(previewOnly: true) }
            }
            if failed { Text(preferences.text("phone_audio_failed")).foregroundStyle(.secondary) }
        }
        .navigationTitle(preferences.text("phone_voice"))
        .searchable(text: $query, prompt: preferences.text("phone_search"))
        .toolbar {
            ToolbarItem(placement: .confirmationAction) {
                Button(preferences.text("done")) { voice = draft; dismiss() }.disabled(draft.isEmpty || importTask != nil)
            }
        }
        .onAppear { draft = voice }
        .onDisappear { importTask?.cancel(); audio.stop(previewOnly: true) }
        .fileImporter(isPresented: $filePicker, allowedContentTypes: [.audio]) { result in
            guard case .success(let url) = result else { return }
            failed = false
            editName = AdhanLibrary.suggestedName(for: url, fallback: preferences.text("phone_audio_import_default"))
            naming = .importing(url)
        }
        .alert(preferences.text("phone_audio_import_name"), isPresented: Binding(
            get: { naming != nil }, set: { if !$0 { naming = nil } }
        ), presenting: naming) { target in
            TextField(preferences.text("phone_audio_name"), text: $editName)
            Button(preferences.text("done")) { saveName(for: target) }
                .disabled(RecordingName.shared.validated(value: editName) == nil)
            Button(preferences.text("cancel"), role: .cancel) {}
        }
        .alert(preferences.text("remove"), isPresented: Binding(
            get: { removing != nil }, set: { if !$0 { removing = nil } }
        )) {
            Button(preferences.text("remove"), role: .destructive) {
                guard let removing else { return }
                audio.stop(previewOnly: true)
                do {
                    try library.remove(removing.id)
                    if draft == removing.id { draft = inheritedVoice == nil ? fallbackVoice : "inherit" }
                } catch { failed = true }
            }
            Button(preferences.text("cancel"), role: .cancel) {}
        }
    }

    private func saveName(for target: Naming) {
        let name = editName
        failed = false
        switch target {
        case .renaming(let id):
            do { try library.rename(id, name: name) }
            catch { failed = true }
        case .importing(let url):
            importTask = Task {
                do { draft = try await library.importFile(url, name: name) }
                catch is CancellationError {}
                catch { failed = true }
                importTask = nil
            }
        }
    }

    private func matches(_ id: String) -> Bool {
        query.isEmpty || library.name(id, preferences: preferences).localizedStandardContains(query)
    }

    private func voiceRow(_ id: String) -> some View {
        HStack {
            Button {
                draft = id
            } label: {
                HStack {
                    Text(library.name(id, preferences: preferences))
                    Spacer()
                    if draft == id { Image(systemName: "checkmark") }
                }
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            Button {
                let resolved = id == "inherit" ? inheritedVoice ?? "" : id
                guard let url = library.url(resolved) else { failed = true; return }
                failed = !audio.play(url: url, name: library.name(resolved, preferences: preferences),
                                     selection: AdhanSelection(voice: resolved))
            } label: {
                Image(systemName: "play.circle")
            }
            .buttonStyle(.borderless)
            .accessibilityLabel(preferences.text("phone_listen"))
            .disabled(audio.prayer != nil)
        }
    }
}

struct AdhanPlayback: View {
    @Environment(\.dismiss) private var dismiss
    @EnvironmentObject private var audio: PhoneAdhan
    let preferences: PhonePreferences
    @State private var confirm = false

    var body: some View {
        VStack(spacing: 18) {
            Text(PhoneData.adhanCall).font(.largeTitle)
            if let prayer = audio.prayer { Text(preferences.prayerNames()[prayer]).font(.title) }
            if let name = audio.name { Text(name).foregroundStyle(.secondary) }
            TimelineView(.periodic(from: .now, by: 0.25)) { _ in ProgressView(value: audio.progress) }
            Button(preferences.text(audio.paused ? "phone_resume" : "phone_pause")) { audio.togglePause() }
                .buttonStyle(.bordered)
            Button(preferences.text("back")) { confirm = true }
            Button(role: .destructive, action: stopAndClose) {
                Label(preferences.text("phone_swipe_stop"), systemImage: "chevron.up")
                    .frame(minHeight: 44)
            }
            .accessibilityLabel(preferences.text("phone_stop_close"))
            .simultaneousGesture(DragGesture(minimumDistance: 24).onEnded { drag in
                let travel = drag.translation
                if travel.height <= -44 && -travel.height > abs(travel.width) { stopAndClose() }
            })
        }
        .padding(28)
        .interactiveDismissDisabled()
        .onChange(of: audio.name) { _, name in if name == nil { dismiss() } }
        .alert(preferences.text("phone_stop_close"), isPresented: $confirm) {
            Button(preferences.text("phone_stop"), role: .destructive, action: stopAndClose)
            Button(preferences.text("phone_keep_playing"), role: .cancel) {}
        }
    }

    private func stopAndClose() {
        audio.stop()
        dismiss()
    }
}
