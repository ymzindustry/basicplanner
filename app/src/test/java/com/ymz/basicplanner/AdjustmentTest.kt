package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.effectiveAdjustmentPercent
import com.ymz.basicplanner.model.scaledBaseRm
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class AdjustmentTest {

    @Test
    fun scaledBaseRm_applies_percentage() {
        assertEquals(220, scaledBaseRm(200, 10))
        assertEquals(180, scaledBaseRm(200, -10))
        assertEquals(200, scaledBaseRm(200, 0))
    }

    @Test
    fun effectiveAdjustmentPercent_sums_chain() {
        val restId = -1L
        val day = DayEntity(id = 1L, name = "Push Day", owner = null, adjustmentPercent = 2)
        val week = WeekEntity(
            id = 2L, name = "Week 1", owner = null,
            dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId),
            adjustmentPercent = -5
        )
        val block = BlockEntity(id = 3L, name = "Block 1", owner = null, weekIds = mutableListOf(week.id), adjustmentPercent = 5)
        val program = ProgramEntity(id = 4L, name = "Demo", owner = null, blockIds = mutableListOf(block.id), adjustmentPercent = 10)
        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(
            id = 5L,
            programId = program.id,
            startDate = startDate,
            name = "Demo Active"
        )
        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            activePrograms = listOf(activeProgram)
        )

        assertEquals(12, effectiveAdjustmentPercent(app, activeProgram.id, startDate))
    }

    @Test
    fun subAdjustments_stack_on_children() {
        val restId = -1L
        val day = DayEntity(id = 1L, name = "Push Day", owner = null, adjustmentPercent = 2)
        val week = WeekEntity(
            id = 2L, name = "Week 1", owner = null,
            dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId),
            adjustmentPercent = -5,
            subAdjustments = mutableListOf(5)  // extra +5% for Push Day (index 0)
        )
        val block = BlockEntity(id = 3L, name = "Block 1", owner = null, weekIds = mutableListOf(week.id), adjustmentPercent = 5)
        val program = ProgramEntity(id = 4L, name = "Demo", owner = null, blockIds = mutableListOf(block.id), adjustmentPercent = 10)
        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(id = 5L, programId = program.id, startDate = startDate, name = "Demo Active")
        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = listOf(week),
            days = listOf(day),
            activePrograms = listOf(activeProgram)
        )
        // 10 + 5 + (-5) + 5 (sub) + 2 = 17
        assertEquals(17, effectiveAdjustmentPercent(app, activeProgram.id, startDate))
    }

    /** Regression: the percentage a block shows on its week rows (block.subAdjustments[weekIdx])
     *  used to be rendered but never included in the computed adjustment, so weeks that only
     *  differ by that chip produced identical goal weights (flat plateaus in the dashboard). */
    @Test
    fun block_subAdjustments_apply_to_their_weeks() {
        val restId = -1L
        val day = DayEntity(id = 1L, name = "Push Day", owner = null)
        fun week(id: Long, name: String) = WeekEntity(
            id = id,
            name = name,
            owner = null,
            dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId)
        )

        val weeks = listOf(week(2L, "Week 1"), week(3L, "Week 2"), week(4L, "Week 3"), week(5L, "Week 4"))
        val block = BlockEntity(
            id = 6L,
            name = "Block 1",
            owner = null,
            weekIds = weeks.map { it.id }.toMutableList(),
            subAdjustments = mutableListOf(0, -2, -3, -5)
        )
        val program = ProgramEntity(id = 7L, name = "Demo", owner = null, blockIds = mutableListOf(block.id))
        val startDate = LocalDate.of(2026, 8, 3) // Monday
        val activeProgram = ActiveProgram(id = 8L, programId = program.id, startDate = startDate, name = "Demo Active")
        val app = AppState(
            programs = listOf(program),
            blocks = listOf(block),
            weeks = weeks,
            days = listOf(day),
            activePrograms = listOf(activeProgram)
        )

        assertEquals(0, effectiveAdjustmentPercent(app, activeProgram.id, startDate))
        assertEquals(-2, effectiveAdjustmentPercent(app, activeProgram.id, startDate.plusWeeks(1)))
        assertEquals(-3, effectiveAdjustmentPercent(app, activeProgram.id, startDate.plusWeeks(2)))
        assertEquals(-5, effectiveAdjustmentPercent(app, activeProgram.id, startDate.plusWeeks(3)))
    }

    @Test
    fun program_subAdjustments_apply_to_weeks_directly_in_program() {
        val restId = -1L
        val day = DayEntity(id = 1L, name = "Push Day", owner = null)
        fun week(id: Long, name: String) = WeekEntity(
            id = id,
            name = name,
            owner = null,
            dayIds = mutableListOf(day.id, restId, restId, restId, restId, restId, restId)
        )

        val weeks = listOf(week(2L, "Week 1"), week(3L, "Week 2"))
        val program = ProgramEntity(
            id = 7L,
            name = "Demo",
            owner = null,
            blockIds = weeks.map { it.id }.toMutableList(),
            subAdjustments = mutableListOf(0, -4)
        )
        val startDate = LocalDate.of(2026, 8, 3)
        val activeProgram = ActiveProgram(id = 8L, programId = program.id, startDate = startDate, name = "Demo Active")
        val app = AppState(
            programs = listOf(program),
            weeks = weeks,
            days = listOf(day),
            activePrograms = listOf(activeProgram)
        )

        assertEquals(0, effectiveAdjustmentPercent(app, activeProgram.id, startDate))
        assertEquals(-4, effectiveAdjustmentPercent(app, activeProgram.id, startDate.plusWeeks(1)))
    }
}
