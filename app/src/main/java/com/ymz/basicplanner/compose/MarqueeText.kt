package com.ymz.basicplanner.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.delay

private const val SPEED_PX_PER_SECOND = 40f
private const val LOOP_GAP_DP = 28f

/** The text stays at rest, fully readable from its first frame, before it starts to travel:
 *  4 s minimum, plus extra time for long messages, capped so it never feels stuck. */
private const val REST_BASE_MS = 4000L
private const val REST_PER_CHAR_MS = 40L
private const val REST_MAX_MS = 9000L

/**
 * Text that scrolls like a ticker instead of being truncated with an ellipsis.
 *
 * If the text fits its box, a single static copy is drawn — nothing moves and nothing is
 * duplicated. Only when it actually overflows does it turn into a ticker: one direction
 * (right → left), the text drawn twice with a gap, so the tail is followed by its own head and the
 * wrap is invisible; a continuous circular movement, never a jump back.
 *
 * Built from `Box`/`Row`/`Text` only — deliberately **no** `SubcomposeLayout` (BoxWithConstraints,
 * LazyList, …), because a parent that measures intrinsic sizes (the entity tree uses
 * `Modifier.height(IntrinsicSize.Min)`) throws on those.
 */
@Composable
fun MarqueeText(
    text: String,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit? = null
) {
    MarqueeText(
        text = AnnotatedString(text),
        modifier = modifier,
        style = style,
        color = color,
        fontWeight = fontWeight,
        fontSize = fontSize
    )
}

@Composable
fun MarqueeText(
    text: AnnotatedString,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
    fontWeight: FontWeight? = null,
    fontSize: TextUnit? = null
) {
    val resolvedStyle = style.copy(
        color = if (color == Color.Unspecified) style.color else color,
        fontWeight = fontWeight ?: style.fontWeight,
        fontSize = fontSize ?: style.fontSize
    )
    val measurer = rememberTextMeasurer()
    val textWidthPx = remember(text, resolvedStyle) {
        measurer.measure(text = text, style = resolvedStyle, maxLines = 1, softWrap = false).size.width
    }
    val gap = with(LocalDensity.current) { LOOP_GAP_DP.dp.toPx() }

    // Width available for a *single* copy, sampled only while a single copy is on screen so the
    // trailing copy can never feed its own width back into the decision.
    var singleWidth by remember(text, resolvedStyle) { mutableIntStateOf(0) }
    val overflowing = singleWidth > 0 && textWidthPx > singleWidth

    if (!overflowing) {
        Box(
            modifier = modifier
                .clip(RectangleShape)
                .onGloballyPositioned { singleWidth = it.size.width },
            contentAlignment = Alignment.CenterStart
        ) {
            Text(
                text = text,
                style = resolvedStyle,
                maxLines = 1,
                softWrap = false,
                modifier = Modifier.wrapContentWidth(unbounded = true, align = Alignment.Start)
            )
        }
    } else {
        val period = textWidthPx + gap
        val duration = (period / SPEED_PX_PER_SECOND * 1000f).toInt().coerceAtLeast(600)
        val restMs = (REST_BASE_MS + text.text.length * REST_PER_CHAR_MS).coerceAtMost(REST_MAX_MS)
        val scrollX = remember(text, resolvedStyle, period) { Animatable(0f) }

        LaunchedEffect(text, resolvedStyle, period, duration, restMs) {
            // Shown at rest first (offset 0 = the text starts at the left edge, fully visible),
            // then it begins to scroll and keeps going in one direction.
            scrollX.snapTo(0f)
            delay(restMs)
            while (true) {
                scrollX.animateTo(-period, tween(durationMillis = duration, easing = LinearEasing))
                scrollX.snapTo(0f)
            }
        }

        Box(modifier = modifier.clip(RectangleShape), contentAlignment = Alignment.CenterStart) {
            Row(
                modifier = Modifier
                    .offset { IntOffset(scrollX.value.toInt(), 0) }
                    // align = Start is essential: the default is Center, which centres the overflow
                    // and shows the tail of the text plus a gap at rest.
                    .wrapContentWidth(unbounded = true, align = Alignment.Start),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = text, style = resolvedStyle, maxLines = 1, softWrap = false)
                Spacer(modifier = Modifier.width(LOOP_GAP_DP.dp))
                // Trailing copy: once the offset reaches the period it sits exactly where the
                // leading copy started, so wrapping back to 0 is invisible.
                Text(text = text, style = resolvedStyle, maxLines = 1, softWrap = false)
            }
        }
    }
}
