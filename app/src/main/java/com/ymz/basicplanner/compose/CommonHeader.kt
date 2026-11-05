package com.ymz.basicplanner.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.ui.AccentOrange

@Composable
fun ScrollingText(
    text: String,
    modifier: Modifier = Modifier
) {
    val headerTextStyle = MaterialTheme.typography.titleLarge.copy(
        fontWeight = FontWeight.Bold,
        fontSize = 22.sp,
        color = AccentOrange
    )
    MarqueeText(
        text = text,
        modifier = modifier.fillMaxWidth(),
        style = headerTextStyle
    )
}

@Composable
fun CommonHeader(
    modifier: Modifier = Modifier,
    startContent: (@Composable RowScope.() -> Unit)? = null,
    centerContent: @Composable RowScope.() -> Unit,
    endContent: (@Composable RowScope.() -> Unit)? = null
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp, vertical = 8.dp), // Standardized padding
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (startContent != null) {
            Row(
                modifier = Modifier.weight(0.4f),
                verticalAlignment = Alignment.CenterVertically
            ) {
                startContent()
            }
        }

        Row(
            modifier = Modifier.weight(0.6f),
            verticalAlignment = Alignment.CenterVertically
        ) {
            centerContent()
        }

        if (endContent != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically
            ) {
                endContent()
            }
        }
    }
}
