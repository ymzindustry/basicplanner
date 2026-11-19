package com.ymz.basicplanner

import com.ymz.basicplanner.compose.buildProgramSections
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import java.time.LocalDate

/**
 * The agenda labels a week with its week and the block at the chosen level. Blocks may contain
 * blocks, so the section walk has to be recursive — a flat `program.blockIds` walk lost every week
 * that lives under a sub-block.
 */
class AgendaSectionsTest {

    private fun state(): CreationState {
        val creationState = CreationState()

        // week 1 and 2 directly in block A, week 3 in sub-block B (nested in A)
        creationState.weeks.add(WeekEntity(id = 1L, name = "W1"))
        creationState.weeks.add(WeekEntity(id = 2L, name = "W2"))
        creationState.weeks.add(WeekEntity(id = 3L, name = "W3"))

        creationState.blocks.add(
            BlockEntity(id = 10L, name = "Block A", weekIds = mutableListOf(1L, 2L), subBlockIds = mutableListOf(11L))
        )
        creationState.blocks.add(
            BlockEntity(id = 11L, name = "Sub B", weekIds = mutableListOf(3L))
        )
        creationState.programs.add(
            ProgramEntity(id = 20L, name = "P1", blockIds = mutableListOf(10L))
        )
        return creationState
    }

    @Test
    fun weeks_are_labelled_through_nested_blocks() {
        val creationState = state()
        val program = creationState.programs.first()
        val start = LocalDate.of(2026, 11, 2) // a Monday

        val sections = buildProgramSections(program, start, creationState)

        // Same order as the calendar: a block's sub-blocks first, then its own weeks.
        assertEquals("W3", sections[start]?.week?.name)
        assertEquals("Block A", sections[start]?.blockChain?.first()?.name)
        assertEquals("Sub B", sections[start]?.blockChain?.getOrNull(1)?.name)
        // Then Block A's own weeks — they used to disappear when weeks lived under a sub-block.
        assertEquals("W1", sections[start.plusDays(7)]?.week?.name)
        assertEquals("W2", sections[start.plusDays(14)]?.week?.name)
        assertEquals(listOf("Block A"), sections[start.plusDays(14)]?.blockChain?.map { it.name })
    }

    @Test
    fun level_zero_is_the_outer_block_for_every_week() {
        val creationState = state()
        val program = creationState.programs.first()
        val start = LocalDate.of(2026, 11, 2)

        val sections = buildProgramSections(program, start, creationState)
        val levels = (0 until 21).mapNotNull { day ->
            sections[start.plusDays(day.toLong())]?.blockChain?.firstOrNull()?.name
        }.distinct()

        assertEquals(listOf("Block A"), levels)
        assertNotNull(sections[start.plusDays(20)])
    }

    @Test
    fun a_program_without_blocks_still_yields_week_sections() {
        val creationState = CreationState()
        creationState.weeks.add(WeekEntity(id = 1L, name = "W1"))
        creationState.programs.add(ProgramEntity(id = 30L, name = "Direct", blockIds = mutableListOf(1L)))

        val sections = buildProgramSections(
            creationState.programs.first(),
            LocalDate.of(2026, 11, 2),
            creationState
        )

        assertEquals("W1", sections[LocalDate.of(2026, 11, 2)]?.week?.name)
        assertEquals(emptyList<BlockEntity>(), sections[LocalDate.of(2026, 11, 2)]?.blockChain)
    }
}
