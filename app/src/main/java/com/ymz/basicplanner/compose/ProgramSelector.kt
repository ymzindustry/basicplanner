package com.ymz.basicplanner.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DatePickerDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.model.ActiveProgram
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.InterpolationStrategy
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.RmSettings
import com.ymz.basicplanner.ui.AccentOrange
import com.ymz.basicplanner.ui.DarkBackground
import com.ymz.basicplanner.ui.DarkSurfaceAlt
import com.ymz.basicplanner.ui.TextMuted
import com.ymz.basicplanner.ui.TextPrimary
import com.ymz.basicplanner.ui.AccentRed
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.getLabel

class RmInputData {
    var start by mutableStateOf("0")
    var target by mutableStateOf("0")
    var strategy by mutableStateOf(InterpolationStrategy.CONST)
    var blockLevel by mutableStateOf(0)
}

@Composable
fun ProgramSelector(
    creationState: CreationState,
    programs: List<ProgramEntity>,
    activePrograms: List<ActiveProgram>,
    selectedActiveProgram: ActiveProgram?,
    enabled: Boolean = true, 
    onActiveProgramSelected: (ActiveProgram?) -> Unit
) {
    val strSwitchProgramDesc = stringResource(id = R.string.selector_switch_program_desc)
    val strUpdate = stringResource(id = R.string.update)
    val strDelete = stringResource(id = R.string.delete)
    val strCreate = stringResource(id = R.string.create)
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var showActiveDialog by remember { mutableStateOf(false) }
    var showProgramDialog by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var showDateDialog by remember { mutableStateOf(false) }
    var programToAssociate by remember { mutableStateOf<ProgramEntity?>(null) }
    var activeProgramName by remember { mutableStateOf("") }
    var selectedStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var startDateText by remember { mutableStateOf("") }
    var foundExercises by remember { mutableStateOf<List<Long>>(emptyList()) }
    val rmInputs = remember { mutableStateMapOf<Long, RmInputData>() }
    var editingActiveProgram by remember { mutableStateOf<ActiveProgram?>(null) }
    val dateFormatter = remember {
        DateTimeFormatter.ofPattern("dd/MM/yy")
    }
    val isDateValid by remember(startDateText) {
        derivedStateOf {
            if (startDateText.isBlank()) {
                false
            } else {
                runCatching { LocalDate.parse(startDateText, dateFormatter) }.isSuccess
            }
        }
    }
    val closeAll: () -> Unit = {
        showActiveDialog = false
        showProgramDialog = false
        showNameDialog = false
        showDateDialog = false
        programToAssociate = null
        activeProgramName = ""
        selectedStartDate = null
        startDateText = ""
        editingActiveProgram = null
    }
    IconButton(
        onClick = { showActiveDialog = true },
        enabled = enabled 
    ) {
        Icon(
            imageVector = Icons.Default.SwapHoriz,
            contentDescription = strSwitchProgramDesc,
            tint = if (enabled) AccentOrange else TextMuted 
        )
    }
    if (showActiveDialog) {
        ActiveProgramSelectDialog(
            activePrograms = activePrograms,
            selectedActiveProgram = selectedActiveProgram,
            onActiveSelected = { ap ->
                onActiveProgramSelected(ap)
                closeAll()
            },
            onAssociateClick = {
                showActiveDialog = false
                showProgramDialog = true
            },
            onEdit = { ap ->
                editingActiveProgram = ap
                programToAssociate = programs.find { it.id == ap.programId }
                activeProgramName = ap.name
                selectedStartDate = ap.startDate
                startDateText = ap.startDate.format(dateFormatter)
                val prog = programs.find { it.id == ap.programId }
                if (prog != null) {
                    foundExercises = creationState.getUniqueMovementIdsForProgram(prog)
                    rmInputs.clear()
                    foundExercises.forEach { exoName ->
                        val savedSettings = ap.rmSettings[exoName]
                        val inputData = RmInputData()
                        if (savedSettings != null) {
                            inputData.start = savedSettings.start.toString()
                            inputData.target = savedSettings.target.toString()
                            inputData.strategy = savedSettings.strategy
                            inputData.blockLevel = savedSettings.blockLevel
                        }

                        rmInputs[exoName] = inputData
                    }
                }
                showActiveDialog = false
                showNameDialog = true
            },
            onDelete = { ap ->
                creationState.deleteActiveProgram(ap)
                scope.launch {
                    AppStateStore.save(context, creationState.toAppState())
                }

                val remaining = creationState.activePrograms
                val newSelection = remaining.firstOrNull()
                onActiveProgramSelected(newSelection)
            },
            onDismiss = { closeAll() }
        )
    }
    if (showProgramDialog) {
        ProgramPickDialog(
            programs = programs,
            selectedProgram = programToAssociate,
            onProgramSelected = { programToAssociate = it },
            onNext = {
                val prog = programToAssociate
                if (prog != null) {
                    activeProgramName = prog.name

                    val today = LocalDate.now()
                    selectedStartDate = today
                    startDateText = today.format(dateFormatter)
                    
                    foundExercises = creationState.getUniqueMovementIdsForProgram(prog)
                    rmInputs.clear()
                    foundExercises.forEach { exoName ->
                        rmInputs[exoName] = RmInputData()
                    }

                    showProgramDialog = false
                    showNameDialog = true
                }
            },
            onBack = {
                showProgramDialog = false
                showActiveDialog = true
            },
            onDismiss = { closeAll() }
        )
    }
    if (showNameDialog) {
        val defaultName = programToAssociate?.name ?: ""
        val isEditing = editingActiveProgram != null
        val buttonText = if (isEditing) strUpdate  else strCreate
        ActiveProgramNameDialog(
            creationState = creationState, 
            currentName = activeProgramName,
            defaultName = defaultName,
            startDateText = startDateText,
            canSubmit = isDateValid,
            exerciseIds = foundExercises, 
            rmInputs = rmInputs,
            maxBlockLevel = programToAssociate?.let { creationState.maxBlockLevel(it) } ?: -1,
            confirmButtonText = buttonText,
            onNameChange = { activeProgramName = it },
            onStartDateTextChange = { startDateText = it },
            onRequestDatePick = {
                showDateDialog = true
            },
            onNext = {
                val prog = programToAssociate
                if (prog != null && isDateValid) {
                    val date = LocalDate.parse(startDateText, dateFormatter)
                    val finalName = activeProgramName.ifBlank { prog.name }

                    val settingsToSave = rmInputs.mapValues { (_, data) ->
                        RmSettings(
                            start = data.start.toIntOrNull() ?: 0,
                            target = data.target.toIntOrNull() ?: 0,
                            strategy = data.strategy,
                            blockLevel = data.blockLevel
                        )
                    }
                    if (editingActiveProgram != null) {
                        val base = editingActiveProgram!!
                        creationState.updateActiveProgram(
                            activeProgram = base,
                            newName = finalName,
                            newStartDate = date,
                            newRmSettings = settingsToSave
                        )
                        scope.launch {
                            AppStateStore.save(context, creationState.toAppState())
                        }
                        if (selectedActiveProgram?.id == base.id) {
                            val updated = creationState.activePrograms.find { it.id == base.id }
                            onActiveProgramSelected(updated)
                        }
                    } else {
                        val active = creationState.startProgram(
                            program = prog,
                            startDate = date,
                            name = finalName,
                            rmSettings = settingsToSave
                        )
                        scope.launch {
                            AppStateStore.save(context, creationState.toAppState())
                        }
                        onActiveProgramSelected(active)
                    }
                    closeAll()
                }
            },
            onBack = {
                showNameDialog = false
                if (editingActiveProgram != null) {
                    showActiveDialog = true
                } else {
                    showProgramDialog = true
                }
            },
            onDismiss = { closeAll() }
        )
    }
    if (showDateDialog) {
        StartDatePickerDialog(
            onDateSelected = { date ->
                selectedStartDate = date
                startDateText = date.format(dateFormatter)
                showDateDialog = false
            },
            onBack = {
                showDateDialog = false
            },
            onDismiss = { closeAll() }
        )
    }
}

