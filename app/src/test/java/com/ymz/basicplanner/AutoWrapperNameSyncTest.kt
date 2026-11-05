package com.ymz.basicplanner

import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalTime

/**
 * The user story: a single exercise dropped into a week slot used to be wrapped in a day + timed
 * session + session, all named after the exercise at that moment (a snapshot). Changing the
 * exercise's movement afterwards left the program showing the old name.
 */
class AutoWrapperNameSyncTest {

    private class Fixture {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val deadlift = cs.createEntity(EntityKind.MOVEMENT, "Deadlift") as MovementEntity
        val exercise = cs.createEntity(EntityKind.EXERCISE, "HARD", listOf(squat.id)) as ExerciseEntity

        lateinit var wrapperSession: SessionEntity
        lateinit var wrapperDay: DayEntity
        lateinit var week: WeekEntity

        /** Exactly what CreationScreen does when an exercise is dropped into a week slot. */
        fun wrapExerciseInAWeekSlot() {
            val displayName = cs.exerciseDisplayName(exercise)
            wrapperSession = SessionEntity(-cs.newId(), displayName, cs.currentUserName, mutableListOf(exercise.id))
            cs.sessions.add(wrapperSession)
            val ts = TimedSessionEntity(-cs.newId(), displayName, "", LocalTime.of(0, 0), wrapperSession.id)
            cs.timedSessions.add(ts)
            wrapperDay = DayEntity(-cs.newId(), displayName, cs.currentUserName, mutableListOf(ts.id))
            cs.days.add(wrapperDay)
            week = cs.createEntity(EntityKind.WEEK, "Week 1", listOf(wrapperDay.id)) as WeekEntity
        }

        fun changeMovementToDeadlift() {
            val stored = cs.exercises.first { it.id == exercise.id }
            cs.updateEntity(EntityKind.EXERCISE, stored, stored.name, listOf(deadlift.id))
        }
    }

    @Test
    fun the_wrapper_is_named_after_the_exercise_when_created() {
        val f = Fixture()
        f.wrapExerciseInAWeekSlot()
        assertEquals("Squat HARD", f.wrapperSession.name)
        assertEquals("Squat HARD", f.wrapperDay.name)
    }

    @Test
    fun changing_the_movement_renames_the_whole_wrapper_chain() {
        val f = Fixture()
        f.wrapExerciseInAWeekSlot()
        f.changeMovementToDeadlift()

        assertEquals("wrapper session", "Deadlift HARD", f.wrapperSession.name)
        assertEquals("wrapper day", "Deadlift HARD", f.wrapperDay.name)
        val timed = f.cs.timedSessions.first { it.sessionId == f.wrapperSession.id }
        assertEquals("timed session", "Deadlift HARD", timed.name)
    }

    @Test
    fun renaming_the_variant_also_renames_the_wrapper() {
        val f = Fixture()
        f.wrapExerciseInAWeekSlot()

        val stored = f.cs.exercises.first { it.id == f.exercise.id }
        f.cs.updateEntity(EntityKind.EXERCISE, stored, "EASY", listOf(f.squat.id))

        assertEquals("Squat EASY", f.wrapperSession.name)
        assertEquals("Squat EASY", f.wrapperDay.name)
    }

    @Test
    fun a_session_named_wrapper_follows_its_session() {
        val f = Fixture()
        val session = f.cs.createEntity(EntityKind.SESSION, "Legs", listOf(f.exercise.id)) as SessionEntity
        val ts = TimedSessionEntity(-f.cs.newId(), session.name, "", LocalTime.of(0, 0), session.id)
        f.cs.timedSessions.add(ts)
        val day = DayEntity(-f.cs.newId(), session.name, f.cs.currentUserName, mutableListOf(ts.id))
        f.cs.days.add(day)
        val week = f.cs.createEntity(EntityKind.WEEK, "Week 1", listOf(day.id)) as WeekEntity

        f.cs.updateEntity(EntityKind.SESSION, session, "Legs + core", listOf(f.exercise.id))

        assertEquals("Legs + core", session.name)
        assertEquals("wrapper timed session", "Legs + core", ts.name)
        assertEquals("wrapper day", "Legs + core", day.name)
        assertEquals("the week still holds the day", week.dayIds, listOf(day.id))
    }

    @Test
    fun a_user_created_day_is_never_renamed() {
        val f = Fixture()
        // A day the user made by hand (positive id) with a custom name must stay untouched.
        val session = f.cs.createEntity(EntityKind.SESSION, "Legs", listOf(f.exercise.id)) as SessionEntity
        val ts = TimedSessionEntity(f.cs.newId(), "Legs", "", LocalTime.of(0, 0), session.id)
        f.cs.timedSessions.add(ts)
        val day = f.cs.createEntity(EntityKind.DAY, "My own day", listOf(ts.id)) as DayEntity
        f.cs.createEntity(EntityKind.WEEK, "Week 1", listOf(day.id))

        f.changeMovementToDeadlift()

        assertEquals("My own day", f.cs.days.first { it.id == day.id }.name)
    }

    @Test
    fun a_week_wrapper_block_follows_the_week_name() {
        val f = Fixture()
        val week = f.cs.createEntity(EntityKind.WEEK, "Week 1") as WeekEntity
        val block = com.ymz.basicplanner.model.BlockEntity(
            id = -f.cs.newId(), name = week.name, owner = f.cs.currentUserName,
            weekIds = mutableListOf(week.id)
        )
        f.cs.blocks.add(block)

        f.cs.updateEntity(EntityKind.WEEK, week, "Week 1 - peak", emptyList())

        assertEquals("Week 1 - peak", week.name)
        assertEquals("Week 1 - peak", block.name)
    }
}
