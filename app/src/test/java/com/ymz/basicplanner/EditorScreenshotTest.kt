package com.ymz.basicplanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.ui.platform.ComposeView
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.module.ModuleDefinition
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
        creationState.itemFamilies.addAll(
            listOf(
                ItemFamily(id = 90L, name = "form", description = "", itemType = ItemType.INT, defaultValue = "7"),
                ItemFamily(id = 92L, name = "sleep", description = "", itemType = ItemType.FLOAT, defaultValue = "7.5"),
                ItemFamily(id = 93L, name = "pain", description = "", itemType = ItemType.INT, defaultValue = "0")
            )
        )
        creationState.modules.add(
            ModuleDefinition(
                id = 1L,
                name = "Deadlift form",
                description = "Lowers reps when form is poor",
                code = """
                    def apply(item):
                        # item comes in already typed
                        # deltas, from a zero baseline
                        if item >= 8:
                            # drained: one set less, 10% lighter
                            return -1, -1, -10.0, 60

                        return 0, 0, 0.0, 0
                """.trimIndent(),
                itemFamilyId = 90L,
                itemName = "form"
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
