package com.ymz.basicplanner.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.RadioButton
import androidx.compose.material3.TextButton
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.R
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.CommonHeader
import com.ymz.basicplanner.compose.ScrollingText
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.MovementEntity
import com.ymz.basicplanner.model.PerformanceEntry
import com.ymz.basicplanner.model.PerformancePoint
import com.ymz.basicplanner.model.PlanSegment
import com.ymz.basicplanner.model.filterByReps
import com.ymz.basicplanner.model.goalSeriesForMovement
import com.ymz.basicplanner.model.latest
import com.ymz.basicplanner.model.personalBest
import com.ymz.basicplanner.model.plannedRepsForMovement
import com.ymz.basicplanner.model.plannedVariantsForMovement
import com.ymz.basicplanner.model.programDateRange
import com.ymz.basicplanner.model.programSegments
import com.ymz.basicplanner.model.progressPercentSinceStart
import com.ymz.basicplanner.model.variantName
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

@Composable
fun DashboardScreen(
    creationState: CreationState,
    onBack: () -> Unit
) {
    val attachedActiveProgram = creationState.activePrograms
        .find { it.id == creationState.selectedActiveProgramId }
        ?: creationState.activePrograms.firstOrNull()
    val attachedActiveProgramId = attachedActiveProgram?.id
    val attachedProgram = attachedActiveProgram?.let { activeProgram ->
        creationState.programs.find { it.id == activeProgram.programId }
    }
    val programMovementIds = attachedProgram?.let {
        creationState.getUniqueMovementIdsForProgram(it).toSet()
    }
    val movements = creationState.movements
        .filter { programMovementIds == null || it.id in programMovementIds }
        .sortedBy { it.name.lowercase(Locale.getDefault()) }
    var selectedMovementId by remember { mutableStateOf<Long?>(null) }
    var selectedReps by remember { mutableIntStateOf(1) }
    // Variant filter: null = every variant of the movement ("Deadlift" as a whole).
    var selectedVariant by remember { mutableStateOf<String?>(null) }
    var showVariantDialog by remember { mutableStateOf(false) }
    // Block bands on the chart: 0 = blocks directly in the program, deeper = sub-blocks,
    // -1 = one band per week. Blocks are the default view.
    var blockLevel by remember { mutableIntStateOf(0) }
    var showBlockLevelDialog by remember { mutableStateOf(false) }

    LaunchedEffect(movements) {
        if (movements.isNotEmpty() && movements.none { it.id == selectedMovementId }) {
            selectedMovementId = movements.first().id
        }
    }

    val selectedMovement = movements.find { it.id == selectedMovementId }
    val appState = remember(creationState.dataVersion.value) { creationState.toAppState() }
    val movementEntries = selectedMovementId?.let { movementId ->
        creationState.performancesForMovement(movementId, attachedActiveProgramId)
    } ?: emptyList()
    val variants = remember(selectedMovementId, attachedActiveProgramId, movementEntries, creationState.dataVersion.value) {
        val planned = if (selectedMovementId != null && attachedActiveProgramId != null) {
            plannedVariantsForMovement(appState, attachedActiveProgramId, selectedMovementId!!)
        } else {
            emptyList()
        }
        (planned + movementEntries.map { it.variantName(appState) })
            .filter { it.isNotBlank() }
            .distinct()
            .sortedBy { it.lowercase(Locale.getDefault()) }
    }
    val allEntries = if (selectedVariant == null) {
        movementEntries
    } else {
        movementEntries.filter { it.variantName(appState).equals(selectedVariant, ignoreCase = true) }
    }
    val realReps = allEntries.map { it.reps }.distinct()
    val plannedReps = remember(selectedMovementId, selectedVariant, attachedActiveProgramId, creationState.dataVersion.value) {
        val movementId = selectedMovementId
        if (movementId != null && attachedActiveProgramId != null) {
            plannedRepsForMovement(
                app = appState,
                activeProgramId = attachedActiveProgramId,
                movementId = movementId,
                variant = selectedVariant
            )
        } else {
            emptyList()
        }
    }
    val availableReps = (realReps + plannedReps).distinct().sorted()

    LaunchedEffect(selectedMovementId) {
        selectedVariant = null
    }

    LaunchedEffect(variants) {
        val current = selectedVariant
        if (current != null && variants.none { it.equals(current, ignoreCase = true) }) {
            selectedVariant = null
        }
    }

    LaunchedEffect(selectedMovementId, selectedVariant, availableReps) {
        if (availableReps.isNotEmpty() && selectedReps !in availableReps) {
            selectedReps = availableReps.minByOrNull { kotlin.math.abs(it - selectedReps) } ?: availableReps.first()
        }
    }

    val programRange = remember(attachedActiveProgramId, creationState.dataVersion.value) {
        attachedActiveProgramId?.let { programDateRange(appState, it) }
    }

    val deepestBlockLevel = attachedProgram?.let { creationState.maxBlockLevel(it) } ?: -1

    LaunchedEffect(attachedActiveProgramId, deepestBlockLevel) {
        // No block to band: the chart simply shows no separators.
        if (deepestBlockLevel < 0) {
            blockLevel = 0
        } else if (blockLevel < 0 || blockLevel > deepestBlockLevel) {
            blockLevel = 0
        }
    }

    val segments = remember(attachedActiveProgramId, blockLevel, creationState.dataVersion.value) {
        if (attachedActiveProgramId == null) {
            emptyList()
        } else {
            programSegments(appState, attachedActiveProgramId, blockLevel)
        }
    }

    val filteredEntries = allEntries.filterByReps(selectedReps)
    val series = filteredEntries
        .sortedBy { it.date }
        .map { PerformancePoint(date = it.date, value = it.weightKg) }
    val entries = filteredEntries.sortedByDescending { it.date }

    val goalPoints = remember(
        selectedMovementId,
        selectedReps,
        selectedVariant,
        attachedActiveProgramId,
        allEntries.size,
        creationState.dataVersion.value
    ) {
        val movementId = selectedMovementId
        if (movementId != null && attachedActiveProgramId != null) {
            goalSeriesForMovement(
                app = appState,
                activeProgramId = attachedActiveProgramId,
                movementId = movementId,
                reps = selectedReps,
                variant = selectedVariant
            )
        } else {
            emptyList()
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    IconButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = AccentOrange
                        )
                    }
                },
                rightButtons = {}
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            CommonHeader(
                centerContent = {
                    ScrollingText(text = stringResource(R.string.dashboard_title))
                },
                endContent = {
                    Text(
                        text = selectedMovement?.name ?: "",
                        color = TextMuted,
                        fontSize = 12.sp,
                        maxLines = 1
                    )
                }
            )

            if (attachedActiveProgramId == null) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_no_program),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else if (movements.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = stringResource(R.string.dashboard_no_movements),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodyMedium
                    )
                }
            } else {
                MovementSelector(
                    movements = movements,
                    selectedMovementId = selectedMovementId,
                    onSelect = { selectedMovementId = it }
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = 8.dp, vertical = 2.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (variants.isNotEmpty()) {
                        ChartFilterPill(
                            icon = Icons.Default.FilterList,
                            text = selectedVariant ?: stringResource(R.string.dashboard_variant_all),
                            active = selectedVariant != null,
                            onClick = { showVariantDialog = true }
                        )
                    }
                    if (deepestBlockLevel >= 0) {
                        ChartFilterPill(
                            icon = Icons.Default.Layers,
                            text = stringResource(R.string.dashboard_bands_level, blockLevel),
                            active = true,
                            onClick = { showBlockLevelDialog = true }
                        )
                    }
                }

                RepsFilterSlider(
                    selectedReps = selectedReps,
                    availableReps = availableReps,
                    onRepsSelected = { selectedReps = it }
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    item {
                        DashboardStatsRow(series = series)
                    }

                    item {
                        PerformanceChartCard(
                            points = series,
                            goalPoints = goalPoints,
                            domainStart = programRange?.first,
                            domainEnd = programRange?.second,
                            segments = segments
                        )
                    }

                    item {
                        Text(
                            text = stringResource(R.string.dashboard_entries),
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp,
                            modifier = Modifier.padding(horizontal = 4.dp)
                        )
                    }

                    if (entries.isEmpty()) {
                        item {
                            Text(
                                text = stringResource(R.string.dashboard_no_entries),
                                color = TextMuted,
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.padding(horizontal = 4.dp)
                            )
                        }
                    } else {
                        items(entries, key = { it.id }) { entry ->
                            PerformanceEntryRow(
                                entry = entry
                            )
                        }
                    }

                    item { Spacer(modifier = Modifier.height(12.dp)) }
                }
            }
        }
    }

    if (showBlockLevelDialog) {
        BlockLevelDialog(
            deepestLevel = deepestBlockLevel,
            selected = blockLevel,
            onSelect = {
                blockLevel = it
                showBlockLevelDialog = false
            },
            onDismiss = { showBlockLevelDialog = false }
        )
    }

    if (showVariantDialog) {
        VariantFilterDialog(
            variants = variants,
            selected = selectedVariant,
            onSelect = {
                selectedVariant = it
                showVariantDialog = false
            },
            onDismiss = { showVariantDialog = false }
        )
    }
}

