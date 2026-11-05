package com.ymz.basicplanner

import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ProgramEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * A loop in the block tree makes every walk — including expanding the tree in the UI — recurse
 * forever, which crashes the app. The tree must therefore be loop-free both when saved and when
 * loaded.
 */
class BlockCycleTest {

    private fun block(id: Long, name: String, subBlocks: List<Long> = emptyList()) =
        BlockEntity(id = id, name = name, owner = null, subBlockIds = subBlocks.toMutableList())

    @Test
    fun a_block_cannot_be_given_one_of_its_ancestors_as_child() {
        val cs = CreationState()
        val a = block(10L, "A", listOf(11L))
        val b = block(11L, "B")
        cs.blocks.addAll(listOf(a, b))
        cs.programs.add(ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L)))

        cs.updateEntity(EntityKind.BLOCK, b, "B", listOf(10L))

        val savedB = cs.blocks.first { it.id == 11L }
        assertFalse("B must not contain its ancestor A", 10L in savedB.subBlockIds)
        assertEquals(setOf(11L), cs.blockDescendants(10L))
        assertTrue(cs.blockAncestors(11L).contains(10L))
    }

    @Test
    fun a_block_cannot_contain_itself() {
        val cs = CreationState()
        val a = block(10L, "A")
        cs.blocks.add(a)
        cs.programs.add(ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L)))

        cs.updateEntity(EntityKind.BLOCK, a, "A", listOf(10L))

        assertTrue(cs.blocks.first { it.id == 10L }.subBlockIds.isEmpty())
    }

    @Test
    fun loading_repairs_an_existing_loop_instead_of_hanging() {
        val a = block(10L, "A", listOf(11L))
        val b = block(11L, "B", listOf(10L))
        val app = AppState(
            blocks = listOf(a, b),
            programs = listOf(ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L)))
        )

        val cs = CreationState()
        cs.loadFrom(app)

        // One of the two links is dropped: A ⊃ B stays, B ⊃ A goes.
        assertTrue(11L in cs.blocks.first { it.id == 10L }.subBlockIds)
        assertTrue(10L !in cs.blocks.first { it.id == 11L }.subBlockIds)
        assertTrue(cs.blockAncestors(10L).isEmpty())
    }

    @Test
    fun ancestors_are_transitive() {
        val cs = CreationState()
        cs.blocks.addAll(
            listOf(
                block(10L, "Top", listOf(11L)),
                block(11L, "Mid", listOf(12L)),
                block(12L, "Leaf")
            )
        )

        assertEquals(setOf(11L, 10L), cs.blockAncestors(12L))
        assertEquals(setOf(10L), cs.blockAncestors(11L))
    }
}
