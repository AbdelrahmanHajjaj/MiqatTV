import Darwin
import Foundation

final class LGSSH: LGInstallation.Transport, @unchecked Sendable {
    enum Failure: Error {
        case invalidAddress, connection, approveHostKey(Data), changedHostKey, authentication, command, timeout
    }

    private static let libraryReady = libssh2_init(0) == 0
    private let queue = DispatchQueue(label: "tv.miqat.ios.lg-ssh")
    private let socketLock = NSLock()
    private var socket: Int32 = -1
    private var stopped = false
    private var session: OpaquePointer?

    static func privateAddress(_ text: String) -> String? {
        let host = text.trimmingCharacters(in: .whitespacesAndNewlines)
        return TVEndpoint.isPrivateIPv4(host) ? host : nil
    }

    static func open(host: String, expectedKey: Data?) async throws -> LGSSH {
        guard let host = privateAddress(host) else { throw Failure.invalidAddress }
        let client = LGSSH()
        do {
            try await client.perform { try client.connect(host: host, expectedKey: expectedKey) }
            return client
        } catch {
            client.close()
            throw error
        }
    }

    func authenticate(key: Data, passphrase: String) async throws {
        guard (1...16384).contains(key.count), !passphrase.utf8.contains(0),
              (1...128).contains(passphrase.utf8.count) else { throw Failure.authentication }
        try await perform {
            guard let session = self.session else { throw Failure.connection }
            let status = key.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
                libssh2_userauth_publickey_frommemory(session, "prisoner", 8, nil, 0,
                                                    bytes.bindMemory(to: CChar.self).baseAddress, bytes.count, passphrase)
            }
            guard status == 0, libssh2_userauth_authenticated(session) == 1 else { throw Failure.authentication }
        }
    }

    func close() {
        socketLock.lock()
        stopped = true
        if socket >= 0 { _ = shutdown(socket, SHUT_RDWR) }
        socketLock.unlock()
        queue.async { self.release() }
    }

    deinit {
        if socket >= 0 { _ = shutdown(socket, SHUT_RDWR) }
        release()
    }

    func run(_ command: LGInstallation.Command, progress: @Sendable (Double) async -> Void) async throws -> Data {
        let (updates, output) = AsyncStream<Double>.makeStream(bufferingPolicy: .bufferingNewest(1))
        async let reporting: Void = sendProgress(updates, progress)
        defer { output.finish() }
        let data = try await perform {
            defer { output.finish() }
            return try self.execute(command) { output.yield($0) }
        }
        await reporting
        return data
    }

    private func sendProgress(_ updates: AsyncStream<Double>, _ progress: @Sendable (Double) async -> Void) async {
        for await update in updates { await progress(update) }
    }

    private func perform<Value: Sendable>(_ work: @escaping @Sendable () throws -> Value) async throws -> Value {
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            let result: Value = try await withCheckedThrowingContinuation { continuation in
                queue.async {
                    do {
                        try self.checkOpen()
                        continuation.resume(returning: try work())
                    } catch {
                        continuation.resume(throwing: error)
                    }
                }
            }
            try Task.checkCancellation()
            return result
        } onCancel: {
            self.close()
        }
    }

    private func checkOpen() throws {
        socketLock.lock()
        defer { socketLock.unlock() }
        if stopped { throw CancellationError() }
    }

    private func release() {
        if let session { _ = libssh2_session_free(session); self.session = nil }
        socketLock.lock()
        if socket >= 0 { _ = Darwin.close(socket); socket = -1 }
        socketLock.unlock()
    }

    private func connect(host: String, expectedKey: Data?) throws {
        guard Self.libraryReady else { throw Failure.connection }
        var address = sockaddr_in()
        address.sin_len = UInt8(MemoryLayout<sockaddr_in>.size)
        address.sin_family = sa_family_t(AF_INET)
        address.sin_port = in_port_t(9922).bigEndian
        guard inet_pton(AF_INET, host, &address.sin_addr) == 1 else { throw Failure.invalidAddress }

        let fd = Darwin.socket(AF_INET, SOCK_STREAM, IPPROTO_TCP)
        guard fd >= 0 else { throw Failure.connection }
        socketLock.lock()
        if stopped {
            socketLock.unlock()
            _ = Darwin.close(fd)
            throw CancellationError()
        }
        socket = fd
        socketLock.unlock()
        var noSignal: Int32 = 1
        guard setsockopt(fd, SOL_SOCKET, SO_NOSIGPIPE, &noSignal, socklen_t(MemoryLayout<Int32>.size)) == 0,
              fcntl(fd, F_SETFL, O_NONBLOCK) == 0 else { throw Failure.connection }

        let connection = withUnsafePointer(to: &address) { pointer in
            pointer.withMemoryRebound(to: sockaddr.self, capacity: 1) {
                Darwin.connect(fd, $0, socklen_t(MemoryLayout<sockaddr_in>.size))
            }
        }
        if connection != 0 {
            guard errno == EINPROGRESS else { throw Failure.connection }
            var descriptor = pollfd(fd: fd, events: Int16(POLLOUT), revents: 0)
            guard poll(&descriptor, 1, 10000) > 0 else { throw Failure.timeout }
            var error: Int32 = 0
            var length = socklen_t(MemoryLayout<Int32>.size)
            guard getsockopt(fd, SOL_SOCKET, SO_ERROR, &error, &length) == 0, error == 0 else { throw Failure.connection }
        }
        try checkOpen()
        guard let session = libssh2_session_init_ex(nil, nil, nil, nil) else { throw Failure.connection }
        self.session = session
        libssh2_session_set_timeout(session, 15000)
        guard libssh2_session_handshake(session, fd) == 0 else { throw Failure.connection }

        var length = 0
        guard let bytes = libssh2_session_hostkey(session, &length, nil),
              (1...4096).contains(length) else { throw Failure.connection }
        let actual = Data(bytes: bytes, count: length)
        guard let expectedKey else { throw Failure.approveHostKey(actual) }
        guard actual == expectedKey else { throw Failure.changedHostKey }
    }

    private func execute(_ command: LGInstallation.Command, progress: (Double) -> Void) throws -> Data {
        guard let session, libssh2_userauth_authenticated(session) == 1,
              let channel = libssh2_channel_open_ex(session, "session", 7, 2 * 1024 * 1024, 32768, nil, 0) else {
            throw Failure.connection
        }
        defer { _ = libssh2_channel_free(channel) }
        guard libssh2_channel_handle_extended_data2(channel, LIBSSH2_CHANNEL_EXTENDED_DATA_IGNORE) == 0,
              libssh2_channel_process_startup(channel, "exec", 4, command.text, UInt32(command.text.utf8.count)) == 0 else {
            throw Failure.command
        }
        let deadline = DispatchTime.now() + .seconds(180)
        if let input = command.input { try send(input, channel: channel, deadline: deadline, report: progress) }
        guard libssh2_channel_send_eof(channel) == 0 else { throw Failure.command }
        var response = Data()
        var buffer = [CChar](repeating: 0, count: 8192)
        while true {
            try checkOpen()
            guard DispatchTime.now() < deadline else { throw Failure.timeout }
            let count = buffer.withUnsafeMutableBufferPointer {
                libssh2_channel_read_ex(channel, 0, $0.baseAddress, $0.count)
            }
            if count > 0 {
                guard response.count + count <= LGInstallation.responseLimit else { throw Failure.command }
                buffer.withUnsafeBytes { response.append(contentsOf: $0.prefix(count)) }
                if command.streamsInstallStatus, try LGInstallation.installCompleted(response) { return response }
            } else {
                guard count == 0 else { throw Failure.command }
                if libssh2_channel_eof(channel) != 0 { break }
                Thread.sleep(forTimeInterval: 0.02)
            }
        }
        guard libssh2_channel_close(channel) == 0, libssh2_channel_get_exit_status(channel) == 0 else {
            throw Failure.command
        }
        return response
    }

    private func send(_ url: URL, channel: OpaquePointer, deadline: DispatchTime, report: (Double) -> Void) throws {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        let size = try file.seekToEnd()
        guard (1...UInt64(LgPackage.maximumSize)).contains(size) else { throw Failure.command }
        try file.seek(toOffset: 0)
        var sent: UInt64 = 0
        var lastPercent = 0
        while let chunk = try file.read(upToCount: 32768), !chunk.isEmpty {
            guard sent + UInt64(chunk.count) <= size else { throw Failure.command }
            try chunk.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
                guard let base = bytes.bindMemory(to: CChar.self).baseAddress else { throw Failure.command }
                var index = 0
                while index < bytes.count {
                    try checkOpen()
                    guard DispatchTime.now() < deadline else { throw Failure.timeout }
                    let count = libssh2_channel_write_ex(channel, 0, base + index, bytes.count - index)
                    guard count > 0 else { throw Failure.command }
                    index += count
                }
            }
            sent += UInt64(chunk.count)
            let percent = Int(sent * 100 / size)
            if lastPercent != percent {
                lastPercent = percent
                report(Double(percent) / 100)
            }
        }
        guard sent == size else { throw Failure.command }
    }
}
