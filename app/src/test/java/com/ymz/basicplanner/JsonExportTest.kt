package com.ymz.basicplanner

import com.ymz.basicplanner.data.JsonExporter
import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.Item
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class JsonExportTest {

    @Test
    fun buildExportData_groups_sessions_by_day_with_exercises_sets_items_and_media() {
        val movement = MovementEntity(id = 1L, name = "Barbell Bench Press")
        val exercise = ExerciseEntity(
            id = 2L,
            movementId = 1L,
            name = "Classic",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 90, rest = 120, finished = true)
            )
        )
        val session = SessionEntity(
            id = 3L,
            name = "Push A",
            exerciseIds = mutableListOf(exercise.id),
            date = java.time.LocalDateTime.of(2026, 8, 6, 18, 0),
            baseRms = mapOf(1L to 200)
        )
        val program = ProgramEntity(id = 4L, name = "Demo Program")
        val activeProgram = ActiveProgram(
            id = 5L,
            programId = program.id,
            startDate = LocalDate.of(2026, 8, 3),
            name = "Demo Program Active",
            sessionIds = mutableListOf(session.id)
        )

        val rpeFamily = ItemFamily(id = 6L, name = "RPE", description = "", itemType = ItemType.STRING)
        val mediaFamily = ItemFamily(id = 7L, name = "Media", description = "", itemType = ItemType.IMAGE)
        val entryItem = Item(
            id = 8L,
            activeProgramId = activeProgram.id,
            entityId = exercise.entries.first().id,
            itemFamilyId = rpeFamily.id,
            value = "8"
        )
        val mediaItem = Item(
            id = 9L,
            activeProgramId = activeProgram.id,
            entityId = exercise.id,
            itemFamilyId = mediaFamily.id,
            value = "content://media/external/images/123"
        )

        val app = AppState(
            movements = listOf(movement),
            exercises = listOf(exercise),
            sessions = listOf(session),
            programs = listOf(program),
            activePrograms = listOf(activeProgram),
            itemFamilies = listOf(rpeFamily, mediaFamily),
            items = listOf(entryItem, mediaItem)
        )

        val export = JsonExporter.buildExportData(app, program, activeProgram)

        assertEquals("Demo Program", export.programName)
        assertEquals(1, export.days.size)
        assertEquals("2026-08-06", export.days.first().date)
        assertEquals(1, export.days.first().sessions.size)
        val jsonSession = export.days.first().sessions.first()
        assertEquals("Push A", jsonSession.name)
        assertEquals("18:00", jsonSession.time)
        assertEquals(1, jsonSession.exercises.size)
        assertEquals("Barbell Bench Press", jsonSession.exercises.first().name)
        assertEquals(1, jsonSession.exercises.first().sets.size)
        val jsonSet = jsonSession.exercises.first().sets.first()
        assertTrue(jsonSet.finished)
        assertEquals(180.0, jsonSet.weightKg ?: 0.0, 0.01)
        assertEquals("8", jsonSet.items["RPE"])
        assertEquals(
            "content://media/external/images/123",
            jsonSession.exercises.first().items["Media"]
        )
    }
}
