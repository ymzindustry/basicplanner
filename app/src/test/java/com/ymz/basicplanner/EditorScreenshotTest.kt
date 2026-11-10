package com.ymz.basicplanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleInputSpec
import com.ymz.basicplanner.ui.ModuleEditorScreen
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/**
 * Draws the module editor off-screen and writes /tmp/editor.png, so the layout can be inspected
 * without a device. Uses a plain ComposeView + manual draw because the Compose test clock never
 * goes idle with a text field in the tree.
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class EditorScreenshotTest {

    @Test
    fun module_editor_renders() {
        val creationState = CreationState()
        creationState.modules.add(
            ModuleDefinition(
                id = 1L,
                name = "Deadlift form",
                description = "Lowers reps when form is poor",
                code = "def apply(ctx):\n" +
                    "    form = ctx[\"items\"].get(\"form\", 10)\n" +
                    "    if form >= 8:\n" +
                    "        return []\n" +
                    "    return [{\"set_id\": s[\"id\"], \"reps_delta\": -1} for s in ctx[\"sets\"]]\n",
                inputs = listOf(ModuleInputSpec("form", "int"))
            )
        )

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent {
                ModuleEditorScreen(creationState = creationState, moduleId = 1L, onBack = {})
            }
        }
        activity.setContentView(view)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val width = 1080
        val height = 2340
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.EXACTLY)
        )
        view.layout(0, 0, width, height)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("/tmp/editor.png").outputStream().use { out ->
            bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
    }
}
