package com.ymz.basicplanner.compose

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.border
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.BasicAlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ymz.basicplanner.data.AppStateStore

import com.ymz.basicplanner.model.*
import com.ymz.basicplanner.model.TimedSessionEntity
import com.ymz.basicplanner.model.allComplete

import com.ymz.basicplanner.ui.AccentGreen
import com.ymz.basicplanner.ui.AccentOrange
import com.ymz.basicplanner.ui.AccentRed
import com.ymz.basicplanner.ui.DarkSurface
import com.ymz.basicplanner.ui.DarkSurfaceAlt
import com.ymz.basicplanner.ui.TextMuted
import com.ymz.basicplanner.ui.TextPrimary
import kotlinx.coroutines.launch
import java.time.LocalTime

import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.getLabel

/** Resize the subAdjustments list to match the child count, copying values in range. */
private fun resizeAndCopy(target: MutableList<Int>, source: List<Int>, childCount: Int) {
    for (i in 0 until childCount) {
        if (i < source.size) target.add(source[i]) else target.add(0)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ClockTimePickerDialog(
    onDismiss: () -> Unit, onConfirmTime: (LocalTime) -> Unit
) {
    val strSelectTime = stringResource(id = R.string.dialog_select_time)
    val strOk = stringResource(id = R.string.dialog_ok)
    val strCancel = stringResource(id = R.string.dialog_cancel)

    val state = rememberTimePickerState()
    AlertDialog(onDismissRequest = onDismiss, title = { Text(strSelectTime) }, text = {
        TimePicker(
            state = state
        )
    }, confirmButton = {
        TextButton(
            onClick = {
                onConfirmTime(
                    LocalTime.of(state.hour, state.minute)
                )
                onDismiss()
            }) { Text(strOk) }
    }, dismissButton = {
        TextButton(onClick = onDismiss) { Text(strCancel) }
    })
}

@Composable
fun UniversalEntityInfoDialog(
    entity: BaseEntity,
    kind: EntityKind,
    title: String,
    creationState: CreationState,
    onDismiss: () -> Unit,
    dailyRms: Map<Long, Int> = emptyMap(),
    showRmInfo: Boolean = false,
    ongoingSessions: List<SessionEntity> = emptyList(),
    onResumeSession: (Long) -> Unit = {},
    onDeleteSession: (Long) -> Unit = {},
    onEditRequest: ((BaseEntity) -> Unit)? = null,
    /** Optional action for the dialog's own entity (e.g. starting a session/exercise slot). */
    rootAction: (@Composable () -> Unit)? = null,
    /** Optional children shown under the root, instead of the entity's own children. */
    childrenOverride: List<BaseEntity>? = null,
    initialAdjustment: Int = 0,
    actionContent: @Composable (BaseEntity, EntityKind, Color) -> Unit = { _, _, _ -> }
) {
    val strClose = stringResource(id = R.string.dialog_close)
    val strStructure = stringResource(id = R.string.dialog_structure)
    val strOngoingSessions = stringResource(id = R.string.dialog_ongoing_sessions)
    val strUnknown = stringResource(id = R.string.unknown)
    val strRestDay = stringResource(id = R.string.rest_day_name)


    var ongoingExpanded by remember { mutableStateOf(false) }

    // Look up parent sub-adjustment for the root entity
    val initialSubAdjustment = when (kind) {
        EntityKind.DAY -> creationState.weeks.firstOrNull { week ->
            week.dayIds.contains(entity.id)
        }?.let { week ->
            val idx = week.dayIds.indexOf(entity.id)
            if (idx >= 0 && idx < week.subAdjustments.size) week.subAdjustments[idx] else 0
        } ?: 0
        EntityKind.SESSION -> creationState.days.firstOrNull { day ->
            day.timedSessionIds.contains(entity.id)
        }?.let { day ->
            val idx = day.timedSessionIds.indexOf(entity.id)
            if (idx >= 0 && idx < day.subAdjustments.size) day.subAdjustments[idx] else 0
        } ?: 0
        EntityKind.WEEK -> creationState.blocks.firstOrNull { block ->
            block.weekIds.contains(entity.id) || block.subBlockIds.contains(entity.id)
        }?.let { block ->
            val idx = if (block.weekIds.contains(entity.id)) block.weekIds.indexOf(entity.id) else block.subBlockIds.indexOf(entity.id)
            if (idx >= 0 && idx < block.subAdjustments.size) block.subAdjustments[idx] else 0
        } ?: 0
        EntityKind.BLOCK -> creationState.programs.firstOrNull { prog ->
            prog.blockIds.contains(entity.id)
        }?.let { prog ->
            val idx = prog.blockIds.indexOf(entity.id)
            if (idx >= 0 && idx < prog.subAdjustments.size) prog.subAdjustments[idx] else 0
        } ?: 0
        else -> 0
    }

    val displayName = entity.getFormatName(creationState.movements, strUnknown = strUnknown, strRestDay = strRestDay)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(strClose, color = AccentOrange) }
        },
        title = {
            Column {
                Text(title, style = MaterialTheme.typography.labelSmall, color = TextMuted)
                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = displayName,
                    style = MaterialTheme.typography.titleMedium,
                    color = TextPrimary,
                    fontSize = 24.sp
                )
                if (entity is BlockEntity && entity.level != null) {
                    val adj = (entity as BlockEntity).adjustmentPercent
                    val suffix = if (adj != 0) " (+$adj)" else ""
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = "Lvl ${entity.level}$suffix",
                        style = MaterialTheme.typography.labelSmall,
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                if (kind != EntityKind.MOVEMENT) {

                Text(
                    text = strStructure,
                    color = TextMuted,
                    style = MaterialTheme.typography.labelSmall,
                    modifier = Modifier.padding(vertical = 8.dp)
                )
                Box(modifier = Modifier.heightIn(max = 280.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {
                        item {
                            EntityTreeItem(
                                entity = entity,
                                kind = kind,
                                creationState = creationState,
                                isRoot = true,
                                dailyRms = dailyRms,
                                showRmInfo = showRmInfo,
                                onEdit = onEditRequest,
                                actionContent = actionContent,
                                rootActionContent = rootAction?.let { action -> { _, _, _ -> action() } },
                                childrenOverride = childrenOverride,
                                adjustmentAccum = initialAdjustment,
                                subAdjustment = initialSubAdjustment
                            )
                        }
                    }
                }
                if (ongoingSessions.isNotEmpty()) {

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { ongoingExpanded = !ongoingExpanded }
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = if (ongoingExpanded) Icons.Default.ExpandMore else Icons.AutoMirrored.Filled.ArrowRight,
                            contentDescription = null, tint = AccentGreen
                        )
                        Text(
                            text = strOngoingSessions,
                            color = AccentGreen,
                            fontWeight = FontWeight.Bold
                        )
                    }
                    if (ongoingExpanded) {
                        Box(modifier = Modifier.heightIn(max = 140.dp)) {
                            LazyColumn {
                                items(ongoingSessions) { session ->
                                    OngoingSessionRow(
                                        session = session,
                                        creationState = creationState,
                                        onResume = onResumeSession,
                                        onDelete = onDeleteSession
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(12.dp))
                    }
                }
            }
            }
        }
    )
}

private val TutorialExplainedTargets = setOf("movement", "variant", "add_set", "ok")

@OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)
@Composable
fun EntityEditDialog(
    kind: EntityKind,
    availableChildren: List<BaseEntity>,
    initialEntity: BaseEntity?,
    initialChildren: List<BaseEntity>,
    initialExerciseEntries: List<SetEntry>,
    creationState: CreationState,
    onDismiss: () -> Unit,
    onConfirm: (name: String, orderedChildIds: List<Long>, entries: List<SetEntry>, adjustment: Int, subAdjustments: List<Int>) -> Unit,
    tutorialDialogActive: Boolean = false,
    onTutorialSkip: () -> Unit = {}
) {

    val strCancel = stringResource(id = R.string.dialog_cancel)
    val strOk = stringResource(id = R.string.dialog_ok)
    val strCreate = stringResource(id = R.string.dialog_create)
    val strEdit = stringResource(id = R.string.dialog_edit)
    val strMovement = stringResource(id = R.string.dialog_movement)
    val strSelect = stringResource(id = R.string.dialog_select)
    val strSelectPlaceholder = stringResource(id = R.string.dialog_select_placeholder)
    val strVariant = stringResource(id = R.string.dialog_variant)
    val strName = stringResource(id = R.string.dialog_name)
    val strWeekDayEmpty = stringResource(id = R.string.week_day_empty)
    val strComposition = stringResource(id = R.string.dialog_composition)
    val strEmpty = stringResource(id = R.string.dialog_composition_empty)
    val strSet = stringResource(id = R.string.dialog_set)
    val strRep = stringResource(id = R.string.dialog_rep)
    val strIntensityPercent = stringResource(id = R.string.dialog_intensity_percent)
    val strRestSec = stringResource(id = R.string.dialog_rest_sec)
    var name by remember { mutableStateOf(initialEntity?.name ?: "") }
    val supportsAdjustment = kind == EntityKind.SESSION || kind == EntityKind.DAY ||
        kind == EntityKind.WEEK || kind == EntityKind.BLOCK || kind == EntityKind.PROGRAM
    val initialAdjustment = when (val e = initialEntity) {
        is SessionEntity -> e.adjustmentPercent
        is DayEntity -> e.adjustmentPercent
        is WeekEntity -> e.adjustmentPercent
        is BlockEntity -> e.adjustmentPercent
        is ProgramEntity -> e.adjustmentPercent
        else -> 0
    }
    var adjustmentText by remember { mutableStateOf(initialAdjustment.toString()) }
    val subAdjustments = remember {
        val e = initialEntity
        val list = when (e) {
            is ProgramEntity -> e.subAdjustments
            is BlockEntity -> e.subAdjustments
            is WeekEntity -> e.subAdjustments
            is DayEntity -> e.subAdjustments
            else -> mutableListOf()
        }
        mutableStateListOf<Int>().apply { addAll(list) }
    }
    var adjustmentEditorChild by remember { mutableStateOf<Int?>(null) }
    val strUnknown = stringResource(id = R.string.dialog_unknown)
    val strRestDay = stringResource(id = R.string.rest_day_name)
    val daysPrefixes = listOf(
        stringResource(id = R.string.dialog_day_prefix_mon),
        stringResource(id = R.string.dialog_day_prefix_tue),
        stringResource(id = R.string.dialog_day_prefix_wed),
        stringResource(id = R.string.dialog_day_prefix_thu),
        stringResource(id = R.string.dialog_day_prefix_fri),
        stringResource(id = R.string.dialog_day_prefix_sat),
        stringResource(id = R.string.dialog_day_prefix_sun)
    )
    val strDeleteLast = stringResource(id = R.string.dialog_delete_last)
    val strAddEntry = stringResource(id = R.string.dialog_add_entry)
    val strAvailableElements = stringResource(id = R.string.dialog_available_elements)
    val strWeekLimitReached = stringResource(id = R.string.dialog_week_limit_reached)
    val strLabel = kind.getLabel()

    val selectedChildIds = remember {
        mutableStateListOf<Long>().apply {
            if (kind == EntityKind.EXERCISE && initialEntity is ExerciseEntity) {
                add(initialEntity.movementId)
            } else if (kind != EntityKind.DAY) {
                addAll(initialChildren.map { it.id })
            }
            if (kind == EntityKind.WEEK) {
                // A week always lists its seven weekdays; unfilled ones hold the empty marker.
                while (size < 7) add(CreationState.EMPTY_DAY_ID)
            }
        }
    }
    val tempSlots = remember { mutableStateListOf<TempSlot>().apply {
        if (kind == EntityKind.DAY) {
            initialChildren.forEach { child ->
                val ts = child as TimedSessionEntity
                creationState.sessions.find { it.id == ts.sessionId }?.let { add(TempSlot(ts.time, it)) }
            }
        }
    } }
    val exerciseEntries = remember { mutableStateListOf<SetEntry>().apply {
        if (kind == EntityKind.EXERCISE) {
            addAll(initialExerciseEntries)
        }
    } }
    val childrenListState = rememberLazyListState()
    var timePickerForSession by remember { mutableStateOf<SessionEntity?>(null) }
    var timePickerEditIndex by remember { mutableStateOf<Int?>(null) }
    if (timePickerForSession != null) {
        ClockTimePickerDialog(
            onDismiss = { timePickerForSession = null },
            onConfirmTime = { selectedTime ->
                val s = timePickerForSession!!
                if (timePickerEditIndex == null) {
                    val slot = TempSlot(selectedTime, s)
                    val idx = tempSlots.indexOfFirst { it.time > selectedTime }
                    if (idx == -1) tempSlots.add(slot) else tempSlots.add(idx, slot)
                } else {
                    val oldIndex = timePickerEditIndex!!
                    val updated = tempSlots[oldIndex].copy(time = selectedTime)
                    tempSlots.removeAt(oldIndex)
                    val insertIndex = tempSlots.indexOfFirst { it.time > selectedTime }
                    if (insertIndex == -1) tempSlots.add(updated) else tempSlots.add(insertIndex, updated)
                }
                timePickerForSession = null
            }
        )
    }
    val strTutorialSkip = stringResource(id = R.string.tutorial_skip)
    val strTutorialGotIt = stringResource(id = R.string.tutorial_got_it)

    // ── Tutorial: the dialog walks the user through its own fields, one target at a time ──
    var explainedTargets by remember { mutableStateOf(emptySet<String>()) }
    var explanationTarget by remember { mutableStateOf<String?>(null) }
    var focusedWeekDay by remember { mutableStateOf(0) }
    // A movement picked in a week asks for its variant first, so a real exercise is created.
    var pendingMovement by remember { mutableStateOf<MovementEntity?>(null) }
    var pendingVariant by remember { mutableStateOf("") }
    var variantHadFocus by remember { mutableStateOf(false) }
    var variantTouched by remember { mutableStateOf(false) }
    var imeWasOpen by remember { mutableStateOf(false) }

    // The variant step ends when the keyboard the user typed with is put away: opening the
    // keyboard on the field is not enough, the user must be able to type the name first.
    val imeVisible = WindowInsets.ime.getBottom(LocalDensity.current) > 0
    LaunchedEffect(variantHadFocus, imeVisible) {
        if (variantHadFocus && imeVisible) imeWasOpen = true
        if (variantHadFocus && imeWasOpen && !imeVisible) variantTouched = true
    }

    // A set row only counts once its four values are filled.
    val entriesComplete = exerciseEntries.allComplete()

    val tutorialTarget: String? = if (!tutorialDialogActive) null else when {
        selectedChildIds.isEmpty() -> "movement"
        !variantTouched && exerciseEntries.isEmpty() -> "variant"
        exerciseEntries.isEmpty() -> "add_set"
        // The four values must be filled AND the keyboard put away before OK is offered.
        !entriesComplete || imeVisible -> "sets"
        else -> "ok"
    }

    // Each target introduces itself once, as soon as it becomes the current one: the
    // explanation opens first, then the target pulses while the user fills it in.
    LaunchedEffect(tutorialTarget) {
        val key = tutorialTarget
        if (key != null && key in TutorialExplainedTargets && key !in explainedTargets) {
            explainedTargets = explainedTargets + key
            explanationTarget = key
        }
    }

    // The pulse only exists while the tutorial drives the dialog, so idle dialogs stay static.
    val tutorialPulseAlpha = if (tutorialDialogActive) {
        val transition = rememberInfiniteTransition(label = "tutorialPulse")
        transition.animateFloat(
            initialValue = 0f,
            targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
            label = "tutorialPulseAlpha"
        ).value
    } else 0f
    // Dark orange -> bright orange, applied to the target's OWN outline (no extra border).
    val tutorialPulseColor = lerp(Color(0xFF6B3400), AccentOrange, tutorialPulseAlpha)

    // While the tutorial waits for the four set values, their own outlines pulse.
    val setsHighlight = if (tutorialTarget == "sets") tutorialPulseColor else null

    val explanationTitle = when (explanationTarget) {
        "movement" -> stringResource(id = R.string.tutorial_target_movement_title)
        "variant" -> stringResource(id = R.string.tutorial_target_variant_title)
        "add_set" -> stringResource(id = R.string.tutorial_target_sets_title)
        "ok" -> stringResource(id = R.string.tutorial_target_ok_title)
        else -> ""
    }
    val explanationBody = when (explanationTarget) {
        "movement" -> stringResource(id = R.string.tutorial_target_movement_body)
        "variant" -> stringResource(id = R.string.tutorial_target_variant_body)
        "add_set" -> stringResource(id = R.string.tutorial_target_sets_body)
        "ok" -> stringResource(id = R.string.tutorial_target_ok_body)
        else -> ""
    }

    // Slots shared by the two dialog layouts: the standard centred card, and the full-screen
    // tutorial frame (which needs the Skip button at the top-left of the screen).
    val dismissSlot: @Composable () -> Unit = {
            // Always present (same components as the normal dialog), greyed while the tutorial
            // runs: the only ways out are the target button (OK) or Skip.
            TextButton(onClick = onDismiss, enabled = !tutorialDialogActive) {
                Text(strCancel, color = if (tutorialDialogActive) TextMuted.copy(alpha = 0.35f) else TextMuted)
            }
            }
    val confirmSlot: @Composable () -> Unit = {
            val blockedEmpty = when {
                // Outside the tutorial an exercise only needs a movement: its set row can still be
                // filled in later. Requiring the four values there silently blocked creation (and
                // edits) behind a greyed-out OK. In the tutorial the four values ARE required —
                // that is enforced by tutorialTarget ("ok") as well.
                kind == EntityKind.EXERCISE ->
                    selectedChildIds.isEmpty() || (tutorialDialogActive && !entriesComplete)
                kind == EntityKind.BLOCK -> selectedChildIds.isEmpty()
                else -> false
            }
            val confirmEnabled = !blockedEmpty && (!tutorialDialogActive || tutorialTarget == "ok")
            TextButton(
                enabled = confirmEnabled,
                border = if (tutorialTarget == "ok") BorderStroke(2.dp, tutorialPulseColor) else null,
                onClick = {
                    val finalName = if (kind == EntityKind.EXERCISE) name else name.ifBlank { strLabel }
                    val adjustment = adjustmentText.toIntOrNull() ?: 0
                    if (supportsAdjustment && initialEntity != null) {
                        when (val e = initialEntity) {
                            is SessionEntity -> e.adjustmentPercent = adjustment
                            is DayEntity -> { e.adjustmentPercent = adjustment; e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is WeekEntity -> { e.adjustmentPercent = adjustment; e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is BlockEntity -> { e.adjustmentPercent = adjustment; e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is ProgramEntity -> { e.adjustmentPercent = adjustment; e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            else -> {}
                        }
                    } else if (initialEntity != null && subAdjustments.isNotEmpty()) {
                        when (val e = initialEntity) {
                            is DayEntity -> { e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is WeekEntity -> { e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is BlockEntity -> { e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            is ProgramEntity -> { e.subAdjustments.clear(); resizeAndCopy(e.subAdjustments, subAdjustments, selectedChildIds.size) }
                            else -> {}
                        }
                    }
                    if (kind == EntityKind.DAY) {
                        val ids = tempSlots.map { slot ->
                            val id = creationState.newId()
                            creationState.timedSessions += TimedSessionEntity(id, slot.session.name, "", slot.time, slot.session.id)
                            id
                        }
                        onConfirm(finalName, ids, emptyList(), adjustment, subAdjustments.toList())
                    } else {
                        onConfirm(finalName, selectedChildIds.toList(), exerciseEntries.toList(), adjustment, subAdjustments.toList())
                    }
                }
            ) {
                Text(
                    strOk,
                    color = when {
                        !confirmEnabled -> TextMuted.copy(alpha = 0.5f)
                        tutorialTarget == "ok" -> tutorialPulseColor
                        else -> AccentOrange
                    },
                    fontWeight = FontWeight.Bold
                )
            }
            }
    val dialogContent: @Composable () -> Unit = {
            Column(modifier = Modifier.fillMaxWidth()) {

                if (kind == EntityKind.EXERCISE)
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {

                        Box(modifier = Modifier.weight(0.7f)) {
                            var expanded by remember { mutableStateOf(false) }
                            val selectedId = selectedChildIds.firstOrNull()
                            val selectedMovement = availableChildren.find { it.id == selectedId }

                            OutlinedTextField(
                                value = selectedMovement?.name ?: "",
                                onValueChange = {},
                                readOnly = true,
                                label = { Text(strMovement) },
                                placeholder = { Text(strSelectPlaceholder, color = TextMuted) },
                                singleLine = true,
                                trailingIcon = {
                                    Icon(
                                        imageVector = Icons.Default.UnfoldMore,
                                        contentDescription = strSelect,

                                        tint = if (expanded) AccentOrange else TextMuted
                                    )
                                },
                                textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(

                                    unfocusedBorderColor = when {
                                        tutorialTarget == "movement" -> tutorialPulseColor
                                        expanded -> AccentOrange
                                        else -> TextMuted
                                    },
                                    focusedBorderColor = if (tutorialTarget == "movement") tutorialPulseColor else AccentOrange,


                                    unfocusedLabelColor = if (expanded) AccentOrange else TextMuted,
                                    focusedLabelColor = AccentOrange,

                                    cursorColor = AccentOrange,
                                    disabledTextColor = TextPrimary,
                                    disabledBorderColor = if (expanded) AccentOrange else TextMuted,
                                    disabledPlaceholderColor = TextMuted,
                                    disabledLabelColor = TextMuted
                                ),
                                modifier = Modifier.fillMaxWidth()
                            )


                            Box(
                                modifier = Modifier
                                    .matchParentSize()
                                    .clickable { expanded = true }
                            )


                            UnifiedSelectionMenu(
                                expanded = expanded,
                                onDismiss = { expanded = false },
                                modifier = Modifier.heightIn(max = 280.dp),
                                items = availableChildren,
                                itemLabel = { it.name },
                                selectedItem = selectedMovement,
                                onItemClick = { movement ->
                                    selectedChildIds.clear()
                                    selectedChildIds.add(movement.id)
                                    expanded = false
                                }
                            )
                        }

                        
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strVariant, fontSize = 10.sp) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = if (tutorialTarget == "variant") tutorialPulseColor else TextMuted,
                                focusedBorderColor = if (tutorialTarget == "variant") tutorialPulseColor else AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            modifier = Modifier
                                .weight(0.3f)
                                .onFocusChanged { state ->
                                    if (state.isFocused) {
                                        variantHadFocus = true
                                    } else if (variantHadFocus) {
                                        // Keyboard dismissed: the next target / explanation takes over.
                                        variantTouched = true
                                    }
                                }
                        )
                    }

                }
                else
                {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text(strName) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            modifier = Modifier.weight(1f)
                        )
                        if (supportsAdjustment) {
                            OutlinedTextField(
                                value = adjustmentText,
                                onValueChange = { input ->
                                    adjustmentText = input.filter { c -> c.isDigit() || c == '-' }
                                },
                                label = { Text(stringResource(R.string.adjustment_percent_short_label)) },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodySmall.copy(color = TextPrimary),
                                colors = OutlinedTextFieldDefaults.colors(
                                    unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                    unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                    cursorColor = AccentOrange
                                ),
                                modifier = Modifier.width(110.dp)
                            )
                        }
                    }
                }

                if (kind != EntityKind.MOVEMENT) {
                Spacer(modifier = Modifier.height(24.dp))
                
                Text(
                    strComposition,
                    style = MaterialTheme.typography.titleSmall,
                    color = TextPrimary
                )
                Spacer(modifier = Modifier.height(12.dp))
                val isEmpty = when (kind) {
                    EntityKind.EXERCISE -> exerciseEntries.isEmpty()
                    EntityKind.DAY -> tempSlots.isEmpty()
                    else -> selectedChildIds.isEmpty()
                }
                if (isEmpty) {
                    Text(strEmpty, style = MaterialTheme.typography.bodySmall, color = TextMuted)
                } else
                {
                    if (kind == EntityKind.EXERCISE) {
                        DraggableReorderableList(
                            items = exerciseEntries,
                            listState = childrenListState,
                            onRemove = null,
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(max = 250.dp),
                        ) { index, entry ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 4.dp), 
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                NumberInputField(
                                    entry.sets.toString(),
                                    { v ->
                                        exerciseEntries[index] =
                                            entry.copy(sets = v.toIntOrNull() ?: 0)
                                    },
                                    strSet,
                                    Modifier.weight(1f),
                                    highlightColor = setsHighlight
                                )
                                NumberInputField(
                                    entry.reps.toString(),
                                    { v ->
                                        exerciseEntries[index] =
                                            entry.copy(reps = v.toIntOrNull() ?: 0)
                                    },
                                    strRep,
                                    Modifier.weight(1f),
                                    highlightColor = setsHighlight
                                )
                                NumberInputField(
                                    entry.intensity.toString(),
                                    { v ->
                                        exerciseEntries[index] =
                                            entry.copy(intensity = v.toIntOrNull() ?: 0)
                                    },
                                    strIntensityPercent,
                                    Modifier.weight(1f),
                                    highlightColor = setsHighlight
                                )
                                NumberInputField(
                                    entry.rest.toString(),
                                    { v ->
                                        exerciseEntries[index] =
                                            entry.copy(rest = v.toIntOrNull() ?: 0)
                                    },
                                    strRestSec,
                                    Modifier.weight(1.1f),
                                    highlightColor = setsHighlight
                                )
                            }
                        }
                    } 
                    else if (kind == EntityKind.DAY) {
                        DraggableReorderableList(
                            items = tempSlots,
                            listState = childrenListState,
                            onRemove = { tempSlots.removeAt(it) },
                            
                            customSwapLogic = { from, to ->
                                val sessionFrom = tempSlots[from].session
                                val sessionTo = tempSlots[to].session
                                tempSlots[from] = tempSlots[from].copy(session = sessionTo)
                                tempSlots[to] = tempSlots[to].copy(session = sessionFrom)
                            },
                            
                            onItemClick = { i, _ ->
                                timePickerEditIndex = i
                                timePickerForSession = tempSlots[i].session
                            }
                        ) { _, slot ->
                            Text("${slot.time}",
                                color = AccentOrange,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.width(60.dp))
                            Text(slot.session.name, color = TextPrimary, modifier = Modifier.weight(1f))
                        }
                    }
                    else if (kind == EntityKind.SESSION) {
                        DraggableReorderableList(
                            items = selectedChildIds,
                            listState = childrenListState,
                            onRemove = { selectedChildIds.removeAt(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp),
                            onItemClick = { index, id ->
                                adjustmentEditorChild = index
                            },
                            trailingContent = { idx ->
                                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                    val v = if (idx < subAdjustments.size) subAdjustments[idx] else 0
                                    if (v != 0) {
                                        Text(
                                            text = "${if (v > 0) "+" else ""}$v%",
                                            color = if (v > 0) AccentGreen else AccentRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        ) { index, id ->
                            
                            val exercise = availableChildren.find { it.id == id } as? ExerciseEntity
                            val displayName = exercise?.getFormatName(creationState.movements, strUnknown=strUnknown, strRestDay=strRestDay)
                                ?: androidx.compose.ui.text.AnnotatedString(strUnknown)

                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = "${index + 1}.",
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                
                                MarqueeText(
                                    text = displayName,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    else if (kind == EntityKind.WEEK) {
                        // Same list and same row design as before, plus the focuser: the weekday being
                        // edited carries the orange frame. The cross empties that day and takes focus.
                        DraggableReorderableList(
                            items = selectedChildIds,
                            listState = childrenListState,
                            onRemove = { index ->
                                creationState.assignToWeekDay(
                                    selectedChildIds, index, CreationState.EMPTY_DAY_ID
                                )
                                focusedWeekDay = index
                            },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 190.dp),
                            rowModifier = { index ->
                                if (index == focusedWeekDay) {
                                    Modifier.border(2.dp, AccentOrange, RoundedCornerShape(10.dp))
                                } else Modifier
                            },
                            onItemClick = { index, _ ->
                                focusedWeekDay = index
                                adjustmentEditorChild = index
                            },
                            trailingContent = { idx ->
                                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                    val v = if (idx < subAdjustments.size) subAdjustments[idx] else 0
                                    if (v != 0) {
                                        Text(
                                            text = "${if (v > 0) "+" else ""}$v%",
                                            color = if (v > 0) AccentGreen else AccentRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        ) { index, id ->
                            val isFocused = index == focusedWeekDay
                            val slotName = when {
                                id == CreationState.EMPTY_DAY_ID -> strWeekDayEmpty
                                id == CreationState.REST_DAY_ID -> stringResource(id = R.string.rest_day_name)
                                else -> {
                                    val exerciseOfSlot = creationState.exercises.find { it.id == id }
                                    if (exerciseOfSlot != null) {
                                        creationState.exerciseDisplayName(exerciseOfSlot)
                                    } else {
                                        (creationState.days.find { it.id == id }
                                            ?: creationState.sessions.find { it.id == id })?.name
                                            ?: strUnknown
                                    }
                                }
                            }
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = daysPrefixes.getOrNull(index) ?: "",
                                    color = if (isFocused) AccentOrange else TextMuted,
                                    fontWeight = FontWeight.Bold
                                )
                                MarqueeText(
                                    text = slotName,
                                    color = TextPrimary
                                )
                            }
                        }
                    }
                    else {
                        DraggableReorderableList(
                            items = selectedChildIds, listState = childrenListState,
                            onRemove = { selectedChildIds.removeAt(it) },
                            modifier = Modifier.fillMaxWidth().heightIn(max = 170.dp),
                            onItemClick = { index, id ->
                                adjustmentEditorChild = index
                            },
                            trailingContent = { idx ->
                                Box(Modifier.width(44.dp), contentAlignment = Alignment.Center) {
                                    val v = if (idx < subAdjustments.size) subAdjustments[idx] else 0
                                    if (v != 0) {
                                        Text(
                                            text = "${if (v > 0) "+" else ""}$v%",
                                            color = if (v > 0) AccentGreen else AccentRed,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Bold,
                                            maxLines = 1
                                        )
                                    }
                                }
                            }
                        ) { index, id ->
                            val cName = if (id == CreationState.REST_DAY_ID) {
                                stringResource(id = R.string.rest_day_name)
                            } else {
                                when (kind) {
                                    EntityKind.WEEK -> (creationState.days.find { it.id == id } ?: creationState.sessions.find { it.id == id })?.name
                                        ?: (creationState.exercises.find { it.id == id })?.name
                                    EntityKind.PROGRAM -> (creationState.blocks.find { it.id == id } ?: creationState.weeks.find { it.id == id })?.name
                                    EntityKind.BLOCK -> {
                                        val w = creationState.weeks.find { it.id == id }
                                        val b = creationState.blocks.find { it.id == id }
                                        val name = w?.name ?: b?.name
                                        val suffix = b?.level?.let { level -> " (Lvl $level)" } ?: ""
                                        if (name != null) name + suffix else null
                                    }
                                    else -> availableChildren.find { it.id == id }?.name
                                } ?: strUnknown
                            }
                            
                            val prefix = if (kind == EntityKind.WEEK) {
                                (daysPrefixes.getOrNull(index) ?: "")
                            } else {
                                "${index + 1}."
                            }
                            
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text(
                                    text = prefix,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold
                                )
                                val exercise = if (kind == EntityKind.WEEK) creationState.exercises.find { it.id == id } else null
                                if (exercise != null) {
                                    MarqueeText(
                                        text = exercise.getFormatName(creationState.movements, strUnknown = strUnknown, strRestDay = strRestDay),
                                        color = TextPrimary
                                    )
                                } else {
                                    MarqueeText(
                                        text = cName,
                                        color = TextPrimary
                                    )
                                }
                            }
                        }
                    }
                }
                Spacer(modifier = Modifier.height(24.dp))
                if (kind == EntityKind.EXERCISE) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.End
                    ) {
                        if (exerciseEntries.isNotEmpty()) {
                            TextButton(onClick = { exerciseEntries.removeAt(exerciseEntries.lastIndex) }) {
                                Icon(
                                    Icons.Default.Close,
                                    null,
                                    tint = AccentRed,
                                    modifier = Modifier.size(18.dp)
                                )
                                Spacer(Modifier.width(4.dp))
                                Text(strDeleteLast, color = AccentRed)
                            }
                            Spacer(modifier = Modifier.width(8.dp))
                        }
                        
                        val addEnabled = !tutorialDialogActive ||
                            tutorialTarget == "add_set" || tutorialTarget == "sets"
                        TextButton(
                            enabled = addEnabled,
                            border = if (tutorialTarget == "add_set") BorderStroke(2.dp, tutorialPulseColor) else null,
                            onClick = {
                            val entryToAdd = when (exerciseEntries.size) {
                                0 -> {
                                    
                                    SetEntry(creationState.newId(), 0, 0, 0, 0)
                                }
                                1 -> {
                                    
                                    exerciseEntries.last().copy(id = creationState.newId())
                                }
                                else -> {
                                    
                                    val last = exerciseEntries.last()
                                    val secondToLast = exerciseEntries[exerciseEntries.size - 2]
                                    val setsDelta = last.sets - secondToLast.sets
                                    val repsDelta = last.reps - secondToLast.reps
                                    val intensityDelta = last.intensity - secondToLast.intensity
                                    val restDelta = last.rest - secondToLast.rest
                                    
                                    val newSets = (last.sets + setsDelta).coerceAtLeast(0)
                                    val newReps = (last.reps + repsDelta).coerceAtLeast(0)
                                    val newIntensity = (last.intensity + intensityDelta).coerceAtLeast(0)
                                    val newRest = (last.rest + restDelta).coerceAtLeast(0)

                                    SetEntry(creationState.newId(), newSets, newReps, newIntensity, newRest)
                                }
                            }
                            exerciseEntries.add(entryToAdd)
                        }) {
                            val addSetHighlight = tutorialTarget == "add_set"
                            val addColor = when {
                                !addEnabled -> TextMuted.copy(alpha = 0.5f)
                                addSetHighlight -> tutorialPulseColor
                                else -> AccentOrange
                            }
                            Icon(
                                Icons.Default.Add,
                                null,
                                tint = addColor,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(4.dp))
                            Text(strAddEntry, color = addColor)
                        }

                    }
                } else {
                    Text(
                        strAvailableElements,
                        style = MaterialTheme.typography.titleSmall,
                        color = TextPrimary
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    val isWeek = (kind == EntityKind.WEEK)
                    if (kind == EntityKind.WEEK || kind == EntityKind.PROGRAM || kind == EntityKind.BLOCK) {
                        if (kind == EntityKind.WEEK) {
                            // The focused weekday receives the pick; the focus then advances so the
                            // user can keep filling Monday -> Tuesday -> ... without re-selecting.
                            val assignToFocusedDay: (Long) -> Unit = { childId ->
                                creationState.assignToWeekDay(selectedChildIds, focusedWeekDay, childId)
                                if (focusedWeekDay < 6) focusedWeekDay += 1
                            }
                            val daysList = availableChildren.filterIsInstance<DayEntity>()
                            val sessionsList = availableChildren.filterIsInstance<SessionEntity>()
                            val exercisesList = availableChildren.filterIsInstance<ExerciseEntity>()
                            // A movement that has no exercise yet is offered too: dropping it into a
                            // weekday slot creates its exercise on the spot (variant to fill later).
                            val freeMovements = creationState.movements.filter { mov ->
                                creationState.exercises.none {
                                    it.movementId == mov.id && creationState.isPlanLevel(it)
                                }
                            }
                            // Second pane of the dialog: every proposal (days, sessions, exercises)
                            // in its own scrollable area, so the exercise row is always visible.
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                Text(stringResource(R.string.days), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    item {
                                        AssistChip(
                                            onClick = { assignToFocusedDay(CreationState.REST_DAY_ID) },
                                            label = { Text(stringResource(id = R.string.rest_day_name), color = Color.Black) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = AssistChipDefaults.assistChipColors(containerColor = Color.White, labelColor = Color.Black),
                                            border = BorderStroke(1.dp, Color.White)
                                        )
                                    }
                                    items(daysList) { child -> Chip(child) { assignToFocusedDay(it) } }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.sessions), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(sessionsList) { child -> Chip(child) { assignToFocusedDay(it) } }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(stringResource(R.string.exercises), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(exercisesList) { child ->
                                        AssistChip(
                                            onClick = { assignToFocusedDay(child.id) },
                                            label = {
                                                Row(
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    val mName = if (child is ExerciseEntity) creationState.movements.find { it.id == child.movementId }?.name ?: strUnknown else child.name
                                                    MarqueeText(mName, color = Color.White, modifier = Modifier.widthIn(max = 160.dp))
                                                    if (child is ExerciseEntity && child.name.isNotBlank()) {
                                                        MarqueeText(
                                                            text = child.name,
                                                            color = AccentOrange,
                                                            modifier = Modifier.widthIn(max = 160.dp)
                                                        )
                                                    }
                                                }
                                            },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = DarkSurface,
                                                labelColor = Color.White
                                            ),
                                            border = BorderStroke(1.dp, AccentOrange)
                                        )
                                    }
                                    items(freeMovements) { movement ->
                                        AssistChip(
                                            onClick = {
                                                pendingMovement = movement
                                                pendingVariant = ""
                                            },
                                            label = { MarqueeText(movement.name, color = Color.White, modifier = Modifier.widthIn(max = 160.dp)) },
                                            shape = RoundedCornerShape(14.dp),
                                            colors = AssistChipDefaults.assistChipColors(
                                                containerColor = DarkSurface,
                                                labelColor = Color.White
                                            ),
                                            border = BorderStroke(1.dp, AccentOrange)
                                        )
                                    }
                                }
                            }
                        } else if (kind == EntityKind.PROGRAM) {
                            val (list1, title1, list2, title2) = Triple(
                                availableChildren.filterIsInstance<BlockEntity>(),
                                stringResource(R.string.blocks),
                                availableChildren.filterIsInstance<WeekEntity>()
                            ).let { (blocks, blocksTitle, weeks) ->
                                Quadruple(blocks, blocksTitle, weeks, stringResource(R.string.weeks))
                            }
                            Column {
                                Text(title1, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(list1) { child -> Chip(child) { selectedChildIds.add(it) } }
                                }
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(title2, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                LazyRow(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                ) {
                                    items(list2) { child -> Chip(child) { selectedChildIds.add(it) } }
                                }
                            }
                        } else { // BLOCK
                            val weeksList = availableChildren.filterIsInstance<WeekEntity>()
                            // Any block may become a child whatever its depth: the only ones
                            // hidden are the block being edited and its own descendants, because
                            // those would close a loop in the tree.
                            val selfId = (initialEntity as? BlockEntity)?.id
                            val forbiddenIds = if (selfId != null) {
                                // Descendants would close a loop below, ancestors one above.
                                creationState.blockDescendants(selfId) +
                                    creationState.blockAncestors(selfId) +
                                    selfId
                            } else {
                                emptySet()
                            }
                            val blocksByLevel = availableChildren.filterIsInstance<BlockEntity>()
                                .filter { it.id !in forbiddenIds }
                                .groupBy { it.level ?: 0 }

                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .heightIn(max = 220.dp)
                                    .verticalScroll(rememberScrollState())
                            ) {
                                if (weeksList.isNotEmpty()) {
                                    Text(stringResource(R.string.weeks), style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(weeksList) { child -> Chip(child) { selectedChildIds.add(it) } }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                                blocksByLevel.forEach { (level, blocks) ->
                                    val label = "Blocks Lvl $level"
                                    Text(label, style = MaterialTheme.typography.labelMedium, color = TextMuted)
                                    LazyRow(
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)
                                    ) {
                                        items(blocks) { child -> Chip(child) { selectedChildIds.add(it) } }
                                    }
                                    Spacer(modifier = Modifier.height(8.dp))
                                }
                            }
                        }
                    } else {
                        LazyRow(
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            
                            if (isWeek) {
                                item {
                                    AssistChip(
                                        onClick = { selectedChildIds.add(CreationState.REST_DAY_ID) },
                                        label = {
                                            Text(
                                                stringResource(id = R.string.rest_day_name),
                                                color = Color.Black
                                            )
                                        },
                                        shape = RoundedCornerShape(14.dp),
                                        colors = AssistChipDefaults.assistChipColors(
                                            containerColor = Color.White,
                                            labelColor = Color.Black
                                        ),
                                        border = BorderStroke(1.dp, Color.White)
                                    )
                                }
                            }
                            itemsIndexed(availableChildren) { _, child ->
                                AssistChip(
                                    onClick = {
                                        if (kind == EntityKind.DAY) timePickerForSession =
                                            child as SessionEntity else selectedChildIds.add(child.id)
                                    },
                                    label = {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                                        )
                                        {
                                            if (child is ExerciseEntity) {
                                            
                                            val mName = creationState.movements.find { it.id == child.movementId }?.name ?: strUnknown
                                            MarqueeText(mName, modifier = Modifier.widthIn(max = 160.dp))

                                            if (child.name.isNotBlank()) {
                                                MarqueeText(
                                                    text = child.name,
                                                    color = AccentOrange,
                                                    modifier = Modifier.widthIn(max = 160.dp)
                                                )
                                            }
                                        } else {
                                            MarqueeText(child.name, modifier = Modifier.widthIn(max = 160.dp))
                                        }
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = DarkSurface,
                                        labelColor = Color.White
                                    ),
                                    border = BorderStroke(1.dp, AccentOrange)
                                )
                            }
                        }
                    }
                }
            }
        }
            }

    if (tutorialDialogActive) {
        BasicAlertDialog(
            onDismissRequest = {},
            properties = DialogProperties(
                usePlatformDefaultWidth = false,
                decorFitsSystemWindows = false,
                dismissOnBackPress = false,
                dismissOnClickOutside = false
            )
        ) {
            Box(modifier = Modifier.fillMaxSize().imePadding()) {
                Surface(
                    modifier = Modifier
                        .align(Alignment.Center)
                        .padding(horizontal = 24.dp)
                        .widthIn(min = 280.dp, max = 560.dp),
                    shape = RoundedCornerShape(28.dp),
                    color = DarkSurfaceAlt
                ) {
                    Column(modifier = Modifier.padding(24.dp)) {
                        Text(
                            text = if (initialEntity == null) strCreate else strEdit,
                            color = TextPrimary,
                            style = MaterialTheme.typography.headlineSmall
                        )
                        Spacer(modifier = Modifier.height(16.dp))
                        dialogContent()
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 8.dp),
                            horizontalArrangement = Arrangement.End,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            dismissSlot()
                            Spacer(modifier = Modifier.width(8.dp))
                            confirmSlot()
                        }
                    }
                }
                // Same button, same place as the tutorial slides: top-left of the screen.
                Button(
                    onClick = {
                        // Skip is the second way out of the locked dialog (the other is OK).
                        onDismiss()
                        onTutorialSkip()
                    },
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .offset(x = 16.dp, y = 48.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E1E)),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(strTutorialSkip, color = TextMuted)
                }
            }
        }
    } else {
        AlertDialog(
            onDismissRequest = onDismiss,
            properties = DialogProperties(),
            containerColor = DarkSurfaceAlt,
            dismissButton = dismissSlot,
            confirmButton = confirmSlot,
            title = { Text(if (initialEntity == null) strCreate else strEdit, color = TextPrimary) },
            text = dialogContent
        )
    }

    // Tutorial: one-time explanation of the target the user just tapped.
    if (explanationTarget != null) {
        AlertDialog(
            onDismissRequest = { explanationTarget = null },
            containerColor = DarkSurfaceAlt,
            title = { Text(explanationTitle, color = AccentOrange, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    text = explanationBody,
                    color = TextPrimary,
                    style = MaterialTheme.typography.bodyMedium
                )
            },
            confirmButton = {
                TextButton(onClick = { explanationTarget = null }) {
                    Text(strTutorialGotIt, color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    // Adjustment editor dialog for a specific child (by index)
    pendingMovement?.let { movement ->
        AlertDialog(
            onDismissRequest = { pendingMovement = null },
            containerColor = DarkSurfaceAlt,
            title = {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(stringResource(id = R.string.dialog_new_exercise), color = TextPrimary, fontWeight = FontWeight.Bold)
                    Text(creationState.movements.find { it.id == movement.id }?.name ?: movement.name, color = AccentOrange)
                }
            },
            text = {
                OutlinedTextField(
                    value = pendingVariant,
                    onValueChange = { pendingVariant = it },
                    label = { Text(strVariant, fontSize = 10.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = TextMuted,
                        focusedBorderColor = AccentOrange,
                        unfocusedLabelColor = TextMuted,
                        focusedLabelColor = AccentOrange,
                        cursorColor = AccentOrange
                    )
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    val exercise = creationState.exerciseForMovement(movement, pendingVariant)
                    creationState.assignToWeekDay(selectedChildIds, focusedWeekDay, exercise.id)
                    if (focusedWeekDay < 6) focusedWeekDay += 1
                    pendingMovement = null
                }) { Text(strOk, color = AccentOrange) }
            },
            dismissButton = {
                TextButton(onClick = { pendingMovement = null }) { Text(strCancel, color = TextMuted) }
            }
        )
    }

    if (adjustmentEditorChild != null) {
        val idx = adjustmentEditorChild!!
        val childId = if (idx < selectedChildIds.size) selectedChildIds[idx] else null
        val childName = when {
            // Never print a raw slot id: a rest day says so, an unfilled day stays empty.
            childId == null -> ""
            childId == CreationState.EMPTY_DAY_ID -> ""
            childId == CreationState.REST_DAY_ID -> stringResource(id = R.string.rest_day_name)
            else -> creationState.nameOf(kind, childId) ?: ""
        }
        val current = if (idx < subAdjustments.size) subAdjustments[idx] else 0
        var editValue by remember(idx) { mutableStateOf(current.toString()) }

        AlertDialog(
            onDismissRequest = { adjustmentEditorChild = null },
            containerColor = DarkSurfaceAlt,
            title = {
                Text(
                    text = childName,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        stringResource(R.string.sub_adjustments_title),
                        color = TextMuted,
                        style = MaterialTheme.typography.bodySmall
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = editValue,
                        onValueChange = { input ->
                            editValue = input.filter { c -> c.isDigit() || c == '-' }
                        },
                        label = { Text("%") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = AccentOrange,
                            unfocusedBorderColor = TextMuted,
                            focusedLabelColor = AccentOrange,
                            unfocusedLabelColor = TextMuted,
                            cursorColor = AccentOrange
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    while (subAdjustments.size <= idx) subAdjustments.add(0)
                    subAdjustments[idx] = editValue.toIntOrNull() ?: 0
                    adjustmentEditorChild = null
                }) {
                    Text(
                        stringResource(R.string.dialog_ok),
                        color = AccentOrange,
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { adjustmentEditorChild = null }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextMuted)
                }
            }
        )
    }
}

@Composable
private fun Chip(child: BaseEntity, label: AnnotatedString? = null, onClick: (Long) -> Unit) {
    AssistChip(
        onClick = { onClick(child.id) },
        label = {
            if (label != null) Text(label)
            else MarqueeText(child.name, modifier = Modifier.widthIn(max = 160.dp))
        },
        shape = RoundedCornerShape(14.dp),
        colors = AssistChipDefaults.assistChipColors(
            containerColor = DarkSurface,
            labelColor = Color.White
        ),
        border = BorderStroke(1.dp, AccentOrange)
    )
}

private fun truncate(text: String, maxLength: Int = 15): String {
    return if (text.length > maxLength) "${text.substring(0, maxLength)}..." else text
}

private data class Quadruple<A, B, C, D>(val first: A, val second: B, val third: C, val fourth: D)


@Composable
fun AdvancedSettingsDialog(
    creationState: CreationState,
    selectedProgram: ProgramEntity?,
    selectedActiveProgram: ActiveProgram?,
    isExporting: Boolean,
    isSharing: Boolean,
    isExportingCsv: Boolean,
    onDismiss: () -> Unit,
    onChangeName: () -> Unit,
    onImportRequest: () -> Unit,
    onExportBpent: () -> Unit,
    onExportExcel: () -> Unit,
    onExportCsv: () -> Unit,
    onActiveProgramSelected: (ActiveProgram?) -> Unit,
    onDataReset: () -> Unit,
    onOpenTutorial: () -> Unit = {}
) {
    val strTitle = stringResource(id = R.string.settings_title)
    val strChangeUserName = stringResource(id = R.string.settings_change_user_name)
    val strTutorial = stringResource(id = R.string.settings_tutorial)
    val strImportProgram = stringResource(id = R.string.settings_import_program)
    val strSharing = stringResource(id = R.string.settings_sharing)
    val strShareProgram = stringResource(id = R.string.settings_share_program)
    val strExporting = stringResource(id = R.string.settings_exporting)
    val strExportToExcel = stringResource(id = R.string.settings_export_to_excel)
    val strGenerateData = stringResource(id = R.string.settings_generate_data)
    val strResetAllData = stringResource(id = R.string.settings_reset_all_data)
    val strClose = stringResource(id = R.string.dialog_close)

    val strExportToCsv = stringResource(id = R.string.settings_export_to_csv)
    val strExportingCsv = stringResource(id = R.string.settings_exporting_csv)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showResetConfirm by remember { mutableStateOf(false) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(strTitle, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {

                SettingsActionRow(strChangeUserName) {
                    onChangeName()
                }
                SettingsActionRow(
                    strTutorial,
                    onClick = {
                        onDismiss()
                        onOpenTutorial()
                    }
                )
                SettingsActionRow(
                    strImportProgram,
                    onClick = onImportRequest
                )
                if (selectedActiveProgram != null && selectedProgram != null) {
                    SettingsActionRow(
                        label = if (isSharing) strSharing else strShareProgram,
                        enabled = !isSharing,
                        onClick = onExportBpent
                    )
                    SettingsActionRow(
                        label = if (isExporting) strExporting else strExportToExcel,
                        enabled = !isExporting,
                        onClick = onExportExcel
                    )
                    // AJOUTÉ : Le bouton pour l'export CSV
                    SettingsActionRow(
                        label = if (isExportingCsv) strExportingCsv else strExportToCsv,
                        enabled = !isExportingCsv,
                        onClick = onExportCsv
                    )
                }
                SettingsActionRow(strGenerateData) {
                    creationState.populateWithGeneratedData()
                    scope.launch {
                        AppStateStore.save(context, creationState.toAppState())
                    }
                    onDismiss()
                }
                SettingsActionRow(strResetAllData, color = Color.Red) {
                    showResetConfirm = true
                }
            }
        },
        confirmButton = {

            TextButton(onClick = onDismiss) { Text(strClose, color = AccentOrange) }
        }
    )

    if (showResetConfirm) {
        ConfirmActionDialog(
            title = stringResource(R.string.confirm_reset_title),
            message = stringResource(R.string.confirm_reset_message),
            confirmLabel = stringResource(R.string.settings_reset_all_data),
            onDismiss = { showResetConfirm = false },
            onConfirm = {
                showResetConfirm = false
                creationState.reset()
                onActiveProgramSelected(null)
                onDataReset()
                scope.launch {
                    AppStateStore.save(context, creationState.toAppState())
                }
                onDismiss()
            }
        )
    }
}

@Composable
fun ChangeNameDialog(
    initialName: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit
) {
    val strTitle = stringResource(id = R.string.change_name_title)
    val strLabel = stringResource(id = R.string.change_name_label)
    val strSave = stringResource(id = R.string.dialog_save)
    val strCancel = stringResource(id = R.string.dialog_cancel)

    var tempName by remember { mutableStateOf(initialName) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(strTitle, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = tempName,
                onValueChange = { tempName = it },
                label = { Text(strLabel) },
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Left,
                    color = TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = TextMuted,
                    focusedLabelColor = AccentOrange,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = AccentOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(tempName) }) {
                Text(strSave, color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(strCancel, color = TextMuted) }
        }
    )
}

@Composable
fun SettingsActionRow(
    label: String,
    color: Color = TextPrimary,
    enabled: Boolean = true,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = enabled) { onClick() }
            .padding(vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = if (enabled) color else TextMuted, fontSize = 14.sp)
    }
}

@Composable
fun ValueEditDialog(initialValue: Int, onDismiss: () -> Unit, onConfirm: (Int) -> Unit) {

    val strTitle = stringResource(id = R.string.value_dialog_title)
    val strOk = stringResource(id = R.string.dialog_ok)

    var textValue by remember { mutableStateOf(initialValue.toString()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(strTitle, color = TextPrimary) },
        text = {
            OutlinedTextField(
                value = textValue,
                onValueChange = { if (it.all { c -> c.isDigit() }) textValue = it },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Left,
                    color = TextPrimary
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = TextMuted,
                    focusedLabelColor = AccentOrange,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = AccentOrange
                )
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(textValue.toIntOrNull() ?: 0) }) {
                Text(strOk, color = AccentOrange)
            }
        }
    )
}

@Composable
fun ConfirmActionDialog(
    title: String,
    message: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(title, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = { Text(message, color = TextPrimary) },
        confirmButton = {
            TextButton(onClick = onConfirm) {
                Text(confirmLabel, color = AccentRed, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_cancel), color = TextMuted)
            }
        }
    )
}
