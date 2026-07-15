package com.senal.tv.util

import com.senal.tv.data.model.CatalogItem
import com.senal.tv.data.model.Category

/**
 * Live-catalog helpers. Category order follows the M3U appearance order;
 * only adult groups are moved to the end.
 */
object CatalogRules {

    private val adultRegex = Regex(
        pattern = """(?i)(\+| )?18\+?|adult|adulto|adultos|xxx|porn|porno|erotic|erotica|nsfw|hot\s*xxx|onlyfans|playboy""",
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

    /** Keep M3U order; demote adult categories to the end. */
    fun sortCategories(categories: List<Category>): List<Category> {
        if (categories.isEmpty()) return categories
        val seen = LinkedHashSet<String>()
        val unique = categories.mapNotNull { cat ->
            val label = cat.label().trim()
            if (label.isEmpty() || !seen.add(label.lowercase())) null else cat
        }
        val adults = unique.filter { isAdultLabel(it.label()) }
        val normal = unique.filterNot { isAdultLabel(it.label()) }
        return normal + adults
    }

    fun defaultCategory(categories: List<Category>): String? =
        sortCategories(categories).firstOrNull { !isAdultLabel(it.label()) }?.label()
            ?: sortCategories(categories).firstOrNull()?.label()

    fun preferredLiveItems(items: List<CatalogItem>): List<CatalogItem> =
        items.filterNot { isAdultItem(it) }.ifEmpty { items }
}