@Composable
private fun ChartFilterPill(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    text: String,
    active: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (active) AccentOrange.copy(alpha = 0.16f) else DarkSurfaceAlt)
            .clickable(onClick = onClick)
            .padding(start = 12.dp, end = 8.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = AccentOrange,
            modifier = Modifier.size(14.dp)
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = text,
            color = TextPrimary,
            fontWeight = FontWeight.SemiBold,
            fontSize = 12.sp,
            maxLines = 1
        )
        Icon(
            imageVector = Icons.Default.ArrowDropDown,
            contentDescription = null,
            tint = TextMuted,
            modifier = Modifier.size(16.dp)
        )
    }
}

/** Chooses the hierarchy level shown as bands over the chart: a block level, or weeks. */
@Composable
private fun BlockLevelDialog(
    deepestLevel: Int,
    selected: Int,
    onSelect: (Int) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = {
            Text(
                text = stringResource(R.string.dashboard_bands_title),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                items((0..deepestLevel.coerceAtLeast(0)).toList()) { level ->
                    BlockLevelRow(
                        label = stringResource(R.string.dashboard_bands_level, level),
                        selected = selected == level,
                        onClick = { onSelect(level) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AccentOrange)
            }
        }
    )
}

@Composable
private fun BlockLevelRow(label: String, selected: Boolean, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            color = if (selected) AccentOrange else TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun VariantFilterDialog(
    variants: List<String>,
    selected: String?,
    onSelect: (String?) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = {
            Text(
                text = stringResource(R.string.dashboard_variant_title),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 16.sp
            )
        },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp)) {
                item {
                    VariantDialogRow(
                        label = stringResource(R.string.dashboard_variant_all),
                        selected = selected == null,
                        onClick = { onSelect(null) }
                    )
                }
                items(variants) { variant ->
                    VariantDialogRow(
                        label = variant,
                        selected = variant.equals(selected, ignoreCase = true),
                        onClick = { onSelect(variant) }
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.close), color = AccentOrange)
            }
        }
    )
}

