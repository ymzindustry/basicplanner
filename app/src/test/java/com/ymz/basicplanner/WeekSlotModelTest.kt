package com.ymz.basicplanner

import com.ymz.basicplanner.compose.buildProgramCalendar
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.RmSettings
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.plannedRepsForMovement
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.InterpolationStrategy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

/**
 * A week weekday slot holds the chosen entity itself - a day, a session or a single exercise.
 * No wrapper (day/session) is created around a bare exercise or session, so what the program
 * shows is always the entity's live name.
 */
class WeekSlotModelTest {

    private class Fixture {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val deadlift = cs.createEntity(EntityKind.MOVEMENT, "Deadlift") as MovementEntity
        val exercise = cs.createEntity(EntityKind.EXERCISE, "HARD", listOf(squat.id)) as ExerciseEntity
        val session = cs.createEntity(EntityKind.SESSION, "Legs", listOf(exercise.id)) as SessionEntity
        val day: DayEntity

        init {
            val ts = TimedSessionEntity(id = cs.newId(), name = "Legs", time = LocalTime.of(18, 0), sessionId = session.id)
            cs.timedSessions.add(ts)
            day = cs.createEntity(EntityKind.DAY, "Monday", listOf(ts.id)) as DayEntity
        }

        fun weekWith(vararg slots: Long): WeekEntity {
            val padded = slots.toMutableList()
            while (padded.size < 7) padded.add(CreationState.REST_DAY_ID)
            return cs.createEntity(EntityKind.WEEK, "Week 1", padded) as WeekEntity
        }
    }

    @Test
    fun a_week_slot_accepts_a_day_a_session_and_an_exercise() {
        val f = Fixture()
        val week = f.weekWith(f.day.id, f.session.id, f.exercise.id)

        val children = f.cs.childrenOf(EntityKind.WEEK, week)

        // 7 weekday slots: the 3 filled ones plus the 4 rest placeholders.
        assertEquals(7, children.size)
        assertTrue(children[0] is DayEntity)
        assertTrue(children[1] is SessionEntity)
        assertTrue(children[2] is ExerciseEntity)
    }

    @Test
    fun creating_a_week_right_after_a_movement_offers_it() {
        // The reported flow: only a movement exists, then a week is created and the exercise is
        // dropped straight into a weekday slot.
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity

        val exercise = cs.exerciseForMovement(squat)
        assertEquals("the exercise carries the movement", squat.id, exercise.movementId)
        assertEquals("Squat", cs.exerciseDisplayName(exercise))

        val week = cs.createEntity(EntityKind.WEEK, "Week 1", listOf(exercise.id)) as WeekEntity
        val inWeek = cs.childrenOf(EntityKind.WEEK, week).filterIsInstance<ExerciseEntity>()
        assertEquals(listOf(exercise.id), inWeek.map { it.id })

        // Tapping the same movement again reuses that exercise instead of duplicating it.
        assertEquals(exercise.id, cs.exerciseForMovement(squat).id)
        assertEquals(1, cs.exercises.count { it.movementId == squat.id })

        // And a movement that already has an exercise is no longer proposed as "free".
        val free = cs.movements.filter { mov ->
            cs.exercises.none { it.movementId == mov.id && cs.isPlanLevel(it) }
        }
        assertTrue("Squat now has an exercise", free.none { it.id == squat.id })
    }

    @Test
    fun a_proposal_fills_the_focused_weekday_and_keeps_the_other_days() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val bench = cs.createEntity(EntityKind.MOVEMENT, "Bench") as MovementEntity
        val slots = mutableListOf<Long>()

        // Monday is empty: the pick simply lands there.
        cs.assignToWeekDay(slots, 0, cs.exerciseForMovement(squat).id)
        // The focus moved to Tuesday, so the next pick lands there and Monday is untouched.
        cs.assignToWeekDay(slots, 1, cs.exerciseForMovement(bench).id)

