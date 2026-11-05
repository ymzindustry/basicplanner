package com.ymz.basicplanner.ui
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FilterList
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.EntityEditDialog
import com.ymz.basicplanner.compose.EntityRow
import com.ymz.basicplanner.compose.HarmonizedLazyColumn
import com.ymz.basicplanner.compose.UnifiedSelectionMenu
import com.ymz.basicplanner.compose.UniversalEntityInfoDialog
import com.ymz.basicplanner.compose.getFormatName
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.model.*
import kotlinx.coroutines.launch
import com.ymz.basicplanner.R
import java.time.LocalTime

@Composable
fun CreationScreen(
    creationState: CreationState,
    tutorialActive: Boolean = false,
    tutorialDialogActive: Boolean = false,
    onCreateRequested: () -> Unit = {},
    onTutorialDialogConfirmed: () -> Unit = {},
    onTutorialDialogSkipped: () -> Unit = {}
) {
    val strFilterByOwner = stringResource(id = R.string.creation_filter_by_owner)
    val strAllOwners = stringResource(id = R.string.creation_all_owners)
    val strCreateEntity = stringResource(id = R.string.creation_create_entity)
    val strNoEntities = stringResource(id = R.string.creation_no_entities)
    val strToBeCompleted = stringResource(id = R.string.creation_composition_to_be_completed)
    val strUnknown = stringResource(id = R.string.unknown)
    val strRestDay = stringResource(id = R.string.rest_day_name)
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    val refreshTick = creationState.dataVersion.value
    var dataVersion by remember { mutableIntStateOf(0) }
    var currentKind by remember { mutableStateOf(EntityKind.EXERCISE) }
    val kinds = EntityKind.entries
    var currentIndex by remember {
        mutableIntStateOf(kinds.indexOf(currentKind).coerceAtLeast(0))
    }
    LaunchedEffect(currentKind) {
        currentIndex = kinds.indexOf(currentKind).coerceAtLeast(0)
    }
    var dxAccum by remember { mutableFloatStateOf(0f) }
    val step = 60f
    var showDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<BaseEntity?>(null) }
    var infoKind by remember { mutableStateOf<EntityKind?>(null) }
    var infoEntity by remember { mutableStateOf<BaseEntity?>(null) }
    var selectedOwner by remember { mutableStateOf<String?>(null) }
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val allEntitiesOfKind = remember(currentKind, dataVersion, refreshTick) {
        creationState.getEntities(currentKind).filter { creationState.isPlanLevel(it) }
    }
    val availableOwners = remember(allEntitiesOfKind) {
        allEntitiesOfKind.mapNotNull { it.owner }.distinct().sorted()
    }
    val entities by remember(allEntitiesOfKind, selectedOwner) {
        derivedStateOf {
            if (selectedOwner == null) {
                allEntitiesOfKind
            } else {
                allEntitiesOfKind.filter { it.owner == selectedOwner }
            }
        }
    }
    val initialExerciseEntries = remember(editingEntity, dataVersion) {
        if (editingEntity is ExerciseEntity)
            (editingEntity as ExerciseEntity).entries.toList()
        else
            emptyList()
    }
    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    EntityKind.entries.forEach { kind ->
                        val isSelected = (kind == currentKind)
                        Text(
                            text = kind.getLabel(),
                            color = if (isSelected) AccentOrange else TextMuted,
                            fontSize = 11.sp,
                            fontWeight = if (isSelected) FontWeight.ExtraBold else FontWeight.Normal,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .clickable(enabled = !tutorialActive) { currentKind = kind }
                                .padding(horizontal = 2.dp)
                        )
                    }
                }
            }
        },
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    Box {
                        IconButton(
                            onClick = { filterMenuExpanded = true },
                            enabled = !tutorialActive
                        ) {
                            Icon(
                                imageVector = Icons.Default.FilterList,
                                contentDescription = strFilterByOwner,
                                tint = if (selectedOwner == null) TextMuted else AccentOrange
                            )
                        }
                        UnifiedSelectionMenu(
                            expanded = filterMenuExpanded,
                            onDismiss = { filterMenuExpanded = false },
                            modifier = Modifier.heightIn(max = 280.dp),
                            headerTitle = strAllOwners,
                            isHeaderSelected = (selectedOwner == null),
                            onHeaderClick = { selectedOwner = null },
                            items = availableOwners,
                            selectedItem = selectedOwner,
                            onItemClick = { owner ->
                                selectedOwner = owner
                            }
                        )
                    }
                    if (selectedOwner != null) {
                        Text(
                            text = "$selectedOwner",
                            fontSize = 16.sp,
                            color = AccentOrange,
                            modifier = Modifier.padding(start = 4.dp)
                        )
                    }
                },
                rightButtons = {
                    IconButton(
                        onClick = {
                            editingEntity = null
                            showDialog = true
                            onCreateRequested()
                        },
                        modifier = Modifier.onGloballyPositioned { coords ->
                            TutorialTargets.creationAddExerciseButton = coords.boundsInRoot()
                        }
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = strCreateEntity,
                            tint = AccentOrange
                        )
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .pointerInput(kinds, currentIndex, tutorialActive) {
                    if (tutorialActive) return@pointerInput
                    detectDragGestures(
                        onDrag = { _, dragAmount ->
                            dxAccum += dragAmount.x
                            while (dxAccum <= -step) {
                                dxAccum += step
                                currentIndex = (currentIndex + 1).let {
                                    if (it > kinds.lastIndex) 0 else it
                                 }
                                currentKind = kinds[currentIndex]
                            }
                            while (dxAccum >= step) {
                                dxAccum -= step
                                currentIndex = (currentIndex - 1).let {
                                    if (it < 0) kinds.lastIndex else it
                                 }
                                currentKind = kinds[currentIndex]
                            }
                        },
                        onDragEnd = { },
                        onDragCancel = { }
                    )
                }
        ) {
            if (entities.isEmpty()) {
                Box(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        strNoEntities.format(currentKind.getLabel().lowercase()),
                        color = TextMuted
                    )
                }
            } else {
                HarmonizedLazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = 8.dp)
                ) {
                    itemsIndexed(entities, key = { _, entity -> entity.id
                    }) { _, entity ->
                        val subKind = SubEntityKind.valueOf(currentKind.name)

                        val size = remember(entity, dataVersion) {
                            if (currentKind == EntityKind.EXERCISE)
                                (entity as? ExerciseEntity)?.entries?.size ?: 0
                            else
                                creationState.childrenOf(currentKind, entity).size
                        }

                        val subKindLabel = subKind.getLabel()

                        val composition = remember(currentKind, size, subKindLabel) {
                            when {
                                currentKind == EntityKind.MOVEMENT -> null
                                size == 0 -> strToBeCompleted
                                else -> {
                                    resources.getQuantityString(R.plurals.creation_composition_items, size, size, subKindLabel)
                                }
                            }
                        }

                        val displayName = remember(
                            entity,
                            entity.name,
                            (entity as? ExerciseEntity)?.movementId,
                            dataVersion
                        ) {
                            entity.getFormatName(
                                creationState.movements,
                                strUnknown = strUnknown,
                                strRestDay = strRestDay
                            )
                        }


                        EntityRow(
                            entity = entity,
                            kind = currentKind,
                            currentName = displayName,
                            composition = composition,
                            creationState = creationState,
                            enabled = !tutorialActive,
                            onEdit = { entityToEdit ->
                                if (!tutorialActive) {
                                    editingEntity = entityToEdit
                                    showDialog = true
                                }
                            },
                            onCopy = {
                                if (!tutorialActive) {
                                    creationState.copyEntity(currentKind, entity)
                                    dataVersion++
                                    scope.launch {
                                        AppStateStore.save(context, creationState.toAppState())
                                    }
                                }
                            },
                            onDelete = {
                                if (!tutorialActive) {
                                    creationState.deleteEntity(currentKind, entity)
                                    dataVersion++
                                    scope.launch {
                                        AppStateStore.save(context, creationState.toAppState())
                                    }
                                }
                            }
                        )
                    }
                }
            }
        }
        if (showDialog) {
            val kindForDialog = (editingEntity?.let {
                when(it) {
                    is MovementEntity -> EntityKind.MOVEMENT
                    is ExerciseEntity -> EntityKind.EXERCISE
                    is SessionEntity -> EntityKind.SESSION
                    is DayEntity -> EntityKind.DAY
                    is WeekEntity -> EntityKind.WEEK
                    is BlockEntity -> EntityKind.BLOCK
                    is ProgramEntity -> EntityKind.PROGRAM
                    else -> currentKind
                }
            }) ?: currentKind

            val availableChildren = creationState.availableChildren(kindForDialog)
                .filter { creationState.isPlanLevel(it) }
            val initialChildren = editingEntity
                ?.let { creationState.childrenOf(kindForDialog, it) }
                ?: emptyList()

            EntityEditDialog(
                kind = kindForDialog,
                availableChildren = availableChildren,
                initialEntity = editingEntity,
                initialChildren = initialChildren,
                initialExerciseEntries = initialExerciseEntries,
                creationState = creationState,
                onDismiss = { showDialog = false },
                tutorialDialogActive = tutorialDialogActive,
                onTutorialSkip = {
                    showDialog = false
                    onTutorialDialogSkipped()
                },
                onConfirm = { name, orderedChildIds, entries, adjustment, subAdj ->
                    when (kindForDialog) {
                        EntityKind.EXERCISE -> {
                            val ex = if (editingEntity == null) {
                                creationState.createEntity(kindForDialog, name, orderedChildIds) as ExerciseEntity
                            } else {
                                val existing = editingEntity as ExerciseEntity
                                creationState.updateEntity(kindForDialog, existing, name, orderedChildIds)
                                existing
                            }
                            ex.entries.clear()
                            ex.entries.addAll(entries)
                        }
                        EntityKind.WEEK -> {
                            // A weekday slot holds the chosen entity itself — a day, a session or a
                            // single exercise. No wrapper is created, so the program always shows the
                            // entity's live name instead of a snapshot taken when it was dropped here.
                            if (editingEntity == null) {
                                creationState.createEntity(kindForDialog, name, orderedChildIds)
                            } else {
                                creationState.updateEntity(kindForDialog, editingEntity!!, name, orderedChildIds)
                            }
                        }
                        EntityKind.PROGRAM -> {
                            // A program holds its blocks and weeks directly: no wrapper block is
                            // created around a week, so the tree always shows the week's live name.
                            if (editingEntity == null) {
                                creationState.createEntity(kindForDialog, name, orderedChildIds)
                            } else {
                                creationState.updateEntity(kindForDialog, editingEntity!!, name, orderedChildIds)
                            }
                        }
                        else -> {
                            // A block's level is derived from where it sits (see
                            // CreationState.recomputeBlockLevels), never from its children.
                            if (editingEntity == null) {
                                creationState.createEntity(kindForDialog, name, orderedChildIds)
                            } else {
                                creationState.updateEntity(kindForDialog, editingEntity!!, name, orderedChildIds)
                            }
                        }
                    }
                    if (editingEntity == null) {
                        when (kindForDialog) {
                            EntityKind.SESSION -> creationState.sessions.lastOrNull()?.adjustmentPercent = adjustment
                            EntityKind.DAY -> creationState.days.lastOrNull()?.adjustmentPercent = adjustment
                            EntityKind.WEEK -> creationState.weeks.lastOrNull()?.adjustmentPercent = adjustment
                            EntityKind.BLOCK -> creationState.blocks.lastOrNull()?.adjustmentPercent = adjustment
                            EntityKind.PROGRAM -> creationState.programs.lastOrNull()?.adjustmentPercent = adjustment
                            else -> {}
                        }
                    } else {
                        when (val target = editingEntity) {
                            is SessionEntity -> target.adjustmentPercent = adjustment
                            is DayEntity -> target.adjustmentPercent = adjustment
                            is WeekEntity -> target.adjustmentPercent = adjustment
                            is BlockEntity -> target.adjustmentPercent = adjustment
                            is ProgramEntity -> target.adjustmentPercent = adjustment
                            else -> {}
                        }
                    }
                    // Apply per-child sub-adjustments (padded to child count)
                    val paddedSubAdj = MutableList(orderedChildIds.size) { i -> subAdj.getOrElse(i) { 0 } }
                    val subTarget: BaseEntity? = if (editingEntity == null) {
                        when (kindForDialog) {
                            EntityKind.DAY -> creationState.days.lastOrNull()
                            EntityKind.WEEK -> creationState.weeks.lastOrNull()
                            EntityKind.BLOCK -> creationState.blocks.lastOrNull()
                            EntityKind.PROGRAM -> creationState.programs.lastOrNull()
                            else -> null
                        }
                    } else editingEntity
                    when (val st = subTarget) {
                        is DayEntity -> { st.subAdjustments.clear(); st.subAdjustments.addAll(paddedSubAdj) }
                        is WeekEntity -> { st.subAdjustments.clear(); st.subAdjustments.addAll(paddedSubAdj) }
                        is BlockEntity -> { st.subAdjustments.clear(); st.subAdjustments.addAll(paddedSubAdj) }
                        is ProgramEntity -> { st.subAdjustments.clear(); st.subAdjustments.addAll(paddedSubAdj) }
                        else -> {}
                    }
                    dataVersion++
                    scope.launch { AppStateStore.save(context, creationState.toAppState()) }
                    showDialog = false
                    if (tutorialDialogActive) onTutorialDialogConfirmed()
                }
            )
        }
        if (infoKind != null && infoEntity != null) {
            UniversalEntityInfoDialog(
                entity = infoEntity!!,
                kind = infoKind!!,
                title = infoKind!!.getLabel().uppercase(),
                creationState = creationState,
                onDismiss = {
                    infoKind = null
                    infoEntity = null
                },
                onEditRequest = { entityToEdit ->
                    editingEntity = entityToEdit
                    showDialog = true
                    infoKind = null
                    infoEntity = null
                }
            )
        }
    }
}
