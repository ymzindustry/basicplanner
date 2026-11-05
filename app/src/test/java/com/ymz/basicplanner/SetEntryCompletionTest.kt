package com.ymz.basicplanner

import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.allComplete
import com.ymz.basicplanner.model.isComplete
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The OK button of the exercise dialog is only enabled once the four values of a set row are
 * filled: sets, reps, intensity (%) and rest.
 */
class SetEntryCompletionTest {

    private fun entry(sets: Int, reps: Int, intensity: Int, rest: Int) =
        SetEntry(id = 1L, sets = sets, reps = reps, intensity = intensity, rest = rest)

    @Test
    fun a_newly_added_row_is_not_complete() {
        // The Add button creates an all-zero row, which must keep OK disabled.
        assertFalse(entry(0, 0, 0, 0).isComplete())
    }

    @Test
    fun every_value_must_be_filled() {
        assertTrue(entry(3, 10, 80, 120).isComplete())
        assertFalse("no sets", entry(0, 10, 80, 120).isComplete())
        assertFalse("no reps", entry(3, 0, 80, 120).isComplete())
        assertFalse("no intensity", entry(3, 10, 0, 120).isComplete())
        assertFalse("no rest", entry(3, 10, 80, 0).isComplete())
    }

    @Test
    fun an_exercise_needs_at_least_one_row() {
        assertFalse(emptyList<SetEntry>().allComplete())
    }

    @Test
    fun every_row_of_the_exercise_must_be_complete() {
        val complete = entry(3, 10, 80, 120)
        assertTrue(listOf(complete, complete.copy(id = 2L)).allComplete())
        assertFalse(listOf(complete, entry(0, 0, 0, 0).copy(id = 3L)).allComplete())
    }

    @Test
    fun values_are_read_back_unchanged() {
        val e = entry(4, 8, 75, 90)
        assertEquals(4, e.sets)
        assertEquals(8, e.reps)
        assertEquals(75, e.intensity)
        assertEquals(90, e.rest)
    }
}
