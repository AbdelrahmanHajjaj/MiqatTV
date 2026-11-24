import Foundation

enum LgPackage {
    enum Invalid: Error { case archive }

    static let packageID = "tv.miqat.webos"
    static let maximumSize = 200 * 1024 * 1024
    private static let controlLimit = 1024 * 1024
    private static let archiveMagic = Data("!<arch>\n".utf8)

    static func version(of url: URL) throws -> String {
        let input = try FileHandle(forReadingFrom: url)
        defer { try? input.close() }
        let length = try input.seekToEnd()
        guard (128...UInt64(maximumSize)).contains(length) else { throw Invalid.archive }
        try input.seek(toOffset: 0)
        guard try exact(input, count: 8) == archiveMagic else { throw Invalid.archive }
        var offset: UInt64 = 8
        var names = Set<String>()
        var control: Data?
        var hasPayload = false
        while offset < length {
            try Task.checkCancellation()
            guard length - offset >= 60 else { throw Invalid.archive }
            let header = try exact(input, count: 60)
            guard header[58] == 96, header[59] == 10,
                  let rawName = String(data: header.prefix(16), encoding: .ascii) else { throw Invalid.archive }
            let trimmed = rawName.trimmingCharacters(in: .whitespaces)
            let name = trimmed.hasSuffix("/") ? String(trimmed.dropLast()) : trimmed
            guard !name.isEmpty, names.count < 64, names.insert(name).inserted else { throw Invalid.archive }
            let count = try number(header[48..<58], radix: 10, limit: maximumSize)
            let padded = UInt64(count + count % 2)
            offset += 60
            guard padded <= length - offset else { throw Invalid.archive }
            if name == "control.tar.gz" {
                guard (1...controlLimit).contains(count) else { throw Invalid.archive }
                control = try exact(input, count: count)
            } else if name == "data.tar.gz" {
                hasPayload = count > 0
            }
            offset += padded
            try input.seek(toOffset: offset)
        }
        guard let control, hasPayload,
              let tar = TVPackageCompression.expand(control, windowBits: 31, limit: controlLimit) else {
            throw Invalid.archive
        }
        return try controlVersion(in: tar)
    }

    private static func controlVersion(in tar: Data) throws -> String {
        var offset = 0
        var record: String?
        var ended = false
        while tar.count - offset >= 512 {
            let header = tar[offset..<(offset + 512)]
            if header.allSatisfy({ $0 == 0 }) { ended = true; break }
            let name = String(decoding: header.prefix(100).prefix { $0 != 0 }, as: UTF8.self)
            let count = try number(tar[(offset + 124)..<(offset + 136)], radix: 8, limit: 65536)
            let padded = ((count + 511) / 512) * 512
            let contents = offset + 512
            guard padded <= tar.count - contents else { throw Invalid.archive }
            if name == "control" || name == "./control" {
                let kind = tar[offset + 156]
                guard record == nil, kind == 0 || kind == 48 else { throw Invalid.archive }
                record = String(data: tar[contents..<(contents + count)], encoding: .utf8)
                guard record != nil else { throw Invalid.archive }
            }
            offset = contents + padded
        }
        guard ended, let record else { throw Invalid.archive }
        var fields: [String: String] = [:]
        for line in record.split(separator: "\n") {
            guard let colon = line.firstIndex(of: ":"), colon != line.startIndex else { continue }
            let key = String(line[..<colon])
            let value = line[line.index(after: colon)...].trimmingCharacters(in: .whitespacesAndNewlines)
            guard fields.updateValue(value, forKey: key) == nil else { throw Invalid.archive }
        }
        guard fields["Package"] == packageID, let version = fields["Version"] else { throw Invalid.archive }
        _ = try versionComponents(version)
        return version
    }

    static func versionComponents(_ version: String) throws -> [Int] {
        let segments = version.split(separator: ".", omittingEmptySubsequences: false)
        guard (2...4).contains(segments.count) else { throw Invalid.archive }
        let numbers = try segments.map { segment in
            guard (1...8).contains(segment.utf8.count),
                  segment.utf8.allSatisfy({ (48...57).contains($0) }),
                  let value = Int(segment) else { throw Invalid.archive }
            return value
        }
        return numbers + Array(repeating: 0, count: 4 - numbers.count)
    }

    private static func number(_ field: Data, radix: Int, limit: Int) throws -> Int {
        let text = String(decoding: field.filter { $0 != 0 }, as: UTF8.self).trimmingCharacters(in: .whitespaces)
        guard !text.isEmpty, text.utf8.allSatisfy({ (48..<(48 + radix)).contains(Int($0)) }),
              let value = Int(text, radix: radix), value <= limit else { throw Invalid.archive }
        return value
    }

    private static func exact(_ input: FileHandle, count: Int) throws -> Data {
        var result = Data()
        result.reserveCapacity(count)
        while result.count < count {
            guard let chunk = try input.read(upToCount: count - result.count), !chunk.isEmpty else {
                throw Invalid.archive
            }
            result.append(chunk)
        }
        return result
    }
}

enum TVPackageCompression {
    static func expand(_ data: Data, windowBits: Int32, limit: Int) -> Data? {
        var inflater = z_stream()
        guard inflateInit2_(&inflater, windowBits, zlibVersion(), Int32(MemoryLayout<z_stream>.size)) == Z_OK else {
            return nil
        }
        defer { _ = inflateEnd(&inflater) }
        var output = Data(count: limit + 1)
        let result = data.withUnsafeBytes { (source: UnsafeRawBufferPointer) in
            output.withUnsafeMutableBytes { (target: UnsafeMutableRawBufferPointer) -> Int32 in
                guard let start = source.bindMemory(to: UInt8.self).baseAddress,
                      let end = target.bindMemory(to: UInt8.self).baseAddress else { return Z_DATA_ERROR }
                inflater.next_in = UnsafeMutablePointer(mutating: start)
                inflater.avail_in = UInt32(source.count)
                inflater.next_out = end
                inflater.avail_out = UInt32(target.count)
                return inflate(&inflater, Z_FINISH)
            }
        }
        guard result == Z_STREAM_END, inflater.avail_in == 0, Int(inflater.total_out) <= limit else {
            return nil
        }
        output.count = Int(inflater.total_out)
        return output
    }
}
