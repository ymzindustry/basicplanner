package com.ymz.basicplanner

import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Block levels are depths: 0 = block directly in the program, 1 = its sub-blocks, and so on.
 * They are derived from the tree (never from the children, never hand-entered), so any nesting
 * and any mix of weeks/blocks is legal as long as the tree has no loop.
 */
class BlockLevelTest {

    private fun week(id: Long) = WeekEntity(id = id, name = "W$id", owner = null)

    @Test
    fun levels_are_derived_from_the_tree_not_from_children() {
        val cs = CreationState()
        cs.weeks.add(week(1L))
        val leaf = BlockEntity(id = 10L, name = "Leaf", owner = null, weekIds = mutableListOf(1L), level = 9)
        val mid = BlockEntity(id = 11L, name = "Mid", owner = null, subBlockIds = mutableListOf(10L), level = 9)
        val top = BlockEntity(id = 12L, name = "Top", owner = null, subBlockIds = mutableListOf(11L), level = 9)
        cs.blocks.addAll(listOf(leaf, mid, top))
        cs.programs.add(ProgramEntity(id = 30L, name = "P", owner = null, blockIds = mutableListOf(12L)))

        cs.recomputeBlockLevels()

        assertEquals(0, top.level ?: -1)
        assertEquals(1, mid.level ?: -1)
        assertEquals(2, leaf.level ?: -1)
    }

    @Test
    fun a_block_directly_in_the_program_is_level_zero() {
        val cs = CreationState()
        cs.weeks.add(week(1L))
        val block = BlockEntity(id = 10L, name = "Block", owner = null, weekIds = mutableListOf(1L), level = 5)
        cs.blocks.add(block)
        cs.programs.add(ProgramEntity(id = 30L, name = "P", owner = null, blockIds = mutableListOf(10L)))

        cs.recomputeBlockLevels()

        assertEquals(0, block.level ?: -1)
    }

    /** Old builds stored levels bottom-up (weeks-only block = 0, its parent = 1). Loading such a
     *  state must relabel it 0 / 1 instead of showing Lvl 2 over Lvl 1. */
    @Test
    fun stale_bottom_up_levels_are_fixed_on_load() {
        val leaf = BlockEntity(id = 10L, name = "SUB-BLOCK 1", owner = null, weekIds = mutableListOf(1L), level = 0)
        val top = BlockEntity(id = 11L, name = "BLOCK", owner = null, subBlockIds = mutableListOf(10L), level = 1)
        val program = ProgramEntity(id = 30L, name = "MY PROGRAM", owner = null, blockIds = mutableListOf(11L))
        val app = AppState(
            weeks = listOf(week(1L)),
            blocks = listOf(leaf, top),
            programs = listOf(program)
        )

        val cs = CreationState()
        cs.loadFrom(app)

        assertEquals(0, cs.blocks.first { it.id == 11L }.level ?: -1)
        assertEquals(1, cs.blocks.first { it.id == 10L }.level ?: -1)
    }

    @Test
    fun a_block_outside_every_program_has_no_level() {
        val cs = CreationState()
        val loose = BlockEntity(id = 10L, name = "Loose", owner = null, level = 3)
        cs.blocks.add(loose)

        cs.recomputeBlockLevels()

        assertNull(loose.level)
    }

    /** The child picker hides `blockDescendants(self) + self`; it has to cover the whole subtree,
     *  otherwise picking a nested block would close a loop. */
    @Test
    fun descendants_cover_the_whole_subtree() {
        val cs = CreationState()
        val deep = BlockEntity(id = 12L, name = "Deep", owner = null)
        val mid = BlockEntity(id = 11L, name = "Mid", owner = null, subBlockIds = mutableListOf(12L))
        val top = BlockEntity(id = 10L, name = "Top", owner = null, subBlockIds = mutableListOf(11L))
        val other = BlockEntity(id = 13L, name = "Other", owner = null)
        cs.blocks.addAll(listOf(deep, mid, top, other))

        val descendants = cs.blockDescendants(top.id)

        assertEquals(setOf(11L, 12L), descendants)
        assertTrue(other.id !in descendants)
    }

    @Test
    fun a_cyclic_tree_cannot_hang_the_level_walk() {
        val a = BlockEntity(id = 10L, name = "A", owner = null, subBlockIds = mutableListOf(11L))
        val b = BlockEntity(id = 11L, name = "B", owner = null, subBlockIds = mutableListOf(10L))
        val cs = CreationState()
        cs.blocks.addAll(listOf(a, b))
        cs.programs.add(ProgramEntity(id = 30L, name = "P", owner = null, blockIds = mutableListOf(10L)))

        cs.recomputeBlockLevels()

        assertEquals(0, a.level ?: -1)
        assertEquals(1, b.level ?: -1)
    }
}
