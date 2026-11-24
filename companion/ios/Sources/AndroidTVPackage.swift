import Foundation

enum AndroidTVPackage {
    enum Invalid: Error { case archive }

    static let maximumSize = 200 * 1024 * 1024
    private static let manifestLimit = 1024 * 1024
    private static let manifestName = Array("AndroidManifest.xml".utf8)
    private static let androidNamespace = "http://schemas.android.com/apk/res/android"

    static func version(of url: URL) throws -> String {
        let file = try FileHandle(forReadingFrom: url)
        defer { try? file.close() }
        let length = Int(try file.seekToEnd())
        guard (22...maximumSize).contains(length) else { throw Invalid.archive }

        let tailSize = min(length, 22 + 65535)
        let tail = try read(file, at: length - tailSize, count: tailSize)
        guard let end = (0...(tailSize - 22)).reversed().first(where: {
            tail.word($0) == 0x06054b50 && $0 + 22 + tail.half($0 + 20) == tailSize
        }), tail.half(end + 4) == 0, tail.half(end + 6) == 0,
              tail.half(end + 8) == tail.half(end + 10) else { throw Invalid.archive }
        let entries = tail.half(end + 10)
        let directorySize = tail.word(end + 12)
        let directoryOffset = tail.word(end + 16)
        guard (1..<65535).contains(entries), (46...(8 * manifestLimit)).contains(directorySize),
              directoryOffset + directorySize == length - tailSize + end else { throw Invalid.archive }

        let directory = try read(file, at: directoryOffset, count: directorySize)
        var offset = 0
        var manifest: Data?
        for _ in 0..<entries {
            try Task.checkCancellation()
            guard directorySize - offset >= 46, directory.word(offset) == 0x02014b50 else { throw Invalid.archive }
            let nameStart = offset + 46
            let nameEnd = nameStart + directory.half(offset + 28)
            let next = nameEnd + directory.half(offset + 30) + directory.half(offset + 32)
            guard next <= directorySize, directory.half(offset + 34) == 0 else { throw Invalid.archive }
            if directory[nameStart..<nameEnd].elementsEqual(manifestName) {
                guard manifest == nil else { throw Invalid.archive }
                manifest = try readManifest(file, directory: directory, entry: offset, limit: directoryOffset)
            }
            offset = next
        }
        guard offset == directorySize, let manifest else { throw Invalid.archive }
        return try version(in: Bytes(manifest))
    }

    private static func readManifest(_ file: FileHandle, directory: Bytes, entry: Int, limit: Int) throws -> Data {
        let flags = directory.half(entry + 8)
        let method = directory.half(entry + 10)
        let compressed = directory.word(entry + 20)
        let size = directory.word(entry + 24)
        let localOffset = directory.word(entry + 42)
        guard flags & ~0x080E == 0, method == 0 || method == 8,
              (1...manifestLimit).contains(size), (1...(manifestLimit + 65536)).contains(compressed),
              localOffset + 30 <= limit else { throw Invalid.archive }

        let local = try read(file, at: localOffset, count: 30)
        let nameSize = local.half(26)
        let start = localOffset + 30 + nameSize + local.half(28)
        let checksum = directory.word(entry + 16)
        guard local.word(0) == 0x04034b50, local.half(6) == flags, local.half(8) == method,
              nameSize == manifestName.count, start + compressed <= limit,
              flags & 8 != 0 || (local.word(14) == checksum && local.word(18) == compressed && local.word(22) == size),
              try read(file, at: localOffset + 30, count: nameSize).data.elementsEqual(manifestName) else {
            throw Invalid.archive
        }

        let contents = try read(file, at: start, count: compressed).data
        let xml: Data
        if method == 8 {
            guard let inflated = TVPackageCompression.expand(contents, windowBits: -15, limit: size) else {
                throw Invalid.archive
            }
            xml = inflated
        } else {
            xml = contents
        }
        let actual = xml.withUnsafeBytes { (bytes: UnsafeRawBufferPointer) in
            crc32(0, bytes.bindMemory(to: UInt8.self).baseAddress, UInt32(bytes.count))
        }
        guard xml.count == size, Int(actual) == checksum else { throw Invalid.archive }
        return xml
    }

    private static func version(in xml: Bytes) throws -> String {
        guard xml.count >= 8, xml.half(0) == 3, xml.word(4) == xml.count else { throw Invalid.archive }
        var offset = xml.half(2)
        guard offset >= 8, offset < xml.count else { throw Invalid.archive }
        var strings: StringPool?
        while xml.count - offset >= 8 {
            let kind = xml.half(offset)
            let header = xml.half(offset + 2)
            let size = xml.word(offset + 4)
            guard header >= 8, size >= header, size <= xml.count - offset else { throw Invalid.archive }
            switch kind {
            case 1:
                guard strings == nil, header >= 28 else { throw Invalid.archive }
                strings = try StringPool(xml: xml, offset: offset, header: header, size: size)
            case 0x0102:
                guard let strings, header >= 16 else { throw Invalid.archive }
                return try version(in: xml, element: offset + header, end: offset + size, strings: strings)
            default:
                break
            }
            offset += size
        }
        throw Invalid.archive
    }

