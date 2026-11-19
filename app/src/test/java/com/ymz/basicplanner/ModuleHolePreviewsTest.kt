package com.ymz.basicplanner

import android.graphics.Bitmap
import android.graphics.Canvas
import android.view.View
import androidx.activity.ComponentActivity
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.ComposeView
import androidx.compose.ui.unit.dp
import com.ymz.basicplanner.ui.HoleVariantA
import com.ymz.basicplanner.ui.HoleVariantB
import com.ymz.basicplanner.ui.HoleVariantC
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import org.robolectric.shadows.ShadowLooper
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33], qualifiers = "w411dp-h891dp-xxhdpi")
@GraphicsMode(GraphicsMode.Mode.NATIVE)
class ModuleHolePreviewsTest {

    private fun render(content: @androidx.compose.runtime.Composable () -> Unit, out: String) {
        val activity = Robolectric.buildActivity(ComponentActivity::class.java).setup().get()
        val view = ComposeView(activity).apply {
            setContent {
                Box(Modifier.fillMaxSize().background(Color(0xFF0A0A0C)).padding(8.dp)) { content() }
            }
        }
        activity.setContentView(view)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val width = 1080
        view.measure(
            View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY),
            View.MeasureSpec.makeMeasureSpec(900, View.MeasureSpec.AT_MOST)
        )
        val measured = view.measuredHeight
        view.layout(0, 0, width, measured)
        ShadowLooper.runUiThreadTasksIncludingDelayedTasks()
        val bitmap = Bitmap.createBitmap(width, measured, Bitmap.Config.ARGB_8888)
        view.draw(Canvas(bitmap))
        File(out).outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
    }

    @Test
    fun a() = render({ HoleVariantA() }, "/tmp/hole_a.png")

    @Test
    fun b() = render({ HoleVariantB() }, "/tmp/hole_b.png")

    @Test
    fun c() = render({ HoleVariantC() }, "/tmp/hole_c.png")
}