        assertEquals(squat.id, cs.exercises.first { it.id == slots[0] }.movementId)
        assertEquals(bench.id, cs.exercises.first { it.id == slots[1] }.movementId)
        assertNull("no other day was touched", slots.getOrNull(2))
    }

    @Test
    fun a_pick_on_an_occupied_weekday_replaces_its_entity() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val bench = cs.createEntity(EntityKind.MOVEMENT, "Bench") as MovementEntity
        val slots = mutableListOf<Long>()

        cs.assignToWeekDay(slots, 0, cs.exerciseForMovement(squat).id)
        cs.assignToWeekDay(slots, 0, cs.exerciseForMovement(bench).id)   // Monday focused again

        assertEquals("Monday now holds the new pick", bench.id,
            cs.exercises.first { it.id == slots[0] }.movementId)
        assertEquals("the day is replaced, not appended", 1, slots.size)
    }

    @Test
    fun untouched_days_stay_empty_while_the_filled_one_is_set() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val slots = mutableListOf<Long>()

        cs.assignToWeekDay(slots, 0, cs.exerciseForMovement(squat).id)   // Monday filled

        assertEquals("Monday holds the exercise", squat.id,
            cs.exercises.first { it.id == slots[0] }.movementId)
        assertNull("Tuesday is still empty", slots.getOrNull(1))
    }

    @Test
    fun assigning_the_last_weekday_pads_the_week_to_seven_slots() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val slots = mutableListOf<Long>()

        cs.assignToWeekDay(slots, 6, cs.exerciseForMovement(squat).id)   // Sunday

        assertEquals("the week view has all seven days", 7, slots.size)
        assertEquals(squat.id, cs.exercises.first { it.id == slots[6] }.movementId)
        assertTrue("the earlier days stay unfilled", slots.take(6).all { it == CreationState.EMPTY_DAY_ID })
    }

    @Test
    fun clearing_a_weekday_leaves_it_unfilled() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val slots = MutableList(7) { CreationState.EMPTY_DAY_ID }
        cs.assignToWeekDay(slots, 2, cs.exerciseForMovement(squat).id)   // Wednesday filled
        assertEquals(squat.id, cs.exercises.first { it.id == slots[2] }.movementId)

        // The cross empties that very day (it does not shift the other weekdays).
        cs.assignToWeekDay(slots, 2, CreationState.EMPTY_DAY_ID)

        assertEquals(7, slots.size)
        assertEquals(CreationState.EMPTY_DAY_ID, slots[2])
        assertTrue("an unfilled day resolves to an empty day", cs.resolveWeekSlot(slots[2]) != null)
    }

    @Test
    fun slot_markers_never_leak_as_a_raw_id() {
        val cs = CreationState()
        // nameOf() is null for a slot holding nothing: the UI shows "Rest day" / "" / "—" there,
        // never the id itself (that used to print a huge number in the adjustment dialog).
        assertNull(cs.nameOf(EntityKind.WEEK, CreationState.EMPTY_DAY_ID))
        assertNull(cs.nameOf(EntityKind.WEEK, CreationState.REST_DAY_ID))
        assertNotNull("an unfilled day still resolves to an empty day", cs.resolveWeekSlot(CreationState.EMPTY_DAY_ID))
    }

    @Test
    fun picking_a_movement_creates_the_exercise_with_its_variant() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity

        val hard = cs.exerciseForMovement(squat, "HARD")
        assertEquals("Squat HARD", cs.exerciseDisplayName(hard))
        assertEquals("reuses the same exercise for the same variant", hard.id, cs.exerciseForMovement(squat, "HARD").id)

        val easy = cs.exerciseForMovement(squat, "EASY")
        assertTrue("another variant is its own exercise", easy.id != hard.id)
        assertEquals(2, cs.exercises.count { it.movementId == squat.id })
    }

    @Test
    fun the_attached_program_finds_its_movements_and_planned_reps() {
        // The RM/goal dialog of the agenda asks for the movements of the attached program; the
        // dashboard asks for the planned reps. Both must work with the flat layout: week directly in
        // the program, exercise directly in a weekday slot.
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val exercise = cs.exerciseForMovement(squat, "HARD")
        exercise.entries.clear()
        exercise.entries.add(SetEntry(id = cs.newId(), sets = 5, reps = 5, intensity = 80, rest = 120))
        val week = cs.createEntity(EntityKind.WEEK, "Week 1", listOf(exercise.id)) as WeekEntity
        val program = cs.createEntity(EntityKind.PROGRAM, "My program", listOf(week.id)) as ProgramEntity
        cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(squat.id to RmSettings(100, 140, InterpolationStrategy.CONST))
        )
        val app = cs.toAppState()
        val activeId = app.activePrograms.first().id

        assertEquals(
            "the movement of a bare exercise inside a week is detected",
            listOf(squat.id),
            cs.getUniqueMovementIdsForProgram(program)
        )
        assertEquals(
            "and its planned reps are found for the goal curve / dashboard",
            listOf(5),
            plannedRepsForMovement(app, activeId, squat.id)
        )
    }

    @Test
    fun a_plan_exercise_is_never_hidden_by_its_id_sign() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        // Whatever id an exercise ended up with, the pickers must still offer it.
        cs.exercises.add(ExerciseEntity(id = -7, movementId = squat.id, name = "HARD", owner = ""))
        assertTrue(cs.isPlanLevel(cs.exercises.last()))
        assertTrue(cs.availableChildren(EntityKind.WEEK).filter { cs.isPlanLevel(it) }.any { it is ExerciseEntity })
    }

    @Test
    fun a_session_instance_copy_is_hidden_from_the_pickers() {
        val f = Fixture()
        val program = f.cs.createEntity(EntityKind.PROGRAM, "P", listOf(f.weekWith(f.exercise.id).id)) as ProgramEntity
        f.cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(f.squat.id to RmSettings(100, 140, InterpolationStrategy.CONST))
        )
        val host = SessionEntity(id = f.cs.newId(), name = "", exerciseIds = mutableListOf(f.exercise.id))
        f.cs.createSessionInstance(
            host,
            mapOf(f.squat.id to 100),
            java.time.LocalDateTime.of(2026, 8, 3, 18, 0),
            1L
        )

        val copies = f.cs.exercises.filter { it.id < 0 }
        assertTrue("the instance made a copy", copies.isNotEmpty())
        assertTrue("copies are not plan level", copies.none { f.cs.isPlanLevel(it) })
        assertTrue("the plan exercise stays plan level", f.cs.isPlanLevel(f.cs.exercises.first { it.id == f.exercise.id }))
    }

    @Test
    fun a_created_exercise_survives_save_and_load_and_is_still_offered() {
        val cs = CreationState()
        val squat = cs.createEntity(EntityKind.MOVEMENT, "Squat") as MovementEntity
        val ex = cs.createEntity(EntityKind.EXERCISE, "HARD", listOf(squat.id)) as ExerciseEntity

        // Round-trip exactly like AppStateStore does on every launch.
        val reloaded = CreationState()
        reloaded.loadFrom(cs.toAppState())

        assertTrue("movement survives", reloaded.movements.any { it.id == squat.id })
        assertTrue("exercise survives", reloaded.exercises.any { it.id == ex.id })
        assertTrue("exercise id stays positive", reloaded.exercises.first { it.id == ex.id }.id >= 0)

        // The week editor's chips are built from this exact expression.
        val chips = reloaded.availableChildren(EntityKind.WEEK).filter { it.id >= 0 }
        assertTrue(
            "the reloaded exercise is offered as a week chip",
            chips.filterIsInstance<ExerciseEntity>().any { it.id == ex.id }
        )
    }

    @Test
    fun a_created_exercise_is_offered_as_a_week_chip() {
        val f = Fixture()
        // The week editor builds its chips from availableChildren(WEEK).filter { it.id >= 0 }.
        val chips = f.cs.availableChildren(EntityKind.WEEK).filter { it.id >= 0 }
        assertTrue("plan exercises have positive ids", chips.any { it.id == f.exercise.id })
        assertTrue("chips include the exercise entity", chips.any { it is ExerciseEntity })
        assertEquals(
            listOf("Squat HARD"),
            chips.filterIsInstance<ExerciseEntity>().map { f.cs.exerciseDisplayName(it) }
        )
    }

    @Test
    fun the_slot_kind_follows_the_entity_type() {
        val f = Fixture()
        assertEquals(EntityKind.DAY, f.cs.weekSlotKind(f.day))
        assertEquals(EntityKind.SESSION, f.cs.weekSlotKind(f.session))
        assertEquals(EntityKind.EXERCISE, f.cs.weekSlotKind(f.exercise))
    }

    @Test
    fun an_exercise_in_a_week_slot_keeps_its_live_name() {
        val f = Fixture()
        val week = f.weekWith(f.exercise.id)
        assertEquals(
            "the slot label before the change",
            listOf("Squat HARD"),
            f.cs.childrenOf(EntityKind.WEEK, week).filterIsInstance<ExerciseEntity>()
                .map { f.cs.exerciseDisplayName(it) }
        )

        val stored = f.cs.exercises.first { it.id == f.exercise.id }
        f.cs.updateEntity(EntityKind.EXERCISE, stored, stored.name, listOf(f.deadlift.id))

        assertEquals(
            "the slot follows the exercise, no snapshot anywhere",
            "Deadlift HARD",
            f.cs.childrenOf(EntityKind.WEEK, week).filterIsInstance<ExerciseEntity>()
                .first().let { f.cs.exerciseDisplayName(it) }
        )
    }

    @Test
    fun the_agenda_maps_a_bare_exercise_slot_to_the_exercise_itself() {
        val f = Fixture()
        val week = f.weekWith(f.exercise.id)
        val program = f.cs.createEntity(EntityKind.PROGRAM, "My program", listOf(week.id)) as ProgramEntity

        val slots = buildProgramCalendar(program, LocalDate.of(2026, 8, 3), f.cs)
        val exerciseSlot = slots.values.first { it.kind == EntityKind.EXERCISE }

        assertTrue(exerciseSlot.entity is ExerciseEntity)
        assertEquals(listOf("Squat HARD"), exerciseSlot.labels)

        val stored = f.cs.exercises.first { it.id == f.exercise.id }
        f.cs.updateEntity(EntityKind.EXERCISE, stored, stored.name, listOf(f.deadlift.id))
        val after = buildProgramCalendar(program, LocalDate.of(2026, 8, 3), f.cs)
            .values.first { it.kind == EntityKind.EXERCISE }
        assertEquals(listOf("Deadlift HARD"), after.labels)
    }

    @Test
    fun a_session_slot_shows_the_session_name() {
        val f = Fixture()
        val week = f.weekWith(f.session.id)
        val program = f.cs.createEntity(EntityKind.PROGRAM, "My program", listOf(week.id)) as ProgramEntity

        val slot = buildProgramCalendar(program, LocalDate.of(2026, 8, 3), f.cs)
            .values.first { it.kind == EntityKind.SESSION }
        assertEquals(listOf("Legs"), slot.labels)
    }

    @Test
    fun legacy_wrappers_are_migrated_away_on_load() {
        val f = Fixture()
        val stale = CreationState()

        // Simulate what the old version persisted: a throw-away day+session named after the exercise.
        val wrapperSession = SessionEntity(-stale.newId(), "Squat HARD", "", mutableListOf(f.exercise.id))
        stale.sessions.add(wrapperSession)
        val ts = TimedSessionEntity(-stale.newId(), "Squat HARD", "", LocalTime.of(0, 0), wrapperSession.id)
        stale.timedSessions.add(ts)
        val wrapperDay = DayEntity(-stale.newId(), "Squat HARD", "", mutableListOf(ts.id))
        stale.days.add(wrapperDay)
        val week = stale.createEntity(EntityKind.WEEK, "Week 1", listOf(wrapperDay.id)) as WeekEntity
        val wrapperBlock = BlockEntity(id = -stale.newId(), name = "Week 1", weekIds = mutableListOf(week.id))
        stale.blocks.add(wrapperBlock)
        stale.createEntity(EntityKind.PROGRAM, "My program", listOf(wrapperBlock.id))
        stale.exercises.add(f.exercise)
        stale.movements.addAll(listOf(f.squat, f.deadlift))

        // Round-trip through persistence, like the app does at startup.
        val reloaded = CreationState()
        reloaded.loadFrom(stale.toAppState())

        val migratedWeek = reloaded.weeks.first()
        assertEquals("the week slot now holds the exercise itself", listOf(f.exercise.id), migratedWeek.dayIds)
        val migratedProgram = reloaded.programs.first()
        assertEquals("the program now holds the week itself", listOf(migratedWeek.id), migratedProgram.blockIds)
    }

    @Test
    fun a_program_can_hold_a_week_and_a_block() {
        val f = Fixture()
        val week = f.weekWith(f.exercise.id)
        val block = f.cs.createEntity(EntityKind.BLOCK, "Block 1", listOf(week.id)) as BlockEntity
        val program = f.cs.createEntity(EntityKind.PROGRAM, "My program", listOf(block.id, week.id)) as ProgramEntity

        val children = f.cs.childrenOf(EntityKind.PROGRAM, program)
        assertEquals(2, children.size)
        assertTrue(children[0] is BlockEntity)
        assertTrue(children[1] is WeekEntity)
    }

    @Test
    fun an_unwrapped_exercise_can_still_start_a_session() {
        val f = Fixture()
        val week = f.weekWith(f.exercise.id)
        val program = f.cs.createEntity(EntityKind.PROGRAM, "My program", listOf(week.id)) as ProgramEntity
        f.cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(f.squat.id to RmSettings(100, 140, InterpolationStrategy.CONST))
        )

        // What the agenda does when the user launches a bare exercise slot.
        val host = SessionEntity(id = f.cs.newId(), name = "", exerciseIds = mutableListOf(f.exercise.id))
        val instance = f.cs.createSessionInstance(
            host,
            mapOf(f.squat.id to 100),
            java.time.LocalDateTime.of(2026, 8, 3, 18, 0),
            1L
        )
        assertEquals(1, instance.exerciseIds.size)
        val copied = f.cs.exercises.first { it.id == instance.exerciseIds.first() }
        assertTrue("the instance hosts a copy, not the plan entity", copied.id != f.exercise.id)
        assertTrue("instance copies are negative (that is why chips filter id >= 0)", copied.id < 0)
        assertEquals("the copy keeps the movement of the launched exercise", f.squat.id, copied.movementId)
    }
}
