import Foundation

let oldFile = """
{"days":[],"names":["Fajr","Sunrise","Dhuhr","Asr","Maghrib","Isha"],"arabic":false,
"eastern":false,"clock":"12","timezone":"UTC","colors":{"lapis-900":"#112233","gold-400":"#778899"}}
"""
var snapshot = try JSONDecoder().decode(WidgetSnapshot.self, from: Data(oldFile.utf8))
precondition(snapshot.palettes == nil)
precondition(snapshot.colors(for: "app") == snapshot.colors)
snapshot.palettes = [.init(id: "sand", name: "Sand", colors: ["lapis-900": "#FFEEDD"])]
let restored = try JSONDecoder().decode(WidgetSnapshot.self, from: JSONEncoder().encode(snapshot))
precondition(restored.colors(for: "sand") == ["lapis-900": "#FFEEDD", "gold-400": "#778899"])
precondition(restored.colors(for: "app") == snapshot.colors)
precondition(restored.colors(for: "retired") == snapshot.colors)
precondition(WidgetSnapshot.opacity(percent: -1) == 0)
precondition(WidgetSnapshot.opacity(percent: 41) == 0.41)
precondition(WidgetSnapshot.opacity(percent: 200) == 1)
precondition(restored.sceneID(for: "app") == nil)
snapshot.scenes = [.init(id: "tea_hills", name: "Tea hills"), .init(id: "miqat_quiet", name: "Miqat Quiet")]
snapshot.phoneScene = "tea_hills"
let withScenes = try JSONDecoder().decode(WidgetSnapshot.self, from: JSONEncoder().encode(snapshot))
precondition(withScenes.sceneID(for: nil) == nil)
precondition(withScenes.sceneID(for: "plain") == nil)
precondition(withScenes.sceneID(for: "app") == "tea_hills")
precondition(withScenes.sceneID(for: "miqat_quiet") == "miqat_quiet")
precondition(withScenes.sceneID(for: "retired_scene") == nil)
snapshot.phoneScene = "plain"
precondition(snapshot.sceneID(for: "app") == nil)
print("Widget palette and scene selection, old snapshot compatibility and opacity bounds passed.")
