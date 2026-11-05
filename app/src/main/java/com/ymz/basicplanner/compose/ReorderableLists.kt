package com.ymz.basicplanner.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import com.ymz.basicplanner.ui.*

@Composable
fun <T> DraggableReorderableList(
    items: MutableList<T>,
    listState: LazyListState,
    modifier: Modifier = Modifier.fillMaxWidth().heightIn(max = 250.dp),
    onRemove: ((index: Int) -> Unit)? = null,
    customSwapLogic: ((from: Int, to: Int) -> Unit)? = null,
    onItemClick: ((index: Int, item: T) -> Unit)? = null,
    /** Lets the caller decorate a single row (e.g. the orange focus frame of a week day). */
    rowModifier: (Int) -> Modifier = { Modifier },
    trailingContent: (@Composable RowScope.(Int) -> Unit)? = null,
    content: @Composable RowScope.(index: Int, item: T) -> Unit
) {

    var draggingIndex by remember { mutableStateOf<Int?>(null) }
    var dragAccumulated by remember { mutableFloatStateOf(0f) }
    LazyColumn(
        state = listState,
        modifier = modifier,
        verticalArrangement = Arrangement.Top
    ) {
        items(items.size, key = { index -> index }) { index ->
            val item = items[index]
            val isDragging = draggingIndex == index
            val isFirst = index == 0
            val isLast = index == items.lastIndex
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(IntrinsicSize.Min)
                    .pointerInput(items.size) {
                        detectDragGesturesAfterLongPress(
                            onDragStart = { draggingIndex = index; dragAccumulated = 0f },
                            onDrag = { change, dragAmount ->
                                change.consume()
                                dragAccumulated += dragAmount.y
                                val current = draggingIndex ?: index
                                val targetIndex = if (dragAccumulated < -24.dp.toPx() && current > 0) current - 1
                                else if (dragAccumulated > 24.dp.toPx() && current < items.lastIndex) current + 1
                                else null
                                if (targetIndex != null) {
                                    if (customSwapLogic != null) {
                                        customSwapLogic(current, targetIndex)
                                    } else {
                                        val moved = items.removeAt(current)
                                        items.add(targetIndex, moved)
                                    }
                                    draggingIndex = targetIndex
                                    dragAccumulated = 0f
                                }
                            },
                            onDragEnd = { draggingIndex = null },
                            onDragCancel = { draggingIndex = null }
                        )
                    }
                    .background(if (isDragging) DarkSurfaceAlt.copy(alpha = 0.5f) else Color.Transparent)
                    .then(rowModifier(index)),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Box(
                    modifier = Modifier
                        .width(42.dp)
                        .fillMaxHeight(),
                    contentAlignment = Alignment.Center
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxHeight()
                            .width(2.dp)
                            .background(if (isFirst && isLast) Color.Transparent else TextMuted.copy(alpha = 0.4f))
                            .padding(
                                top = if (isFirst) 20.dp else 0.dp,
                                bottom = if (isLast) 20.dp else 0.dp
                            )
                    )
                    Surface(
                        shape = androidx.compose.foundation.shape.CircleShape,
                        color = DarkSurfaceAlt,
                        border = BorderStroke(1.dp, if (isDragging) AccentOrange else TextMuted),
                        modifier = Modifier.size(24.dp)
                    ) {
                        if (isDragging) {
                            Icon(
                                imageVector = Icons.Default.UnfoldMore,
                                contentDescription = null,
                                tint = AccentOrange,
                                modifier = Modifier.padding(4.dp)
                            )
                        } else {
                            Box(
                                modifier = Modifier
                                    .padding(8.dp)
                                    .background(TextMuted, androidx.compose.foundation.shape.CircleShape)
                            )
                        }
                    }
                }
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .then(
                            if (onItemClick != null) Modifier.clickable { onItemClick(index, item) }
                            else Modifier
                        )
                        .padding(vertical = 12.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        content(index, item)
                    }
                }
                if (trailingContent != null) {
                    trailingContent(index)
                }
                if (onRemove != null) {
                    IconButton(onClick = { onRemove(index) }) {
                        Icon(Icons.Default.Close, "Remove", tint = AccentRed.copy(alpha = 0.7f))
                    }
                }
            }
        }
    }
}