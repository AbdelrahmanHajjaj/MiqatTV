import Darwin
import Foundation
import MiqatKit

struct AndroidADBPairing {
    enum Failure: Error { case address, code, timeout, connection, identity, malformed, authentication }

    private static let queue = DispatchQueue(label: "tv.miqat.ios.adb-pairing")

    static func pair(host: String, port: Int, code: String) async throws -> String {
        guard TVEndpoint.isPrivateIPv4(host), let port = UInt16(exactly: port), port > 0 else {
            throw Failure.address
        }
        let wire = AdbPairingWire.shared
        guard wire.password(code: code, exporter: KotlinByteArray(size: wire.exporterSize)) != nil else {
            throw Failure.code
        }
        let socket = AndroidADBPairingSocket()
        return try await withTaskCancellationHandler {
            try Task.checkCancellation()
            let guid: String = try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    do { continuation.resume(returning: try execute(host: host, port: port, code: code, socket: socket)) }
                    catch { continuation.resume(throwing: error) }
                }
            }
            try Task.checkCancellation()
            return guid
        } onCancel: { socket.cancel() }
    }

    private static func execute(host: String, port: UInt16, code: String, socket: AndroidADBPairingSocket) throws -> String {
        let deadline = DispatchTime.now() + .seconds(30)
        let identity = try AndroidADBIdentity.load()
        try socket.connect(host: host, port: port, deadline: deadline)
        try socket.startTLS(identity: identity, deadline: deadline)
        let wire = AdbPairingWire.shared
        var exporter = try socket.exporter()
        defer { exporter.resetBytes(in: exporter.startIndex..<exporter.endIndex) }
        guard let secret = wire.password(code: code, exporter: exporter.adbBytes) else { throw Failure.code }
        var password = Data(adbBytes: secret)
        defer {
            password.resetBytes(in: password.startIndex..<password.endIndex)
            for index in 0..<secret.size { secret.set(index: index, value: 0) }
        }

        var challenge = [UInt8](repeating: 0, count: 32)
        let session = password.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            miqat_adb_spake_begin(0, bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count, &challenge)
        }
        guard let session else { throw Failure.authentication }
        defer { miqat_adb_spake_free(session) }
        try send(Data(challenge), frame: .challenge, on: socket, deadline: deadline)
        let reply = try receive(.challenge, on: socket, deadline: deadline)
        var shared = [UInt8](repeating: 0, count: 64)
        defer { _ = shared.withUnsafeMutableBytes { $0.initializeMemory(as: UInt8.self, repeating: 0) } }
        let verified = reply.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            miqat_adb_spake_finish(session, bytes.bindMemory(to: UInt8.self).baseAddress, &shared)
        }
        guard verified == 1, let offer = wire.hostPeer(publicKey: try identity.offer().adbBytes) else {
            throw Failure.authentication
        }

        var sealed = [UInt8](repeating: 0, count: Int(AdbPairingFrame.peer.payloadSize))
        let plain = Data(adbBytes: offer)
        let success = plain.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            miqat_adb_peer_seal(shared, bytes.bindMemory(to: UInt8.self).baseAddress, &sealed)
        }
        guard success == 1 else { throw Failure.authentication }
        try send(Data(sealed), frame: .peer, on: socket, deadline: deadline)

        let peer = try receive(.peer, on: socket, deadline: deadline)
        var decoded = [UInt8](repeating: 0, count: Int(wire.peerSize))
        let opened = peer.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            miqat_adb_peer_open(shared, bytes.bindMemory(to: UInt8.self).baseAddress, &decoded)
        }
        guard opened == 1, let guid = wire.deviceGuid(plaintext: Data(decoded).adbBytes) else {
            throw Failure.authentication
        }
        return guid
    }

    private static func send(_ data: Data, frame: AdbPairingFrame, on socket: AndroidADBPairingSocket,
                             deadline: DispatchTime) throws {
        guard data.count == Int(frame.payloadSize) else { throw Failure.malformed }
        try socket.write(Data(adbBytes: AdbPairingWire.shared.header(frame: frame)) + data, deadline: deadline)
    }

    private static func receive(_ frame: AdbPairingFrame, on socket: AndroidADBPairingSocket,
                                deadline: DispatchTime) throws -> Data {
        let wire = AdbPairingWire.shared
        let header = try socket.read(Int(wire.headerSize), deadline: deadline)
        guard wire.matchesHeader(bytes: header.adbBytes, frame: frame) else { throw Failure.malformed }
        return try socket.read(Int(frame.payloadSize), deadline: deadline)
    }
}

