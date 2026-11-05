package com.ymz.basicplanner.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.R
import com.ymz.basicplanner.data.*
import com.ymz.basicplanner.model.*
import com.ymz.basicplanner.compose.*
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@Composable
fun AnchorWithPlusIcon(
    modifier: Modifier = Modifier,
    contentDescription: String
) {
    Box(modifier = modifier) {

        Icon(
            imageVector = Icons.Default.Anchor,
            contentDescription = contentDescription,
            tint = AccentOrange,
            modifier = Modifier.size(24.dp)
        )


        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset(x = 4.dp, y = (-4).dp)
                .size(12.dp)
                .clip(CircleShape)
                .background(AccentOrange),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Add,
                contentDescription = null,
                tint = DarkBackground,
                modifier = Modifier.size(8.dp)
            )
        }
    }
}
@Composable
fun SessionScreen(
    sessionId: Long,
    creationState: CreationState,
    onBack: () -> Unit
) {
    val strRestOver = stringResource(id = R.string.session_rest_over)
    val strNextSetPrompt = stringResource(id = R.string.session_next_set_prompt)
    val strOk = stringResource(id = R.string.session_ok)
    val strSessionNotFound = stringResource(id = R.string.session_not_found)
    val strClose = stringResource(id = R.string.close)
    val strSet = stringResource(id = R.string.set)
    val strCancel = stringResource(id = R.string.dialog_cancel)
    val strAddSetTitle = stringResource(id = R.string.session_add_set_title)
    val strAddSetSetsLabel = stringResource(id = R.string.session_add_set_sets_label)
    val strAddSetRepsLabel = stringResource(id = R.string.session_add_set_reps_label)
    val strAddSetKgLabel = stringResource(id = R.string.session_add_set_kg_label)
    val strAddSetRestLabel = stringResource(id = R.string.session_add_set_rest_label)
    val strAddSetConfirm = stringResource(id = R.string.session_add_set_confirm)

    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var activeTimerValue by remember { mutableStateOf<Int?>(null) }
    var activeTimerKey by remember { mutableStateOf<String?>(null) }
    var showTimerDialog by remember { mutableStateOf(false) }

    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { }

    var showHangItemDialog by remember { mutableStateOf(false) }
    var selectedHangEntityId by remember { mutableStateOf<Long?>(null) }
    var selectedHangEntityName by remember { mutableStateOf("") }


    var showItemFamilyDialog by remember { mutableStateOf(false) }

    var showAddSetDialog by remember { mutableStateOf(false) }
    var addSetExerciseId by remember { mutableStateOf<Long?>(null) }
    var addSetSets by remember { mutableStateOf("") }
    var addSetReps by remember { mutableStateOf("") }
    var addSetKg by remember { mutableStateOf("") }
    var addSetRest by remember { mutableStateOf("") }

    LaunchedEffect(Unit) {
        while (true) {
            val end = TimerService.persistedEndTime(context)
            if (end != null) {
                val left = ((end - System.currentTimeMillis()) / 1000).toInt()
                if (left > 0) {
                    activeTimerValue = left
                    if (activeTimerKey == null) {
                        activeTimerKey = TimerService.persistedLabel(context)
                    }
                } else if (activeTimerKey != null) {
                    showTimerDialog = true
                    activeTimerKey = null
                    activeTimerValue = null
                }
            }
            delay(500L)
        }
    }


    if (showTimerDialog) {
        AlertDialog(
            onDismissRequest = { showTimerDialog = false },
            containerColor = DarkSurfaceAlt,
            title = { Text(strRestOver, color = TextPrimary) },
            text = { Text(strNextSetPrompt, color = TextPrimary) },
            confirmButton = {
                TextButton(onClick = { showTimerDialog = false }) {
                    Text(strOk, color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }

    val session = remember(sessionId, creationState.sessions.toList()) {
        creationState.sessions.find { it.id == sessionId }
    }

    if (session == null) {
        Box(modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground), contentAlignment = Alignment.Center) {
            Text(strSessionNotFound, color = Color.Red)
        }
        return
    }

    BackHandler {
        val activeProg = creationState.getActiveProgramForSession(sessionId)
        creationState.recordSessionBestPerformances(sessionId, activeProg?.id)
        scope.launch {
            AppStateStore.save(context, creationState.toAppState())
            onBack()
        }
    }

    val effectivePct = remember(sessionId, creationState.activePrograms.toList()) {
        val activeProg = creationState.getActiveProgramForSession(sessionId)
        val dayPct = if (activeProg != null && session.date != null) {
            effectiveAdjustmentPercent(
                creationState.toAppState(),
                activeProg.id,
                session.date!!.toLocalDate()
            )
        } else {
            0
        }
        session.adjustmentPercent + dayPct
    }

    fun calculateProgress(currentSession: SessionEntity) {

        val sessionExercises = currentSession.exerciseIds.mapNotNull { id ->
            creationState.exercises.find { it.id == id }
        }
        val totalSets = sessionExercises.sumOf { it.entries.size }
        val completedSets = sessionExercises.sumOf { it.entries.count { it.finished == true } }
        val newPercent = if (totalSets > 0) completedSets.toFloat() / totalSets else 0f

        val sessionIndex = creationState.sessions.indexOfFirst { it.id == currentSession.id }
        if (sessionIndex != -1) {

            creationState.sessions[sessionIndex] = currentSession.copy(percent = newPercent)
        }
    }

    fun updateExerciseInState(updatedExercise: ExerciseEntity) {
        val index = creationState.exercises.indexOfFirst { it.id == updatedExercise.id }
        if (index != -1) {
            creationState.exercises[index] = updatedExercise


            session.let { calculateProgress(it) }

            scope.launch {
                AppStateStore.save(context, creationState.toAppState())
            }
        }
    }

    fun addSetsToExercise() {
        val exId = addSetExerciseId ?: return
        val exercise = creationState.exercises.find { it.id == exId } ?: return
        val setsCount = addSetSets.toIntOrNull() ?: return
        val reps = addSetReps.toIntOrNull() ?: return
        val kg = addSetKg.toIntOrNull() ?: return
        val rest = addSetRest.toIntOrNull() ?: 0
        if (setsCount <= 0 || reps <= 0 || kg <= 0) return
        val newEntries = exercise.entries.toMutableList()
        repeat(setsCount) {
            newEntries.add(SetEntry(sets = 1, reps = reps, intensity = 0, rest = rest, finished = true, weightKg = kg))
        }
        updateExerciseInState(exercise.copy(entries = newEntries))
        showAddSetDialog = false
        addSetExerciseId = null
        addSetSets = ""
        addSetReps = ""
        addSetKg = ""
        addSetRest = ""
    }

    fun deleteSetFromExercise(exerciseId: Long, entryIndex: Int) {
        val exercise = creationState.exercises.find { it.id == exerciseId } ?: return
        if (entryIndex !in exercise.entries.indices) return
        if (exercise.entries[entryIndex].weightKg == null) return // only added sets are deletable
        val newEntries = exercise.entries.toMutableList()
        newEntries.removeAt(entryIndex)
        updateExerciseInState(exercise.copy(entries = newEntries))
    }

    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                CommonHeader(
                    centerContent = {
                        SessionTopBar(title = session.name, percent = session.percent)
                    },
                    endContent = {
                        IconButton(onClick = {
                            selectedHangEntityId = session.id
                            selectedHangEntityName = session.name
                            showHangItemDialog = true
                        }) {
                            Icon(
                                imageVector = Icons.Default.Anchor,
                                contentDescription = stringResource(R.string.hang_new_item),
                                tint = AccentOrange
                            )
                        }
                    }
                )
            }
        },
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    IconButton(onClick = { showItemFamilyDialog = true }) {

                        AnchorWithPlusIcon(
                            contentDescription = stringResource(R.string.item_family_management)
                        )
                    }
                },
                rightButtons = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                val activeProg = creationState.getActiveProgramForSession(sessionId)
                                creationState.recordSessionBestPerformances(sessionId, activeProg?.id)
                                AppStateStore.save(context, creationState.toAppState())
                                onBack()
                            }
                        }
                    ) {
                        Text(
                            text = strClose,
                            color = AccentOrange,
                            fontWeight = FontWeight.Bold,
                            fontSize = 16.sp
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
        ) {
            HarmonizedLazyColumn(
                modifier = Modifier
                    .weight(1f)
                    .padding(horizontal = 8.dp)
            ) {


                itemsIndexed(session.exerciseIds, key = { _, exId -> exId }) { index, exId -> 
                    val exercise = creationState.exercises.find { it.id == exId } 

                    if (exercise != null) {

                        val baseRm = session.baseRms?.get(exercise.movementId)


                        val displayName = exercise.getFormatName(creationState.movements)

                        ExerciseExecutionItem(
                            exercise = exercise,
                            displayName = displayName,
                            baseRm = baseRm,
                            adjustmentPct = effectivePct,

                            activeTimerValue = activeTimerValue,
                            activeTimerKey = activeTimerKey,
                            onStartTimer = { initialRest, key ->
                                activeTimerValue = initialRest
                                activeTimerKey = key
                                if (Build.VERSION.SDK_INT >= 33 &&
                                    ContextCompat.checkSelfPermission(
                                        context,
                                        Manifest.permission.POST_NOTIFICATIONS
                                    ) != PackageManager.PERMISSION_GRANTED
                                ) {
                                    notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                }
                                TimerService.start(context, initialRest, key)
                            },
                            onSetToggle = { entryIndex, isChecked ->
                                val newEntries = exercise.entries.toMutableList()
                                newEntries[entryIndex] = newEntries[entryIndex].copy(finished = isChecked)

                                updateExerciseInState(exercise.copy(entries = newEntries))
                            },
                            onExerciseToggle = { isChecked ->

                                val newEntries = exercise.entries.map { it.copy(finished = isChecked) }.toMutableList()
                                updateExerciseInState(exercise.copy(entries = newEntries))
                            },
                            onAddSet = {
                                addSetExerciseId = exercise.id
                                addSetSets = ""
                                addSetReps = ""
                                addSetKg = ""
                                addSetRest = ""
                                showAddSetDialog = true
                            },
                            onDeleteSet = { entryIndex -> deleteSetFromExercise(exercise.id, entryIndex) },
                            onHangOnExerciseClick = {
                                selectedHangEntityId = exercise.id
                                selectedHangEntityName = displayName.toString()
                                showHangItemDialog = true
                            },
                            onHangOnSetEntryClick = { setEntry, index ->
                                selectedHangEntityId = setEntry.id
                                selectedHangEntityName = "$strSet ${index + 1}"
                                showHangItemDialog = true
                            }
                        )
                    }
                }
            }

        }
    }
    if (showHangItemDialog && selectedHangEntityId != null) {
        val activeProgram = creationState.getActiveProgramForSession(sessionId)
        ItemManagementDialog(
            mode = DialogMode.HANG_ITEMS,
            entityName = selectedHangEntityName,
            items = creationState.getItemsForEntity(selectedHangEntityId!!, activeProgram?.id),
            itemFamilies = creationState.itemFamilies,
            onDismiss = {
                showHangItemDialog = false
                selectedHangEntityId = null
                selectedHangEntityName = ""
            },
            onHang = { family, value ->
                creationState.createAndHangItem(selectedHangEntityId!!, activeProgram?.id, family, value)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onUpdateItem = { item, newValue ->
                creationState.updateHangedItem(item, newValue)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onDeleteItem = { item ->
                creationState.deleteHangedItem(item)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            }
        )
    }

    if (showItemFamilyDialog) {
        ItemManagementDialog(
            mode = DialogMode.MANAGE_FAMILIES,
            itemFamilies = creationState.itemFamilies,
            onDismiss = { showItemFamilyDialog = false },
            onCreateFamily = { name, description, itemType, defaultValue ->
                creationState.createItemFamily(name, description, itemType, defaultValue)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onUpdateFamily = { itemFamily, newName, newDescription, newItemType, newDefaultValue ->
                creationState.updateItemFamily(itemFamily, newName, newDescription, newItemType, newDefaultValue)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onDeleteFamily = { itemFamily ->
                creationState.deleteItemFamily(itemFamily)
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            }
        )
    }

    if (showAddSetDialog) {
        AlertDialog(
            onDismissRequest = {
                showAddSetDialog = false
                addSetExerciseId = null
            },
            containerColor = DarkSurfaceAlt,
            title = { Text(strAddSetTitle, color = TextPrimary) },
            text = {
                Column {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addSetSets,
                            onValueChange = { addSetSets = it.filter(Char::isDigit) },
                            label = { Text(strAddSetSetsLabel) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = addSetReps,
                            onValueChange = { addSetReps = it.filter(Char::isDigit) },
                            label = { Text(strAddSetRepsLabel) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = addSetKg,
                            onValueChange = { addSetKg = it.filter(Char::isDigit) },
                            label = { Text(strAddSetKgLabel) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedTextField(
                            value = addSetRest,
                            onValueChange = { addSetRest = it.filter(Char::isDigit) },
                            label = { Text(strAddSetRestLabel) },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                                cursorColor = AccentOrange
                            ),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { addSetsToExercise() }) {
                    Text(strAddSetConfirm, color = AccentOrange, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    showAddSetDialog = false
                    addSetExerciseId = null
                }) {
                    Text(strCancel, color = TextMuted)
                }
            }
        )
    }

}
