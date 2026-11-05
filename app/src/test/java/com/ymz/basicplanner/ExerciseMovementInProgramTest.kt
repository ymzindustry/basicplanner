package com.ymz.basicplanner

import com.ymz.basicplanner.model.CreationState
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
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.BlockEntity
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * Architecture check for the user story:
 *
 * "I create an exercise (Squat / variant HARD), I put it in my program, I add it to a session,
 *  then I change the exercise's movement to Deadlift — the program must show Deadlift."
 *
 * The whole graph is ID based: an exercise owns its movementId (an attribute), and every parent
 * (session -> day -> week -> block -> program) only stores the exercise's key. So renaming the
 * movement attribute on the exercise must be visible everywhere, with no copy in between.
 */
class ExerciseMovementInProgramTest {

    private class Program {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val deadlift = cs.createEntity(EntityKind.MOVEMENT, "Deadlift") as MovementEntity

        /** Created "by hand" through the same API the dialog uses. */
        val exercise = cs.createEntity(EntityKind.EXERCISE, "HARD", listOf(squat.id)) as ExerciseEntity
        val session: SessionEntity
        val day: DayEntity
        val week: WeekEntity
        val block: BlockEntity
        val program: ProgramEntity

        init {
            exercise.entries.add(SetEntry(id = 1L, sets = 3, reps = 10, intensity = 80, rest = 120))
            session = cs.createEntity(EntityKind.SESSION, "Legs", listOf(exercise.id)) as SessionEntity
            val timed = TimedSessionEntity(id = 900L, name = "Legs", time = LocalTime.of(18, 0), sessionId = session.id)
            cs.timedSessions.add(timed)
            day = cs.createEntity(EntityKind.DAY, "Day 1", listOf(timed.id)) as DayEntity
            week = cs.createEntity(EntityKind.WEEK, "Week 1", listOf(day.id)) as WeekEntity
            block = cs.createEntity(EntityKind.BLOCK, "Block 1", listOf(week.id)) as BlockEntity
            program = cs.createEntity(EntityKind.PROGRAM, "My program", listOf(block.id)) as ProgramEntity
        }

        /** What the user does in the dialog: pick Deadlift and press OK. */
        fun changeMovementToDeadlift() {
            val stored = cs.exercises.first { it.id == exercise.id }
            cs.updateEntity(EntityKind.EXERCISE, stored, stored.name, listOf(deadlift.id))
        }

        /** Walks the hierarchy the way the tree view does: program -> block -> week -> day -> session. */
        fun exercisesAsSeenFromTheProgram(): List<ExerciseEntity> {
            val storedProgram = cs.programs.first { it.id == program.id }
            return storedProgram.blockIds
                .mapNotNull { id -> cs.blocks.find { it.id == id } }
                .flatMap { b -> b.weekIds }
                .mapNotNull { id -> cs.weeks.find { it.id == id } }
                .flatMap { w -> w.dayIds }
                .mapNotNull { id -> cs.days.find { it.id == id } }
                .flatMap { d -> d.timedSessionIds }
                .mapNotNull { id -> cs.timedSessions.find { it.id == id } }
                .mapNotNull { ts -> cs.sessions.find { it.id == ts.sessionId } }
                .flatMap { s -> s.exerciseIds }
                .mapNotNull { id -> cs.exercises.find { it.id == id } }
        }
    }

    @Test
    fun the_program_resolves_the_new_movement() {
        val p = Program()
        assertEquals(1, p.exercisesAsSeenFromTheProgram().size)
        assertEquals("Squat^HARD", p.exercisesAsSeenFromTheProgram().first().computeNameString(p.cs.movements, "?"))

        p.changeMovementToDeadlift()

        val seen = p.exercisesAsSeenFromTheProgram()
        assertEquals(1, seen.size)
        assertEquals("Deadlift^HARD", seen.first().computeNameString(p.cs.movements, "?"))
    }

    @Test
    fun the_session_keeps_referencing_the_same_key() {
        val p = Program()
        p.changeMovementToDeadlift()

        // Same key: the exercise object may be replaced, but never duplicated inside the program.
        assertEquals(1, p.cs.exercises.size)
        assertTrue(p.session.exerciseIds.contains(p.exercise.id))
        assertEquals(p.deadlift.id, p.cs.exercises.first { it.id == p.exercise.id }.movementId)
    }

    @Test
    fun the_variant_is_kept_when_the_movement_changes() {
        val p = Program()
        p.changeMovementToDeadlift()
        assertEquals("HARD", p.cs.exercises.first { it.id == p.exercise.id }.name)
    }

    @Test
    fun the_active_program_swaps_the_rm_entry_to_the_new_movement() {
        val p = Program()
        val ap = p.cs.startProgram(
            program = p.program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(p.squat.id to RmSettings(100, 140, InterpolationStrategy.CONST))
        )
        assertTrue(ap.exerciseDailyRms.containsKey(p.squat.id))

        p.changeMovementToDeadlift()

        val refreshed = p.cs.activePrograms.first { it.id == ap.id }
        assertEquals(setOf(p.deadlift.id), refreshed.exerciseDailyRms.keys)
        assertEquals(setOf(p.deadlift.id), refreshed.rmSettings.keys)
    }

    /** Duplicating an exercise deliberately creates a NEW entity: editing the original must not
     *  touch the copy, and vice versa. */
    @Test
    fun a_duplicated_exercise_is_a_separate_entity() {
        val p = Program()
        p.cs.copyEntity(EntityKind.EXERCISE, p.exercise)

        assertEquals(2, p.cs.exercises.size)
        val copy = p.cs.exercises.first { it.id != p.exercise.id }
        assertNotEquals(p.exercise.id, copy.id)

        p.changeMovementToDeadlift()
        assertEquals(p.deadlift.id, p.cs.exercises.first { it.id == p.exercise.id }.movementId)
        assertEquals("the copy keeps its own movement", p.squat.id, copy.movementId)
    }

    /** A started session is a snapshot of the plan: this documents the known behaviour. */
    @Test
    fun a_started_session_keeps_its_own_exercise_copy() {
        val p = Program()
        val instance = p.cs.createSessionInstance(
            originalSession = p.session,
            dailyRms = mapOf(p.squat.id to 100),
            sessionDate = java.time.LocalDateTime.of(2026, 8, 3, 18, 0),
            activeProgramId = 1L
        )
        val instanceExercise = p.cs.exercises.first { it.id == instance.exerciseIds.first() }
        assertEquals(p.squat.id, instanceExercise.movementId)

        p.changeMovementToDeadlift()

        assertEquals("the plan moved on", p.deadlift.id, p.cs.exercises.first { it.id == p.exercise.id }.movementId)
        assertEquals("the instance is frozen", p.squat.id, p.cs.exercises.first { it.id == instanceExercise.id }.movementId)
    }
}
