package com.ymz.basicplanner

import com.ymz.basicplanner.model.AppState
import com.ymz.basicplanner.model.DemoSeedData
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.PerformanceEntry
import com.ymz.basicplanner.model.PerformancePoint
import com.ymz.basicplanner.model.filterByReps
import com.ymz.basicplanner.model.latest
import com.ymz.basicplanner.model.personalBest
import com.ymz.basicplanner.model.progressPercentSinceStart
import com.ymz.basicplanner.model.seriesForMovement
import com.ymz.basicplanner.model.shouldSeedGeneratedData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

class ProgressTrackingTest {

    @Test
    fun seriesForMovement_filters_sorts_and_scopes_to_active_program() {
        val benchId = 10L
        val squatId = 20L
        val programA = 100L
        val programB = 200L
        val entries = listOf(
            PerformanceEntry(
                id = 1L,
                movementId = benchId,
                activeProgramId = programA,
                date = LocalDate.of(2026, 8, 3),
                weightKg = 150.0
            ),
            PerformanceEntry(
                id = 2L,
                movementId = squatId,
                activeProgramId = programA,
                date = LocalDate.of(2026, 8, 2),
                weightKg = 999.0
            ),
            PerformanceEntry(
                id = 3L,
                movementId = benchId,
                activeProgramId = programA,
                date = LocalDate.of(2026, 8, 1),
                weightKg = 140.0
            ),
            PerformanceEntry(
                id = 4L,
                movementId = benchId,
                activeProgramId = programB,
                date = LocalDate.of(2026, 8, 4),
                weightKg = 500.0
            )
        )

        val series = entries.seriesForMovement(benchId, programA)

        assertEquals(
            listOf(LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 3)),
            series.map { it.date }
        )
        assertEquals(listOf(140.0, 150.0), series.map { it.value })
    }

    @Test
    fun seriesForMovement_returns_empty_when_no_active_program_is_attached() {
        val entries = listOf(
            PerformanceEntry(
                id = 1L,
                movementId = 10L,
                activeProgramId = 100L,
                date = LocalDate.of(2026, 8, 3),
                weightKg = 150.0
            )
        )

        assertTrue(entries.seriesForMovement(10L, null).isEmpty())
    }

    @Test
    fun filterByReps_keeps_only_entries_with_matching_reps() {
        val entries = listOf(
            PerformanceEntry(
                id = 1L,
                movementId = 10L,
                activeProgramId = 100L,
                date = LocalDate.of(2026, 8, 1),
                weightKg = 150.0,
                reps = 5
            ),
            PerformanceEntry(
                id = 2L,
                movementId = 10L,
                activeProgramId = 100L,
                date = LocalDate.of(2026, 8, 2),
                weightKg = 160.0,
                reps = 8
            )
        )

        val filtered = entries.filterByReps(5)

        assertEquals(1, filtered.size)
        assertEquals(150.0, filtered.first().weightKg, 0.01)
    }

    @Test
    fun personalBest_and_latest_read_the_right_points() {
        val points = listOf(
            PerformancePoint(LocalDate.of(2026, 8, 1), 150.0),
            PerformancePoint(LocalDate.of(2026, 8, 2), 200.0),
            PerformancePoint(LocalDate.of(2026, 8, 3), 180.0)
        )

        assertEquals(200.0, points.personalBest()?.value ?: 0.0, 0.0001)
        assertEquals(LocalDate.of(2026, 8, 3), points.latest()?.date)
    }

    @Test
    fun progressPercentSinceStart_compares_latest_to_first() {
        val points = listOf(
            PerformancePoint(LocalDate.of(2026, 8, 1), 150.0),
            PerformancePoint(LocalDate.of(2026, 8, 8), 200.0)
        )

        assertEquals(33.3333, points.progressPercentSinceStart() ?: 0.0, 0.01)
    }

    @Test
    fun progressPercentSinceStart_returns_null_when_start_is_zero_or_empty() {
        assertNull(emptyList<PerformancePoint>().progressPercentSinceStart())
        assertNull(
            listOf(
                PerformancePoint(LocalDate.of(2026, 8, 1), 0.0),
                PerformancePoint(LocalDate.of(2026, 8, 8), 200.0)
            ).progressPercentSinceStart()
        )
    }

    @Test
    fun shouldSeedGeneratedData_only_for_blank_unseeded_state() {
        val fresh = AppState()

        assertTrue(shouldSeedGeneratedData(fresh))
        assertFalse(shouldSeedGeneratedData(fresh.copy(hasSeededGeneratedData = true)))
        assertFalse(
            shouldSeedGeneratedData(
                fresh.copy(movements = listOf(MovementEntity(name = "Bench Press")))
            )
        )
    }

    @Test
    fun demoSeedData_builds_an_imaginary_progression_attached_to_the_program() {
        val movementIds = mapOf(
            "Barbell Bench Press" to 101L,
            "Back Squat" to 102L
        )
        val endDate = LocalDate.of(2026, 8, 5)
        val activeProgramId = 900L

        val entries = DemoSeedData.performanceEntries(
            movementIds = movementIds,
            endDate = endDate,
            pointsPerMovement = 8,
            activeProgramId = activeProgramId
        )

        val bench = entries.filter { it.movementId == 101L }.sortedBy { it.date }
        assertEquals(8, bench.size)
        assertEquals(150.0, bench.first().weightKg, 0.0001)
        assertEquals(200.0, bench.last().weightKg, 0.0001)
        assertTrue(bench.zipWithNext().all { (a, b) -> b.weightKg >= a.weightKg })
        assertTrue(entries.all { it.movementId in movementIds.values })
        assertTrue(entries.all { it.activeProgramId == activeProgramId })
    }
}