// All SSL operations run serially. Cancellation only shuts down the socket.
private final class AndroidADBPairingSocket: @unchecked Sendable {
    private typealias Failure = AndroidADBPairing.Failure
    private let lock = NSLock()
    private var fd: Int32 = -1
    private var stopped = false
    private var tls: OpaquePointer?

    deinit {
        if let tls { miqat_adb_tls_free(tls) }
        if fd >= 0 { _ = Darwin.close(fd) }
    }

    func cancel() {
        lock.lock()
        stopped = true
        if fd >= 0 { _ = shutdown(fd, SHUT_RDWR) }
        lock.unlock()
    }

    func connect(host: String, port: UInt16, deadline: DispatchTime) throws {
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = port.bigEndian
        guard inet_pton(AF_INET, host, &address.sin_addr) == 1 else { throw Failure.address }
        let socket = Darwin.socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        guard socket >= 0 else { throw Failure.connection }
        lock.lock()
        if stopped {
            lock.unlock()
            _ = Darwin.close(socket)
            throw CancellationError()
        }
        fd = socket
        lock.unlock()
        var noSignal: Int32 = 1
        guard setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSignal, socklen_t(MemoryLayout<Int32>.size)) == 0,
              fcntl(fd, F_SETFL, O_NONBLOCK) == 0 else { throw Failure.connection }
        let result = withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if result != 0 {
            guard errno == EINPROGRESS else { throw Failure.connection }
            try wait(for: POLLOUT, until: deadline)
            var status: Int32 = 0
            var size = socklen_t(MemoryLayout<Int32>.size)
            guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &status, &size) == 0, status == 0 else { throw Failure.connection }
        }
    }

    func startTLS(identity: AndroidADBIdentity, deadline: DispatchTime) throws {
        var key = try identity.privateRepresentation()
        defer { key.resetBytes(in: key.startIndex..<key.endIndex) }
        let session = key.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            miqat_adb_tls_new(bytes.bindMemory(to: UInt8.self).baseAddress, bytes.count)
        }
        guard let session else { throw Failure.identity }
        tls = session
        guard miqat_adb_tls_set_fd(session, fd) == 1 else { throw Failure.connection }
        _ = try advance(until: deadline) { miqat_adb_tls_connect(session) }
    }

    func exporter() throws -> Data {
        var material = Data(count: 64)
        let result = material.withUnsafeMutableBytes { (bytes: UnsafeMutableRawBufferPointer) in
            miqat_adb_tls_export(tls, bytes.bindMemory(to: UInt8.self).baseAddress)
        }
        guard result == 1 else { throw Failure.connection }
        return material
    }

    func read(_ count: Int, deadline: DispatchTime) throws -> Data {
        var data = Data(count: count)
        try data.withUnsafeMutableBytes { (bytes: UnsafeMutableRawBufferPointer) in
            guard let first = bytes.bindMemory(to: UInt8.self).baseAddress else { throw Failure.malformed }
            var offset = 0
            while offset < count {
                let read = try advance(until: deadline) { miqat_adb_tls_read(tls, first + offset, Int32(count - offset)) }
                offset += Int(read)
            }
        }
        return data
    }

    func write(_ data: Data, deadline: DispatchTime) throws {
        try data.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            guard let first = bytes.bindMemory(to: UInt8.self).baseAddress else { throw Failure.malformed }
            var offset = 0
            while offset < bytes.count {
                let written = try advance(until: deadline) { miqat_adb_tls_write(tls, first + offset, Int32(bytes.count - offset)) }
                offset += Int(written)
            }
        }
    }

    private func advance(until deadline: DispatchTime, _ operation: () -> Int32) throws -> Int32 {
        while true {
            try check(deadline)
            let count = operation()
            if count > 0 { return count }
            if count == MIQAT_TLS_READ { try wait(for: POLLIN, until: deadline) }
            else if count == MIQAT_TLS_WRITE { try wait(for: POLLOUT, until: deadline) }
            else { throw Failure.connection }
        }
    }

    private func wait(for events: Int32, until deadline: DispatchTime) throws {
        while true {
            try check(deadline)
            var state = pollfd(fd: fd, events: Int16(events), revents: 0)
            let result = poll(&state, 1, 250)
            if result > 0 { return }
            if result < 0 && errno != EINTR { throw Failure.connection }
        }
    }

    private func check(_ deadline: DispatchTime) throws {
        lock.lock()
        let cancelled = stopped
        lock.unlock()
        if cancelled { throw CancellationError() }
        guard DispatchTime.now() < deadline else { throw Failure.timeout }
    }
}
