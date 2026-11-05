package com.ymz.basicplanner.compose

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.ui.*
@Composable
fun CommonBottomBar(
    leftButtons: @Composable RowScope.() -> Unit = {},
    rightButtons: @Composable RowScope.() -> Unit = {}
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 56.dp)
            .background(Color.Black)
            .drawBehind {
                drawLine(
                    color = AccentOrange,
                    start = Offset(0f, 0f),
                    end = Offset(size.width, 0f),
                    strokeWidth = 2.dp.toPx()
                )
            }
            .padding(horizontal = 8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                leftButtons()
            }
            Spacer(modifier = Modifier.weight(1f))

            Row(verticalAlignment = Alignment.CenterVertically) {
                rightButtons()
            }
        }
    }
}

@Composable
fun ProgressCircle(
    percent: Float,
    modifier: Modifier = Modifier,
    color: Color = AccentOrange,
    strokeWidth: Float = 8f
) {
    Box(contentAlignment = Alignment.Center, modifier = modifier) {
        Canvas(modifier = Modifier.fillMaxSize()) {

            drawCircle(
                color = Color.White.copy(alpha = 0.1f),
                style = androidx.compose.ui.graphics.drawscope.Stroke(width = strokeWidth)
            )

            drawArc(
                color = color,
                startAngle = -90f,
                sweepAngle = 360f * percent,
                useCenter = false,
                style = androidx.compose.ui.graphics.drawscope.Stroke(
                    width = strokeWidth,
                    cap = StrokeCap.Round
                )
            )
        }
        Text(
            text = "${(percent * 100).toInt()}%",
            color = TextPrimary,
            fontSize = 10.sp,
            fontWeight = FontWeight.Bold
        )
    }
}

@Composable
fun NumberInputField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    /** Optional tutorial highlight: the field's own outline pulses with this colour. */
    highlightColor: Color? = null
) {
    val focusRequester = remember { FocusRequester() }
    var textFieldValueState by remember {
        mutableStateOf(TextFieldValue(text = value, selection = TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (textFieldValueState.text != value) {
            textFieldValueState = textFieldValueState.copy(text = value, selection = TextRange(value.length))
        }
    }
    Box(modifier = modifier) {
        OutlinedTextField(
            value = textFieldValueState,
            onValueChange = { newValue ->
                val filtered = newValue.text.filter { it.isDigit() }
                textFieldValueState = newValue.copy(text = filtered)
                onValueChange(filtered)
            },
            label = { Text(label, fontSize = 10.sp) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth().focusRequester(focusRequester),
            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = highlightColor ?: TextMuted, focusedBorderColor = AccentOrange,
                unfocusedLabelColor = highlightColor ?: TextMuted, focusedLabelColor = AccentOrange,
                cursorColor = AccentOrange, focusedTextColor = TextPrimary, unfocusedTextColor = TextPrimary
            )
        )
        Box(
            modifier = Modifier.matchParentSize().pointerInput(value) {
                detectTapGestures(onTap = {
                    focusRequester.requestFocus()
                    textFieldValueState = textFieldValueState.copy(selection = TextRange(textFieldValueState.text.length))
                })
            }
        )
    }
}