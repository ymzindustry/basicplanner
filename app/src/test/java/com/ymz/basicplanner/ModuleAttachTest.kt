package com.ymz.basicplanner

import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.ui.entitiesInSubtree
import com.ymz.basicplanner.ui.entitiesOfProgram
import com.ymz.basicplanner.ui.parentOf
import com.ymz.basicplanner.ui.programOf
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalTime

/**
 * The attach flow needs three answers from the tree: which program an entity belongs to, what sits
 * directly above it, and which entities that program offers as an input.
 */
class ModuleAttachTest {

    private fun state(): CreationState {
        val creationState = CreationState()
        creationState.sessions.add(SessionEntity(id = 1L, name = "Push A", exerciseIds = mutableListOf()))
        creationState.timedSessions.add(TimedSessionEntity(id = 2L, name = "10:00", time = LocalTime.of(10, 0), sessionId = 1L))
        creationState.days.add(DayEntity(id = 3L, name = "D1", timedSessionIds = mutableListOf(2L)))
        creationState.weeks.add(WeekEntity(id = 4L, name = "W1", dayIds = mutableListOf(3L)))
        creationState.blocks.add(BlockEntity(id = 5L, name = "Block A", weekIds = mutableListOf(4L)))
        creationState.programs.add(ProgramEntity(id = 6L, name = "P1", blockIds = mutableListOf(5L)))
        return creationState
    }

    @Test
    fun an_entity_finds_its_program_by_walking_up() {
        val creationState = state()
        val day = creationState.days.first()
        assertEquals("P1", programOf(creationState, day)?.name)
        assertEquals("P1", programOf(creationState, creationState.weeks.first())?.name)
        assertEquals("P1", programOf(creationState, creationState.programs.first())?.name)
    }

    @Test
    fun the_parent_is_the_entity_above() {
        val creationState = state()
        assertEquals("W1", parentOf(creationState, creationState.days.first())?.name)
        assertEquals("Block A", parentOf(creationState, creationState.weeks.first())?.name)
        assertEquals("P1", parentOf(creationState, creationState.blocks.first())?.name)
        assertEquals("D1", parentOf(creationState, creationState.sessions.first())?.name)
    }

    @Test
    fun the_input_scope_is_the_section_root_subtree() {
        val creationState = state()
        // A second block, outside the first one, must never show up in its subtree
        creationState.weeks.add(WeekEntity(id = 7L, name = "W9"))
        creationState.blocks.add(BlockEntity(id = 8L, name = "Block B", weekIds = mutableListOf(7L)))
        creationState.programs.first().blockIds.add(8L)

        val blockA = creationState.blocks.first { it.id == 5L }
        val labels = entitiesInSubtree(creationState, blockA).map { it.third }

        assertTrue("Block A" in labels)
        assertTrue("W1" in labels)
        assertTrue("D1" in labels)
        assertTrue("Push A" in labels)
        // Not the program above it, not a sibling block: the scope stops at the block
        assertTrue("P1" !in labels)
        assertTrue("Block B" !in labels)
        assertTrue("W9" !in labels)
    }

    @Test
    fun a_program_lists_every_entity_it_contains() {
        val creationState = state()
        val program = creationState.programs.first()
        val labels = entitiesOfProgram(creationState, program).map { it.third }

        assertTrue("P1" in labels)
        assertTrue("Block A" in labels)
        assertTrue("W1" in labels)
        assertTrue("D1" in labels)
        assertTrue("Push A" in labels)
    }
}