@Composable
private fun ActiveProgramSelectDialog(
    activePrograms: List<ActiveProgram>,
    selectedActiveProgram: ActiveProgram?,
    onActiveSelected: (ActiveProgram) -> Unit,
    onAssociateClick: () -> Unit,
    onEdit: (ActiveProgram) -> Unit,
    onDelete: (ActiveProgram) -> Unit,
    onDismiss: () -> Unit
) {

    val strTitle = stringResource(id = R.string.ps_select_active_program_title)
    val strOptionsDesc = stringResource(id = R.string.ps_options_desc)
    val strEdit = stringResource(id = R.string.edit)
    val strDelete = stringResource(id = R.string.delete)
    val strAssociate = stringResource(id = R.string.ps_associate)
    val strClose = stringResource(id = R.string.close)
    val strNoActiveProgram = stringResource(id = R.string.ps_no_active_programs)
    val strToBeUpdated = stringResource(id = R.string.ps_to_be_updated)           // NOUVEAU



    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = {
            Text(
                text = strTitle,
                color = TextPrimary
            )
        },
        text = {
            if (activePrograms.isEmpty()) {
                Text(
                    text = strNoActiveProgram,
                    color = TextMuted
                )
            } else {

                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp)
                ) {
                    item {
                        Spacer(modifier = Modifier.height(8.dp))
                    }

                    itemsIndexed(
                        items = activePrograms,
                        key = { index, ap -> "${ap.id}_$index" }
                    ) { _, ap ->
                        var menuExpanded by remember { mutableStateOf(false) }
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onActiveSelected(ap) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedActiveProgram?.id == ap.id,
                                onClick = { onActiveSelected(ap) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentOrange,
                                    unselectedColor = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                MarqueeText(
                                    text = ap.name,
                                    color = TextPrimary
                                )
                                if (ap.requiresUpdate) {
                                    Text(
                                        text = strToBeUpdated,
                                        color = Color.Red,
                                        fontSize = 10.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                            }
                            Box {
                                IconButton(onClick = { menuExpanded = true }) {
                                    Icon(
                                        imageVector = Icons.Default.MoreVert,
                                        contentDescription = "Options",
                                        tint = TextMuted
                                    )
                                }
                                DropdownMenu(
                                    expanded = menuExpanded,
                                    onDismissRequest = { menuExpanded = false }
                                ) {
                                    DropdownMenuItem(
                                        text = { Text(strEdit) },
                                        onClick = {
                                            menuExpanded = false
                                            onEdit(ap)
                                        }
                                    )
                                    DropdownMenuItem(
                                        text = { Text(strDelete, color = AccentRed) },
                                        onClick = {
                                            menuExpanded = false
                                            onDelete(ap)
                                        }
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onAssociateClick) {
                Text(strAssociate, color = AccentOrange)
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(strClose, color = TextPrimary)
            }
        }
    )
}

@Composable
private fun ProgramPickDialog(
    programs: List<ProgramEntity>,
    selectedProgram: ProgramEntity?,
    onProgramSelected: (ProgramEntity) -> Unit,
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {

    val strTitle = stringResource(id = R.string.ps_select_program_title)
    val strNoPrograms = stringResource(id = R.string.ps_no_programs_message)
    val strBack = stringResource(id = R.string.back)
    val strNext = stringResource(id = R.string.next)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(strTitle, color = TextPrimary) },
        text = {
            if (programs.isEmpty()) {
                Text(
                    strNoPrograms,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMuted
                )
            } else {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 240.dp)
                )
                {
                    itemsIndexed(
                        items = programs,
                        key = { index, program -> "${program.id}_$index" }
                    ) { _, program ->
                        Row(
                            Modifier
                                .fillMaxWidth()
                                .clickable { onProgramSelected(program) }
                                .padding(vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = selectedProgram?.id == program.id,
                                onClick = { onProgramSelected(program) },
                                colors = RadioButtonDefaults.colors(
                                    selectedColor = AccentOrange,
                                    unselectedColor = TextPrimary
                                )
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(program.name, color = TextPrimary)
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onBack) {
                    Text(strBack, color = TextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onNext, enabled = selectedProgram != null) {
                    Text(
                        strNext,
                        color = if (selectedProgram != null) AccentOrange else TextMuted
                    )
                }
            }
        }
    )
}

/** A strategy choice in the picker: a plain strategy or a block level. */
private sealed interface StrategyOption {
    data class Plain(val strategy: InterpolationStrategy) : StrategyOption
    data class BlockLevel(val level: Int) : StrategyOption
}

@Composable
private fun ExerciseRmRow(
    exerciseName: String,
    inputData: RmInputData,
    maxBlockLevel: Int = 0
) {
    val strStartLabel = stringResource(id = R.string.ps_rm_start_label)
    val strTargetLabel = stringResource(id = R.string.ps_rm_target_label)
    val strFreqLabel = stringResource(id = R.string.ps_rm_freq_label)
    val strStrategyDesc = stringResource(id = R.string.ps_strategy_select_desc)

    val originallyCustom = remember { inputData.strategy == InterpolationStrategy.CUSTOM }

    val availableOptions = remember(inputData.strategy, originallyCustom, maxBlockLevel) {
        val options = mutableListOf<StrategyOption>()
        InterpolationStrategy.entries.forEach { strategy ->
            if (strategy == InterpolationStrategy.BLOCK || strategy == InterpolationStrategy.CUSTOM) return@forEach
            options.add(StrategyOption.Plain(strategy))
        }
        if (maxBlockLevel >= 0) {
            for (level in 0..maxBlockLevel) {
                options.add(StrategyOption.BlockLevel(level))
            }
        }
        if (originallyCustom || inputData.strategy == InterpolationStrategy.CUSTOM) {
            options.add(StrategyOption.Plain(InterpolationStrategy.CUSTOM))
        }
        options
    }

    val selectedOption: StrategyOption = if (inputData.strategy == InterpolationStrategy.BLOCK) {
        StrategyOption.BlockLevel(inputData.blockLevel)
    } else {
        StrategyOption.Plain(inputData.strategy)
    }
    var expanded by remember { mutableStateOf(false) }
    Column(modifier = Modifier
        .fillMaxWidth()
        .padding(bottom = 12.dp)) {
        MarqueeText(
            text = exerciseName,
            color = TextPrimary,
            fontWeight = FontWeight.Bold,
            fontSize = 14.sp
        )
        Spacer(modifier = Modifier.height(4.dp))
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            OutlinedTextField(
                value = inputData.start,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        inputData.start = it
                    }
                },
                label = { Text(strStartLabel, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = TextMuted,
                    focusedLabelColor = AccentOrange,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = AccentOrange
                )
            )

            OutlinedTextField(
                value = inputData.target,
                onValueChange = {
                    if (it.length <= 4 && it.all { c -> c.isDigit() }) {
                        inputData.target = it
                    }
                },
                label = { Text(strTargetLabel, fontSize = 12.sp) },
                modifier = Modifier.weight(1f),
                singleLine = true,
                textStyle = LocalTextStyle.current.copy(
                    textAlign = TextAlign.Center,
                    color = TextPrimary
                ),
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = TextMuted,
                    focusedLabelColor = AccentOrange,
                    unfocusedLabelColor = TextMuted,
                    cursorColor = AccentOrange
                )
            )
            
            Box(modifier = Modifier.weight(1.6f)) {
                var expanded by remember { mutableStateOf(false) }

                OutlinedTextField(
                    value = when (val o = selectedOption) {
                        is StrategyOption.Plain -> o.strategy.getLabel()
                        is StrategyOption.BlockLevel -> stringResource(R.string.interpolation_strategy_block_level, o.level)
                    },
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(strFreqLabel, fontSize = 11.sp) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(
                        color = TextPrimary,
                        fontSize = 12.sp
                    ),
                    trailingIcon = {
                        Icon(
                            imageVector = Icons.Default.ArrowDropDown,
                            contentDescription = null,
                            
                            tint = if (expanded) AccentOrange else TextMuted
                        )
                    },
                    colors = OutlinedTextFieldDefaults.colors(
                        
                        unfocusedBorderColor = if (expanded) AccentOrange else TextMuted,
                        focusedBorderColor = AccentOrange,
                        unfocusedLabelColor = if (expanded) AccentOrange else TextMuted,
                        focusedLabelColor = AccentOrange,
                        cursorColor = AccentOrange,
                        
                        disabledTextColor = TextPrimary,
                        disabledBorderColor = if (expanded) AccentOrange else TextMuted,
                        disabledLabelColor = if (expanded) AccentOrange else TextMuted
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
                    items = availableOptions,
                    itemLabel = { option ->
                        when (option) {
                            is StrategyOption.Plain -> option.strategy.getLabel()
                            is StrategyOption.BlockLevel -> stringResource(R.string.interpolation_strategy_block_level, option.level)
                        }
                    },
                    selectedItem = selectedOption,
                    onItemClick = { option ->
                        when (option) {
                            is StrategyOption.Plain -> {
                                inputData.strategy = option.strategy
                            }
                            is StrategyOption.BlockLevel -> {
                                inputData.strategy = InterpolationStrategy.BLOCK
                                inputData.blockLevel = option.level
                            }
                        }
                        expanded = false
                    }
                )
            }
        }
        Spacer(modifier = Modifier.height(4.dp))
    }
}

@Composable
private fun ActiveProgramNameDialog(
    creationState: CreationState,
    currentName: String,
    defaultName: String,
    startDateText: String,
    canSubmit: Boolean,
    exerciseIds: List<Long>,
    rmInputs: MutableMap<Long, RmInputData>,
    maxBlockLevel: Int = -1,
    onNameChange: (String) -> Unit,
    onStartDateTextChange: (String) -> Unit,
    onRequestDatePick: () -> Unit,
    confirmButtonText: String = stringResource(id = R.string.create),
    onNext: () -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val strTitle = stringResource(id = R.string.ps_configure_program_title)
    val strProgramNameLabel = stringResource(id = R.string.ps_program_name_label)
    val strStartDateLabel = stringResource(id = R.string.ps_start_date_label)
    val strPickDateDesc = stringResource(id = R.string.ps_pick_date_desc)
    val strLoadProgression = stringResource(id = R.string.ps_load_progression_title)
    val strUnknown = stringResource(id = R.string.unknown)
    val strBack = stringResource(id = R.string.back)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        titleContentColor = TextPrimary,
        textContentColor = TextPrimary,
        title = { Text(strTitle, color = TextPrimary) },
        text = {
            Column(modifier = Modifier.fillMaxWidth()) {

                OutlinedTextField(
                    value = currentName,
                    onValueChange = onNameChange,
                    singleLine = true,
                    label = { Text(strProgramNameLabel) },
                    placeholder = { Text(defaultName, color = TextMuted) },
                    textStyle = LocalTextStyle.current.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                        unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                        cursorColor = AccentOrange
                    ),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(modifier = Modifier.height(8.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {

                    Box(modifier = Modifier.weight(1f)) {
                        OutlinedTextField(
                            value = startDateText,
                            onValueChange = {},
                            readOnly = true,
                            enabled = true,
                            singleLine = true,
                            label = { Text(strStartDateLabel) },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = LocalTextStyle.current.copy(color = TextPrimary),
                            colors = OutlinedTextFieldDefaults.colors(
                                unfocusedBorderColor = TextMuted,
                                focusedBorderColor = AccentOrange,
                                unfocusedLabelColor = TextMuted,
                                focusedLabelColor = AccentOrange
                            )
                        )

                        Box(
                            modifier = Modifier
                                .matchParentSize()
                                .clickable(onClick = onRequestDatePick)
                        )
                    }
                    Spacer(modifier = Modifier.width(8.dp))
                    IconButton(onClick = onRequestDatePick) {
                        Icon(Icons.Default.DateRange, strPickDateDesc, tint = AccentOrange)
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = strLoadProgression,
                    style = MaterialTheme.typography.titleMedium,
                    color = AccentOrange,
                    fontSize = 16.sp
                )
                Spacer(modifier = Modifier.height(8.dp))

                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                    LazyColumn(modifier = Modifier.fillMaxWidth()) {

                        items(exerciseIds) { movementId ->
                            val inputData = rmInputs[movementId]

                            val mName = creationState.movements.find { it.id == movementId }?.name ?: strUnknown

                            if (inputData != null) {
                                ExerciseRmRow(
                                    exerciseName = mName,
                                    inputData = inputData,
                                    maxBlockLevel = maxBlockLevel
                                )
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Row {
                TextButton(onClick = onBack) { Text(strBack, color = TextPrimary) }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(onClick = onNext, enabled = canSubmit) {
                    Text(confirmButtonText, color = if (canSubmit) AccentOrange else TextMuted)
                }
            }
        }
    )
}

@Composable
private fun StartDatePickerDialog(
    onDateSelected: (LocalDate) -> Unit,
    onBack: () -> Unit,
    onDismiss: () -> Unit
) {
    val strBack = stringResource(id = R.string.back)
    val strOk = stringResource(id = R.string.ok)
    val strTitle = stringResource(id = R.string.ps_datepicker_title)

    val datePickerState = rememberDatePickerState(
        initialSelectedDateMillis = LocalDate.now()
            .atStartOfDay(ZoneId.systemDefault())
            .toInstant().toEpochMilli()
    )
    DatePickerDialog(
        onDismissRequest = onDismiss,
        colors = DatePickerDefaults.colors(
            containerColor = DarkSurfaceAlt,
            titleContentColor = TextPrimary,
            headlineContentColor = TextPrimary
        ),
        confirmButton = {
            Row {
                TextButton(onClick = onBack) {
                    Text(strBack, color = TextPrimary)
                }
                Spacer(modifier = Modifier.width(12.dp))
                TextButton(
                    onClick = {
                        val millis = datePickerState.selectedDateMillis
                        if (millis != null) {
                            val date = Instant.ofEpochMilli(millis)
                                .atZone(ZoneId.systemDefault())
                                .toLocalDate()
                            onDateSelected(date)
                        }
                    }
                ) {
                    Text(strOk, color = AccentOrange)
                }
            }
        }
    ) {
        Column(
            Modifier
                .background(DarkSurfaceAlt)
        ) {
            Text(
                text = strTitle,
                color = TextPrimary,
                modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp)
            )
            Spacer(Modifier.height(12.dp))
            DatePicker(
                state = datePickerState,
                colors = DatePickerDefaults.colors(
                    containerColor = DarkSurfaceAlt,
                    titleContentColor = TextPrimary,
                    headlineContentColor = TextPrimary,
                    weekdayContentColor = TextPrimary,
                    subheadContentColor = TextMuted,
                    dayContentColor = TextPrimary,
                    disabledDayContentColor = TextMuted,
                    selectedDayContainerColor = AccentOrange,
                    selectedDayContentColor = DarkBackground,
                    todayDateBorderColor = AccentOrange
                )
            )
        }
    }
}