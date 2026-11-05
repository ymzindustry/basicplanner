package com.ymz.basicplanner

import com.ymz.basicplanner.model.Item
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemSort
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.buildItemOverview
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/** The item dialog shows attached items and free ones in two separate sections. */
class ItemOverviewTest {

    private val rpe = ItemFamily(id = 1L, name = "RPE", description = "", itemType = ItemType.INT)
    private val note = ItemFamily(id = 2L, name = "Note", description = "", itemType = ItemType.STRING)
    private val video = ItemFamily(id = 3L, name = "Technique", description = "", itemType = ItemType.VIDEO)

    private fun item(id: Long, familyId: Long, value: String) = Item(
        id = id,
        activeProgramId = 1L,
        entityId = 10L,
        itemFamilyId = familyId,
        value = value
    )

    @Test
    fun attached_items_are_paired_with_their_family_and_the_rest_stays_available() {
        val overview = buildItemOverview(
            items = listOf(item(100L, rpe.id, "8")),
            families = listOf(rpe, note, video)
        )

        assertEquals(1, overview.attached.size)
        assertEquals("RPE", overview.attached.first().first.name)
        assertEquals("8", overview.attached.first().second.value)
        assertEquals(listOf("Note", "Technique"), overview.available.map { it.name })
    }

    @Test
    fun the_type_filter_narrows_both_sections() {
        val overview = buildItemOverview(
            items = listOf(item(100L, rpe.id, "8")),
            families = listOf(rpe, note, video),
            filter = ItemType.STRING
        )

        assertTrue(overview.attached.isEmpty())
        assertEquals(listOf("Note"), overview.available.map { it.name })
    }

    @Test
    fun items_of_unknown_families_are_ignored() {
        val overview = buildItemOverview(
            items = listOf(item(100L, 99L, "orphan")),
            families = listOf(rpe)
        )

        assertTrue(overview.attached.isEmpty())
        assertEquals(listOf("RPE"), overview.available.map { it.name })
    }

    @Test
    fun everything_attached_leaves_nothing_available() {
        val overview = buildItemOverview(
            items = listOf(item(100L, rpe.id, "8"), item(101L, note.id, "felt heavy")),
            families = listOf(rpe, note)
        )

        assertEquals(2, overview.attached.size)
        assertTrue(overview.available.isEmpty())
    }

    @Test
    fun alphabetical_sort_orders_both_sections_by_name() {
        val overview = buildItemOverview(
            items = listOf(item(100L, rpe.id, "8")),
            families = listOf(video, rpe, note)
        )

        assertEquals("Note", overview.available.first().name)
        assertEquals(listOf("Note", "Technique"), overview.available.map { it.name })
    }

    @Test
    fun type_sort_puts_every_type_together_starting_with_string() {
        val more = ItemFamily(id = 4L, name = "Load", description = "", itemType = ItemType.FLOAT)
        val overview = buildItemOverview(
            items = listOf(item(100L, note.id, "felt heavy"), item(101L, rpe.id, "8")),
            families = listOf(rpe, note, video, more),
            sort = ItemSort.BY_TYPE
        )

        // STRING → INT → FLOAT → VIDEO
        assertEquals(listOf("Note", "RPE", "Load", "Technique"), overview.attached.map { it.first.name } + overview.available.map { it.name })
        assertEquals("Note", overview.attached.first().first.name)
    }
}
