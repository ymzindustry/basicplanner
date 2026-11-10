package com.ymz.basicplanner.ui
import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.util.Log
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
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
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Extension
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.automirrored.filled.TrendingUp
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalResources
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.data.*
import com.ymz.basicplanner.model.*
import com.ymz.basicplanner.compose.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import java.util.UUID
import com.ymz.basicplanner.R
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.filled.Anchor
import androidx.compose.runtime.mutableStateSetOf


@RequiresApi(Build.VERSION_CODES.Q)
@Composable
fun ProgramAgendaScreen(
    creationState: CreationState,
    programStartDate: LocalDate,
    onOpenCreation: () -> Unit,
    onOpenDashboard: () -> Unit,
    onStartSession: (Long) -> Unit,
    onOpenTutorial: () -> Unit = {},
    onOpenModules: () -> Unit = {},
    /** True when the tutorial is in guidance mode — non-target buttons are disabled. */
    tutorialActive: Boolean = false
) {

    val strUnknown = stringResource(id = R.string.unknown)
    val strAgendaSelectExercise = stringResource(id = R.string.agenda_select_exercise)
    val strAgendaSelectExerciseToEdit = stringResource(id = R.string.agenda_select_exercise_to_edit)
    val strAgendaUnknownExercise = stringResource(id = R.string.agenda_unknown_exercise)
    val strAgendaNoActiveProgram = stringResource(id = R.string.agenda_no_active_program)
    val strAgendaValidate = stringResource(id = R.string.agenda_validate)
    val strAgendaEditBaseRm = stringResource(id = R.string.agenda_edit_base_rm)
    val strAgendaNoActiveProgramMessage = stringResource(id = R.string.agenda_no_active_program_message)
    val strImportSuccessful = stringResource(id = R.string.agenda_import_successful)
    val strImportFailed = stringResource(id = R.string.agenda_import_failed)
    val strExportBpentSuccess = stringResource(id = R.string.dialog_export_bpent_success)
    val strExportBpentFailed = stringResource(id = R.string.dialog_export_bpent_failed)
    val strExportExcelSuccess = stringResource(id = R.string.dialog_export_excel_success)
    val strExportExcelError = stringResource(id = R.string.dialog_export_excel_error)
    val strExportExcelLabel = stringResource(id = R.string.settings_export_to_excel)
    val strExportCsvSuccess = stringResource(id = R.string.agenda_export_csv_success)
    val strExportCsvFailed = stringResource(id = R.string.agenda_export_csv_failed)
    val strDialogStartNew = stringResource(id = R.string.dialog_start_new_session)
    val strDialogDateFormat = stringResource(id = R.string.dialog_date_format)
    val strDialogImportType = stringResource(id = R.string.dialog_import_request_type)
    val programs = creationState.getEntities(EntityKind.PROGRAM).filterIsInstance<ProgramEntity>()
    val activePrograms = creationState.activePrograms
    var isExporting by remember { mutableStateOf(false) }
    var isSharing by remember { mutableStateOf(false) }
    var isExportingCsv by remember { mutableStateOf(false) }
    var pendingNotifAction by remember { mutableStateOf<(() -> Unit)?>(null) }
    val context = LocalContext.current
    val resources = LocalResources.current
    val scope = rememberCoroutineScope()
    var showAdvancedSettings by remember { mutableStateOf(false) }
    var showNameDialog by remember { mutableStateOf(false) }
    var selectedViewKey by remember { mutableStateOf<Long?>(null) }
    val modifiedMovementIds = remember { mutableStateSetOf<Long>() }
    var dragStartDate by remember { mutableStateOf<LocalDate?>(null) }
    var currentDragDate by remember { mutableStateOf<LocalDate?>(null) }
    val sessionGroupIds = remember { mutableStateMapOf<LocalDate, String>() }
    var selectedDateForEdit by remember { mutableStateOf<LocalDate?>(null) }
    var isEditMode by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var editingEntity by remember { mutableStateOf<BaseEntity?>(null) }
    var editingKind by remember { mutableStateOf<EntityKind?>(null) }

    val currentSelectionVisual = remember(dragStartDate, currentDragDate) {
        if (dragStartDate != null && currentDragDate != null) {
            val start =
                if (dragStartDate!!.isBefore(currentDragDate)) dragStartDate else currentDragDate
            val end =
                if (dragStartDate!!.isBefore(currentDragDate)) currentDragDate else dragStartDate
            val list = mutableListOf<LocalDate>()
            var d = start
            while (d != null && !d.isAfter(end)) {
                list.add(d)
                d = d.plusDays(1)
            }
            list
        } else emptyList()
    }
    var selectedActiveProgram by remember(activePrograms) {
        mutableStateOf(
            activePrograms.find { it.id == creationState.selectedActiveProgramId }
                ?: activePrograms.firstOrNull()
        )
    }
    val selectedProgram = remember(selectedActiveProgram, programs) {
        selectedActiveProgram?.let { ap ->
            programs.find { it.id == ap.programId }
        }
    }
    val getDailyRmsForDate = remember(selectedActiveProgram) {
        { date: LocalDate ->
            val ap = selectedActiveProgram
            if (ap == null) emptyMap<Long, Int>()
            else {
                val dayIndex =
                    ChronoUnit.DAYS.between(ap.startDate, date).toInt()
                ap.exerciseDailyRms.mapValues { (_, list) ->
                    list.getOrNull(dayIndex) ?: list.lastOrNull() ?: 0
                }
            }
        }
    }
    var currentYearMonth by remember { mutableStateOf(YearMonth.now()) }
    val daysByDate = remember(selectedProgram, selectedActiveProgram, creationState, creationState.dataVersion.value) {
        val active = selectedActiveProgram
        if (selectedProgram != null && active != null) {
            buildProgramCalendar(
                selectedProgram,
                active.startDate,
                creationState
            )
        } else {
            emptyMap()
        }
    }
    val importLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri: Uri? ->
        uri?.let {
            scope.launch(Dispatchers.IO) {
                try {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val tempFile = File(context.cacheDir, "import_temp.bpent")
                    tempFile.outputStream().use { output -> inputStream?.copyTo(output) }
                    EntityFileConverter.importFromBpentFile(tempFile, creationState)
                    AppStateStore.save(context, creationState.toAppState())
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, strImportSuccessful, Toast.LENGTH_SHORT).show()
                    }
                } catch (_: Exception) {
                    scope.launch(Dispatchers.Main) {
                        Toast.makeText(context, strImportFailed, Toast.LENGTH_LONG)
                            .show()
                    }
                }
            }
        }
    }
    val runJsonExport: () -> Unit = {
        if (!isExportingCsv) {
            scope.launch {
                isExportingCsv = true
                try {
                    withContext(Dispatchers.IO) {
                        JsonExporter.exportFullJson(context, creationState, selectedActiveProgram!!)
                    }
                    Toast.makeText(context, strExportCsvSuccess, Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    Log.e("JsonExportError", "Failed to export JSON", e)
                    Toast.makeText(context, strExportCsvFailed, Toast.LENGTH_SHORT).show()
                } finally {
                    isExportingCsv = false
                }
            }
        }
    }
    val notifPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) {
        pendingNotifAction?.invoke()
        pendingNotifAction = null
    }
    val runWithNotifPermission: (() -> Unit) -> Unit = { action ->
        val needsPermission = Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.POST_NOTIFICATIONS
            ) != PackageManager.PERMISSION_GRANTED
        if (needsPermission) {
            pendingNotifAction = action
            notifPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            action()
        }
    }
    var infoSlot by remember { mutableStateOf<AgendaSlot?>(null) }
    var infoDate by remember { mutableStateOf<LocalDate?>(null) }
    LaunchedEffect(selectedViewKey) {
        sessionGroupIds.clear()
    }
    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    IconButton(
                        onClick = onOpenDashboard,
                        enabled = !isEditMode && !tutorialActive
                    ) {
                        Icon(
                            Icons.AutoMirrored.Filled.TrendingUp,
                            contentDescription = stringResource(R.string.dashboard_open),
                            tint = if (!isEditMode) AccentOrange else TextMuted
                        )
                    }
                    IconButton(
                        onClick = onOpenModules,
                        enabled = !isEditMode && !tutorialActive
                    ) {
                        Icon(
                            Icons.Default.Extension,
                            contentDescription = "modules",
                            tint = if (!isEditMode) AccentOrange else TextMuted
                        )
                    }
                    IconButton(
                        onClick = { showAdvancedSettings = true },
                        enabled = !isEditMode && !tutorialActive
                    )
                    {
                        Icon(
                            Icons.Default.MoreVert,
                            stringResource(R.string.agenda_settings),
                            tint = if (!isEditMode) AccentOrange else TextMuted
                        )
                    }
                },
                rightButtons = {
                    if (selectedActiveProgram != null && !isEditMode) {
                        IconButton(
                            onClick = {
                                val firstExo =
                                    selectedActiveProgram?.exerciseDailyRms?.keys?.minOrNull()
                                selectedViewKey = firstExo
                                isEditMode = true
                            },
                            enabled = !tutorialActive
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.ShowChart,
                                contentDescription = strAgendaEditBaseRm,
                                tint = AccentOrange
                            )
                        }
                    }

                    if (!isEditMode) {
                        IconButton(
                            onClick = onOpenCreation,
                            modifier = Modifier.onGloballyPositioned { coords ->
                                TutorialTargets.agendaCreateButton = coords.boundsInRoot()
                            }
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = stringResource(R.string.agenda_create),
                                tint = AccentOrange
                            )
                        }
                    } else {
                        Box {
                            var showEditSelector by remember { mutableStateOf(false) }
                            val selectedName = remember(selectedViewKey, creationState.movements) {
                                creationState.movements.find { it.id == selectedViewKey }?.name
                            }
                            TextButton(onClick = { showEditSelector = true }) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = selectedName ?: strAgendaSelectExercise,
                                        color = AccentOrange,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 14.sp
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Icon(
                                        imageVector = Icons.Default.ArrowDropDown,
                                        contentDescription = strAgendaSelectExerciseToEdit,
                                        tint = AccentOrange,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                            }
                            UnifiedSelectionMenu(
                                expanded = showEditSelector,
                                onDismiss = { showEditSelector = false },
                                modifier = Modifier.heightIn(max = 280.dp),
                                items = selectedActiveProgram?.exerciseDailyRms?.keys?.sorted() ?: emptyList(),
                                itemLabel = { id -> creationState.movements.find { it.id == id }?.name ?: strAgendaUnknownExercise },
                                selectedItem = selectedViewKey,
                                onItemClick = { movementId ->
                                    selectedViewKey = movementId
                                    showEditSelector = false}
                            )
                        }
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
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 4.dp)
            ) {
                val strMonthYearFormat = stringResource(id = R.string.agenda_view_month_year_format)
                val monthLabel = remember(currentYearMonth) {
                    val months = resources.getStringArray(R.array.months_short)
                    val monthName = months[currentYearMonth.monthValue - 1]
                    strMonthYearFormat.format(monthName, currentYearMonth.year)
                }

                CommonHeader(
                    startContent = {
                        Text(
                            text = monthLabel,
                            style = MaterialTheme.typography.titleLarge.copy(
                                fontWeight = FontWeight.Bold,
                                fontSize = 22.sp
                            ),
                            color = TextPrimary,
                            maxLines = 1,
                            softWrap = false
                        )
                    },
                    centerContent = {
                        ScrollingText(
                            text = selectedActiveProgram?.name ?: strAgendaNoActiveProgram
                        )
                    },
                    endContent = {
                        if (isEditMode) {
                            IconButton(onClick = {
                                if (modifiedMovementIds.isNotEmpty()) {
                                    val ap = selectedActiveProgram
                                    if (ap != null) {
                                        val index =
                                            creationState.activePrograms.indexOfFirst { it.id == ap.id }
                                        if (index != -1) {
                                            val updatedRmSettings =
                                                ap.rmSettings.toMutableMap()
                                            modifiedMovementIds.forEach { movementId ->
                                                val currentList =
                                                    ap.exerciseDailyRms[movementId]
                                                if (currentList != null && currentList.isNotEmpty()) {
                                                    val newStart = currentList.first()
                                                    val newTarget = currentList.last()
                                                    updatedRmSettings[movementId] =
                                                        RmSettings(
                                                            start = newStart,
                                                            target = newTarget,
                                                            strategy = InterpolationStrategy.CUSTOM
                                                        )
                                                }
                                            }
                                            creationState.activePrograms[index] = ap.copy(
                                                rmSettings = updatedRmSettings,
                                                requiresUpdate = false
                                            )
                                        }
                                    }
                                    scope.launch {
                                        AppStateStore.save(
                                            context,
                                            creationState.toAppState()
                                        )
                                    }
                                    modifiedMovementIds.clear()
                                }
                                isEditMode = false
                                selectedViewKey = null
                            }) {
                                Icon(
                                    Icons.Default.Check,
                                    contentDescription = strAgendaValidate,
                                    tint = AccentGreen
                                )
                            }
                        } else {
                            ProgramSelector(
                                creationState = creationState,
                                programs = programs,
                                activePrograms = activePrograms,
                                selectedActiveProgram = selectedActiveProgram,
                                enabled = !isEditMode && !tutorialActive,
                                onActiveProgramSelected = { ap ->
                                    selectedActiveProgram = ap
                                    creationState.selectedActiveProgramId = ap?.id
                                    scope.launch {
                                        AppStateStore.save(
                                            context,
                                            creationState.toAppState()
                                        )
                                    }
                                    if (ap != null) {
                                        currentYearMonth = YearMonth.now()
                                    }
                                }
                            )
                        }
                    }
                )
                Spacer(modifier = Modifier.height(8.dp))
                WeekdayRow()
                Spacer(modifier = Modifier.height(4.dp))
                if (selectedProgram != null && selectedActiveProgram != null && daysByDate.isNotEmpty()) {
                    val program = selectedProgram!!
                    val activeProgram = selectedActiveProgram!!
                    val firstOfMonth = currentYearMonth.atDay(1)
                    val visibleDates = remember(currentYearMonth) {
                        val startOffset = firstOfMonth.dayOfWeek.value - 1
                        val firstCellDate = firstOfMonth.minusDays(startOffset.toLong())
                        (0 until 42).map { i -> firstCellDate.plusDays(i.toLong()) }
                    }


                    val (blockIndicators, weekIndicators) = remember(
                        program,
                        activeProgram,
                        visibleDates,
                        creationState.blocks,
                        creationState.weeks
                    ) {
                        val blockIndicators = mutableMapOf<Int, Pair<BlockEntity, Int>>()
                        val weekIndicators = mutableMapOf<Int, Triple<WeekEntity, Int, Int>>()


                            val calendarInfo = mutableMapOf<LocalDate, CalendarDayInfo>()
                            val blockStartDates = mutableMapOf<Long, LocalDate>()
                            val weekStartDates = mutableSetOf<LocalDate>()
                            var currentDate = activeProgram.startDate

                            program.blockIds.forEachIndexed { blockIndex, blockId ->
                                val block = creationState.blocks.find { it.id == blockId }
                                if (block != null) {
                                    if (!blockStartDates.containsKey(block.id)) {
                                        blockStartDates[block.id] = currentDate
                                    }
                                    block.weekIds.forEachIndexed { weekIndexInBlock, weekId ->
                                        val week = creationState.weeks.find { it.id == weekId }
                                        if (week != null) {

                                            weekStartDates.add(currentDate)

                                            repeat(7) {
                                                calendarInfo[currentDate] = CalendarDayInfo(
                                                    week = week,
                                                    block = block,
                                                    weekIndexInBlock = weekIndexInBlock + 1,
                                                    blockIndexInProgram = blockIndex + 1
                                                )
                                                currentDate = currentDate.plusDays(1)
                                            }
                                        }
                                    }
                                }
                            }


                            (0 until 6).forEach { weekIndex ->
                                val datesInRow = (0..6).map { visibleDates[weekIndex * 7 + it] }
                                val programInfosInRow = datesInRow.mapNotNull { calendarInfo[it] }

                                if (programInfosInRow.isNotEmpty()) {
                                    val isFirstVisibleRow = (weekIndex == 0)


                                    val startingBlockDate =
                                        datesInRow.find { blockStartDates.containsValue(it) }
                                    if (startingBlockDate != null) {
                                        calendarInfo[startingBlockDate]?.let { info ->
                                            blockIndicators[weekIndex] =
                                                Pair(info.block, info.blockIndexInProgram)
                                        }
                                    } else if (isFirstVisibleRow) {
                                        blockIndicators[weekIndex] = Pair(
                                            programInfosInRow.first().block,
                                            programInfosInRow.first().blockIndexInProgram
                                        )
                                    }



                                    val startingWeekDate =
                                        datesInRow.find { weekStartDates.contains(it) }
                                    if (startingWeekDate != null) {
                                        calendarInfo[startingWeekDate]?.let { info ->
                                            weekIndicators[weekIndex] = Triple(
                                                info.week,
                                                info.blockIndexInProgram,
                                                info.weekIndexInBlock
                                            )
                                        }
                                    } else if (isFirstVisibleRow) {
                                        weekIndicators[weekIndex] = Triple(
                                            programInfosInRow.first().week,
                                            programInfosInRow.first().blockIndexInProgram,
                                            programInfosInRow.first().weekIndexInBlock
                                        )
                                    }
                                }
                            }
                        Pair(blockIndicators, weekIndicators)
                    }
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .pointerInput(currentYearMonth, selectedViewKey) {
                                var dragX = 0f
                                detectHorizontalDragGestures(
                                    onHorizontalDrag = { _, delta ->
                                        if (dragStartDate == null) dragX += delta
                                    },
                                    onDragEnd = {
                                        if (!tutorialActive) {
                                            val threshold = 80f
                                            if (dragX > threshold) {
                                                currentYearMonth = currentYearMonth.minusMonths(1)
                                            } else if (dragX < -threshold) {
                                                currentYearMonth = currentYearMonth.plusMonths(1)
                                            }
                                            dragX = 0f
                                        }
                                    }
                                )
                            }
                            .pointerInput(currentYearMonth, selectedViewKey, daysByDate) {
                                if (selectedViewKey != null) {
                                    detectDragGesturesAfterLongPress(
                                        onDragStart = { offset ->
                                            calculateDateFromGrid(
                                                offset,
                                                size,
                                                visibleDates
                                            )?.let { date ->
                                                if (daysByDate.containsKey(date)) {
                                                    dragStartDate = date
                                                    currentDragDate = date
                                                }
                                            }
                                        },
                                        onDrag = { change, _ ->
                                            calculateDateFromGrid(
                                                change.position,
                                                size,
                                                visibleDates
                                            )?.let { date ->
                                                if (daysByDate.containsKey(date)) {
                                                    currentDragDate = date
                                                }
                                            }
                                        },
                                        onDragEnd = {

                                            val start = dragStartDate
                                            val end = currentDragDate
                                            val viewKey = selectedViewKey
                                            val activeProg = selectedActiveProgram


                                            dragStartDate = null
                                            currentDragDate = null
                                            if (start != null && end != null && activeProg != null && viewKey != null) {
                                                val rangeStart =
                                                    if (start.isBefore(end)) start else end
                                                val rangeEnd =
                                                    if (start.isBefore(end)) end else start


                                                val affectedGroupIds = mutableSetOf<String>()
                                                var scanDate = rangeStart
                                                while (!scanDate.isAfter(rangeEnd)) {
                                                    sessionGroupIds[scanDate]?.let {
                                                        affectedGroupIds.add(
                                                            it
                                                        )
                                                    }
                                                    scanDate = scanDate.plusDays(1)
                                                }

                                                val allDatesToRewrite =
                                                    sessionGroupIds.filter { it.value in affectedGroupIds }.keys.toMutableSet()

                                                var dFill = rangeStart
                                                while (!dFill.isAfter(rangeEnd)) {
                                                    allDatesToRewrite.add(dFill)
                                                    dFill = dFill.plusDays(1)
                                                }
                                                val sortedDates = allDatesToRewrite.sorted()

                                                val newGroupIdsAssignment =
                                                    mutableMapOf<LocalDate, String>()
                                                val updatedMap =
                                                    activeProg.exerciseDailyRms.toMutableMap()
                                                val currentList =
                                                    updatedMap[viewKey]?.toMutableList()
                                                        ?: mutableListOf()

                                                val dayIndexStart =
                                                    ChronoUnit.DAYS.between(
                                                        activeProg.startDate,
                                                        rangeStart
                                                    ).toInt()
                                                val selectionValue =
                                                    currentList.getOrNull(dayIndexStart) ?: 0

                                                val newSelectionGroupId =
                                                    UUID.randomUUID().toString()
                                                var currentOuterGroupId =
                                                    UUID.randomUUID().toString()
                                                for (i in sortedDates.indices) {
                                                    val date = sortedDates[i]
                                                    val isInsideSelection =
                                                        !date.isBefore(rangeStart) && !date.isAfter(
                                                            rangeEnd
                                                        )
                                                    if (isInsideSelection) {

                                                        newGroupIdsAssignment[date] =
                                                            newSelectionGroupId

                                                        val idx = ChronoUnit.DAYS.between(
                                                            activeProg.startDate,
                                                            date
                                                        ).toInt()
                                                        if (idx in currentList.indices) currentList[idx] =
                                                            selectionValue
                                                    } else {

                                                        if (i > 0) {
                                                            val prevDate = sortedDates[i - 1]
                                                            val wasInside =
                                                                !prevDate.isBefore(rangeStart) && !prevDate.isAfter(
                                                                    rangeEnd
                                                                )
                                                            val isGap =
                                                                ChronoUnit.DAYS.between(
                                                                    prevDate,
                                                                    date
                                                                ) > 1

                                                            if (wasInside || isGap) {
                                                                currentOuterGroupId =
                                                                    UUID.randomUUID().toString()
                                                            }
                                                        }
                                                        newGroupIdsAssignment[date] =
                                                            currentOuterGroupId
                                                    }
                                                }


                                                sessionGroupIds.putAll(newGroupIdsAssignment)

                                                updatedMap[viewKey] = currentList
                                                val updatedAp = activeProg.copy(
                                                    exerciseDailyRms = updatedMap
                                                )

                                                selectedActiveProgram = updatedAp
                                                val globalIdx =
                                                    creationState.activePrograms.indexOfFirst { it.id == updatedAp.id }
                                                if (globalIdx != -1) creationState.activePrograms[globalIdx] =
                                                    updatedAp
                                                modifiedMovementIds.add(viewKey)
                                            }
                                        }
                                    )
                                }
                            }
                    ) {
                        MonthGrid(
                            yearMonth = currentYearMonth,
                            daysByDate = daysByDate,
                            creationState = creationState,
                            selectedActiveProgram = selectedActiveProgram,
                            onDayClick = { date ->
                                if (!tutorialActive) {
                                    if (selectedViewKey == null) {
                                        infoDate = date
                                        infoSlot = daysByDate[date]
                                    } else {
                                        selectedDateForEdit = date
                                    }
                                }
                            },
                            selectedViewKey = selectedViewKey,
                            sessionGroupIds = sessionGroupIds,
                            currentSelectionVisual = currentSelectionVisual,
                        )


                        Column(modifier = Modifier.fillMaxSize()) {
                            repeat(6) { weekIndex ->

                                Box(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth()
                                ) {

                                    blockIndicators[weekIndex]?.let { (block, blockIndex) ->
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomStart)
                                                .width(100.dp)
                                                .height(20.dp)
                                                .background(
                                                    DarkBackground,
                                                    shape = BlockIndicatorShape()
                                                )
                                                .padding(
                                                    start = 4.dp,
                                                    end = 12.dp,
                                                    top = 2.dp,
                                                    bottom = 2.dp
                                                )
                                        ) {
                                            Text(

                                                text = block.computeNameString(
                                                    movements = creationState.movements,
                                                    unknownText = strUnknown,
                                                    maxLength = 12
                                                ),
                                                color = AccentOrange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.align(Alignment.CenterStart)
                                            )
                                        }
                                    }

                                    weekIndicators[weekIndex]?.let { (week, blockIndex, weekIndexInBlock) ->
                                        Box(
                                            modifier = Modifier
                                                .align(Alignment.BottomEnd)
                                                .width(100.dp)
                                                .height(20.dp)
                                                .background(
                                                    DarkBackground,
                                                    shape = WeekIndicatorShape()
                                                )
                                                .padding(
                                                    start = 12.dp,
                                                    end = 4.dp,
                                                    top = 2.dp,
                                                    bottom = 2.dp
                                                )
                                        ) {
                                            Text(

                                                text = week.computeNameString(
                                                    movements = creationState.movements,
                                                    unknownText = strUnknown,
                                                    maxLength = 12
                                                ),
                                                color = AccentOrange,
                                                fontSize = 10.sp,
                                                fontWeight = FontWeight.Bold,
                                                modifier = Modifier.align(Alignment.CenterEnd)
                                            )
                                        }
                                    }
                                }

                            }
                        }
                    }
                } else {
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = strAgendaNoActiveProgramMessage,
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMuted
                        )
                    }
                }
            }
        }
    }
    infoSlot?.let { slot ->
        val dailyRmsForThisDay =
            if (infoDate != null) getDailyRmsForDate(infoDate!!) else emptyMap()
        val ongoingSessions =
            remember(infoDate, selectedActiveProgram, creationState.sessions.size) {
                val activeProg = selectedActiveProgram
                if (infoDate == null || activeProg == null) emptyList()
                else {
                    creationState.sessions.filter { it.id in activeProg.sessionIds && it.date?.toLocalDate() == infoDate}
                }
            }
        val effectiveDate = infoDate
        val activeProg = selectedActiveProgram
        val initialAdjustment =
            if (effectiveDate != null && activeProg != null) {
                val total = effectiveAdjustmentPercent(
                    creationState.toAppState(),
                    activeProg.id,
                    effectiveDate
                )
                val ownAdjustment = when (val e = slot.entity) {
                    is DayEntity -> e.adjustmentPercent
                    is SessionEntity -> e.adjustmentPercent
                    is WeekEntity -> e.adjustmentPercent
                    is BlockEntity -> e.adjustmentPercent
                    is ProgramEntity -> e.adjustmentPercent
                    else -> 0
                }
                total - ownAdjustment
            } else {
                0
            }
        // A weekday may hold a bare exercise or session: for display it is shown as it was when such a
        // slot was wrapped — a day-like root whose child session carries the exercise — so the row,
        // its connector bar and the "Start new" action look exactly the same.
        val hostSession: SessionEntity? = when (slot.kind) {
            EntityKind.EXERCISE -> {
                val exercise = slot.entity as? ExerciseEntity
                exercise?.let {
                    SessionEntity(
                        id = 0L,
                        name = creationState.exerciseDisplayName(it),
                        owner = creationState.currentUserName,
                        exerciseIds = mutableListOf(it.id)
                    )
                }
            }
            EntityKind.SESSION -> slot.entity as? SessionEntity
            else -> null
        }
        val displayEntity: BaseEntity = hostSession?.let {
            DayEntity(id = 0L, name = it.name, owner = creationState.currentUserName)
        } ?: slot.entity
        val displayKind = if (hostSession != null) EntityKind.DAY else slot.kind

        UniversalEntityInfoDialog(
            entity = displayEntity,
            kind = displayKind,
            childrenOverride = hostSession?.let { listOf(it) },
            title = infoDate?.format(DateTimeFormatter.ofPattern(strDialogDateFormat, Locale.getDefault()))
                ?: "",
            creationState = creationState,
            dailyRms = dailyRmsForThisDay,
            showRmInfo = true,
            initialAdjustment = initialAdjustment,
            ongoingSessions = ongoingSessions,
            rootAction = null,
            onResumeSession = { sessionId ->
                onStartSession(sessionId)
            },
            onDeleteSession = { sessionId ->
                val sessionToDelete = creationState.sessions.find { it.id == sessionId }
                val sessionDate = sessionToDelete?.date?.toLocalDate()
                creationState.performanceEntries.removeAll { entry ->
                    entry.sessionId == sessionId ||
                        (sessionDate != null && entry.note.contains("auto", ignoreCase = true) && entry.date == sessionDate)
                }
                selectedActiveProgram?.sessionIds?.remove(sessionId)
                sessionToDelete?.exerciseIds?.let { exIds ->
                    if (exIds.isNotEmpty()) {
                        creationState.exercises.removeAll { it.id in exIds }
                    }
                }
                creationState.sessions.removeAll { it.id == sessionId }
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onDismiss = {
                infoSlot = null
                infoDate = null
            },
            onEditRequest = { entityToEdit ->
                val kind = when (entityToEdit) {
                    is MovementEntity -> EntityKind.MOVEMENT
                    is ExerciseEntity -> EntityKind.EXERCISE
                    is SessionEntity -> EntityKind.SESSION
                    is DayEntity -> EntityKind.DAY
                    is WeekEntity -> EntityKind.WEEK
                    is BlockEntity -> EntityKind.BLOCK
                    is ProgramEntity -> EntityKind.PROGRAM
                    else -> null
                }
                if (kind != null) {
                    editingEntity = entityToEdit
                    editingKind = kind
                    showEditDialog = true
                    infoSlot = null
                    infoDate = null
                }
            },
            actionContent = { child, childKind, treeLineColor ->
                if (childKind == EntityKind.SESSION && child is SessionEntity && infoDate != null) {
                    run {
                        TreeActionConnector(treeLineColor) {
                            TextButton(onClick = {
                                val newSession = creationState.createSessionInstance(
                                    child,
                                    dailyRmsForThisDay,
                                    java.time.LocalDateTime.of(infoDate, java.time.LocalTime.now()),
                                    selectedActiveProgram?.id ?: -1
                                )
                                scope.launch {
                                    AppStateStore.save(
                                        context,
                                        creationState.toAppState()
                                    ); onStartSession(newSession.id);
                                }
                            }, modifier = Modifier.height(32.dp)) {
                                Text(
                                    strDialogStartNew,
                                    color = AccentOrange,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }
                }
            }
        )
    }
    if (showAdvancedSettings) {
        AdvancedSettingsDialog(
            creationState = creationState,
            selectedProgram = selectedProgram,
            selectedActiveProgram = selectedActiveProgram,
            onDismiss = { showAdvancedSettings = false },
            onImportRequest = { importLauncher.launch(strDialogImportType) },
            isExporting = isExporting,
            isSharing = isSharing,
            isExportingCsv = isExportingCsv,
            onExportBpent = {
                runWithNotifPermission {
                    if (!isSharing) {
                        scope.launch {
                            isSharing = true
                            try {
                                val file = withContext(Dispatchers.IO) {
                                    EntityFileConverter.exportToBpentFile(context, selectedProgram!!, creationState)
                                }
                                val exportedUri = EntityFileConverter.saveBpentToDownloads(context, file)
                                Toast.makeText(context, strExportBpentSuccess, Toast.LENGTH_SHORT).show()
                                ExportNotifications.show(
                                    context,
                                    strExportBpentSuccess,
                                    file.name,
                                    exportedUri,
                                    "application/octet-stream"
                                )
                            } catch (e: Exception) {
                                Log.e("ExportError", "Failed to export .bpent file", e)

                                Toast.makeText(context, strExportBpentFailed, Toast.LENGTH_SHORT).show()
                            } finally {
                                isSharing = false
                            }
                        }
                    }
                }
            },
            onExportCsv = {
                runWithNotifPermission {
                    runJsonExport()
                }
            },
            onExportExcel = {
                runWithNotifPermission {
                    if (!isExporting) {
                        scope.launch {
                            isExporting = true
                            try {
                                val exportedUri = withContext(Dispatchers.IO) {
                                    ExcelExporter.exportActiveProgram(context, selectedActiveProgram!!, selectedProgram!!, creationState)
                                }
                                Toast.makeText(context, strExportExcelSuccess, Toast.LENGTH_LONG).show()
                                ExportNotifications.show(
                                    context,
                                    strExportExcelSuccess,
                                    strExportExcelLabel,
                                    exportedUri,
                                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"
                                )
                            } catch (_: Exception) {
                                Toast.makeText(context, strExportExcelError, Toast.LENGTH_SHORT).show()
                            } finally {
                                isExporting = false
                            }
                        }
                    }
                }
            },
            onChangeName = {
                showNameDialog = true
            },
            onActiveProgramSelected = { ap ->
                selectedActiveProgram = ap
                creationState.selectedActiveProgramId = ap?.id
            },
            onDataReset = {
                creationState.reset()
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
            },
            onOpenTutorial = onOpenTutorial
        )
    }
    if (showNameDialog) {
        ChangeNameDialog(
            initialName = creationState.currentUserName,
            onDismiss = { showNameDialog = false },
            onConfirm = { newName ->
                creationState.currentUserName = newName
                scope.launch { AppStateStore.save(context, creationState.toAppState())
                    showNameDialog = false}
            }
        )
    }

    if (showEditDialog && editingEntity != null && editingKind != null) {
        val kind = editingKind!!
        val availableChildren = creationState.availableChildren(kind)
            .filter { creationState.isPlanLevel(it) }
            .filter { child ->
                if (kind == EntityKind.BLOCK && editingEntity is BlockEntity) {
                    val b = editingEntity as BlockEntity
                    child.id != b.id && child.id !in creationState.blockDescendants(b.id)
                } else {
                    true
                }
            }
        val initialChildren = editingEntity
            ?.let { creationState.childrenOf(kind, it) }
            ?: emptyList()
        val initialExerciseEntries = if (kind == EntityKind.EXERCISE && editingEntity is ExerciseEntity) {
            (editingEntity as ExerciseEntity).entries.toList()
        } else {
            emptyList()
        }
        EntityEditDialog(
            kind = kind,
            availableChildren = availableChildren,
            initialEntity = editingEntity,
            initialChildren = initialChildren,
            initialExerciseEntries = initialExerciseEntries,
            creationState = creationState,
            onDismiss = { showEditDialog = false },
            onConfirm = { name, orderedChildIds, entries, adjustment, subAdj ->
                if (editingEntity != null) {
                    when (kind) {
                        EntityKind.EXERCISE -> {
                            val ex = editingEntity as ExerciseEntity
                            creationState.updateEntity(kind, ex, name, orderedChildIds)
                            ex.entries.clear()
                            ex.entries.addAll(entries)
                        }
                        else -> {
                            creationState.updateEntity(kind, editingEntity!!, name, orderedChildIds)
                        }
                    }
                    when (val target = editingEntity) {
                        is SessionEntity -> target.adjustmentPercent = adjustment
                        is DayEntity -> { target.adjustmentPercent = adjustment; target.subAdjustments.clear(); target.subAdjustments.addAll(subAdj) }
                        is WeekEntity -> { target.adjustmentPercent = adjustment; target.subAdjustments.clear(); target.subAdjustments.addAll(subAdj) }
                        is BlockEntity -> { target.adjustmentPercent = adjustment; target.subAdjustments.clear(); target.subAdjustments.addAll(subAdj) }
                        is ProgramEntity -> { target.adjustmentPercent = adjustment; target.subAdjustments.clear(); target.subAdjustments.addAll(subAdj) }
                        else -> {}
                    }
                }
                scope.launch { AppStateStore.save(context, creationState.toAppState()) }
                showEditDialog = false
                editingEntity = null
                editingKind = null
            }
        )
    }
    if (selectedDateForEdit != null) {
        val dateToEdit = selectedDateForEdit!!
        val ap = selectedActiveProgram!!
        val movementId = selectedViewKey!!
        val dayIndex = ChronoUnit.DAYS.between(ap.startDate, dateToEdit).toInt()
        val currentVal = ap.exerciseDailyRms[movementId]?.getOrNull(dayIndex) ?: 0
        ValueEditDialog(
            initialValue = currentVal,
            onDismiss = { selectedDateForEdit = null },
            onConfirm = { newVal ->
                val currentList = ap.exerciseDailyRms[movementId]?.toMutableList() ?: mutableListOf()
                val targetGroupId = sessionGroupIds[dateToEdit]
                if (targetGroupId != null) {
                    sessionGroupIds.forEach { (date, id) ->
                        if (id == targetGroupId) {
                            val idx = ChronoUnit.DAYS.between(ap.startDate, date).toInt()
                            if (idx in currentList.indices) currentList[idx] = newVal
                        }
                    }
                } else {
                    val idx = ChronoUnit.DAYS.between(ap.startDate, dateToEdit).toInt()
                    if (idx in currentList.indices) currentList[idx] = newVal
                }
                val index = creationState.activePrograms.indexOfFirst { it.id == ap.id }
                if (index != -1) {
                    val newMap = ap.exerciseDailyRms.toMutableMap()
                    newMap[movementId] = currentList
                    val updatedAp = ap.copy(exerciseDailyRms = newMap)
                    creationState.activePrograms[index] = updatedAp
                    selectedActiveProgram = updatedAp
                    modifiedMovementIds.add(movementId)
                }
                selectedDateForEdit = null
            }
        )
    }
}
