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

    /// Orden exacto de las capturas FLUJO (Deportes después de HD+(265)).
    static let preferredLiveOrder: [String] = [
        "Copa Mundial",
        "MLB PASS",
        "NBA PASS",
        "NFL PASS",
        "Eventos PPV",
        "Full HD",
        "HD+(265)",
        "Deportes",
        "Cine y Series",
        "Cultura",
        "Infantil",
        "Noticias",
        "Religioso",
        "Música",
        "Premium Español",
        "Canales 24/7",
        "Cinema Channels",
        "Argentina",
        "Bolivia",
        "Brasil",
        "Canadá",
        "República Dominicana",
        "Chile",
        "Colombia",
        "Centroamérica",
        "Costa Rica",
        "España",
        "Ecuador",
        "El Salvador",
        "Honduras",
        "Panamá",
        "Paraguay",
        "México",
        "Perú",
        "Puerto Rico",
        "Uruguay",
        "US Channels",
        "Venezuela",
        "Italia",
        "Adultos",
    ]

    private static let aliases: [String: String] = [
        "cine premium": "Premium Español",
        "premium espanol": "Premium Español",
        "premium español": "Premium Español",
        "cinema channel": "Cinema Channels",
        "cinema channels": "Cinema Channels",
        "republica dominicana": "República Dominicana",
        "república dominicana": "República Dominicana",
        "canada": "Canadá",
        "canadá": "Canadá",
        "mexico": "México",
        "méxico": "México",
        "peru": "Perú",
        "perú": "Perú",
        "panama": "Panamá",
        "panamá": "Panamá",
        "musica": "Música",
        "música": "Música",
        "espana": "España",
        "españa": "España",
        "centroamerica": "Centroamérica",
        "centroamérica": "Centroamérica",
        "deportes": "Deportes",
        "sports": "Deportes",
        "adulto": "Adultos",
        "adultos": "Adultos",
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

    static func normalize(_ value: String) -> String {
        value.trimmingCharacters(in: .whitespacesAndNewlines).lowercased()
            .replacingOccurrences(of: "á", with: "a")
            .replacingOccurrences(of: "é", with: "e")
            .replacingOccurrences(of: "í", with: "i")
            .replacingOccurrences(of: "ó", with: "o")
            .replacingOccurrences(of: "ú", with: "u")
            .replacingOccurrences(of: "ü", with: "u")
            .replacingOccurrences(of: "ñ", with: "n")
    }

    static func canonicalLabel(_ label: String) -> String {
        let raw = label.trimmingCharacters(in: .whitespacesAndNewlines)
        if raw.isEmpty { return raw }
        let key = normalize(raw)
        if let alias = aliases[key] { return alias }
        if let hit = preferredLiveOrder.first(where: { normalize($0) == key }) { return hit }
        return raw
    }

    static func isPreferredLabel(_ label: String) -> Bool {
        let canon = canonicalLabel(label)
        return preferredLiveOrder.contains { normalize($0) == normalize(canon) }
    }

    static func sortCategories(_ cats: [CategoryInfo]) -> [CategoryInfo] {
        var byCanon: [String: CategoryInfo] = [:]
        for cat in cats {
            let canon = canonicalLabel(cat.name)
            guard isPreferredLabel(canon) else { continue }
            let key = normalize(canon)
            if byCanon[key] == nil {
                byCanon[key] = CategoryInfo(id: canon, name: canon, count: cat.count)
            }
        }
        return preferredLiveOrder.compactMap { byCanon[normalize($0)] }
    }

    static func defaultCategory(_ cats: [CategoryInfo]) -> String? {
        sortCategories(cats).first { !isAdult($0.name) }?.name
            ?? sortCategories(cats).first?.name
    }

    /// Limpia títulos: sin (1080p)/(España)/[tags]/flags; Title Case.
    static func cleanChannelTitle(_ raw: String) -> String {
        var t = raw.trimmingCharacters(in: .whitespacesAndNewlines)
        guard !t.isEmpty else { return t }
        t = t.replacingOccurrences(of: #"\s*\[[^\]]*\]"#, with: "", options: .regularExpression)
        t = t.replacingOccurrences(
            of: #"\s*\((?:\d{3,4}\s*[pPiI]|SD|HD|FHD|UHD|4K|8K|HEVC|H\.?\s*265|H\.?\s*264|HDR)\)"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        )
        t = t.replacingOccurrences(
            of: #"\s*\((?:España|Espana|Spain|México|Mexico|Argentina|Colombia|Chile|Perú|Peru|Brasil|Brazil|Venezuela|Bolivia|Ecuador|Uruguay|Paraguay|Honduras|Guatemala|Nicaragua|Panamá|Panama|Costa Rica|República Dominicana|Republica Dominicana|Puerto Rico|El Salvador|Italia|Italy|Canadá|Canada|USA|US|UK|FR|DE|LAT|LATAM|EUA|EE\.?\s*UU\.?)\)"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        )
        t = t.replacingOccurrences(
            of: #"\s*\((?:Geo-?blocked|Not\s*24/?7|24/?7|Offline|Backup|Alt|Mirror)\)"#,
            with: "",
            options: [.regularExpression, .caseInsensitive]
        )
        t = t.replacingOccurrences(
            of: #"[\u{1F1E6}-\u{1F1FF}]{2}"#,
            with: "",
            options: .regularExpression
        )
        t = t.replacingOccurrences(of: #"\s*[|·]\s*$"#, with: "", options: .regularExpression)
        t = t.replacingOccurrences(of: #"\s{2,}"#, with: " ", options: .regularExpression)
            .trimmingCharacters(in: .whitespacesAndNewlines)
        if t.isEmpty { return raw.trimmingCharacters(in: .whitespacesAndNewlines) }
        return toTitleCase(t)
    }

    private static func toTitleCase(_ value: String) -> String {
        let lowerWords: Set<String> = ["de", "del", "la", "las", "el", "los", "y", "e", "en", "a", "al", "por", "vs", "and", "the", "of"]
        let keepUpper: Set<String> = [
            "hd", "sd", "uhd", "fhd", "hdr", "tv", "nba", "nfl", "mlb", "nhl", "ufc", "ppv",
            "espn", "cnn", "bbc", "fox", "hbo", "amc", "mtv", "tnt", "usa", "uk", "us", "uefa",
            "fifa", "f1", "ok", "fm", "am", "hd+", "4k", "8k"
        ]
        let parts = value.split(whereSeparator: \.isWhitespace).map(String.init)
        return parts.enumerated().map { index, word in
            let lower = word.lowercased()
            let letters = word.filter(\.isLetter)
            if keepUpper.contains(lower) || (letters.count >= 2 && letters.count <= 3 && word == word.uppercased() && !letters.isEmpty) {
                return word.uppercased()
            }
            if index > 0 && lowerWords.contains(lower) { return lower }
            guard let first = lower.first else { return lower }
            return String(first).uppercased() + lower.dropFirst()
        }.joined(separator: " ")
    }
}
