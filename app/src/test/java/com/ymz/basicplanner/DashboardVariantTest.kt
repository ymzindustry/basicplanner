package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.goalSeriesForMovement
import com.ymz.basicplanner.model.plannedVariantsForMovement
import com.ymz.basicplanner.model.sessionBestPerformanceEntries
import com.ymz.basicplanner.model.variantName
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The dashboard can be narrowed to one variant of a movement ("Deadlift Medium" vs "Deadlift
 * Wide"): the filter needs the planned variants of the program and the variant each recorded
 * performance came from.
 */
class DashboardVariantTest {

    private val movementId = 100L
    private val restId = -1L
    private val startDate = LocalDate.of(2026, 8, 3) // Monday

    private class Fixture {
        val medium = ExerciseEntity(
            id = 20L,
            movementId = 100L,
            name = "Medium",
            entries = mutableListOf(SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120, finished = true))
        )
        val wide = ExerciseEntity(
            id = 21L,
            movementId = 100L,
            name = "Wide",
            entries = mutableListOf(SetEntry(sets = 1, reps = 5, intensity = 90, rest = 180, finished = true))
        )
        val session = SessionEntity(
            id = 30L,
            name = "Pull A",
            exerciseIds = mutableListOf(20L, 21L),
            date = LocalDateTime.of(2026, 8, 3, 10, 0)
        )
        val day = DayEntity(id = 1L, name = "Pull Day", timedSessionIds = mutableListOf(10L))
        val week = WeekEntity(
            id = 40L,
            name = "Week 1",
            dayIds = mutableListOf(1L, -1L, -1L, -1L, -1L, -1L, -1L)
        )
        val block = BlockEntity(id = 50L, name = "Block 1", weekIds = mutableListOf(40L))
        val program = ProgramEntity(id = 60L, name = "P", blockIds = mutableListOf(50L))
        val timedSession = TimedSessionEntity(id = 10L, name = "10:00", time = LocalTime.of(10, 0), sessionId = 30L)
        val activeProgram = ActiveProgram(
            id = 70L,
            programId = 60L,
            startDate = LocalDate.of(2026, 8, 3), // Monday
            name = "P active",
            exerciseDailyRms = mapOf(100L to List(28) { 200 })
        )

        fun app() = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            timedSessions = listOf(timedSession),
            sessions = listOf(session),
            exercises = listOf(medium, wide),
            activePrograms = listOf(activeProgram)
        )
    }

    @Test
    fun plannedVariants_are_the_exercise_names_of_the_movement() {
        val f = Fixture()
        assertEquals(listOf("Medium", "Wide"), plannedVariantsForMovement(f.app(), f.activeProgram.id, movementId))
    }

    @Test
    fun goalSeries_narrows_to_the_selected_variant() {
        val f = Fixture()
        val all = goalSeriesForMovement(f.app(), f.activeProgram.id, movementId, reps = 5)
        val medium = goalSeriesForMovement(f.app(), f.activeProgram.id, movementId, reps = 5, variant = "Medium")
        val wide = goalSeriesForMovement(f.app(), f.activeProgram.id, movementId, reps = 5, variant = "Wide")

        assertEquals(180.0, all.first().value, 0.01)   // heaviest variant wins for "all"
        assertEquals(160.0, medium.first().value, 0.01) // ceil(200 × 0.80)
        assertEquals(180.0, wide.first().value, 0.01)   // ceil(200 × 0.90)
    }

    @Test
    fun an_unknown_variant_yields_no_goal_points() {
        val f = Fixture()
        assertTrue(goalSeriesForMovement(f.app(), f.activeProgram.id, movementId, 5, variant = "Nope").isEmpty())
    }

    @Test
    fun recorded_entries_remember_their_variant() {
        val f = Fixture()
        f.session.baseRms = mapOf(movementId to 200)

        val entries = sessionBestPerformanceEntries(
            session = f.session,
            exercises = listOf(f.medium, f.wide),
            activeProgramId = f.activeProgram.id,
            adjustmentPct = 0
        )

        assertEquals(2, entries.size)
        assertEquals(setOf(20L, 21L), entries.mapNotNull { it.exerciseId }.toSet())
        assertEquals(setOf("Medium", "Wide"), entries.map { it.variant }.toSet())
        assertEquals(160.0, entries.first { it.exerciseId == 20L }.weightKg, 0.01)
        assertEquals(180.0, entries.first { it.exerciseId == 21L }.weightKg, 0.01)
    }

    @Test
    fun variantName_falls_back_to_the_recorded_name() {
        val f = Fixture()
        val app = f.app()
        val live = sessionBestPerformanceEntries(f.session.copy(baseRms = mapOf(movementId to 200)), listOf(f.medium), 0L, 0)
            .first()
        assertEquals("Medium", live.variantName(app))
        // Exercise gone: the name captured on the entry is used instead of losing the entry.
        assertEquals("Medium", live.copy(exerciseId = 999L).variantName(app))
    }
}
