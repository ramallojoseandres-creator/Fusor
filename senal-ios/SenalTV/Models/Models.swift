import Foundation

struct Channel: Identifiable, Hashable {
    let id: String
    let number: Int
    let name: String
    let logo: String?
    let group: String
    let url: URL
    let userAgent: String?
    let referrer: String?

    var isAdult: Bool {
        CatalogRules.isAdult(group) || CatalogRules.isAdult(name)
    }

    var isMovieGroup: Bool {
        CatalogRules.isMovie(group, name)
    }

    var isSeriesGroup: Bool {
        CatalogRules.isSeries(group, name)
    }
}

struct CategoryInfo: Identifiable, Hashable {
    let id: String
    let name: String
    let count: Int
}

enum CatalogRules {
    private static let adultPattern = try! NSRegularExpression(
        pattern: #"(?i)(\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw|onlyfans|playboy"#
    )

    private static let preferred: [String] = [
        "deportes", "noticias", "cultura", "documentales", "series 24/7",
        "series", "películas", "peliculas", "infantil", "música", "musica",
        "variados", "latino", "españa", "mexico", "méxico", "estados unidos"
    ]

    static func isAdult(_ text: String) -> Bool {
        let range = NSRange(text.startIndex..., in: text)
        return adultPattern.firstMatch(in: text, range: range) != nil
    }

    static func isSeries(_ group: String, _ name: String) -> Bool {
        let hay = "\(group) \(name)".lowercased()
        return hay.contains("serie") || hay.contains("series")
    }

    static func isMovie(_ group: String, _ name: String) -> Bool {
        if isSeries(group, name) { return false }
        let hay = "\(group) \(name)".lowercased()
        return hay.contains("película") || hay.contains("pelicula")
            || hay.contains("cine") || hay.contains("movie") || hay.contains("vod")
    }

    static func sortCategories(_ cats: [CategoryInfo]) -> [CategoryInfo] {
        let unique = Dictionary(grouping: cats, by: { $0.name.lowercased() })
            .compactMap { $0.value.first }
        let normal = unique.filter { !isAdult($0.name) }
        let adults = unique.filter { isAdult($0.name) }
        let ranked = normal.sorted { a, b in
            let ia = preferredIndex(a.name)
            let ib = preferredIndex(b.name)
            if ia != ib { return ia < ib }
            return a.name.localizedCaseInsensitiveCompare(b.name) == .orderedAscending
        }
        return ranked + adults
    }

    static func defaultCategory(_ cats: [CategoryInfo]) -> String? {
        sortCategories(cats).first { !isAdult($0.name) }?.name
            ?? sortCategories(cats).first?.name
    }

    private static func preferredIndex(_ label: String) -> Int {
        let key = label.lowercased()
        if let i = preferred.firstIndex(where: { key == $0 || key.contains($0) }) {
            return i
        }
        return preferred.count + 1
    }
}
