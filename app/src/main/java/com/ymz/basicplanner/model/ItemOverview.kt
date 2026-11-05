package com.ymz.basicplanner.model

/** How the item bubbles are ordered in the dialog. */
enum class ItemSort { ALPHABETICAL, BY_TYPE }

/** What the item dialog shows: what is already attached to this entity, and what could be. */
data class ItemOverview(
    val attached: List<Pair<ItemFamily, Item>>,
    val available: List<ItemFamily>
)

/**
 * Splits the item families into "already attached to this entity" (with the item holding the
 * value) and "still free", optionally narrowed to one [filter] type and ordered either
 * alphabetically or grouped by type (STRING first, following the enum order).
 */
fun buildItemOverview(
    items: List<Item>,
    families: List<ItemFamily>,
    filter: ItemType? = null,
    sort: ItemSort = ItemSort.ALPHABETICAL
): ItemOverview {
    val familiesById = families.associateBy { it.id }
    val attached = items
        .mapNotNull { item -> familiesById[item.itemFamilyId]?.let { family -> family to item } }
        .filter { (family, _) -> filter == null || family.itemType == filter }
    val attachedFamilyIds = items.map { it.itemFamilyId }.toSet()
    val available = families
        .filter { it.id !in attachedFamilyIds }
        .filter { filter == null || it.itemType == filter }

    fun <T> order(list: List<T>, typeOf: (T) -> ItemType, nameOf: (T) -> String): List<T> =
        when (sort) {
            ItemSort.ALPHABETICAL -> list.sortedBy { nameOf(it).lowercase() }
            ItemSort.BY_TYPE -> list.sortedWith(
                compareBy({ ItemType.entries.indexOf(typeOf(it)) }, { nameOf(it).lowercase() })
            )
        }

    return ItemOverview(
        attached = order(attached, { it.first.itemType }, { it.first.name }),
        available = order(available, { it.itemType }, { it.name })
    )
}
