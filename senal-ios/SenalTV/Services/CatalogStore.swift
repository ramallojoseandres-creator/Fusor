import Foundation
import Combine
import Compression
import UIKit

@MainActor
final class CatalogStore: ObservableObject {
    @Published private(set) var channels: [Channel] = []
    @Published private(set) var categories: [CategoryInfo] = []
    @Published private(set) var isLoading = false
    @Published private(set) var loadError: String?
    @Published private(set) var sourceLabel: String = "—"
    @Published var selectedCategory: String?

    private var loaded = false

    var movieChannels: [Channel] {
        channels.filter(\.isMovieGroup)
    }

    var seriesChannels: [Channel] {
        channels.filter(\.isSeriesGroup)
    }

    /// Carga una vez: disco → /api/catalog (si hay token) → bundle embebido.
    func loadIfNeeded(token: String? = nil, deviceId: String? = nil) async {
        guard !loaded else { return }
        await ensureReady(token: token, deviceId: deviceId, forceNetwork: false)
    }

    /// Tras login o desde Ajustes → Actualizar lista.
    func ensureReady(token: String?, deviceId: String?, forceNetwork: Bool) async {
        isLoading = true
        loadError = nil
        defer { isLoading = false }

        do {
            if !forceNetwork, loaded, !channels.isEmpty {
                return
            }

            if !forceNetwork, let disk = try? await Task.detached(priority: .userInitiated, operation: {
                try PlaylistParser.parseDiskCache()
            }).value, !disk.isEmpty {
                apply(disk, source: "cache")
                return
            }

            if let token, !token.isEmpty, let deviceId, !deviceId.isEmpty,
               forceNetwork || !PlaylistParser.hasDiskCache() {
                let remote = try await CatalogAPI.fetchChannels(token: token, deviceId: deviceId)
                if !remote.isEmpty {
                    let m3u = CatalogAPI.toM3U(remote)
                    try? PlaylistParser.writeDiskCache(m3u: m3u)
                    apply(remote, source: "api/catalog")
                    return
                }
            }

            if loaded, !channels.isEmpty { return }

            let bundled = try await Task.detached(priority: .userInitiated) {
                try PlaylistParser.parseBundled()
            }.value
            apply(bundled, source: "bundle")
        } catch {
            if channels.isEmpty {
                loadError = error.localizedDescription
            }
        }
    }

    func refreshFromServer(token: String, deviceId: String) async {
        loaded = false
        await ensureReady(token: token, deviceId: deviceId, forceNetwork: true)
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

    private func apply(_ list: [Channel], source: String) {
        channels = list
        categories = CatalogRules.sortCategories(buildCategories(from: list))
        if selectedCategory == nil || !categories.contains(where: { $0.name == selectedCategory }) {
            selectedCategory = CatalogRules.defaultCategory(categories)
        }
        sourceLabel = source
        loaded = true
        loadError = nil
    }

    private func buildCategories(from list: [Channel]) -> [CategoryInfo] {
        var counts: [String: Int] = [:]
        var order: [String] = []
        for ch in list {
            if counts[ch.group] == nil { order.append(ch.group) }
            counts[ch.group, default: 0] += 1
        }
        return order.map { CategoryInfo(id: $0, name: $0, count: counts[$0] ?? 0) }
    }
}

/// GET /api/catalog via RawHTTP (LiveContainer-safe).
enum CatalogAPI {
    private struct CatalogResponse: Decodable {
        let items: [CatalogItemDTO]?
        let channels: [CatalogItemDTO]?
        let movies: [CatalogItemDTO]?
        let series: [CatalogItemDTO]?
        let data: [CatalogItemDTO]?
        let results: [CatalogItemDTO]?
        let page: Int?
        let limit: Int?
        let total: Int?
        let hasMore: Bool?
        let nextPage: Int?
        let error: String?
        let message: String?

        func resolveItems() -> [CatalogItemDTO] {
            items ?? channels ?? movies ?? series ?? data ?? results ?? []
        }

        func resolveHasMore(requestedLimit: Int) -> Bool {
            if let hasMore { return hasMore }
            if nextPage != nil { return true }
            let list = resolveItems()
            if list.count >= requestedLimit { return true }
            let p = page ?? 1
            guard let total else { return false }
            return p * (limit ?? requestedLimit) < total
        }
    }

    private struct CatalogItemDTO: Decodable {
        let id: String?
        let _id: String?
        let streamId: String?
        let name: String?
        let title: String?
        let number: Int?
        let channelNumber: Int?
        let logo: String?
        let poster: String?
        let cover: String?
        let image: String?
        let icon: String?
        let category: String?
        let group: String?
        let type: String?
        let url: String?
        let streamUrl: String?
        let userAgent: String?

        enum CodingKeys: String, CodingKey {
            case id, streamId, name, title, number, channelNumber
            case logo, poster, cover, image, icon, category, group, type
            case url, streamUrl, userAgent
            case _id = "_id"
        }

        func resolveId() -> String {
            id ?? _id ?? streamId ?? name ?? UUID().uuidString
        }

