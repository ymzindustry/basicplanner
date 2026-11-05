package com.ymz.basicplanner

import androidx.compose.ui.test.junit4.createComposeRule
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.performClick
import com.ymz.basicplanner.compose.EntityTreeItem
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.SetEntry
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.WeekEntity
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalTime

/**
 * Expanding a program in the Creation screen must render its tree without blowing up. This runs
 * the real composables through Robolectric so a layout/composition crash is caught here.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
class ExpandTreeUiTest {

    @get:Rule
    val rule = createComposeRule()

    @Test
    fun expanding_a_program_renders_its_tree() {
        val cs = CreationState()
        val movement = MovementEntity(id = 1L, name = "Deadlift")
        val exercise = ExerciseEntity(
            id = 2L,
            movementId = 1L,
            name = "Medium",
            entries = mutableListOf(SetEntry(sets = 1, reps = 5, intensity = 80, rest = 120))
        )
        val session = SessionEntity(id = 3L, name = "Pull", exerciseIds = mutableListOf(2L))
        val timed = TimedSessionEntity(id = 4L, name = "10:00", time = LocalTime.of(10, 0), sessionId = 3L)
        val day = DayEntity(id = 5L, name = "Pull day", timedSessionIds = mutableListOf(4L))
        val week = WeekEntity(id = 6L, name = "Week 1", dayIds = mutableListOf(5L, -1L, -1L, -1L, -1L, -1L, -1L))
        val block = BlockEntity(id = 7L, name = "BLOCK", weekIds = mutableListOf(6L))
        val program = ProgramEntity(id = 8L, name = "MY PROGRAM", blockIds = mutableListOf(7L))

        cs.movements.add(movement)
        cs.exercises.add(exercise)
        cs.sessions.add(session)
        cs.timedSessions.add(timed)
        cs.days.add(day)
        cs.weeks.add(week)
        cs.blocks.add(block)
        cs.programs.add(program)
        cs.recomputeBlockLevels()

        rule.setContent {
            EntityTreeItem(
                entity = program,
                kind = EntityKind.PROGRAM,
                creationState = cs,
                isRoot = true,
                actionContent = { _, _, _ -> }
            )
        }
        rule.waitForIdle()

        // The root itself has no row (isRoot renders only its children), so the Program's
        // children are already on screen: expand the block, which is the failing path.
        rule.onAllNodesWithText("BLOCK", substring = true).onFirst().performClick()
        rule.waitForIdle()

        rule.onAllNodesWithText("Week 1", substring = true).onFirst().assertExists()
    }
}
