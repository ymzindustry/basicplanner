package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.Item
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleEngine
import com.ymz.basicplanner.model.module.ModuleExecutor
import com.ymz.basicplanner.model.module.ModuleInputBinding
import com.ymz.basicplanner.model.module.ModuleInputSpec
import com.ymz.basicplanner.model.module.ModuleTargetKind
import com.ymz.basicplanner.model.module.moduleInputCandidates
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * The module engine: modules read items from the target (or its ancestors), return deltas, and the
 * engine sums them per set — without ever touching the plan.
 */
class ModuleEngineTest {

    private class Fixture {
        val movement = MovementEntity(id = 1L, name = "Deadlift")
        val exercise = ExerciseEntity(
            id = 10L,
            movementId = 1L,
            name = "Medium",
            entries = mutableListOf(
                SetEntry(id = 100L, sets = 1, reps = 5, intensity = 80, rest = 120),
                SetEntry(id = 101L, sets = 1, reps = 3, intensity = 90, rest = 180)
            )
        )
        val session = SessionEntity(
            id = 20L,
            name = "Pull A",
            exerciseIds = mutableListOf(10L),
            date = LocalDateTime.of(2026, 8, 3, 10, 0)
        )
        val timed = TimedSessionEntity(id = 30L, name = "10:00", time = LocalTime.of(10, 0), sessionId = 20L)
        val day = DayEntity(id = 40L, name = "Monday", timedSessionIds = mutableListOf(30L))
        val week = WeekEntity(id = 50L, name = "Week 1", dayIds = mutableListOf(40L, -1L, -1L, -1L, -1L, -1L, -1L))
        val block = BlockEntity(id = 60L, name = "Block 1", weekIds = mutableListOf(50L))
        val program = ProgramEntity(id = 70L, name = "Block 1", blockIds = mutableListOf(60L))
        val otherProgram = ProgramEntity(id = 71L, name = "Block 2", blockIds = mutableListOf(60L))
        val active = ActiveProgram(
            id = 80L,
            programId = 70L,
            startDate = LocalDate.of(2026, 8, 3),
            name = "Block 1 active",
            exerciseDailyRms = mapOf(1L to List(28) { 200 })
        )
        val otherActive = ActiveProgram(
            id = 81L,
            programId = 71L,
            startDate = LocalDate.of(2026, 8, 3),
            name = "Block 2 active",
            exerciseDailyRms = mapOf(1L to List(28) { 200 })
        )
        val formFamily = ItemFamily(id = 90L, name = "form", description = "", itemType = ItemType.INT)
        val formItem = Item(id = 91L, activeProgramId = 80L, entityId = 40L, itemFamilyId = 90L, value = "7")

        fun app(
            modules: List<ModuleDefinition> = emptyList(),
            attachments: List<ModuleAttachment> = emptyList()
        ) = AppState(
            movements = listOf(movement),
            exercises = listOf(exercise),
            sessions = listOf(session),
            timedSessions = listOf(timed),
            days = listOf(day),
            weeks = listOf(week),
            blocks = listOf(block),
            programs = listOf(program, otherProgram),
            activePrograms = listOf(active, otherActive),
            itemFamilies = listOf(formFamily),
            items = listOf(formItem),
            modules = modules,
            moduleAttachments = attachments
        )
    }

    private fun module(id: Long = 1L, code: String = "") = ModuleDefinition(
        id = id,
        name = "Form",
        code = code,
        inputs = listOf(ModuleInputSpec("form", "int"))
    )

    private fun dayAttachment(programId: Long = 70L, moduleId: Long = 1L) = ModuleAttachment(
        id = 1L,
        moduleId = moduleId,
        programId = programId,
        targetKind = ModuleTargetKind.DAY,
        targetId = 40L,
        bindings = listOf(ModuleInputBinding("form", 40L, "Monday"))
    )

