package com.ymz.basicplanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleInputSpec
import com.ymz.basicplanner.model.module.ModuleTargetKind
import com.ymz.basicplanner.ui.ModulesScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Headless render of the Modules list → /tmp/modules.png. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModulesScreenshotTest {

    @Test
    fun modules_list_renders() {
        val cs = CreationState()
        cs.modules.add(
            ModuleDefinition(
                id = 1L,
                name = "Deadlift form",
                description = "Lowers reps when form is poor",
                inputs = listOf(ModuleInputSpec("form", "int"))
            )
        )
        cs.modules.add(
            ModuleDefinition(
                id = 2L,
                name = "Deload week",
                description = "Drops intensity on the last week",
                inputs = listOf(ModuleInputSpec("week", "int"))
            )
        )
        cs.moduleAttachments.add(
            ModuleAttachment(
                id = 10L,
                moduleId = 1L,
                programId = 70L,
                targetKind = ModuleTargetKind.DAY,
                targetId = 40L
            )
        )

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent { ModulesScreen(creationState = cs, onBack = {}) }
        }
        activity.setContentView(view)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val width = 1080
        val height = 1600
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("/tmp/modules.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
