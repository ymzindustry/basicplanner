package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.plannedRepsForMovement
import com.ymz.basicplanner.model.programDateRange
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate

/** The dashboard x axis spans the whole program: first day on the left, last day on the right. */
class ChartRangeTest {

    private fun week(id: Long) = WeekEntity(id = id, name = "W$id", owner = null)

    @Test
    fun range_covers_every_week_of_every_block() {
        val weeks = listOf(week(1L), week(2L), week(3L), week(4L))
        val b1 = BlockEntity(id = 10L, name = "B1", owner = null, weekIds = mutableListOf(1L, 2L))
        val b2 = BlockEntity(id = 11L, name = "B2", owner = null, weekIds = mutableListOf(3L, 4L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L, 11L))
        val start = LocalDate.of(2026, 8, 3) // Monday
        val app = AppState(
            weeks = weeks,
            blocks = listOf(b1, b2),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val range = programDateRange(app, 30L)

        assertEquals(start, range?.first)
        assertEquals(start.plusDays(27), range?.second) // 4 weeks × 7 days
    }

    @Test
    fun range_starts_on_the_activation_weekday_and_ends_on_the_last_day() {
        val week = week(1L)
        val block = BlockEntity(id = 10L, name = "B1", owner = null, weekIds = mutableListOf(1L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L))
        val start = LocalDate.of(2026, 8, 5) // Wednesday → the first week only runs Wed..Sun
        val app = AppState(
            weeks = listOf(week),
            blocks = listOf(block),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val range = programDateRange(app, 30L)

        assertEquals(start, range?.first)
        assertEquals(start.plusDays(4), range?.second) // 5 days: Wed..Sun
    }

    @Test
    fun range_is_null_without_an_active_program() {
        assertNull(programDateRange(AppState(), 99L))
    }

    @Test
    fun planned_reps_follow_the_variant() {
        // Two variants of the same movement with different rep schemes: filtering by variant must
        // only offer that variant's reps, so the slider never lands on an empty stop.
        val medium = com.ymz.basicplanner.model.ExerciseEntity(
            id = 1L, movementId = 5L, name = "Medium",
            entries = mutableListOf(
                com.ymz.basicplanner.model.SetEntry(sets = 1, reps = 1, intensity = 80, rest = 60),
                com.ymz.basicplanner.model.SetEntry(sets = 1, reps = 4, intensity = 75, rest = 60)
            )
        )
        val wide = com.ymz.basicplanner.model.ExerciseEntity(
            id = 2L, movementId = 5L, name = "Wide",
            entries = mutableListOf(
                com.ymz.basicplanner.model.SetEntry(sets = 1, reps = 3, intensity = 70, rest = 60)
            )
        )
        val session = com.ymz.basicplanner.model.SessionEntity(
            id = 3L, name = "S", exerciseIds = mutableListOf(1L, 2L)
        )
        val day = com.ymz.basicplanner.model.DayEntity(
            id = 4L, name = "Day", timedSessionIds = mutableListOf(6L)
        )
        val week = WeekEntity(id = 7L, name = "W", owner = null, dayIds = mutableListOf(4L, -1L, -1L, -1L, -1L, -1L, -1L))
        val block = BlockEntity(id = 8L, name = "B", owner = null, weekIds = mutableListOf(7L))
        val program = ProgramEntity(id = 9L, name = "P", owner = null, blockIds = mutableListOf(8L))
        val app = AppState(
            exercises = listOf(medium, wide),
            sessions = listOf(session),
            timedSessions = listOf(
                com.ymz.basicplanner.model.TimedSessionEntity(
                    id = 6L, name = "10:00", time = java.time.LocalTime.of(10, 0), sessionId = 3L
                )
            ),
            days = listOf(day),
            weeks = listOf(week),
            blocks = listOf(block),
            programs = listOf(program),
            activePrograms = listOf(
                ActiveProgram(id = 10L, programId = 9L, startDate = LocalDate.of(2026, 8, 3), name = "P active")
            )
        )

        assertEquals(listOf(1, 3, 4), plannedRepsForMovement(app, 10L, 5L))
        assertEquals(listOf(1, 4), plannedRepsForMovement(app, 10L, 5L, variant = "Medium"))
        assertEquals(listOf(3), plannedRepsForMovement(app, 10L, 5L, variant = "Wide"))
    }
}