        func resolveTitle() -> String {
            title ?? name ?? "Sin título"
        }

        func resolveCategory() -> String {
            let raw = category ?? group ?? "General"
            return raw.trimmingCharacters(in: .whitespacesAndNewlines).isEmpty ? "General" : raw
        }

        func resolveLogo() -> String? {
            logo ?? poster ?? cover ?? image ?? icon
        }

        func resolveStreamUrl() -> String? {
            let u = streamUrl ?? url
            let t = u?.trimmingCharacters(in: .whitespacesAndNewlines)
            return (t?.isEmpty == false) ? t : nil
        }

        func resolveNumber() -> Int? { number ?? channelNumber }
    }

    static func fetchChannels(token: String, deviceId: String) async throws -> [Channel] {
        var all: [String: Channel] = [:]
        // Bulk first
        if let bulk = try? await fetchPage(token: token, deviceId: deviceId, page: nil, limit: 20_000) {
            for ch in bulk.channels { all[ch.id] = ch }
            if bulk.channels.count >= 50 && !bulk.hasMore {
                return Array(all.values).sorted { $0.number < $1.number }
            }
        }
        var page = 1
        while page <= 40 {
            let chunk = try await fetchPage(token: token, deviceId: deviceId, page: page, limit: 500)
            if chunk.channels.isEmpty { break }
            for ch in chunk.channels { all[ch.id] = ch }
            if !chunk.hasMore { break }
            page += 1
        }
        let list = Array(all.values).sorted { $0.number < $1.number }
        if list.isEmpty {
            throw NSError(domain: "SenalCatalog", code: 1, userInfo: [
                NSLocalizedDescriptionKey: "Catálogo vacío en /api/catalog"
            ])
        }
        return list
    }

    private struct PageResult {
        let channels: [Channel]
        let hasMore: Bool
    }

    private static func fetchPage(
        token: String,
        deviceId: String,
        page: Int?,
        limit: Int?
    ) async throws -> PageResult {
        var path = "/api/catalog"
        var query: [String] = []
        if let page { query.append("page=\(page)") }
        if let limit { query.append("limit=\(limit)") }
        if let page, let limit { query.append("offset=\((page - 1) * limit)") }
        if !query.isEmpty { path += "?" + query.joined(separator: "&") }

        var request = Data()
        func append(_ s: String) { request.append(contentsOf: s.utf8) }
        append("GET \(path) HTTP/1.1\r\n")
        append("Host: \(AuthService.host):\(AuthService.port)\r\n")
        append("Accept: application/json\r\n")
        append("Authorization: Bearer \(token)\r\n")
        append("X-Device-Id: \(deviceId)\r\n")
        append("X-Device-Name: SENAL iPhone\r\n")
        append("X-Device-Platform: ios\r\n")
        append("Connection: close\r\n")
        append("\r\n")

        let data = try await RawHTTP.exchange(
            host: AuthService.host,
            port: AuthService.port,
            request: request
        )
        let decoded = try JSONDecoder().decode(CatalogResponse.self, from: data)
        if let err = decoded.error ?? decoded.message, decoded.resolveItems().isEmpty {
            throw NSError(domain: "SenalCatalog", code: 2, userInfo: [
                NSLocalizedDescriptionKey: err
            ])
        }
        let items = decoded.resolveItems()
        let requested = limit ?? 20_000
        let channels = items.enumerated().compactMap { idx, item -> Channel? in
            guard let urlStr = item.resolveStreamUrl(), let url = URL(string: urlStr) else { return nil }
            let number = item.resolveNumber() ?? (idx + 1 + ((page ?? 1) - 1) * requested)
            return Channel(
                id: item.resolveId(),
                number: number,
                name: item.resolveTitle(),
                logo: item.resolveLogo(),
                group: item.resolveCategory(),
                url: url,
                userAgent: item.userAgent,
                referrer: nil
            )
        }
        return PageResult(channels: channels, hasMore: decoded.resolveHasMore(requestedLimit: requested))
    }

    static func toM3U(_ channels: [Channel]) -> String {
        var sb = "#EXTM3U\n"
        for ch in channels {
            let name = ch.name.replacingOccurrences(of: "\n", with: " ")
            let group = ch.group.replacingOccurrences(of: ",", with: " ")
            let id = ch.id.replacingOccurrences(of: ",", with: " ")
            let logo = ch.logo?.replacingOccurrences(of: "\"", with: "'") ?? ""
            sb += "#EXTINF:-1 tvg-chno=\"\(ch.number)\" tvg-id=\"\(id)\""
            if !logo.isEmpty { sb += " tvg-logo=\"\(logo)\"" }
            sb += " group-title=\"\(group)\",\(name)\n"
            if let ua = ch.userAgent, !ua.isEmpty {
                sb += "#EXTVLCOPT:http-user-agent=\(ua)\n"
            }
            sb += "\(ch.url.absoluteString)\n"
        }
        return sb
    }
}

enum PlaylistParser {
    private static var cacheDir: URL {
        let base = FileManager.default.urls(for: .documentDirectory, in: .userDomainMask).first!
        let dir = base.appendingPathComponent("catalog", isDirectory: true)
        try? FileManager.default.createDirectory(at: dir, withIntermediateDirectories: true)
        return dir
    }

