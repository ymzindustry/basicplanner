package com.ymz.basicplanner

import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.InterpolationStrategy
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.RmSettings
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class InterpolationTest {

    /**
     * Tree:
     * Program
     * ├── C1 (level 0)
     * │   ├── LeafA (level 1) → w1
     * │   └── LeafB (level 1) → w2
     * └── C2 (level 0)
     *     ├── LeafC (level 1) → w3
     *     └── LeafD (level 1) → w4
     */
    private fun buildState(): Pair<CreationState, ProgramEntity> {
        val cs = CreationState()
        val w1 = WeekEntity(id = 1L, name = "W1", owner = null)
        val w2 = WeekEntity(id = 2L, name = "W2", owner = null)
        val w3 = WeekEntity(id = 3L, name = "W3", owner = null)
        val w4 = WeekEntity(id = 4L, name = "W4", owner = null)
        cs.weeks.addAll(listOf(w1, w2, w3, w4))

        val leafA = BlockEntity(id = 10L, name = "LeafA", owner = null, weekIds = mutableListOf(w1.id))
        val leafB = BlockEntity(id = 11L, name = "LeafB", owner = null, weekIds = mutableListOf(w2.id))
        val leafC = BlockEntity(id = 12L, name = "LeafC", owner = null, weekIds = mutableListOf(w3.id))
        val leafD = BlockEntity(id = 13L, name = "LeafD", owner = null, weekIds = mutableListOf(w4.id))
        val c1 = BlockEntity(id = 20L, name = "C1", owner = null, subBlockIds = mutableListOf(leafA.id, leafB.id))
        val c2 = BlockEntity(id = 21L, name = "C2", owner = null, subBlockIds = mutableListOf(leafC.id, leafD.id))
        cs.blocks.addAll(listOf(leafA, leafB, leafC, leafD, c1, c2))

        val program = ProgramEntity(id = 30L, name = "P", owner = null, blockIds = mutableListOf(c1.id, c2.id))
        cs.programs.add(program)
        return cs to program
    }

    @Test
    fun maxBlockLevel_reports_deepest_level() {
        val (cs, program) = buildState()
        assertEquals(1, cs.maxBlockLevel(program))
    }

    @Test
    fun blockLevel0_interpolates_across_top_level_blocks() {
        val (cs, program) = buildState()
        val ap = cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3), // Monday
            rmSettings = mapOf(999L to RmSettings(100, 200, InterpolationStrategy.BLOCK, blockLevel = 0))
        )
        val daily = ap.exerciseDailyRms[999L] ?: error("missing daily rms")
        // 4 weeks × 7 = 28 days; C1 (first 2 weeks) = 100, C2 (last 2 weeks) = 200.
        assertEquals(28, daily.size)
        assertEquals(100, daily[0])
        assertEquals(100, daily[13])
        assertEquals(200, daily[14])
        assertEquals(200, daily[27])
    }

    @Test
    fun blockLevel1_interpolates_across_covering_blocks() {
        val (cs, program) = buildState()
        val ap = cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3), // Monday
            rmSettings = mapOf(999L to RmSettings(100, 200, InterpolationStrategy.BLOCK, blockLevel = 1))
        )
        val daily = ap.exerciseDailyRms[999L] ?: error("missing daily rms")
        // LeafA (0) → 100, LeafB (1/3) → 134, LeafC (2/3) → 167, LeafD (1) → 200.
        assertEquals(28, daily.size)
        assertEquals(100, daily[0])
        assertEquals(100, daily[6])
        assertEquals(134, daily[7])
        assertEquals(134, daily[13])
        assertEquals(167, daily[14])
        assertEquals(167, daily[20])
        assertEquals(200, daily[21])
        assertEquals(200, daily[27])
    }

    @Test
    fun constStrategy_returns_start_everywhere() {
        val (cs, program) = buildState()
        val ap = cs.startProgram(
            program = program,
            startDate = LocalDate.of(2026, 8, 3),
            rmSettings = mapOf(999L to RmSettings(140, 200, InterpolationStrategy.CONST))
        )
        val daily = ap.exerciseDailyRms[999L] ?: error("missing daily rms")
        assertEquals(28, daily.size)
        assertEquals(List(28) { 140 }, daily)
    }
}
