package com.ymz.basicplanner

import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.programSegments
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

/**
 * Bands on the dashboard chart: level-0 blocks by default, deeper levels on demand, weeks as the
 * finest step. Each band must cover exactly the days its weeks span.
 */
class PlanSegmentsTest {

    private fun week(id: Long, name: String) = WeekEntity(id = id, name = name, owner = null)

    private fun state(
        weeks: List<WeekEntity>,
        blocks: List<BlockEntity>,
        startDate: LocalDate,
        programId: Long = 20L,
        activeId: Long = 30L
    ): AppState {
        val program = ProgramEntity(id = programId, name = "P", owner = null, blockIds = mutableListOf())
        return AppState(
            weeks = weeks,
            blocks = blocks,
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = activeId, programId = programId, startDate = startDate, name = "P active"))
        )
    }

    @Test
    fun level_zero_bands_are_the_top_level_blocks() {
        val start = LocalDate.of(2026, 8, 3) // Monday
        val weeks = listOf(week(1L, "W1"), week(2L, "W2"), week(3L, "W3"))
        val b1 = BlockEntity(id = 10L, name = "Block 1", owner = null, weekIds = mutableListOf(1L))
        val b2 = BlockEntity(id = 11L, name = "Block 2", owner = null, weekIds = mutableListOf(2L))
        val b3 = BlockEntity(id = 12L, name = "Block 3", owner = null, weekIds = mutableListOf(3L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L, 11L, 12L))
        val app = AppState(
            weeks = weeks,
            blocks = listOf(b1, b2, b3),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val bands = programSegments(app, 30L, 0)

        assertEquals(listOf("Block 1", "Block 2", "Block 3"), bands.map { it.name })
        assertEquals(start, bands[0].start)
        assertEquals(start.plusDays(6), bands[0].end)
        assertEquals(start.plusDays(7), bands[1].start)
        assertEquals(start.plusDays(13), bands[1].end)
        assertEquals(start.plusDays(14), bands[2].start)
        assertEquals(start.plusDays(20), bands[2].end)
    }

    @Test
    fun level_one_bands_are_the_sub_blocks_and_span_their_weeks() {
        val start = LocalDate.of(2026, 8, 3)
        val weeks = listOf(week(1L, "W1"), week(2L, "W2"), week(3L, "W3"), week(4L, "W4"))
        val leafA = BlockEntity(id = 11L, name = "Leaf A", owner = null, weekIds = mutableListOf(1L, 2L))
        val leafB = BlockEntity(id = 12L, name = "Leaf B", owner = null, weekIds = mutableListOf(3L, 4L))
        val top = BlockEntity(id = 10L, name = "Block 1", owner = null, subBlockIds = mutableListOf(11L, 12L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L))
        val app = AppState(
            weeks = weeks,
            blocks = listOf(leafA, leafB, top),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val bands = programSegments(app, 30L, 1)

        assertEquals(listOf("Leaf A", "Leaf B"), bands.map { it.name })
        assertEquals(start, bands[0].start)
        assertEquals(start.plusDays(13), bands[0].end)
        assertEquals(start.plusDays(14), bands[1].start)
        assertEquals(start.plusDays(27), bands[1].end)
    }

    @Test
    fun weeks_are_the_finest_step() {
        val start = LocalDate.of(2026, 8, 3)
        val weeks = listOf(week(1L, "W1"), week(2L, "W2"))
        val block = BlockEntity(id = 10L, name = "Block 1", owner = null, weekIds = mutableListOf(1L, 2L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L))
        val app = AppState(
            weeks = weeks,
            blocks = listOf(block),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val bands = programSegments(app, 30L, null)

        assertEquals(listOf("W1", "W2"), bands.map { it.name })
        assertEquals(start, bands[0].start)
        assertEquals(start.plusDays(6), bands[0].end)
        assertEquals(start.plusDays(7), bands[1].start)
        assertEquals(start.plusDays(13), bands[1].end)
    }

    @Test
    fun a_mid_week_start_shrinks_the_first_band() {
        val start = LocalDate.of(2026, 8, 5) // Wednesday → 5 days
        val weeks = listOf(week(1L, "W1"), week(2L, "W2"))
        val b1 = BlockEntity(id = 10L, name = "Block 1", owner = null, weekIds = mutableListOf(1L))
        val b2 = BlockEntity(id = 11L, name = "Block 2", owner = null, weekIds = mutableListOf(2L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L, 11L))
        val app = AppState(
            weeks = weeks,
            blocks = listOf(b1, b2),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val bands = programSegments(app, 30L, 0)

        assertEquals(start, bands[0].start)
        assertEquals(start.plusDays(4), bands[0].end)
        assertEquals(start.plusDays(5), bands[1].start)
        assertEquals(start.plusDays(11), bands[1].end)
    }

    @Test
    fun a_block_listed_twice_reads_as_two_sections() {
        val start = LocalDate.of(2026, 8, 3)
        val weeks = listOf(week(1L, "W1"), week(2L, "W2"))
        // The same block is referenced twice by the program: the tree shows two rows, so the
        // chart has to show two bands, not one merged blob.
        val block = BlockEntity(id = 10L, name = "BLOCK", owner = null, weekIds = mutableListOf(1L, 2L))
        val program = ProgramEntity(id = 20L, name = "P", owner = null, blockIds = mutableListOf(10L, 10L))
        val app = AppState(
            weeks = weeks,
            blocks = listOf(block),
            programs = listOf(program),
            activePrograms = listOf(ActiveProgram(id = 30L, programId = 20L, startDate = start, name = "P active"))
        )

        val bands = programSegments(app, 30L, 0)

        assertEquals(2, bands.size)
        assertEquals(start, bands[0].start)
        assertEquals(start.plusDays(13), bands[0].end)
        assertEquals(start.plusDays(14), bands[1].start)
        assertEquals(start.plusDays(27), bands[1].end)
    }
}
