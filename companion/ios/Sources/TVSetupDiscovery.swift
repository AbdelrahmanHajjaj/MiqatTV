import Foundation
import SwiftUI

final class TVSetupDiscovery: NSObject, ObservableObject, NetServiceBrowserDelegate, NetServiceDelegate {
    enum Service: String, CaseIterable, Hashable {
        case classic = "_adb._tcp"
        case wireless = "_adb-tls-connect._tcp"
        case pairing = "_adb-tls-pairing._tcp"

        var title: String {
            switch self {
            case .classic: "tv_find_classic"
            case .wireless: "tv_find_wireless"
            case .pairing: "tv_find_pairing"
            }
        }
    }

    struct Key: Hashable {
        let name: String
        let type: Service
    }

    struct Port: Identifiable {
        let id: Key
        let address: String
        let number: Int
    }

    enum Search: Equatable {
        case idle, browsing, finished, unavailable
    }

    @Published private(set) var search = Search.idle
    @Published private(set) var ports: [Port] = []
    private var browsers: [NetServiceBrowser] = []
    private var services: [Key: NetService] = [:]
    private var deadline: Timer?

    func start() {
        guard search != .browsing else { return }
        ports = []
        search = .browsing
        deadline = Timer.scheduledTimer(withTimeInterval: 15, repeats: false) { [weak self] _ in
            self?.stop()
        }
        browsers = Service.allCases.map { _ in
            let browser = NetServiceBrowser()
            browser.delegate = self
            return browser
        }
        for (type, browser) in zip(Service.allCases, browsers) {
            browser.searchForServices(ofType: type.rawValue + ".", inDomain: "local.")
        }
    }

    func stop() {
        finish(.finished)
    }

    func reset() {
        finish(.idle)
        ports = []
    }

    private func finish(_ state: Search) {
        deadline?.invalidate()
        deadline = nil
        for browser in browsers {
            browser.delegate = nil
            browser.stop()
        }
        for service in services.values {
            service.delegate = nil
            service.stop()
        }
        browsers.removeAll()
        services.removeAll()
        search = state
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didFind service: NetService, moreComing: Bool) {
        guard browsers.contains(where: { $0 === browser }), let key = key(for: service),
              services[key] == nil, services.count < 64 else { return }
        services[key] = service
        service.delegate = self
        service.resolve(withTimeout: 4)
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didRemove service: NetService, moreComing: Bool) {
        guard browsers.contains(where: { $0 === browser }), let key = key(for: service) else { return }
        if let found = services.removeValue(forKey: key) {
            found.delegate = nil
            found.stop()
        }
        ports.removeAll { $0.id == key }
    }

    func netServiceBrowser(_ browser: NetServiceBrowser, didNotSearch errorDict: [String: NSNumber]) {
        guard let position = browsers.firstIndex(where: { $0 === browser }) else { return }
        browsers.remove(at: position)
        browser.delegate = nil
        browser.stop()
        if browsers.isEmpty { finish(.unavailable) }
    }

    func netServiceDidResolveAddress(_ service: NetService) {
        guard let key = key(for: service), services[key] === service,
              let hostname = service.hostName,
              (1...65535).contains(service.port) else { return }
        let address = hostname.hasSuffix(".") ? String(hostname.dropLast()) : hostname
        guard address.lowercased().hasSuffix(".local") else { return }
        let port = Port(id: key, address: address, number: service.port)
        ports.removeAll { $0.id == key }
        ports.append(port)
        ports.sort { ($0.address, $0.number, $0.id.name) < ($1.address, $1.number, $1.id.name) }
    }

    private func key(for service: NetService) -> Key? {
        let name = service.type.hasSuffix(".") ? String(service.type.dropLast()) : service.type
        guard let type = Service(rawValue: name) else { return nil }
        return Key(name: service.name, type: type)
    }
}

struct TVSetupDiscoveryView: View {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var discovery = TVSetupDiscovery()
    let preferences: PhonePreferences

    var body: some View {
        List {
            Section {
                if discovery.search == .browsing {
                    ProgressView(preferences.text("tv_find_searching"))
                    Button(preferences.text("cancel")) { discovery.stop() }
                } else {
                    Button(preferences.text("tv_find_search")) { discovery.start() }
                }
                if discovery.search == .finished && discovery.ports.isEmpty {
                    Text(preferences.text("tv_find_empty")).foregroundStyle(.secondary)
                }
                if discovery.search == .unavailable {
                    Text(preferences.text("tv_find_unavailable")).foregroundStyle(.secondary)
                }
            }
            ForEach(discovery.ports) { port in
                VStack(alignment: .leading, spacing: 4) {
                    Text(port.id.name)
                    Text(preferences.text(port.id.type.title))
                        .foregroundStyle(.secondary)
                    Text(verbatim: "\(port.address):\(port.number)")
                        .font(.footnote.monospaced())
                        .environment(\.layoutDirection, .leftToRight)
                        .textSelection(.enabled)
                }
            }
        }
        .navigationTitle(preferences.text("tv_find_search"))
        .onDisappear { discovery.reset() }
        .onChange(of: scenePhase) { _, phase in
            if phase == .background { discovery.reset() }
        }
    }
}
