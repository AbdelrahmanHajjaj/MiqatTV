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

let apps: Set = ["tv.a", "tv.b", "tv.c"]
var redirects = TVRedirects("tv.b=tv.c,tv.a=tv.b", apps: apps, maximum: 2048)!
precondition(redirects.value == "tv.a=tv.b,tv.b=tv.c")
redirects.add(from: "tv.c", to: "tv.a")
redirects.add(from: "tv.a", to: "tv.c")
redirects.add(from: "tv.c", to: "unknown")
precondition(redirects.value == "tv.a=tv.b,tv.b=tv.c")
redirects.remove("tv.a")
redirects.add(from: "tv.c", to: "tv.a")
precondition(redirects.value == "tv.b=tv.c,tv.c=tv.a")
redirects.remove("tv.b")
redirects.remove("tv.c")
precondition(redirects.value.isEmpty)

for invalid in ["tv.a=tv.a", "tv.a=tv.b,tv.b=tv.a", "tv.a=tv.b,tv.a=tv.c",
                "tv.a=unknown", "tv.a=tv.b,", "tv.a=tv.b=tv.c"] {
    precondition(TVRedirects(invalid, apps: apps, maximum: 2048) == nil)
}
var bounded = TVRedirects("", apps: apps, maximum: 8)!
bounded.add(from: "tv.a", to: "tv.b")
precondition(bounded.value.isEmpty)
print("TV redirects allow valid chains and reject cycles, unknown apps, duplicate sources and oversized payloads.")