    @Test
    fun a_day_module_lowers_every_set_of_that_day() {
        val f = Fixture()
        val executor = ModuleExecutor { _, contextJson ->
            // The module sees the day's form item, not the plan.
            assertTrue(contextJson.contains("\"form\":\"7\""))
            """[{"set_id":100,"reps_delta":-1},{"set_id":101,"reps_delta":-1}]"""
        }
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module()), listOf(dayAttachment())),
            activeProgramId = 80L,
            sessionId = 20L
        )

        assertEquals(-1, outcome.adjustments[100L]?.repsDelta)
        assertEquals(-1, outcome.adjustments[101L]?.repsDelta)
        assertTrue(outcome.issues.isEmpty())
        assertEquals(listOf(1L), outcome.applied)
    }

    @Test
    fun a_module_of_another_program_does_not_touch_this_one() {
        val f = Fixture()
        val executor = ModuleExecutor { _, _ -> """[{"set_id":100,"reps_delta":-1}]""" }
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module()), listOf(dayAttachment(programId = 70L))),
            activeProgramId = 81L, // other program, same exercises
            sessionId = 20L
        )

        assertTrue(outcome.adjustments.isEmpty())
        assertTrue(outcome.applied.isEmpty())
    }

    @Test
    fun deltas_from_several_modules_add_up() {
        val f = Fixture()
        val executor = ModuleExecutor { module, _ ->
            if (module.id == 1L) """[{"set_id":100,"reps_delta":-1}]""" else """[{"set_id":100,"percent_delta":-5.0}]"""
        }
        val attachments = listOf(
            dayAttachment(),
            ModuleAttachment(
                id = 2L,
                moduleId = 2L,
                programId = 70L,
                targetKind = ModuleTargetKind.EXERCISE,
                targetId = 10L
            )
        )
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module(1L), module(2L)), attachments),
            activeProgramId = 80L,
            sessionId = 20L
        )

        assertEquals(-1, outcome.adjustments[100L]?.repsDelta)
        assertEquals(-5.0, outcome.adjustments[100L]?.percentDelta ?: 0.0, 0.001)
    }

    @Test
    fun a_set_module_only_touches_its_own_set() {
        val f = Fixture()
        val executor = ModuleExecutor { _, _ -> """[{"set_id":100,"kg_delta":-2.5},{"set_id":101,"kg_delta":-2.5}]""" }
        val attachment = ModuleAttachment(
            id = 3L,
            moduleId = 1L,
            programId = 70L,
            targetKind = ModuleTargetKind.SET,
            targetId = 100L
        )
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module()), listOf(attachment)),
            activeProgramId = 80L,
            sessionId = 20L
        )

        assertEquals(-2.5, outcome.adjustments[100L]?.kgDelta ?: 0.0, 0.001)
        assertFalse(outcome.adjustments.containsKey(101L))
    }

    @Test
    fun a_failing_module_is_reported_and_changes_nothing() {
        val f = Fixture()
        val executor = ModuleExecutor { _, _ -> throw IllegalStateException("boom") }
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module()), listOf(dayAttachment())),
            activeProgramId = 80L,
            sessionId = 20L
        )

        assertTrue(outcome.adjustments.isEmpty())
        assertEquals(1, outcome.issues.size)
        assertEquals("boom", outcome.issues.first().message)
    }

    @Test
    fun unknown_set_ids_in_the_output_are_ignored() {
        val f = Fixture()
        val executor = ModuleExecutor { _, _ -> """[{"set_id":999,"reps_delta":-5}]""" }
        val outcome = ModuleEngine(executor).evaluate(
            f.app(listOf(module()), listOf(dayAttachment())),
            activeProgramId = 80L,
            sessionId = 20L
        )

        assertTrue(outcome.adjustments.isEmpty())
        assertTrue(outcome.applied.isEmpty())
    }

    @Test
    fun inputs_can_come_from_the_target_or_its_ancestors_only() {
        val f = Fixture()
        val app = f.app()
        val forSet = moduleInputCandidates(app, ModuleTargetKind.SET, 100L).map { it.entityId }.toSet()
        // set → exercise → session → day → week → block → program
        assertEquals(setOf(10L, 20L, 40L, 50L, 60L, 70L), forSet)
        // A set is a child of the exercise, so it must never appear as an input for itself.
        assertFalse(100L in forSet)
    }
}
