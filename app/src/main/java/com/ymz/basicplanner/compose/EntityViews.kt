package com.ymz.basicplanner.compose

import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.*
import com.ymz.basicplanner.ui.*
import kotlin.math.ceil

@Composable
fun EntityRow(
    entity: BaseEntity,
    kind: EntityKind,
    currentName: androidx.compose.ui.text.AnnotatedString,
    composition: String? = null,
    creationState: CreationState,
    onEdit: (BaseEntity) -> Unit,
    onCopy: () -> Unit,
    onDelete: () -> Unit,
    enabled: Boolean = true
) {
    var expanded by remember { mutableStateOf(false) }
    var menuExpanded by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val rowAdjustment = when (entity) {
        is SessionEntity -> entity.adjustmentPercent
        is DayEntity -> entity.adjustmentPercent
        is WeekEntity -> entity.adjustmentPercent
        is BlockEntity -> entity.adjustmentPercent
        is ProgramEntity -> entity.adjustmentPercent
        else -> 0
    }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceAlt)
            .animateContentSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(enabled = enabled) { expanded = !expanded }
                .padding(horizontal = 16.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val nameWithAdjustment = buildAnnotatedString {
                        append(currentName)
                        if (rowAdjustment != 0) {
                            withStyle(
                                SpanStyle(
                                    color = if (rowAdjustment > 0) AccentGreen else AccentRed,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(if (rowAdjustment > 0) " (+${rowAdjustment}%)" else " (${rowAdjustment}%)")
                            }
                        }
                    }
                    MarqueeText(
                        text = nameWithAdjustment,
                        style = MaterialTheme.typography.bodyLarge.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.Bold
                        ),
                        modifier = Modifier.weight(1f)
                    )
                }

                if (!composition.isNullOrBlank()) {
                    val parts = composition.split("|||")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = parts[0],
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMuted
                        )
                        if (parts.size > 1) {
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = parts[1],
                                style = MaterialTheme.typography.bodySmall,
                                color = AccentRed,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically) {

                IconButton(onClick = { expanded = !expanded }, enabled = enabled) {
                    Icon(
                        imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = stringResource(R.string.entity_row_expand_structure_desc),
                        tint = AccentOrange,
                        modifier = Modifier.size(28.dp)
                    )
                }

                Box {
                    
                    IconButton(
                        onClick = { menuExpanded = true },
                        enabled = enabled,
                        modifier = Modifier.size(28.dp) 
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = stringResource(R.string.entity_row_options_desc),
                            tint = TextMuted
                            
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.edit)) },
                            onClick = {
                                menuExpanded = false
                                onEdit(entity)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.action_copy)) },
                            onClick = {
                                menuExpanded = false
                                onCopy()
                            }
                        )
                        DropdownMenuItem(
                            text = { Text(stringResource(R.string.delete), color = AccentRed) },
                            onClick = {
                                menuExpanded = false
                                showDeleteConfirm = true
                            }
                        )
                    }
                }
            }
        }

        if (expanded && kind != EntityKind.MOVEMENT) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp, top = 0.dp)
            ) {
                EntityTreeItem(
                    entity = entity,
                    kind = kind,
                    creationState = creationState,
                    isRoot = true,
                    dailyRms = emptyMap(),
                    showRmInfo = false,
                    onEdit = onEdit,
                    actionContent = { _, _, _ -> }
                )
            }
        }
    }

    if (showDeleteConfirm) {
        ConfirmActionDialog(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.confirm_delete_message),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { showDeleteConfirm = false },
            onConfirm = {
                showDeleteConfirm = false
                onDelete()
            }
        )
    }
}

