package com.ymz.basicplanner

import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.PerformanceEntry
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.bestPerformanceFromSets
import com.ymz.basicplanner.model.sessionBestPerformanceEntries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SessionAutoTrackingTest {

    @Test
    fun bestPerformanceFromSets_uses_only_finished_sets() {
        val baseRm = 100
        val entries = listOf(
            SetEntry(sets = 3, reps = 5, intensity = 80, rest = 90, finished = true),
            SetEntry(sets = 1, reps = 10, intensity = 90, rest = 90, finished = false),
            SetEntry(sets = 2, reps = 8, intensity = 85, rest = 120) // null = not finished
        )

        val best = bestPerformanceFromSets(entries, baseRm)

        assertNotNull(best)
        assertEquals(80.0, best!!.first, 0.01)
        assertEquals(5, best.second)
    }

    @Test
    fun bestPerformanceFromSets_returns_null_when_no_set_is_finished() {
        val entries = listOf(
            SetEntry(sets = 1, reps = 10, intensity = 90, rest = 90, finished = false)
        )
        assertNull(bestPerformanceFromSets(entries, 100))
    }

    @Test
    fun bestPerformanceFromSets_returns_null_when_baseRm_is_missing() {
        assertNull(bestPerformanceFromSets(listOf(SetEntry(sets = 1, reps = 10, intensity = 80, rest = 60)), null))
    }

    @Test
    fun bestPerformanceFromSets_empty_entries_returns_null() {
        assertNull(bestPerformanceFromSets(emptyList(), 100))
    }

    @Test
    fun sessionBestPerformanceEntries_creates_one_entry_per_reps_with_max_weight() {
        val movementId = 10L
        val exercise = ExerciseEntity(
            id = 1L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 3, reps = 5, intensity = 80, rest = 120, finished = true),
                SetEntry(sets = 1, reps = 10, intensity = 90, rest = 120, finished = true),
                SetEntry(sets = 2, reps = 5, intensity = 85, rest = 120, finished = true)
            )
        )

        val session = SessionEntity(
            id = 100L,
            name = "Push Day",
            exerciseIds = mutableListOf(exercise.id),
            date = java.time.LocalDateTime.of(2026, 8, 6, 18, 0),
            baseRms = mapOf(movementId to 200)
        )

        val activeProgramId = 999L
        val entries = sessionBestPerformanceEntries(
            session = session,
            exercises = listOf(exercise),
            activeProgramId = activeProgramId
        )

        assertEquals(2, entries.size)
        val byReps = entries.associate { it.reps to it.weightKg }
        assertEquals(170.0, byReps[5] ?: 0.0, 0.01) // max(160, 170) for 5 reps
        assertEquals(180.0, byReps[10] ?: 0.0, 0.01) // 90% of 200
        assertTrue(entries.all { it.activeProgramId == activeProgramId })
        assertTrue(entries.all { it.sessionId == session.id })
        assertTrue(entries.all { it.note.contains("auto", ignoreCase = true) })
    }

    @Test
    fun sessionBestPerformanceEntries_applies_adjustment_percentage() {
        val movementId = 10L
        val exercise = ExerciseEntity(
            id = 1L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120, finished = true)
            )
        )
        val session = SessionEntity(
            id = 100L,
            name = "Push Day",
            exerciseIds = mutableListOf(exercise.id),
            date = java.time.LocalDateTime.of(2026, 8, 6, 18, 0),
            baseRms = mapOf(movementId to 100)
        )

        val entries = sessionBestPerformanceEntries(
            session = session,
            exercises = listOf(exercise),
            activeProgramId = 999L,
            adjustmentPct = 10
        )

        // baseRm 100 +10% = 110, at 80% intensity = 88.
        assertEquals(88.0, entries.first().weightKg, 0.01)
    }

    @Test
    fun bestPerformanceFromSets_uses_direct_weight_for_added_sets() {
        val baseRm = 100
        val entries = listOf(
            SetEntry(sets = 1, reps = 5, intensity = 80, rest = 90, finished = true), // planned = 80 kg
            SetEntry(sets = 1, reps = 5, intensity = 0, rest = 90, finished = true, weightKg = 95) // added = 95 kg
        )

        val best = bestPerformanceFromSets(entries, baseRm)

        assertNotNull(best)
        assertEquals(95.0, best!!.first, 0.01)
        assertEquals(5, best.second)
    }

    @Test
    fun sessionBestPerformanceEntries_uses_direct_weight_for_added_sets() {
        val movementId = 10L
        val exercise = ExerciseEntity(
            id = 1L,
            movementId = movementId,
            name = "Bench",
            entries = mutableListOf(
                SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120, finished = true), // 80% of 200 = 160
                SetEntry(sets = 1, reps = 5, intensity = 0, rest = 120, finished = true, weightKg = 170) // added, 170 kg
            )
        )
        val session = SessionEntity(
            id = 100L,
            name = "Push Day",
            exerciseIds = mutableListOf(exercise.id),
            date = java.time.LocalDateTime.of(2026, 8, 6, 18, 0),
            baseRms = mapOf(movementId to 200)
        )

        val entries = sessionBestPerformanceEntries(
            session = session,
            exercises = listOf(exercise),
            activeProgramId = 999L
        )

        // 5 reps: max(160 planned, 170 added) = 170
        assertEquals(1, entries.size)
        assertEquals(5, entries.first().reps)
        assertEquals(170.0, entries.first().weightKg, 0.01)
    }
}
