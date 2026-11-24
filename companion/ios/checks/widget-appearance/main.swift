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
print("Widget palette selection, fallback, previous snapshot compatibility and opacity bounds passed.")