@Composable
fun EntityTreeItem(
    entity: BaseEntity,
    kind: EntityKind,
    creationState: CreationState,
    prefix: String? = null,
    isRoot: Boolean = false,
    dailyRms: Map<Long, Int> = emptyMap(),
    showRmInfo: Boolean = false,
    onEdit: ((BaseEntity) -> Unit)? = null,
    /** Optional action drawn on the root row itself (same slot as a child row's action). */
    rootActionContent: (@Composable (BaseEntity, EntityKind, Color) -> Unit)? = null,
    /** Optional replacement of the tree's own children (used to show a bare slot as its session). */
    childrenOverride: List<BaseEntity>? = null,
    actionContent: @Composable (BaseEntity, EntityKind, Color) -> Unit,
    adjustmentAccum: Int = 0,
    subAdjustment: Int = 0
) {
    var expanded by remember { mutableStateOf(isRoot) }
    val children = remember(entity, kind, creationState.dataVersion.value, childrenOverride) {
        childrenOverride ?: creationState.childrenOf(kind, entity)
    }
    val treeLineColor = TextMuted.copy(alpha = 0.3f)
    val strUnknown = stringResource(id = R.string.unknown)
    val strRestDay = stringResource(id = R.string.rest_day_name)

    val myAdjustment = when (entity) {
        is SessionEntity -> entity.adjustmentPercent
        is DayEntity -> entity.adjustmentPercent
        is WeekEntity -> entity.adjustmentPercent
        is BlockEntity -> entity.adjustmentPercent
        is ProgramEntity -> entity.adjustmentPercent
        else -> 0
    }
    val entitySubAdj: List<Int> = when (entity) {
        is SessionEntity -> emptyList()
        is DayEntity -> (entity as DayEntity).subAdjustments
        is WeekEntity -> (entity as WeekEntity).subAdjustments
        is BlockEntity -> (entity as BlockEntity).subAdjustments
        is ProgramEntity -> (entity as ProgramEntity).subAdjustments
        else -> emptyList()
    }
    val effectiveAccum = adjustmentAccum + myAdjustment + subAdjustment

    val displayName = entity.getFormatName(creationState.movements, strUnknown=strUnknown, strRestDay = strRestDay)

    Column(modifier = Modifier
        .fillMaxWidth()
        .animateContentSize()) {
        if (!isRoot) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { expanded = !expanded }
                    .padding(vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = null, tint = AccentOrange, modifier = Modifier.size(20.dp)
                )
                Spacer(modifier = Modifier.width(4.dp))
                if (prefix != null) Text(
                    "$prefix : ",
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 14.sp
                )
                Row(
                    modifier = Modifier.weight(1f),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    
                    
                    val treeNameWithAdjustment = buildAnnotatedString {
                        append(displayName)
                        if (entity is BlockEntity && entity.level != null) {
                            withStyle(SpanStyle(color = TextMuted, fontWeight = FontWeight.Normal)) {
                                append(" Lvl ${entity.level}")
                            }
                        }
                        if (myAdjustment != 0) {
                            withStyle(
                                SpanStyle(
                                    color = if (myAdjustment > 0) AccentGreen else AccentRed,
                                    fontWeight = FontWeight.Bold
                                )
                            ) {
                                append(if (myAdjustment > 0) " (+${myAdjustment}%)" else " (${myAdjustment}%)")
                            }
                        }
                        if (subAdjustment != 0) {
                            withStyle(SpanStyle(
                                color = if (subAdjustment > 0) AccentGreen else AccentRed,
                                fontWeight = FontWeight.Bold
                            )) {
                                append(if (subAdjustment > 0) " (+${subAdjustment}%)" else " (${subAdjustment}%)")
                            }
                        }
                    }
                    MarqueeText(
                        text = treeNameWithAdjustment,
                        style = MaterialTheme.typography.bodyMedium.copy(
                            color = TextPrimary,
                            fontWeight = FontWeight.SemiBold
                        ),
                        modifier = Modifier.weight(1f)
                    )

                    
                    if (onEdit != null && !expanded && !showRmInfo && entity.id != CreationState.REST_DAY_ID && kind != EntityKind.MOVEMENT && kind != EntityKind.EXERCISE) {
                        IconButton(
                            onClick = { onEdit(entity) },
                            modifier = Modifier.size(20.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Default.Edit,
                                contentDescription = stringResource(id = R.string.edit),
                                tint = TextMuted,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                if (showRmInfo && kind == EntityKind.EXERCISE) {
                    val ex = entity as ExerciseEntity
                    val rm = dailyRms[ex.movementId]
                    if (rm != null) Text(
                        stringResource(R.string.entity_tree_base_rm_display, rm),
                        color = TextMuted.copy(alpha = 0.7f),
                        fontSize = 10.sp
                    )
                    else Text(
                        stringResource(R.string.entity_tree_base_update_needed),
                        color = Color.Red,
                        fontSize = 10.sp,
                        fontWeight = FontWeight.Bold
                    )
                }

                
            }
        }
        if (expanded) {
            
            Row(modifier = Modifier.height(IntrinsicSize.Min)) {
                if (!isRoot) {
                    Spacer(modifier = Modifier.width(9.dp))
                    Box(modifier = Modifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(treeLineColor))
                    Spacer(modifier = Modifier.width(12.dp))
                }
                Column(modifier = Modifier.weight(1f)) {
                    if (kind == EntityKind.EXERCISE) {
                        val ex = entity as ExerciseEntity
                        ExerciseSetsContent(
                            ex,
                            if (showRmInfo) dailyRms[ex.movementId] else null,
                            adjustment = effectiveAccum
                        )
                    } else {
                        children.forEachIndexed { index, child ->
                            val subKind = when {
                                kind == EntityKind.PROGRAM ->
                                    if (child is WeekEntity) EntityKind.WEEK else EntityKind.BLOCK
                                kind == EntityKind.BLOCK ->
                                    if (child is BlockEntity) EntityKind.BLOCK else EntityKind.WEEK
                                // A weekday slot holds a day, a session or an exercise directly.
                                kind == EntityKind.WEEK -> creationState.weekSlotKind(child)
                                kind == EntityKind.DAY -> EntityKind.SESSION
                                else -> EntityKind.EXERCISE
                            }
                            val actualEntity =
                                if (child is TimedSessionEntity) creationState.sessions.find { it.id == child.sessionId }
                                    ?: child else child
                            val childSubAdj = if (index < entitySubAdj.size) entitySubAdj[index] else 0
                            EntityTreeItem(
                                entity = actualEntity,
                                kind = subKind,
                                creationState = creationState,
                                prefix = if (kind == EntityKind.WEEK) listOf(
                                    stringResource(R.string.day_monday_short),
                                    stringResource(R.string.day_tuesday_short),
                                    stringResource(R.string.day_wednesday_short),
                                    stringResource(R.string.day_thursday_short),
                                    stringResource(R.string.day_friday_short),
                                    stringResource(R.string.day_saturday_short),
                                    stringResource(R.string.day_sunday_short)
                                ).getOrNull(index)
                                else if (child is TimedSessionEntity) child.time.toString() else null,
                                dailyRms = dailyRms,
                                showRmInfo = showRmInfo,
                                onEdit = onEdit,
                                actionContent = actionContent,
                                adjustmentAccum = effectiveAccum,
                                subAdjustment = childSubAdj
                            )
                        }
                    }
                }
            }
            if (isRoot) {
                // The dialog can ask for the root's own action (e.g. "Start new" for a planned
                // session), rendered exactly like a child row's action.
                rootActionContent?.invoke(entity, kind, treeLineColor)
            } else {
                actionContent(entity, kind, treeLineColor)
            }
        }
    }
}

@Composable
fun TreeActionConnector(treeLineColor: Color, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(IntrinsicSize.Min),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Spacer(modifier = Modifier.width(9.dp))
        Box(
            modifier = Modifier
                .width(1.dp)
                .fillMaxHeight(0.5f)
                .align(Alignment.Top)
                .background(treeLineColor)
        )
        Box(modifier = Modifier
            .height(1.dp)
            .weight(1f)
            .background(treeLineColor))
        content()
    }
}

@Composable
private fun ExerciseSetsContent(exercise: ExerciseEntity, dailyRm: Int?, adjustment: Int = 0) {
    exercise.entries.forEach { entry ->
        val loadText =
            if (dailyRm != null) stringResource(
                R.string.exercise_set_content_kg_format,
                kotlin.math.ceil(scaledBaseRm(dailyRm, adjustment) * (entry.intensity / 100.0)).toInt()
            )
            else stringResource(R.string.intensity_percent_format, entry.intensity + adjustment)
        Text(
            text = stringResource(R.string.exercise_set_content_format, entry.sets, entry.reps, loadText, entry.rest),
            color = AccentGreen,
            style = MaterialTheme.typography.bodySmall,
            fontSize = 12.sp
        )
    }
}

@Composable
fun OngoingSessionRow(
    session: SessionEntity,
    creationState: CreationState,
    onResume: (Long) -> Unit,
    onDelete: (Long) -> Unit
) {
    val livePercent = remember(session.id, creationState.exercises.toList()) {
        val sessionExercises = session.exerciseIds.mapNotNull { id ->
            creationState.exercises.find { it.id == id }
        }
        val totalSets = sessionExercises.sumOf { it.entries.size }
        val completedSets = sessionExercises.sumOf { it.entries.count { it.finished == true } }
        if (totalSets > 0) completedSets.toFloat() / totalSets else session.percent
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp)
            .clip(RoundedCornerShape(8.dp))
            .background(DarkSurface)
            .clickable { onResume(session.id) }
            .padding(horizontal = 10.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        ProgressCircle(
            percent = livePercent,
            modifier = Modifier.size(28.dp)
        )
        Spacer(modifier = Modifier.width(12.dp))
        Column(modifier = Modifier.weight(1f)) {
            Text(
                session.name,
                color = TextPrimary,
                fontWeight = FontWeight.Bold,
                fontSize = 14.sp
            )
            val time = session.date?.format(java.time.format.DateTimeFormatter.ofPattern(stringResource(R.string.time_format_hm)))
            if (time != null) {
                Text(stringResource(R.string.ongoing_session_started_at, time), color = TextPrimary, fontSize = 10.sp)
            }
        }
        Icon(
            imageVector = Icons.Default.PlayArrow,
            contentDescription = stringResource(R.string.ongoing_session_resume_desc),
            tint = AccentGreen,
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        IconButton(onClick = { onDelete(session.id) }, modifier = Modifier.size(32.dp)) {
            Icon(
                Icons.Default.Close,
                contentDescription = null,
                tint = AccentRed,
                modifier = Modifier.size(16.dp)
            )
        }
    }
}

@Composable
fun SessionTopBar(title: String, percent: Float) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 12.dp, horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleLarge,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.weight(1f)
        )

        ProgressCircle(
            percent = percent,
            modifier = Modifier.size(36.dp)
        )
    }
}
@Composable
fun ExerciseExecutionItem(
    exercise: ExerciseEntity,
    displayName: androidx.compose.ui.text.AnnotatedString,
    baseRm: Int?,
    adjustmentPct: Int = 0,
    activeTimerValue: Int?,
    activeTimerKey: String?,
    onStartTimer: (Int, String) -> Unit,
    onSetToggle: (Int, Boolean) -> Unit,
    onExerciseToggle: (Boolean) -> Unit,
    onAddSet: () -> Unit,
    onDeleteSet: (Int) -> Unit,
    onHangOnExerciseClick: () -> Unit,
    onHangOnSetEntryClick: (SetEntry, Int) -> Unit
) {

    var expanded by remember { mutableStateOf(true) }
    val allFinished = exercise.entries.isNotEmpty() && exercise.entries.all { it.finished == true }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurfaceAlt)
            .animateContentSize()
    ) {

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 16.dp, end = 8.dp, top = 12.dp, bottom = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {

            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
                if (baseRm != null) {
                    Text(stringResource(R.string.base_rm, baseRm), style = MaterialTheme.typography.bodySmall, color = TextMuted)
                }
            }


            
            Row(verticalAlignment = Alignment.CenterVertically) {
                
                IconButton(onClick = onHangOnExerciseClick) {
                    Icon(
                        imageVector = Icons.Default.Anchor,
                        contentDescription = stringResource(R.string.hang_new_item),
                        tint = AccentOrange
                    )
                }

                Checkbox(
                    checked = allFinished,
                    onCheckedChange = { isChecked -> onExerciseToggle(isChecked) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentGreen,
                        uncheckedColor = TextMuted,
                        checkmarkColor = DarkBackground
                    )
                )

                Icon(
                    imageVector = if (expanded) Icons.Default.ArrowDropDown else Icons.AutoMirrored.Filled.ArrowRight,
                    contentDescription = null,
                    tint = AccentOrange
                )
            }
        }


        if (expanded) {
            Column(modifier = Modifier.padding(bottom = 16.dp)) {


                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.set_header), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(30.dp))
                    Spacer(modifier = Modifier.weight(1f))
                    Text(stringResource(R.string.reps_header), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                    Text(stringResource(R.string.kg_header), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(50.dp))
                    Text(stringResource(R.string.rest_header), color = TextMuted, fontSize = 10.sp, modifier = Modifier.width(50.dp))

                    
                    Spacer(modifier = Modifier.width(24.dp)) 

                    Spacer(modifier = Modifier.width(40.dp)) 
                }


                val plannedIndices = exercise.entries.indices.filter { exercise.entries[it].weightKg == null }
                val addedIndices = exercise.entries.indices.filter { exercise.entries[it].weightKg != null }

                plannedIndices.forEach { index ->
                    val entry = exercise.entries[index]
                    val rowKey = "${exercise.id}_$index"
                    SetRow(
                        index = index + 1,
                        entry = entry,
                        baseRm = baseRm,
                        adjustmentPct = adjustmentPct,
                        isTicking = activeTimerKey == rowKey,
                        currentTimerValue = if (activeTimerKey == rowKey) activeTimerValue else null,
                        onRestClick = { onStartTimer(entry.rest, rowKey) },
                        onCheckedChange = { isChecked -> onSetToggle(index, isChecked) },
                        onDelete = null,
                        onHangItemClick = { onHangOnSetEntryClick(entry, index) }
                    )
                }

                if (addedIndices.isNotEmpty()) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                            .height(1.dp)
                            .background(TextMuted.copy(alpha = 0.35f))
                    )
                }

                addedIndices.forEach { index ->
                    val entry = exercise.entries[index]
                    val rowKey = "${exercise.id}_$index"
                    SetRow(
                        index = index + 1,
                        entry = entry,
                        baseRm = baseRm,
                        adjustmentPct = adjustmentPct,
                        isTicking = activeTimerKey == rowKey,
                        currentTimerValue = if (activeTimerKey == rowKey) activeTimerValue else null,
                        onRestClick = { onStartTimer(entry.rest, rowKey) },
                        onCheckedChange = { isChecked -> onSetToggle(index, isChecked) },
                        onDelete = { onDeleteSet(index) },
                        onHangItemClick = null
                    )
                }

                TextButton(
                    onClick = onAddSet,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp)
                ) {
                    Icon(Icons.Default.Add, contentDescription = null, tint = AccentOrange, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(stringResource(R.string.session_add_set), color = AccentOrange, fontWeight = FontWeight.SemiBold)
                }
            }
        }
    }
}

