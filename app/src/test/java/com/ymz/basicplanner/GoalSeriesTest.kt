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
import com.ymz.basicplanner.model.plannedRepsForMovement
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class GoalSeriesTest {

    @Test
    fun goalSeries_uses_planned_weight_per_reps_for_each_program_day() {
        val restId = -1L
        val movementId = 100L

        val day = DayEntity(id = 1L, name = "Push Day", owner = null, timedSessionIds = mutableListOf(10L))
        val exercise = ExerciseEntity(
            id = 20L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120),
                SetEntry(sets = 1, reps = 5, intensity = 90, rest = 180)
            )
        )
        val session = SessionEntity(id = 30L, name = "Push A", exerciseIds = mutableListOf(exercise.id))
        val week = WeekEntity(id = 40L, name = "Week 1", owner = null, dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId))
        val block = BlockEntity(id = 50L, name = "Block 1", owner = null, weekIds = mutableListOf(week.id))
        val program = ProgramEntity(id = 60L, name = "Demo", owner = null, blockIds = mutableListOf(block.id))
        val timedSession = TimedSessionEntity(id = 10L, name = "10:00", time = LocalTime.of(10, 0), sessionId = session.id)

        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(
            id = 70L,
            programId = program.id,
            startDate = startDate,
            name = "Demo Active",
            exerciseDailyRms = mapOf(movementId to mutableListOf(200, 210, 220, 230, 240, 250, 260, 270))
        )

        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            timedSessions = listOf(timedSession),
            sessions = listOf(session),
            exercises = listOf(exercise),
            activePrograms = listOf(activeProgram)
        )

        val series = goalSeriesForMovement(app, activeProgram.id, movementId, reps = 5)

        assertEquals(1, series.size)
        assertEquals(startDate, series.first().date)
        assertEquals(180.0, series.first().value, 0.01) // ceil(200 * 0.90)
    }

    @Test
    fun goalSeries_applies_adjustment_percent() {
        val restId = -1L
        val movementId = 100L

        val day = DayEntity(id = 1L, name = "Push Day", owner = null, timedSessionIds = mutableListOf(10L))
        val exercise = ExerciseEntity(
            id = 20L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120),
                SetEntry(sets = 1, reps = 5, intensity = 90, rest = 180)
            )
        )
        val session = SessionEntity(id = 30L, name = "Push A", exerciseIds = mutableListOf(exercise.id))
        val week = WeekEntity(id = 40L, name = "Week 1", owner = null, dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId))
        val block = BlockEntity(id = 50L, name = "Block 1", owner = null, weekIds = mutableListOf(week.id))
        val program = ProgramEntity(id = 60L, name = "Demo", owner = null, blockIds = mutableListOf(block.id), adjustmentPercent = 10)
        val timedSession = TimedSessionEntity(id = 10L, name = "10:00", time = LocalTime.of(10, 0), sessionId = session.id)

        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(
            id = 70L,
            programId = program.id,
            startDate = startDate,
            name = "Demo Active",
            exerciseDailyRms = mapOf(movementId to mutableListOf(200, 210, 220, 230, 240, 250, 260, 270))
        )

        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            timedSessions = listOf(timedSession),
            sessions = listOf(session),
            exercises = listOf(exercise),
            activePrograms = listOf(activeProgram)
        )

        val series = goalSeriesForMovement(app, activeProgram.id, movementId, reps = 5)

        assertEquals(1, series.size)
        assertEquals(198.0, series.first().value, 0.01) // ceil(scaledBaseRm(200, +10%) * 0.90) = ceil(220 * 0.9)
    }

    /** End-to-end regression for the dashboard: four identical weeks inside one block, only the
     *  block's per-week chip differs (-5%, -10%, -15%). The goal curve must step down, not plateau. */
    @Test
    fun goalSeries_reflects_block_subAdjustments_week_by_week() {
        val restId = -1L
        val movementId = 100L

        val day = DayEntity(id = 1L, name = "Push Day", owner = null, timedSessionIds = mutableListOf(10L))
        val exercise = ExerciseEntity(
            id = 20L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(SetEntry(sets = 1, reps = 5, intensity = 90, rest = 180))
        )
        val session = SessionEntity(id = 30L, name = "Push A", exerciseIds = mutableListOf(exercise.id))
        val timedSession = TimedSessionEntity(id = 10L, name = "10:00", time = LocalTime.of(10, 0), sessionId = session.id)

        fun week(id: Long, name: String) = WeekEntity(
            id = id,
            name = name,
            owner = null,
            dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId)
        )
        val weeks = listOf(week(40L, "Week 1"), week(41L, "Week 2"), week(42L, "Week 3"), week(43L, "Week 4"))
        val block = BlockEntity(
            id = 50L,
            name = "Block 1",
            owner = null,
            weekIds = weeks.map { it.id }.toMutableList(),
            subAdjustments = mutableListOf(0, -5, -10, -15)
        )
        val program = ProgramEntity(id = 60L, name = "Demo", owner = null, blockIds = mutableListOf(block.id))

        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(
            id = 70L,
            programId = program.id,
            startDate = startDate,
            name = "Demo Active",
            exerciseDailyRms = mapOf(movementId to List(28) { 200 })
        )

        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = weeks,
            days = listOf(day),
            timedSessions = listOf(timedSession),
            sessions = listOf(session),
            exercises = listOf(exercise),
            activePrograms = listOf(activeProgram)
        )

        val values = goalSeriesForMovement(app, activeProgram.id, movementId, reps = 5).map { it.value }

        assertEquals(listOf(180.0, 171.0, 162.0, 153.0), values) // ceil(200 × (1+p) × 0.90)
    }

    @Test
    fun plannedReps_returns_distinct_reps_of_movement() {
        val restId = -1L
        val movementId = 100L

        val day = DayEntity(id = 1L, name = "Push Day", owner = null, timedSessionIds = mutableListOf(10L))
        val exercise = ExerciseEntity(
            id = 20L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120),
                SetEntry(sets = 1, reps = 10, intensity = 70, rest = 120)
            )
        )
        val session = SessionEntity(id = 30L, name = "Push A", exerciseIds = mutableListOf(exercise.id))
        val week = WeekEntity(id = 40L, name = "Week 1", owner = null, dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId))
        val block = BlockEntity(id = 50L, name = "Block 1", owner = null, weekIds = mutableListOf(week.id))
        val program = ProgramEntity(id = 60L, name = "Demo", owner = null, blockIds = mutableListOf(block.id))
        val timedSession = TimedSessionEntity(id = 10L, name = "10:00", time = LocalTime.of(10, 0), sessionId = session.id)

        val startDate = LocalDate.of(2026, 8, 3)
        val activeProgram = ActiveProgram(
            id = 70L,
            programId = program.id,
            startDate = startDate,
            name = "Demo Active"
        )

        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            timedSessions = listOf(timedSession),
            sessions = listOf(session),
            exercises = listOf(exercise),
            activePrograms = listOf(activeProgram)
        )

        assertEquals(listOf(5, 10), plannedRepsForMovement(app, activeProgram.id, movementId))
    }
}
