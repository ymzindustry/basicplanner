package com.ymz.basicplanner

import com.ymz.basicplanner.ui.tutorialSteps
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Locks the tutorial step sequence so the guided exercise creation cannot silently regress.
 */
class TutorialFlowTest {

    @Test
    fun firstStep_isWelcomeDialogWithGoAndSkip() {
        val first = tutorialSteps.first()

        assertNotNull(first.dialogTitle)
        assertEquals("Go", first.goLabel)
        assertTrue(first.showSkip)
        assertFalse(first.inDialog)
    }

    @Test
    fun creationGuide_isFollowedByTheInDialogStep() {
        val creationGuideIndex = tutorialSteps.indexOfFirst { it.targetKey == "creation_add_exercise" }
        assertTrue("creation_add_exercise guide step must exist", creationGuideIndex >= 0)

        val dialogStep = tutorialSteps.getOrNull(creationGuideIndex + 1)
        assertNotNull("an in-dialog step must follow the creation guide", dialogStep)
        assertTrue("the step after the creation guide drives the create dialog", dialogStep!!.inDialog)
    }

    @Test
    fun inDialogStep_hasNoOverlayGuidance() {
        val dialogStep = tutorialSteps.first { it.inDialog }

        // Guidance for this step is rendered by the dialog itself, not by the activity overlay.
        assertEquals(null, dialogStep.guideText)
        assertEquals(null, dialogStep.dialogTitle)
        assertEquals(null, dialogStep.targetKey)
    }

    @Test
    fun lastStep_isCompletionSlideWithFinishOnly() {
        val last = tutorialSteps.last()

        assertNotNull("the tutorial must end on a completion slide", last.dialogTitle)
        assertEquals("Finish", last.goLabel)
        assertFalse("the completion slide has no Skip button", last.showSkip)
        assertFalse(last.inDialog)
    }
}
