import Foundation
import MiqatKit
import Network

actor AndroidADB {
    enum Failure: Error {
        case address, timeout, incomplete, invalidPacket, authorization, wirelessRequired, refused, busy
    }

    private let connection: NWConnection?
    private let wireless: AndroidADBSocket?
    private var version = InstallerAdb.shared.legacyVersion
    private var payloadLimit: Int32 = 4096
    private var authorized = false
    private var busy = false
    private var streamID: Int32 = 0
    private static let queue = DispatchQueue(label: "tv.miqat.ios.android-adb")

    private init(host: String, port: NWEndpoint.Port) {
        let connection = NWConnection(host: NWEndpoint.Host(host), port: port, using: .tcp)
        self.connection = connection
        wireless = nil
        connection.start(queue: Self.queue)
    }

    private init(wireless: AndroidADBSocket) {
        self.wireless = wireless
        connection = nil
    }

    deinit { connection?.cancel(); wireless?.cancel() }

    static func open(host: String, port: Int,
                     approvalRequested: @Sendable @escaping () async -> Void) async throws -> AndroidADB {
        guard TVEndpoint.isPrivateIPv4(host), let value = UInt16(exactly: port), value > 0,
              let endpoint = NWEndpoint.Port(rawValue: value) else { throw Failure.address }
        let client = AndroidADB(host: host, port: endpoint)
        do {
            try await timed(.seconds(90)) { try await client.authorize(approvalRequested: approvalRequested) }
            return client
        } catch {
            await client.close()
            throw error
        }
    }

    static func openWireless(host: String, port: Int) async throws -> (client: AndroidADB, digest: Data) {
        guard TVEndpoint.isPrivateIPv4(host), let port = UInt16(exactly: port), port > 0 else {
            throw Failure.address
        }
        let socket = AndroidADBSocket()
        let client = AndroidADB(wireless: socket)
        do {
            let digest = try await timed(.seconds(30)) {
                try await socket.perform { try socket.connect(host: host, port: port, deadline: .now() + .seconds(30)) }
                return try await client.upgrade(socket)
            }
            return (client, digest)
        } catch {
            await client.close()
            throw error
        }
    }

    func close() {
        authorized = false
        connection?.cancel()
        wireless?.cancel()
    }

    func exchange(_ destination: String, input: URL? = nil,
                  progress: @escaping @Sendable (Double) async -> Void = { _ in }) async throws -> Data {
        guard authorized else { throw Failure.authorization }
        guard !busy else { throw Failure.busy }
        busy = true
        defer { busy = false }
        do {
            return try await Self.timed(.seconds(180)) {
                try await self.exchangeStream(destination, input: input, progress: progress)
            }
        } catch {
            close()
            throw error
        }
    }

    private func authorize(approvalRequested: @Sendable () async -> Void) async throws {
        let identity = try AndroidADBIdentity.load()
        let handshake = ClassicAdbHandshake()
        try await send(handshake.hello(), version: InstallerAdb.shared.legacyVersion)
        while true {
            let packet = try await receive(handshake: true)
            switch handshake.receive(packet: packet) {
            case .sign:
                let signed = try identity.sign(Data(adbBytes: packet.payload))
                try await send(AdbPacket(command: .auth, first: 2, second: 0, payload: signed.adbBytes), version: version)
            case .offerKey:
                try await send(AdbPacket(command: .auth, first: 3, second: 0, payload: try identity.offer().adbBytes),
                               version: version)
                await approvalRequested()
            case .connected:
                accept(handshake)
                return
            case .wirelessRequired:
                throw Failure.wirelessRequired
            default:
                throw Failure.authorization
            }
        }
    }

    private func upgrade(_ socket: AndroidADBSocket) async throws -> Data {
        let handshake = ClassicAdbHandshake()
        try await send(handshake.hello(), version: version)
        let request = try await receive(handshake: true)
        guard handshake.receive(packet: request) == .wirelessRequired else { throw Failure.authorization }
        try await send(request, version: version)
        let digest = try await socket.perform {
            try socket.startTLS(identity: AndroidADBIdentity.load(), deadline: .now() + .seconds(30))
            return try socket.peerDigest()
        }
        let banner = try await receive(handshake: true)
        guard handshake.receiveOverTls(packet: banner) == .connected else { throw Failure.authorization }
        accept(handshake)
        return digest
    }

    private func accept(_ handshake: ClassicAdbHandshake) {
        version = handshake.version
        payloadLimit = handshake.payloadLimit
        authorized = true
    }

    private func exchangeStream(_ destination: String, input: URL?,
                                progress: @Sendable (Double) async -> Void) async throws -> Data {
        try Task.checkCancellation()
        if streamID == Int32.max { streamID = 0 }
        streamID += 1
        let stream = ClassicAdbStream(localId: streamID, payloadLimit: payloadLimit)
        guard let open = stream.open(destination: destination) else { throw Failure.invalidPacket }
        try await send(open, version: version)
        let first = try await receive()
        switch stream.receive(packet: first) {
        case .opened: break
        case .refused: throw Failure.refused
        default: throw Failure.invalidPacket
        }

        var output = Data()
        if let input {
            let file = try FileHandle(forReadingFrom: input)
            defer { try? file.close() }
            let length = try file.seekToEnd()
            guard (1...UInt64(AndroidTVPackage.maximumSize)).contains(length) else { throw Failure.incomplete }
            try file.seek(toOffset: 0)
            var sent: UInt64 = 0
            var percent = 0
            while let bytes = try file.read(upToCount: min(Int(payloadLimit), 65536)), !bytes.isEmpty {
                guard sent + UInt64(bytes.count) <= length, let packet = stream.write(bytes: bytes.adbBytes) else {
                    throw Failure.invalidPacket
                }
                try await send(packet, version: version)
                while true {
                    let response = try await receive()
                    let action = stream.receive(packet: response)
                    if action == .acknowledged { break }
                    guard action == .output else { throw Failure.incomplete }
                    try await consume(response, on: stream, into: &output)
                }
                sent += UInt64(bytes.count)
                let updated = Int(sent * 100 / length)
                if updated != percent {
                    percent = updated
                    await progress(Double(percent) / 100)
                }
            }
            guard sent == length else { throw Failure.incomplete }
        }

        while true {
            let packet = try await receive()
            switch stream.receive(packet: packet) {
            case .output: try await consume(packet, on: stream, into: &output)
            case .closed:
                guard let reply = stream.close() else { throw Failure.invalidPacket }
                try await send(reply, version: version)
                return output
            default: throw Failure.invalidPacket
            }
        }
    }

    private func consume(_ packet: AdbPacket, on stream: ClassicAdbStream, into output: inout Data) async throws {
        let limit = 1024 * 1024
        guard output.count + Int(packet.payload.size) <= limit, let ack = stream.acknowledgeOutput() else {
            throw Failure.invalidPacket
        }
        output.append(Data(adbBytes: packet.payload))
        try await send(ack, version: version)
    }

    private func receive(handshake: Bool = false) async throws -> AdbPacket {
        let codec = InstallerAdb.shared
        let raw = try await read(Int(codec.headerSize))
        guard let header = codec.decodeHeader(bytes: raw.adbBytes, payloadLimit: payloadLimit) else {
            throw Failure.invalidPacket
        }
        let payload = try await read(Int(header.payloadSize)).adbBytes
        let packet = handshake ? codec.decodeHandshakePayload(header: header, payload: payload)
            : codec.decodePayload(header: header, payload: payload, version: version)
        guard let packet else { throw Failure.invalidPacket }
        return packet
    }

    private func send(_ packet: AdbPacket, version: Int32) async throws {
        guard let header = InstallerAdb.shared.encodeHeader(packet: packet, version: version) else {
            throw Failure.invalidPacket
        }
        let message = Data(adbBytes: header) + Data(adbBytes: packet.payload)
        if let wireless {
            return try await wireless.perform { try wireless.write(message, deadline: .now() + .seconds(30)) }
        }
        guard let connection else { throw Failure.incomplete }
        try await withTaskCancellationHandler {
            try Task.checkCancellation()
            try await withCheckedThrowingContinuation { (reply: CheckedContinuation<Void, Error>) in
                connection.send(content: message, completion: .contentProcessed { error in
                    if let error { reply.resume(throwing: error) }
                    else { reply.resume() }
                })
            }
        } onCancel: { connection.cancel() }
    }

    private func read(_ length: Int) async throws -> Data {
        if length == 0 { return Data() }
        if let wireless {
            return try await wireless.perform { try wireless.read(length, deadline: .now() + .seconds(30)) }
        }
        guard let connection else { throw Failure.incomplete }
        return try await withTaskCancellationHandler {
            try Task.checkCancellation()
            return try await withCheckedThrowingContinuation { reply in
                connection.receive(minimumIncompleteLength: length, maximumLength: length) { data, _, _, error in
                    if let error { reply.resume(throwing: error) }
                    else if let data, data.count == length { reply.resume(returning: data) }
                    else { reply.resume(throwing: Failure.incomplete) }
                }
            }
        } onCancel: { connection.cancel() }
    }

    private static func timed<Value: Sendable>(_ timeout: Duration,
                                                work: @escaping @Sendable () async throws -> Value) async throws -> Value {
        try await withThrowingTaskGroup(of: Value.self) { group in
            group.addTask(operation: work)
            group.addTask {
                try await Task.sleep(for: timeout)
                throw Failure.timeout
            }
            defer { group.cancelAll() }
            guard let result = try await group.next() else { throw Failure.incomplete }
            return result
        }
    }
}
