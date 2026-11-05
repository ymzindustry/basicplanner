package com.ymz.basicplanner

import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.InterpolationStrategy
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.RmSettings
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Editing an exercise's movement must be reflected everywhere the exercise is used:
 * the session/program tree, the resolved display name and the active program RM maps.
 */
class ExerciseMovementChangeTest {

    private class Fixture {
        val cs = CreationState()
        val squat = MovementEntity(id = 1L, name = "Squat")
        val bench = MovementEntity(id = 2L, name = "Bench")
        lateinit var exercise: ExerciseEntity
        lateinit var program: ProgramEntity

        init {
            cs.movements.addAll(listOf(squat, bench))

            exercise = ExerciseEntity(id = 10L, movementId = squat.id, name = "Hard")
            exercise.entries.add(SetEntry(id = 11L, sets = 1, reps = 5, intensity = 80, rest = 120))
            cs.exercises.add(exercise)

            val session = SessionEntity(id = 20L, name = "Legs", exerciseIds = mutableListOf(exercise.id))
            cs.sessions.add(session)

            val timed = TimedSessionEntity(id = 30L, name = "Legs", time = LocalTime.of(18, 0), sessionId = session.id)
            cs.timedSessions.add(timed)

            val day = DayEntity(id = 40L, name = "Day", timedSessionIds = mutableListOf(timed.id))
            cs.days.add(day)

            val week = WeekEntity(id = 50L, name = "W1", dayIds = mutableListOf(day.id))
            cs.weeks.add(week)

            val block = BlockEntity(id = 60L, name = "B1", weekIds = mutableListOf(week.id))
            cs.blocks.add(block)

            program = ProgramEntity(id = 70L, name = "Push Pull Legs", blockIds = mutableListOf(block.id))
            cs.programs.add(program)
        }
    }

    private fun editMovementToBench(f: Fixture) {
        f.cs.updateEntity(
            kind = EntityKind.EXERCISE,
            entity = f.exercise,
            newName = "Hard",
            selectedChildIds = listOf(f.bench.id)
        )
    }

    @Test
    fun exercise_movement_is_updated() {
        val f = Fixture()
        editMovementToBench(f)

        val stored = f.cs.exercises.first { it.id == f.exercise.id }
        assertEquals("Bench", stored.movementId.let { id -> f.cs.movements.first { it.id == id }.name })
    }

    @Test
    fun resolved_display_name_follows_the_new_movement() {
        val f = Fixture()
        editMovementToBench(f)

        val stored = f.cs.exercises.first { it.id == f.exercise.id }
        assertEquals("Bench^Hard", stored.computeNameString(f.cs.movements, "Unknown"))
    }

    @Test
    fun program_tree_resolves_the_new_movement() {
        val f = Fixture()
        editMovementToBench(f)

        // The session referenced by the program must resolve the exercise to Bench.
        val session = f.cs.sessions.first { it.name == "Legs" }
        val referenced = session.exerciseIds.mapNotNull { id -> f.cs.exercises.firstOrNull { it.id == id } }
        assertEquals(1, referenced.size)
        assertEquals(f.bench.id, referenced.first().movementId)
    }

    @Test
    fun active_program_rm_maps_follow_the_movement_change() {
        val f = Fixture()
        val ap = f.cs.startProgram(
            program = f.program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(f.squat.id to RmSettings(100, 120, InterpolationStrategy.CONST))
        )
        assertTrue(ap.rmSettings.containsKey(f.squat.id))

        editMovementToBench(f)

        val refreshed = f.cs.activePrograms.first { it.id == ap.id }
        assertTrue("Bench RM settings must exist", refreshed.rmSettings.containsKey(f.bench.id))
        assertTrue("Bench daily RMs must exist", refreshed.exerciseDailyRms.containsKey(f.bench.id))
        assertFalse("Squat is no longer used by the program", refreshed.rmSettings.containsKey(f.squat.id))
        assertFalse("Squat daily RMs must be dropped", refreshed.exerciseDailyRms.containsKey(f.squat.id))
    }
}