@Composable
private fun VariantDialogRow(
    label: String,
    selected: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(10.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 4.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Text(
            text = label,
            color = if (selected) AccentOrange else TextPrimary,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            fontSize = 14.sp
        )
    }
}

@Composable
private fun RepsFilterSlider(
    selectedReps: Int,
    availableReps: List<Int>,
    onRepsSelected: (Int) -> Unit
) {
    if (availableReps.isEmpty()) return
    // The slider indexes the reps that actually have data for the current movement/variant, so a
    // movement planned only for 1, 4 and 10 reps offers exactly those three stops.
    val selectedIndex = availableReps.indexOf(selectedReps).coerceAtLeast(0)
    val lastIndex = availableReps.lastIndex
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = stringResource(R.string.dashboard_reps),
            color = TextMuted,
            fontSize = 12.sp,
            modifier = Modifier.padding(end = 8.dp)
        )
        Slider(
            value = selectedIndex.toFloat(),
            onValueChange = { value ->
                val index = value.roundToInt().coerceIn(0, lastIndex)
                onRepsSelected(availableReps[index])
            },
            valueRange = 0f..lastIndex.coerceAtLeast(1).toFloat(),
            steps = (availableReps.size - 2).coerceAtLeast(0),
            enabled = availableReps.size > 1,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = selectedReps.toString(),
            color = AccentOrange,
            fontWeight = FontWeight.ExtraBold,
            fontSize = 16.sp,
            modifier = Modifier.padding(start = 8.dp)
        )
    }
}