    static var cacheFile: URL { cacheDir.appendingPathComponent("playlist.m3u") }

    static func hasDiskCache() -> Bool {
        let url = cacheFile
        guard let attrs = try? FileManager.default.attributesOfItem(atPath: url.path),
              let size = attrs[.size] as? NSNumber else { return false }
        return size.intValue > 64
    }

    static func parseDiskCache() throws -> [Channel] {
        guard hasDiskCache() else {
            throw NSError(domain: "SenalPlaylist", code: 4, userInfo: [
                NSLocalizedDescriptionKey: "Sin caché local"
            ])
        }
        let text = try String(contentsOf: cacheFile, encoding: .utf8)
        let channels = parse(text: text)
        if channels.isEmpty {
            throw NSError(domain: "SenalPlaylist", code: 5, userInfo: [
                NSLocalizedDescriptionKey: "Caché local vacía"
            ])
        }
        return channels
    }

    static func writeDiskCache(m3u: String) throws {
        try m3u.write(to: cacheFile, atomically: true, encoding: .utf8)
    }

    static func parseBundled() throws -> [Channel] {
        let url = try locatePlaylist()
        let data = try Data(contentsOf: url)
        let name = url.lastPathComponent.lowercased()
        let text: String
        if name.hasSuffix(".gz") || name.hasSuffix(".dat") || name == "playlist" {
            text = try String(decoding: gunzip(data), as: UTF8.self)
        } else {
            text = String(decoding: data, as: UTF8.self)
        }
        let channels = parse(text: text)
        if channels.isEmpty {
            throw NSError(domain: "SenalPlaylist", code: 2, userInfo: [
                NSLocalizedDescriptionKey: "Lista vacía en \(url.lastPathComponent)"
            ])
        }
        return channels
    }

    /// Find playlist in guest bundle (LiveContainer-safe path probing).
    private static func locatePlaylist() throws -> URL {
        // 1) Asset catalog data set (if compiled into Assets)
        if let asset = NSDataAsset(name: "Playlist") {
            let tmp = FileManager.default.temporaryDirectory.appendingPathComponent("playlist-asset.dat")
            try asset.data.write(to: tmp, options: .atomic)
            return tmp
        }

        let candidates: [(String, String?)] = [
            ("playlist", "dat"),
            ("playlist", "m3u"),
            ("lista_fusionada", "m3u"),
            ("lista_fusionada", "m3u.gz"),
            ("lista_fusionada.m3u", nil),
            ("playlist.dat", nil),
        ]

        var bundles: [Bundle] = [Bundle.main, Bundle(for: BundleToken.self)]
        bundles.append(contentsOf: Bundle.allBundles)
        bundles.append(contentsOf: Bundle.allFrameworks)

        for bundle in bundles {
            for (resource, ext) in candidates {
                if let ext,
                   let url = bundle.url(forResource: resource, withExtension: ext) {
                    return url
                }
                if ext == nil {
                    let direct = bundle.bundleURL.appendingPathComponent(resource)
                    if FileManager.default.fileExists(atPath: direct.path) {
                        return direct
                    }
                    if let res = bundle.resourceURL?.appendingPathComponent(resource),
                       FileManager.default.fileExists(atPath: res.path) {
                        return res
                    }
                }
            }

            // Brute-force scan of bundle (covers LiveContainer odd layouts)
            if let urls = bundle.urls(forResourcesWithExtension: "dat", subdirectory: nil) {
                if let hit = urls.first(where: { $0.lastPathComponent.lowercased().contains("playlist")
                    || $0.lastPathComponent.lowercased().contains("lista") }) {
                    return hit
                }
            }
            if let urls = bundle.urls(forResourcesWithExtension: "m3u", subdirectory: nil) {
                if let hit = urls.first {
                    return hit
                }
            }
            if let urls = bundle.urls(forResourcesWithExtension: "gz", subdirectory: nil) {
                if let hit = urls.first(where: { $0.lastPathComponent.lowercased().contains("lista")
                    || $0.lastPathComponent.lowercased().contains("playlist") }) {
                    return hit
                }
            }
        }

        // Last resort: walk Bundle.main file tree
        if let enumerator = FileManager.default.enumerator(at: Bundle.main.bundleURL, includingPropertiesForKeys: nil) {
            for case let fileURL as URL in enumerator {
                let n = fileURL.lastPathComponent.lowercased()
                if n == "playlist.dat" || n == "playlist.m3u"
                    || n == "lista_fusionada.m3u" || n == "lista_fusionada.m3u.gz" {
                    return fileURL
                }
            }
        }

        throw NSError(domain: "SenalPlaylist", code: 1, userInfo: [
            NSLocalizedDescriptionKey: "Falta lista_fusionada / playlist en el bundle"
        ])
    }

    private final class BundleToken {}

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
