package com.senal.tv.util

import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category

/** Shared live-catalog ordering / adult demotion. */
object CatalogRules {

    private val adultRegex = Regex(
        pattern = """(?i)(\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw|hot\s*xxx|onlyfans|playboy""",
    )

    private val preferredLiveOrder = listOf(
        "deportes",
        "sports",
        "noticias",
        "news",
        "cultura",
        "documentales",
        "series 24/7",
        "series",
        "películas",
        "peliculas",
        "cine",
        "infantil",
        "kids",
        "música",
        "musica",
        "variados",
        "latino",
        "latinos",
        "españa",
        "espana",
        "mexico",
        "méxico",
        "estados unidos",
        "usa",
        "eeuu",
        "4k",
        "general",
    )

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

    fun sortCategories(categories: List<Category>): List<Category> {
        if (categories.isEmpty()) return categories
        val seen = LinkedHashSet<String>()
        val unique = categories.mapNotNull { cat ->
            val label = cat.label().trim()
            if (label.isEmpty() || !seen.add(label.lowercase())) null else cat
        }
        val adults = unique.filter { isAdultLabel(it.label()) }
        val normal = unique.filterNot { isAdultLabel(it.label()) }
        val ranked = normal.sortedWith(
            compareBy<Category> { preferredIndex(it.label()) }
                .thenBy { it.label().lowercase() }
        )
        return ranked + adults
    }

    fun defaultCategory(categories: List<Category>): String? =
        sortCategories(categories).firstOrNull { !isAdultLabel(it.label()) }?.label()
            ?: sortCategories(categories).firstOrNull()?.label()

    fun preferredLiveItems(items: List<CatalogItem>): List<CatalogItem> =
        items.filterNot { isAdultItem(it) }.ifEmpty { items }

    private fun preferredIndex(label: String): Int {
        val key = label.trim().lowercase()
        val hit = preferredLiveOrder.indexOfFirst { key == it || key.contains(it) }
        return if (hit >= 0) hit else preferredLiveOrder.size + 1
    }
}