@Composable
private fun MovementSelector(
    movements: List<MovementEntity>,
    selectedMovementId: Long?,
    onSelect: (Long) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        movements.forEach { movement ->
            val selected = movement.id == selectedMovementId
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (selected) AccentOrange else DarkSurfaceAlt)
                    .clickable { onSelect(movement.id) }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Text(
                    text = movement.name,
                    color = if (selected) DarkBackground else TextPrimary,
                    fontWeight = if (selected) FontWeight.ExtraBold else FontWeight.SemiBold,
                    fontSize = 12.sp,
                    maxLines = 1
                )
            }
        }
    }
}

@Composable
private fun DashboardStatsRow(series: List<PerformancePoint>) {
    val latest = series.latest()
    val best = series.personalBest()
    val progress = series.progressPercentSinceStart()

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        DashboardStatCard(
            title = stringResource(R.string.dashboard_latest),
            value = latest?.let { formatKg(it.value) } ?: "--",
            modifier = Modifier.weight(1f)
        )
        DashboardStatCard(
            title = stringResource(R.string.dashboard_best),
            value = best?.let { formatKg(it.value) } ?: "--",
            valueColor = AccentGreen,
            modifier = Modifier.weight(1f)
        )
        DashboardStatCard(
            title = stringResource(R.string.dashboard_progress),
            value = progress?.let { formatPercent(it) } ?: "--",
            valueColor = if ((progress ?: 0.0) >= 0.0) AccentGreen else AccentRed,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun DashboardStatCard(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    valueColor: Color = TextPrimary
) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceAlt)
            .padding(horizontal = 10.dp, vertical = 12.dp)
    ) {
        Text(title, color = TextMuted, fontSize = 11.sp)
        Spacer(modifier = Modifier.height(6.dp))
        Text(value, color = valueColor, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
    }
}

@Composable
private fun PerformanceChartCard(
    points: List<PerformancePoint>,
    goalPoints: List<PerformancePoint> = emptyList(),
    domainStart: java.time.LocalDate? = null,
    domainEnd: java.time.LocalDate? = null,
    segments: List<PlanSegment> = emptyList()
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 4.dp)
            .clip(RoundedCornerShape(18.dp))
            .background(DarkSurfaceAlt)
            .padding(14.dp)
    ) {
        if (points.isEmpty() && goalPoints.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(180.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = stringResource(R.string.dashboard_no_entries),
                    color = TextMuted,
                    style = MaterialTheme.typography.bodyMedium
                )
            }
            return@Column
        }

        PerformanceLineChart(
            points = points,
            goalPoints = goalPoints,
            domainStart = domainStart,
            domainEnd = domainEnd,
            segments = segments,
            modifier = Modifier
                .fillMaxWidth()
                .height(240.dp)
        )
    }
}

private data class ChartTap(val point: PerformancePoint, val isGoal: Boolean)

