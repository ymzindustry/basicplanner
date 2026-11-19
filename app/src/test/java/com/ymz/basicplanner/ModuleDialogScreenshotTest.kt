package com.ymz.basicplanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.ui.DarkSurfaceAlt
import com.ymz.basicplanner.ui.ModuleFormPreview
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

/** Renders the module form's code-shaped content → /tmp/dialog.png (dialogs can't be captured). */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModuleDialogScreenshotTest {

    @Test
    fun module_form_preview_renders() {
        val family = ItemFamily(id = 90L, name = "form", description = "", itemType = ItemType.INT)

        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent {
                Column(
                    Modifier
                        .padding(20.dp)
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(28.dp))
                        .background(DarkSurfaceAlt)
                        .padding(24.dp)
                ) {
                    ModuleFormPreview(
                        name = "deadlift_form",
                        onNameChange = {},
                        chosen = family,
                        canSelect = true,
                        onSelectItem = {}
                    )
                }
            }
        }
        activity.setContentView(view)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val width = 1080
        val height = 600
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(height, View.MeasureSpec.AT_MOST)
        )
        val measured = view.measuredHeight
        view.layout(0, 0, width, measured)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()

        val bitmap = Bitmap.createBitmap(width, measured, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File("/tmp/dialog.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }
}
