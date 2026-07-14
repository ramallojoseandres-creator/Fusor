import Foundation
import Combine
import Compression

@MainActor
final class CatalogStore: ObservableObject {
    @Published private(set) var channels: [Channel] = []
    @Published private(set) var categories: [CategoryInfo] = []
    @Published private(set) var isLoading = false
    @Published private(set) var loadError: String?
    @Published var selectedCategory: String?

    private var loaded = false

    var movieChannels: [Channel] {
        channels.filter(\.isMovieGroup)
    }

    var seriesChannels: [Channel] {
        channels.filter(\.isSeriesGroup)
    }

    func loadIfNeeded() async {
        guard !loaded else { return }
        isLoading = true
        loadError = nil
        defer { isLoading = false }
        do {
            let parsed = try await Task.detached(priority: .userInitiated) {
                try PlaylistParser.parseBundled()
            }.value
            channels = parsed
            categories = CatalogRules.sortCategories(buildCategories(from: parsed))
            selectedCategory = CatalogRules.defaultCategory(categories)
            loaded = true
        } catch {
            loadError = error.localizedDescription
        }
    }

    func channels(in category: String?) -> [Channel] {
        guard let category, !category.isEmpty else { return channels }
        return channels.filter { $0.group.caseInsensitiveCompare(category) == .orderedSame }
    }

    func search(_ query: String, limit: Int = 80) -> [Channel] {
        let q = query.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
        guard !q.isEmpty else { return [] }
        return channels
            .filter {
                $0.name.lowercased().contains(q) || $0.group.lowercased().contains(q)
            }
            .prefix(limit)
            .map { $0 }
    }

    private func buildCategories(from list: [Channel]) -> [CategoryInfo] {
        var counts: [String: Int] = [:]
        for ch in list {
            counts[ch.group, default: 0] += 1
        }
        return counts.map { CategoryInfo(id: $0.key, name: $0.key, count: $0.value) }
    }
}

enum PlaylistParser {
    static func parseBundled() throws -> [Channel] {
        guard let url = Bundle.main.url(forResource: "lista_fusionada", withExtension: "m3u")
                ?? Bundle.main.url(forResource: "lista_fusionada", withExtension: "m3u.gz") else {
            throw NSError(domain: "SenalPlaylist", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Falta lista_fusionada.m3u(.gz) en el bundle"
            ])
        }
        let data = try Data(contentsOf: url)
        let text: String
        if url.pathExtension == "gz" {
            text = try String(decoding: gunzip(data), as: UTF8.self)
        } else {
            text = String(decoding: data, as: UTF8.self)
        }
        return parse(text: text)
    }

    static func parse(text: String) -> [Channel] {
        var pending: ExtInf?
        var out: [Channel] = []
        var index = 0

        for raw in text.split(whereSeparator: \.isNewline) {
            let line = raw.trimmingCharacters(in: .whitespacesAndNewlines)
            if line.isEmpty { continue }
            if line.uppercased().hasPrefix("#EXTINF") {
                pending = ExtInf.parse(line)
                continue
            }
            if line.hasPrefix("#") { continue }
            guard let ext = pending, let stream = URL(string: line) else {
                pending = nil
                continue
            }
            pending = nil
            index += 1
            let group = ext.group.isEmpty ? "Variados" : ext.group
            let name = ext.name.isEmpty ? "Canal \(index)" : ext.name
            let id: String
            if !ext.tvgId.isEmpty {
                id = "tvg:\(ext.tvgId)"
            } else {
                id = "m3u:\(stableHash(name.lowercased())):\(index)"
            }
            out.append(
                Channel(
                    id: id,
                    number: index,
                    name: name,
                    logo: ext.logo,
                    group: group,
                    url: stream,
                    userAgent: ext.userAgent,
                    referrer: ext.referrer
                )
            )
        }
        return out
    }

    private static func stableHash(_ s: String) -> Int {
        var h = 5381
        for u in s.utf8 {
            h = ((h << 5) &+ h) &+ Int(u)
        }
        return h
    }

    private struct ExtInf {
        var name: String
        var group: String
        var logo: String?
        var tvgId: String
        var userAgent: String?
        var referrer: String?

        static func parse(_ line: String) -> ExtInf {
            let comma = line.lastIndex(of: ",")
            let name = comma.map { String(line[line.index(after: $0)...]).trimmingCharacters(in: .whitespaces) } ?? ""
            let attrs = comma.map { String(line[..<$0]) } ?? line
            func attr(_ key: String) -> String? {
                let pattern = #"(?i)\#(NSRegularExpression.escapedPattern(for: key))="([^"]*)""#
                guard let re = try? NSRegularExpression(pattern: pattern) else { return nil }
                let range = NSRange(attrs.startIndex..., in: attrs)
                guard let m = re.firstMatch(in: attrs, range: range),
                      let r = Range(m.range(at: 1), in: attrs) else { return nil }
                let v = String(attrs[r]).trimmingCharacters(in: .whitespaces)
                return v.isEmpty ? nil : v
            }
            return ExtInf(
                name: name,
                group: attr("group-title") ?? "Variados",
                logo: attr("tvg-logo"),
                tvgId: attr("tvg-id") ?? "",
                userAgent: attr("http-user-agent") ?? attr("user-agent"),
                referrer: attr("http-referrer") ?? attr("referrer")
            )
        }
    }

    /// Minimal gzip inflate via Compression framework.
    private static func gunzip(_ data: Data) throws -> Data {
        // Skip 10-byte gzip header (assumes no FNAME/extra for our asset; fallback to full scan).
        var input = data
        if data.count > 10, data[0] == 0x1f, data[1] == 0x8b {
            var offset = 10
            let flags = data[3]
            if flags & 0x04 != 0, data.count > offset + 2 {
                let xlen = Int(data[offset]) | (Int(data[offset + 1]) << 8)
                offset += 2 + xlen
            }
            if flags & 0x08 != 0 {
                while offset < data.count && data[offset] != 0 { offset += 1 }
                offset += 1
            }
            if flags & 0x10 != 0 {
                while offset < data.count && data[offset] != 0 { offset += 1 }
                offset += 1
            }
            if flags & 0x02 != 0 { offset += 2 }
            input = data.subdata(in: offset..<(data.count - 8))
        }

        let dstSize = max(input.count * 8, 2_000_000)
        var dest = Data(count: dstSize)
        let decodedCount: Int = try dest.withUnsafeMutableBytes { destBuf in
            try input.withUnsafeBytes { srcBuf in
                guard let destPtr = destBuf.bindMemory(to: UInt8.self).baseAddress,
                      let srcPtr = srcBuf.bindMemory(to: UInt8.self).baseAddress else {
                    throw NSError(domain: "SenalGzip", code: 2, userInfo: [
                        NSLocalizedDescriptionKey: "No se pudo descomprimir la lista"
                    ])
                }
                let written = compression_decode_buffer(
                    destPtr, dstSize,
                    srcPtr, input.count,
                    nil,
                    COMPRESSION_ZLIB
                )
                if written == 0 {
                    throw NSError(domain: "SenalGzip", code: 3, userInfo: [
                        NSLocalizedDescriptionKey: "Gzip inválido"
                    ])
                }
                return written
            }
        }
        dest.removeSubrange(decodedCount...)
        return dest
    }
}