@Composable
private fun PerformanceLineChart(
    points: List<PerformancePoint>,
    goalPoints: List<PerformancePoint> = emptyList(),
    domainStart: java.time.LocalDate? = null,
    domainEnd: java.time.LocalDate? = null,
    segments: List<PlanSegment> = emptyList(),
    modifier: Modifier = Modifier
) {
    val allPoints = points + goalPoints
    if (allPoints.isEmpty()) return

    val textMeasurer = rememberTextMeasurer()
    var selectedTap by remember(points, goalPoints, domainStart, domainEnd) { mutableStateOf<ChartTap?>(null) }
    val tooltipFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())
    val axisFormatter = DateTimeFormatter.ofPattern("dd MMM", Locale.getDefault())

    // The x axis runs over the whole program (first day → last day) when the caller knows it,
    // so a short recorded history still reads against the full plan.
    val minDate = domainStart ?: allPoints.minOf { it.date }
    val maxDate = domainEnd ?: allPoints.maxOf { it.date }
    val dayRange = java.time.temporal.ChronoUnit.DAYS.between(minDate, maxDate).toDouble()
        .takeIf { it > 0.0 } ?: 1.0

    var minValue = allPoints.minOf { it.value }
    var maxValue = allPoints.maxOf { it.value }
    if (maxValue - minValue < 1.0) {
        minValue -= 0.5
        maxValue += 0.5
    } else {
        val pad = (maxValue - minValue) * 0.08
        minValue -= pad
        maxValue += pad
    }
    if (minValue < 0.0) minValue = 0.0
    val valueRange = (maxValue - minValue).takeIf { it > 0.0 } ?: 1.0

    // Weight labels top → bottom; the top one carries the unit, the others just the number.
    val axisStyle = TextStyle(color = TextMuted, fontSize = 9.sp)
    val tickValues = listOf(maxValue, (maxValue + minValue) / 2.0, minValue)
    val tickLabels = tickValues.mapIndexed { index, value ->
        if (index == 0) "${value.roundToInt()} kg" else value.roundToInt().toString()
    }
    val leftGutter = remember(tickLabels) {
        tickLabels.maxOf { textMeasurer.measure(it, axisStyle).size.width }.toFloat() + 10f
    }
    val density = LocalDensity.current
    val rightPad = with(density) { 6.dp.toPx() }
    // Band labels sit above the plot, so leave them room when bands are drawn.
    val topPad = with(density) { if (segments.isEmpty()) 8.dp.toPx() else 22.dp.toPx() }
    val bottomGutter = with(density) { 24.dp.toPx() }

    fun xAt(date: java.time.LocalDate, width: Float): Float {
        val left = leftGutter
        val right = (width - rightPad).coerceAtLeast(left + 1f)
        return left + ((java.time.temporal.ChronoUnit.DAYS.between(minDate, date).toDouble() / dayRange).toFloat() * (right - left))
    }

    fun yAt(value: Double, height: Float): Float {
        val top = topPad
        val bottom = (height - bottomGutter).coerceAtLeast(top + 1f)
        return bottom - (((value - minValue) / valueRange).toFloat() * (bottom - top))
    }

    Canvas(
        modifier = modifier.pointerInput(points, goalPoints, minDate, maxDate) {
            detectTapGestures { tap ->
                val chartWidth = size.width.toFloat()
                val chartHeight = size.height.toFloat()
                val candidates = buildList {
                    points.forEach { add(ChartTap(it, false) to Offset(xAt(it.date, chartWidth), yAt(it.value, chartHeight))) }
                    goalPoints.forEach { add(ChartTap(it, true) to Offset(xAt(it.date, chartWidth), yAt(it.value, chartHeight))) }
                }
                val nearest = candidates.minByOrNull { (tap.x - it.second.x) * (tap.x - it.second.x) + (tap.y - it.second.y) * (tap.y - it.second.y) }
                val distSq = if (nearest != null) {
                    (tap.x - nearest.second.x) * (tap.x - nearest.second.x) + (tap.y - nearest.second.y) * (tap.y - nearest.second.y)
                } else Float.MAX_VALUE
                selectedTap = if (nearest != null && distSq <= 60f * 60f) nearest.first else null
            }
        }
    ) {
        val right = (size.width - rightPad).coerceAtLeast(leftGutter + 1f)
        val bottom = (size.height - bottomGutter).coerceAtLeast(topPad + 1f)

        // Plan bands: level-0 blocks (or the chosen level / weeks) behind the curves. Alternating
        // fill plus a dashed separator at each boundary, and the name in the band when it fits.
        if (segments.isNotEmpty()) {
            val bandStyle = TextStyle(color = AccentOrange.copy(alpha = 0.85f), fontSize = 10.sp, fontWeight = FontWeight.SemiBold)
            val dashed = PathEffect.dashPathEffect(floatArrayOf(6f, 5f), 0f)
            segments.forEachIndexed { index, segment ->
                val startX = xAt(segment.start, size.width)
                val endX = xAt(segment.end, size.width).coerceAtLeast(startX + 1f)
                if (index > 0) {
                    drawLine(
                        color = AccentOrange.copy(alpha = 0.35f),
                        start = Offset(startX, topPad),
                        end = Offset(startX, bottom),
                        strokeWidth = 1.5f,
                        pathEffect = dashed
                    )
                }
                val available = endX - startX - 6f
                if (available > 24f) {
                    var label = segment.name
                    var layout = textMeasurer.measure(label, bandStyle)
                    while (layout.size.width > available && label.length > 1) {
                        label = label.dropLast(2) + "…"
                        layout = textMeasurer.measure(label, bandStyle)
                    }
                    drawText(
                        textMeasurer = textMeasurer,
                        text = label,
                        topLeft = Offset(startX + 3f, topPad - layout.size.height - 4f),
                        style = bandStyle
                    )
                }
            }
        }

        // Weight scale (y axis) with grid lines.
        tickValues.forEachIndexed { index, value ->
            val y = yAt(value, size.height)
            drawLine(
                color = TextMuted.copy(alpha = 0.16f),
                start = Offset(leftGutter, y),
                end = Offset(right, y),
                strokeWidth = 1f
            )
            val label = tickLabels[index]
            val layout = textMeasurer.measure(label, axisStyle)
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset((leftGutter - 6f - layout.size.width).coerceAtLeast(0f), y - layout.size.height / 2f),
                style = axisStyle
            )
        }

        // Date scale (x axis): program start on the left, program end on the right, midpoint between.
        val midDate = minDate.plusDays((dayRange / 2.0).toLong())
        listOf(minDate, midDate, maxDate).forEachIndexed { index, date ->
            val x = xAt(date, size.width)
            drawLine(
                color = TextMuted.copy(alpha = 0.10f),
                start = Offset(x, topPad),
                end = Offset(x, bottom),
                strokeWidth = 1f
            )
            val label = date.format(axisFormatter)
            val layout = textMeasurer.measure(label, axisStyle)
            val labelX = when (index) {
                0 -> x
                2 -> x - layout.size.width
                else -> x - layout.size.width / 2f
            }
            drawText(
                textMeasurer = textMeasurer,
                text = label,
                topLeft = Offset(labelX.coerceIn(0f, (size.width - layout.size.width).coerceAtLeast(0f)), bottom + 5f),
                style = axisStyle
            )
        }

        val goalOffsets = goalPoints.map { point -> Offset(xAt(point.date, size.width), yAt(point.value, size.height)) }
        val realOffsets = points.map { point -> Offset(xAt(point.date, size.width), yAt(point.value, size.height)) }

        clipRect(left = leftGutter, top = topPad, right = right, bottom = bottom) {
            // Goal curve: always fully visible, drawn under the real curve.
            if (goalOffsets.size >= 2) {
                val goalPath = Path().apply {
                    moveTo(goalOffsets.first().x, goalOffsets.first().y)
                    for (index in 1 until goalOffsets.size) {
                        val previous = goalOffsets[index - 1]
                        val current = goalOffsets[index]
                        val controlX = (previous.x + current.x) / 2f
                        cubicTo(controlX, previous.y, controlX, current.y, current.x, current.y)
                    }
                }
                drawPath(path = goalPath, color = AccentOrange.copy(alpha = 0.45f), style = Stroke(width = 4f, cap = StrokeCap.Round))
                goalOffsets.forEach { offset ->
                    drawCircle(color = AccentOrange.copy(alpha = 0.45f), radius = 5f, center = offset)
                }
            } else if (goalOffsets.size == 1) {
                drawCircle(color = AccentOrange.copy(alpha = 0.45f), radius = 6f, center = goalOffsets.first())
            }

            if (realOffsets.size == 1) {
                drawCircle(color = AccentOrange, radius = 8f, center = realOffsets.first())
                drawCircle(color = DarkBackground, radius = 3f, center = realOffsets.first())
            } else if (realOffsets.size >= 2) {
                val linePath = Path().apply {
                    moveTo(realOffsets.first().x, realOffsets.first().y)
                    for (index in 1 until realOffsets.size) {
                        val previous = realOffsets[index - 1]
                        val current = realOffsets[index]
                        val controlX = (previous.x + current.x) / 2f
                        cubicTo(controlX, previous.y, controlX, current.y, current.x, current.y)
                    }
                }

                val areaPath = Path().apply {
                    addPath(linePath)
                    lineTo(realOffsets.last().x, bottom)
                    lineTo(realOffsets.first().x, bottom)
                    close()
                }

                drawPath(
                    path = areaPath,
                    brush = Brush.verticalGradient(
                        colors = listOf(AccentOrange.copy(alpha = 0.18f), Color.Transparent),
                        startY = topPad,
                        endY = bottom
                    )
                )

                drawPath(path = linePath, color = AccentOrange, style = Stroke(width = 5f, cap = StrokeCap.Round))

                val bestValue = points.maxOf { it.value }
                realOffsets.forEachIndexed { index, offset ->
                    val isBest = points[index].value == bestValue
                    drawCircle(color = if (isBest) AccentGreen else AccentOrange, radius = if (isBest) 8f else 6f, center = offset)
                    drawCircle(color = DarkBackground, radius = 2.5f, center = offset)
                }
            }
        }

        selectedTap?.let { tap ->
            val offset = Offset(xAt(tap.point.date, size.width), yAt(tap.point.value, size.height))
            val label = "${tap.point.date.format(tooltipFormatter)}  ${formatKg(tap.point.value)}" +
                (if (tap.isGoal) "  (Goal)" else "")
            val style = TextStyle(color = TextPrimary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
            val layout = textMeasurer.measure(label, style)
            val boxW = layout.size.width + 16f
            val boxH = layout.size.height + 10f
            val boxX = (offset.x - boxW / 2f).coerceIn(leftGutter, (right - boxW).coerceAtLeast(leftGutter))
            val boxY = (offset.y - boxH - 10f).coerceAtLeast(topPad)
            drawRoundRect(color = DarkSurface, topLeft = Offset(boxX, boxY), size = Size(boxW, boxH), cornerRadius = CornerRadius(8f))
            drawRoundRect(
                color = AccentOrange.copy(alpha = 0.6f),
                topLeft = Offset(boxX, boxY),
                size = Size(boxW, boxH),
                cornerRadius = CornerRadius(8f),
                style = Stroke(width = 1.5f)
            )
            drawText(textMeasurer = textMeasurer, text = label, topLeft = Offset(boxX + 8f, boxY + 5f), style = style)
        }
    }
}

@Composable
private fun PerformanceEntryRow(
    entry: PerformanceEntry
) {
    val formatter = DateTimeFormatter.ofPattern("EEE dd MMM yyyy", Locale.getDefault())
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(10.dp)
                .clip(CircleShape)
                .background(AccentOrange)
        )
        Spacer(modifier = Modifier.width(10.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = formatKgWithReps(entry.weightKg, entry.reps),
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )
            Text(
                text = entry.date.format(formatter),
                color = TextMuted,
                fontSize = 11.sp
            )
        }
    }
}

private fun formatKg(value: Double): String {
    return if (value % 1.0 == 0.0) "${value.toInt()} kg" else "${"%.1f".format(Locale.US, value)} kg"
}

private fun formatKgWithReps(value: Double, reps: Int): String {
    return "${formatKg(value)} × $reps"
}

private fun formatPercent(value: Double): String {
    val sign = if (value >= 0.0) "+" else ""
    return "$sign${"%.1f".format(Locale.US, value)}%"
}
