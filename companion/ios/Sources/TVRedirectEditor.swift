import SwiftUI

struct TVRedirectEditor: View {
    @Environment(\.dismiss) private var dismiss
    let preferences: PhonePreferences
    let title: String
    let apps: [TVControl.Option]
    let savedValue: String?
    let editable: Bool
    let save: (String) -> Void
    @State var draft: TVRedirects
    @State private var source = ""
    @State private var target = ""

    private var candidate: TVRedirects {
        var next = draft
        next.add(from: source, to: target)
        return next
    }

    var body: some View {
        NavigationStack {
            Form {
                ForEach(draft.replacements.keys.sorted(), id: \.self) { source in
                    HStack {
                        LabeledContent(label(for: source), value: label(for: draft.replacements[source] ?? ""))
                        Button(role: .destructive) { draft.remove(source) } label: {
                            Image(systemName: "minus.circle").accessibilityLabel(preferences.text("remove"))
                        }
                        .buttonStyle(.borderless)
                    }
                }
                appPicker("source_app", selection: $source, options: apps.filter { draft.replacements[$0.value] == nil })
                appPicker("target_app", selection: $target, options: apps.filter { $0.value != source })
                Button(preferences.text("add")) {
                    draft = candidate
                    source = ""
                    target = ""
                }
                .disabled(candidate.value == draft.value)
                if !source.isEmpty && !target.isEmpty && candidate.value == draft.value {
                    Text(preferences.text("invalid")).foregroundStyle(.secondary)
                }
            }
            .navigationTitle(title)
            .toolbar {
                ToolbarItem(placement: .cancellationAction) {
                    Button(preferences.text("cancel")) { dismiss() }
                }
                ToolbarItem(placement: .confirmationAction) {
                    Button(preferences.text("done")) {
                        save(draft.value)
                        dismiss()
                    }
                    .disabled(!editable || draft.value == savedValue)
                }
            }
        }
    }

    private func appPicker(_ title: String, selection: Binding<String>, options: [TVControl.Option]) -> some View {
        Picker(preferences.text(title), selection: selection) {
            Text("—").tag("")
            ForEach(options, id: \.value) { option in
                Text(preferences.arabic ? option.ar : option.en).tag(option.value)
            }
        }
    }

    private func label(for id: String) -> String {
        guard let option = apps.first(where: { $0.value == id }) else { return id }
        return preferences.arabic ? option.ar : option.en
    }
}
