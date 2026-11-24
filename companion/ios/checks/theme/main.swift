import Foundation

func changes(_ draft: TVThemeDraft) -> [String: String]? {
    draft.changes(keys: ["gold-400"], backgrounds: ["theme", "solid"], surfaces: ["glass"], maximum: 8192)
}

let existing = """
{"version":1,"background":"solid","lighting":20,"surface":"glass","colors":{"gold-400":"#123ABC"}}
"""
var draft = TVThemeDraft.read(existing)!
draft.colors?["gold-400"] = "#DEF123"
let patch = changes(draft)!
precondition(patch.count == 2 && patch["appearance.theme"] == "custom")
let saved = TVThemeDraft.read(patch["appearance.custom-theme"]!)!
precondition(saved.colors == ["gold-400": "#DEF123"] && saved.background == "solid"
    && saved.lighting == 20 && saved.surface == "glass")

precondition(TVThemeDraft.read("")?.version == 1)
precondition(TVThemeDraft.read("{\"version\":1,\"unknown\":true}") == nil)
precondition(TVThemeDraft.read("{\"version\":1,\"colors\":null}") == nil)
draft.colors?["gold-400"] = "#123"
precondition(changes(draft) == nil)
draft.colors = ["unexpected": "#123ABC"]
precondition(changes(draft) == nil)
draft = saved
draft.background = "theme"
precondition(changes(draft) == nil)
draft = saved
draft.lighting = 101
precondition(changes(draft) == nil)
print("TV theme edits preserve supported fields and select the custom theme together.")
