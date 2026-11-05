package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.Item
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.resolveDatedItems
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class ItemDashboardTest {

    @Test
    fun resolveDatedItems_maps_items_to_their_session_date() {
        val rpeFamily = ItemFamily(id = 1L, name = "RPE", description = "", itemType = ItemType.INT)
        val noteFamily = ItemFamily(id = 2L, name = "Note", description = "", itemType = ItemType.STRING)

        val exercise = ExerciseEntity(
            id = 10L,
            movementId = 100L,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120, finished = true)
            )
        )
        val session = SessionEntity(
            id = 20L,
            name = "Push A",
            exerciseIds = mutableListOf(exercise.id),
            date = java.time.LocalDateTime.of(2026, 8, 6, 18, 0)
        )
        val activeProgram = ActiveProgram(
            id = 30L,
            programId = 40L,
            startDate = LocalDate.of(2026, 8, 1),
            name = "Demo",
            sessionIds = mutableListOf(session.id)
        )

        val rpeOnSet = Item(
            id = 1L,
            activeProgramId = activeProgram.id,
            entityId = exercise.entries.first().id,
            itemFamilyId = rpeFamily.id,
            value = "8"
        )
        val noteOnSession = Item(
            id = 2L,
            activeProgramId = activeProgram.id,
            entityId = session.id,
            itemFamilyId = noteFamily.id,
            value = "Great session"
        )

        val app = AppState(
            exercises = listOf(exercise),
            sessions = listOf(session),
            activePrograms = listOf(activeProgram),
            itemFamilies = listOf(rpeFamily, noteFamily),
            items = listOf(rpeOnSet, noteOnSession)
        )

        val dated = resolveDatedItems(app, activeProgram.id)

        assertEquals(LocalDate.of(2026, 8, 6), dated[rpeFamily.id]!!.first().date)
        assertEquals("8", dated[rpeFamily.id]!!.first().value)
        assertEquals(LocalDate.of(2026, 8, 6), dated[noteFamily.id]!!.first().date)
        assertEquals("Great session", dated[noteFamily.id]!!.first().value)
    }
}
