package com.ymz.basicplanner.compose

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.ui.AccentGreen
import com.ymz.basicplanner.ui.AccentOrange
import com.ymz.basicplanner.ui.DarkSurfaceAlt
import com.ymz.basicplanner.ui.TextMuted
import com.ymz.basicplanner.ui.TextPrimary
import java.time.LocalDate
import java.time.YearMonth
import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.WeekEntity

data class CalendarDayInfo(
    val week: WeekEntity,
    val block: BlockEntity,
    val weekIndexInBlock: Int,
    val blockIndexInProgram: Int
)


class BlockIndicatorShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            moveTo(0f, 0f)
            lineTo(size.width * 0.9f, 0f)
            quadraticTo(
                size.width, 0f,
                size.width, size.height
            )
            lineTo(0f, size.height)
            close()
        })
    }
}

class WeekIndicatorShape : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        return Outline.Generic(Path().apply {
            moveTo(size.width, 0f)
            lineTo(size.width * 0.1f, 0f)
            quadraticTo(
                0f, 0f,
                0f, size.height
            )
            lineTo(size.width, size.height)
            close()
        })
    }
}

@Composable
fun WeekdayRow() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        val days = listOf(stringResource(R.string.day_monday_short),
            stringResource(R.string.day_tuesday_short),
            stringResource(R.string.day_wednesday_short),
            stringResource(R.string.day_thursday_short),
            stringResource(R.string.day_friday_short),
            stringResource(R.string.day_saturday_short),
            stringResource(R.string.day_sunday_short)
        )
        days.forEach { dow ->
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = dow,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = TextPrimary
                )
            }
        }
    }

}
@Composable
fun DayCell(
    date: LocalDate?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    mode: DayCellMode = DayCellMode.SEQUENCE,
    isOut: Boolean = false,
    sequenceValue: Int = 0,
    isGrouped: Boolean = false,
    isSelectionStart: Boolean = false,
    isSelectionEnd: Boolean = false,
    isCurrentMonth: Boolean = true,
    isToday: Boolean,
    groupColor: Color = AccentOrange,
    labels: List<String> = emptyList(),
    hasOngoingSession: Boolean = false
) {

    val strRest = stringResource(id = R.string.rest)
    val cornerSize = 12.dp
    val alpha = if (isCurrentMonth) 1f else 0.3f

    val cellShape = if (mode == DayCellMode.SEQUENCE && !isOut) {
        when {
            !isGrouped -> RoundedCornerShape(2.dp)
            isSelectionStart && isSelectionEnd -> RoundedCornerShape(cornerSize)
            isSelectionStart -> RoundedCornerShape(topStart = cornerSize, bottomStart = cornerSize)
            isSelectionEnd -> RoundedCornerShape(topEnd = cornerSize, bottomEnd = cornerSize)
            else -> RectangleShape
        }
    } else {
        RoundedCornerShape(2.dp)
    }

    val backgroundColor = when
    {
        isOut -> DarkSurfaceAlt.copy(alpha = alpha)
        mode == DayCellMode.SEQUENCE -> {
            if (isGrouped) groupColor.copy(alpha = 0.3f * alpha)
            else DarkSurfaceAlt.copy(alpha = alpha)
        }
        else -> {
            if (date != null) DarkSurfaceAlt.copy(alpha = alpha) else DarkSurfaceAlt.copy(alpha = alpha)
        }
    }

    val baseModifier = modifier
        .fillMaxSize()
        .padding(1.dp)

    val todayBorderModifier = if (isToday) {
        baseModifier.border(1.dp, AccentOrange, RoundedCornerShape(2.dp))
    } else {
        baseModifier
    }

    Box(
        modifier = todayBorderModifier
            .padding(1.dp)
            .clip(cellShape)
            .background(backgroundColor)
            .clickable(enabled = date != null && !isOut, onClick = onClick)
    ) {
        if (date != null) {
            BoxWithConstraints(modifier = Modifier
                .fillMaxSize()
                .padding(4.dp)) {
                val h = maxHeight
                val dateFontSize = (h.value / 5.5f).coerceIn(8f, 15f).sp

                Column(modifier = Modifier.fillMaxSize()) {

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Top
                    ) {
                        val dateTextColor = if (!isOut && mode == DayCellMode.SEQUENCE && isGrouped)
                            Color.White.copy(alpha = 0.7f)
                        else TextPrimary.copy(alpha = alpha)

                        Text(
                            text = date.dayOfMonth.toString(),
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Medium,
                            fontSize = dateFontSize,
                            color = dateTextColor
                        )


                        if (!isOut && hasOngoingSession && mode == DayCellMode.AGENDA) {
                            val indicatorSize = (h.value / 9f).coerceIn(4f, 8.dp.value).dp
                            Box(
                                modifier = Modifier
                                    .size(indicatorSize)
                                    .background(AccentGreen.copy(alpha = alpha), CircleShape)
                            )
                        }
                    }


                    if (!isOut) {
                        Spacer(modifier = Modifier.height(4.dp))

                        if (mode == DayCellMode.SEQUENCE) {
                            val valueColor = if (isGrouped) Color.White else AccentOrange.copy(alpha = if (isCurrentMonth) 1f else 0.4f)
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                Text(
                                    text = sequenceValue.toString(),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = valueColor
                                )
                            }
                        } else {
                            val sessionFontSize = (h.value / 9.5f).coerceIn(6f, 10f).sp
                            val maxVisibleSessions = if (h < 55.dp) 1 else 2

                            Column(verticalArrangement = Arrangement.spacedBy((-2).dp)) {
                                if (labels.isNotEmpty()) {
                                    labels.take(maxVisibleSessions).forEach { label ->
                                        MarqueeText(
                                            text = label,
                                            style = MaterialTheme.typography.bodySmall.copy(
                                                color = AccentOrange.copy(alpha = alpha),
                                                fontSize = sessionFontSize
                                            )
                                        )
                                    }
                                    if (labels.size > maxVisibleSessions) {
                                        val strMoreSessions = stringResource(id = R.string.agenda_view_more_sessions_format)
                                        Text(
                                            text = strMoreSessions.format(labels.size - maxVisibleSessions),
                                            style = MaterialTheme.typography.bodySmall,
                                            fontSize = sessionFontSize,
                                            color = TextMuted.copy(alpha = alpha),
                                            maxLines = 1
                                        )
                                    }
                                } else {
                                    Text(
                                        text = strRest,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextMuted.copy(alpha = alpha),
                                        fontSize = sessionFontSize,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun MonthGrid(
    yearMonth: YearMonth,
    daysByDate: Map<LocalDate, AgendaSlot>,
    creationState: CreationState,
    selectedActiveProgram: ActiveProgram?,
    onDayClick: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
    selectedViewKey: Long?,
    currentSelectionVisual: List<LocalDate>,
    sessionGroupIds: SnapshotStateMap<LocalDate, String>
) {
    val firstOfMonth = yearMonth.atDay(1)
    val startOffset = firstOfMonth.dayOfWeek.value - 1
    val firstCellDate = firstOfMonth.minusDays(startOffset.toLong())
    val today = LocalDate.now()

    Column(modifier = modifier.fillMaxWidth()) {
        val rowCount = 6
        val columnCount = 7
        for (row in 0 until rowCount) {
            Row(modifier = Modifier
                .fillMaxWidth()
                .weight(1f)) {
                for (col in 0 until columnCount) {
                    val cellIndex = row * columnCount + col
                    val date = firstCellDate.plusDays(cellIndex.toLong())
                    val isCurrentMonth = date.month == yearMonth.month

                    val slot = daysByDate[date]
                    val isDateInProgram = daysByDate.containsKey(date)

                    val isOut = !isDateInProgram

                    val forcedMode = if (selectedViewKey != null && isDateInProgram) {
                        DayCellMode.SEQUENCE
                    } else {
                        DayCellMode.AGENDA
                    }
                    val dayIndex = if (selectedActiveProgram != null) {
                        java.time.temporal.ChronoUnit.DAYS.between(selectedActiveProgram.startDate, date).toInt()
                    } else -1

                    val rmValue = if (isDateInProgram && selectedViewKey != null && dayIndex >= 0) {

                        selectedActiveProgram?.exerciseDailyRms?.get(selectedViewKey)?.getOrNull(dayIndex) ?: 0
                    } else 0

                    val labels = slot?.labels ?: emptyList()

                    val hasOngoing = selectedActiveProgram != null && creationState.sessions.any {
                        it.id in selectedActiveProgram.sessionIds &&
                                it.date?.toLocalDate() == date &&
                                it.finished != true
                    }

                    val groupId = sessionGroupIds[date]
                    val isBeingSelected = currentSelectionVisual.contains(date)
                    val showCapsule = isDateInProgram && (isBeingSelected || groupId != null)
                    val groupColor = if (isBeingSelected) Color.White else AccentOrange

                    DayCell(
                        date = date,
                        mode = forcedMode,
                        isOut = isOut,
                        isCurrentMonth = isCurrentMonth,
                        isToday = date == today,
                        sequenceValue = if (isDateInProgram) rmValue else 0,
                        isGrouped = showCapsule,
                        labels = labels,
                        hasOngoingSession = hasOngoing,
                        groupColor = groupColor,
                        isSelectionStart = showCapsule && (
                                (isBeingSelected && date == currentSelectionVisual.minOrNull()) ||
                                        (groupId != null && sessionGroupIds[date.minusDays(1)] != groupId)
                                ),
                        isSelectionEnd = showCapsule && (
                                (isBeingSelected && date == currentSelectionVisual.maxOrNull()) ||
                                        (groupId != null && sessionGroupIds[date.plusDays(1)] != groupId)
                                ),
                        onClick = {
                            if (selectedViewKey != null) {
                                if (isDateInProgram) onDayClick(date)
                            } else {
                                onDayClick(date)
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxHeight()
                    )
                }
            }
        }
    }
}