    private static func version(in xml: Bytes, element: Int, end: Int, strings: StringPool) throws -> String {
        guard end - element >= 20, xml.word(element) == 0xffffffff,
              try strings.string(at: xml.word(element + 4)) == "manifest" else { throw Invalid.archive }
        let start = xml.half(element + 8)
        let stride = xml.half(element + 10)
        let count = xml.half(element + 12)
        // shortcut: cap root attributes while inspecting identity, revisit if Miqat's manifest outgrows it.
        guard count <= 256, start >= 20, stride >= 20,
              start + count * stride <= end - element else { throw Invalid.archive }

        var package: String?
        var versionCode: Int?
        var versionMajor: Int?
        for index in 0..<count {
            let attribute = element + start + index * stride
            let namespace = xml.word(attribute)
            let name = try strings.string(at: xml.word(attribute + 4))
            let value = xml.word(attribute + 16)
            guard xml.half(attribute + 12) == 8 else { throw Invalid.archive }
            let type = xml.data[attribute + 15]
            if name == "package", namespace == 0xffffffff {
                guard package == nil, type == 3 else { throw Invalid.archive }
                package = try strings.string(at: value)
            } else if (name == "versionCode" || name == "versionCodeMajor"), namespace != 0xffffffff,
                      try strings.string(at: namespace) == androidNamespace {
                guard type == 0x10 || type == 0x11, value <= Int(Int32.max) else { throw Invalid.archive }
                if name == "versionCode" {
                    guard versionCode == nil else { throw Invalid.archive }
                    versionCode = value
                } else {
                    guard versionMajor == nil else { throw Invalid.archive }
                    versionMajor = value
                }
            }
        }
        guard package == "tv.miqat.home", let versionCode, versionCode > 0 else { throw Invalid.archive }
        return String((UInt64(versionMajor ?? 0) << 32) | UInt64(versionCode))
    }

    private struct StringPool {
        let xml: Bytes
        let offsets: Int
        let count: Int
        let start: Int
        let end: Int
        let utf8: Bool

        init(xml: Bytes, offset: Int, header: Int, size: Int) throws {
            self.xml = xml
            offsets = offset + header
            count = xml.word(offset + 8)
            let styles = xml.word(offset + 12)
            let boundary = xml.word(offset + 24)
            start = offset + xml.word(offset + 20)
            end = offset + (boundary == 0 ? size : boundary)
            utf8 = xml.word(offset + 16) & 0x100 != 0
            guard offsets + (count + styles) * 4 <= start, start < end, end <= offset + size else {
                throw Invalid.archive
            }
        }

        func string(at index: Int) throws -> String {
            guard index < count else { throw Invalid.archive }
            var position = start + xml.word(offsets + index * 4)

            func length(unit: Int) throws -> Int {
                guard position + unit <= end else { throw Invalid.archive }
                let first = unit == 1 ? Int(xml.data[position]) : xml.half(position)
                position += unit
                let bits = unit * 8
                let continuation = 1 << (bits - 1)
                guard first & continuation != 0 else { return first }
                guard position + unit <= end else { throw Invalid.archive }
                let second = unit == 1 ? Int(xml.data[position]) : xml.half(position)
                position += unit
                return ((first & (continuation - 1)) << bits) | second
            }

            let units = try length(unit: utf8 ? 1 : 2)
            let size = utf8 ? try length(unit: 1) : units * 2
            let terminator = position + size
            let width = utf8 ? 1 : 2
            guard terminator + width <= end,
                  xml.data[terminator..<(terminator + width)].allSatisfy({ $0 == 0 }),
                  let result = String(data: xml.data[position..<terminator], encoding: utf8 ? .utf8 : .utf16LittleEndian),
                  result.utf16.count == units else { throw Invalid.archive }
            return result
        }
    }

    private struct Bytes {
        let data: Data
        init(_ data: Data) { self.data = data }
        var count: Int { data.count }
        subscript(range: Range<Int>) -> Data { data[range] }
        func half(_ offset: Int) -> Int { Int(data[offset]) | (Int(data[offset + 1]) << 8) }
        func word(_ offset: Int) -> Int { half(offset) | (half(offset + 2) << 16) }
    }

    private static func read(_ file: FileHandle, at offset: Int, count: Int) throws -> Bytes {
        try file.seek(toOffset: UInt64(offset))
        var data = Data()
        data.reserveCapacity(count)
        while data.count < count {
            guard let chunk = try file.read(upToCount: count - data.count), !chunk.isEmpty else { throw Invalid.archive }
            data.append(chunk)
        }
        return Bytes(data)
    }
}
