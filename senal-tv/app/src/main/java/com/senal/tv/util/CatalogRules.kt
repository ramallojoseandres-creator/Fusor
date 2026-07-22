package com.senal.tv.util

import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category

/**
 * Live category order from the user's FLUJO reference screenshots.
 * Preferred groups only; Adultos is included last.
 */
object CatalogRules {

    private val adultRegex = Regex(
        pattern = """(?i)(\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw|hot\s*xxx|onlyfans|playboy""",
    )

    /**
     * Exact sidebar order the user wants (screenshot scroll top → bottom).
     * Adultos stays at the end so it never opens first.
     */
    val preferredLiveOrder: List<String> = listOf(
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
    )

    /** Playlist / API aliases → preferred display name. */
    private val aliases: Map<String, String> = mapOf(
        "cine premium" to "Premium Español",
        "premium espanol" to "Premium Español",
        "premium español" to "Premium Español",
        "cinema channel" to "Cinema Channels",
        "cinema channels" to "Cinema Channels",
        "republica dominicana" to "República Dominicana",
        "república dominicana" to "República Dominicana",
        "rep. dominicana" to "República Dominicana",
        "canada" to "Canadá",
        "canadá" to "Canadá",
        "mexico" to "México",
        "méxico" to "México",
        "peru" to "Perú",
        "perú" to "Perú",
        "panama" to "Panamá",
        "panamá" to "Panamá",
        "musica" to "Música",
        "música" to "Música",
        "espana" to "España",
        "españa" to "España",
        "centroamerica" to "Centroamérica",
        "centroamérica" to "Centroamérica",
        "hd+(265)" to "HD+(265)",
        "hd+ (265)" to "HD+(265)",
        "full hd" to "Full HD",
        "copa mundial" to "Copa Mundial",
        "adulto" to "Adultos",
        "adultos" to "Adultos",
        "adultos +18" to "Adultos",
        "adultos+18" to "Adultos",
        "adult" to "Adultos",
        "xxx" to "Adultos",
    )

    private val preferredIndex: Map<String, Int> =
        preferredLiveOrder.mapIndexed { i, name -> normalize(name) to i }.toMap()

    fun isAdultLabel(label: String?): Boolean {
        val value = label?.trim().orEmpty()
        if (value.isEmpty()) return false
        return adultRegex.containsMatchIn(value)
    }

    fun isAdultItem(item: CatalogItem): Boolean =
        isAdultLabel(item.resolveCategory()) ||
            isAdultLabel(item.resolveTitle()) ||
            isAdultLabel(item.group) ||
            isAdultLabel(item.category)

    fun canonicalLabel(label: String): String {
        val raw = label.trim()
        if (raw.isEmpty()) return raw
        aliases[normalize(raw)]?.let { return it }
        preferredLiveOrder.firstOrNull { normalize(it) == normalize(raw) }?.let { return it }
        return raw
    }

    fun isPreferredLabel(label: String?): Boolean {
        val canon = canonicalLabel(label.orEmpty())
        return preferredIndex.containsKey(normalize(canon))
    }

    fun sortCategories(categories: List<Category>): List<Category> {
        if (categories.isEmpty()) return categories
        val byCanon = LinkedHashMap<String, Category>()
        for (cat in categories) {
            val canon = canonicalLabel(cat.label())
            if (canon.isBlank()) continue
            if (!isPreferredLabel(canon)) continue
            byCanon.putIfAbsent(normalize(canon), Category(id = canon, name = canon, title = canon))
        }
        return preferredLiveOrder.mapNotNull { name ->
            byCanon[normalize(name)]
        }
    }

    /** Open first non-adult preferred category (Adultos stays last). */
    fun defaultCategory(categories: List<Category>): String? =
        sortCategories(categories).firstOrNull { !isAdultLabel(it.label()) }?.label()
            ?: sortCategories(categories).firstOrNull()?.label()

    fun preferredLiveItems(items: List<CatalogItem>): List<CatalogItem> =
        items.filter { isPreferredLabel(it.resolveCategory()) && !isAdultItem(it) }
            .ifEmpty { items.filterNot { isAdultItem(it) }.ifEmpty { items } }

    private fun normalize(value: String): String =
        value.trim().lowercase()
            .replace('á', 'a').replace('é', 'e').replace('í', 'i')
            .replace('ó', 'o').replace('ú', 'u').replace('ü', 'u')
            .replace('ñ', 'n')
}