@Composable
fun SetRow(
    index: Int,
    entry: SetEntry,
    baseRm: Int?,
    adjustmentPct: Int = 0,
    isTicking: Boolean,
    currentTimerValue: Int?,
    onRestClick: () -> Unit,
    onCheckedChange: (Boolean) -> Unit,
    onDelete: (() -> Unit)? = null,
    onHangItemClick: (() -> Unit)? = null
) {
    val isFinished = entry.finished == true

    val weightText = if (entry.weightKg != null) {
        "${entry.weightKg}"
    } else if (baseRm != null) {
        val adjustedBase = scaledBaseRm(baseRm, adjustmentPct)
        val load = ceil(adjustedBase * (entry.intensity / 100.0)).toInt()
        "$load"
    } else {
        stringResource(R.string.intensity_percent_format, entry.intensity)
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (isFinished) AccentGreen.copy(alpha = 0.1f) else Color.Transparent)
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text("$index", color = if (isFinished) AccentGreen else TextPrimary, fontWeight = FontWeight.Bold, modifier = Modifier.width(30.dp))
        Spacer(modifier = Modifier.weight(1f))

        Text(text = "${entry.reps}", color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(50.dp))
        Text(text = weightText, color = TextPrimary, fontWeight = FontWeight.SemiBold, modifier = Modifier.width(50.dp))


        Text(
            text = if (isTicking && currentTimerValue != null) "${currentTimerValue}s" else "${entry.rest}s",
            color = if (isTicking) AccentOrange else TextMuted,
            fontSize = 12.sp,
            fontWeight = if (isTicking) FontWeight.Bold else FontWeight.Normal,
            modifier = Modifier
                .width(50.dp)
                .clickable { onRestClick() }
        )

        if (onHangItemClick != null) {
            IconButton(onClick = onHangItemClick, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Anchor,
                    contentDescription = stringResource(R.string.hang_new_item),
                    tint = AccentOrange,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        if (onDelete != null) {
            IconButton(onClick = onDelete, modifier = Modifier.size(24.dp)) {
                Icon(
                    imageVector = Icons.Default.Delete,
                    contentDescription = stringResource(R.string.session_delete_set),
                    tint = AccentRed,
                    modifier = Modifier.size(18.dp)
                )
            }
        }

        Box(modifier = Modifier.width(40.dp), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clip(CircleShape)
                    .background(if (isFinished) AccentGreen else Color.Transparent)
                    .border(2.dp, if (isFinished) AccentGreen else TextMuted, CircleShape)
                    .clickable { onCheckedChange(!isFinished) },
                contentAlignment = Alignment.Center
            ) {
                if (isFinished) {
                    Icon(Icons.Default.Check, stringResource(R.string.set_row_done_desc), tint = DarkBackground, modifier = Modifier.size(16.dp))
                }
            }
        }
    }
}
