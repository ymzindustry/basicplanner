package com.ymz.basicplanner.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.CommonHeader
import com.ymz.basicplanner.compose.MarqueeText
import com.ymz.basicplanner.compose.ScrollingText
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.data.PythonModuleExecutor
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleInputBinding
import com.ymz.basicplanner.model.module.ModuleInputSpec
import com.ymz.basicplanner.model.module.ModuleTargetKind
import com.ymz.basicplanner.model.module.moduleInputCandidates
import kotlinx.coroutines.launch

/**
 * Modules section: the Python programs that make a plan dynamic, and where each of them is plugged
 * in. A module is written and tested here; the session screen only displays its effect.
 */
@Composable
fun ModulesScreen(
    creationState: CreationState,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var editingModule by remember { mutableStateOf<ModuleDefinition?>(null) }
    var creating by remember { mutableStateOf(false) }
    var attachingTo by remember { mutableStateOf<ModuleDefinition?>(null) }
    var deleting by remember { mutableStateOf<ModuleDefinition?>(null) }

    fun save() {
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
                    }
                },
                rightButtons = {
                    IconButton(onClick = { creating = true }) {
                        Icon(Icons.Default.Add, "new module", tint = AccentOrange)
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
            CommonHeader(centerContent = { ScrollingText(text = "Modules") })

            if (creationState.modules.isEmpty()) {
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    Text(
                        text = "No module yet. Add one with + — it is a Python function apply(ctx).",
                        color = TextMuted,
                        fontSize = 12.sp,
                        modifier = Modifier.padding(24.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize().padding(horizontal = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(creationState.modules, key = { it.id }) { module ->
                        val attachments = creationState.moduleAttachments.filter { it.moduleId == module.id }
                        ModuleCard(
                            module = module,
                            attachments = attachments,
                            attachmentLabel = { attachment ->
                                val target = attachmentLabel(creationState, attachment)
                                val program = creationState.programs.find { it.id == attachment.programId }?.name ?: ""
                                "$program · $target"
                            },
                            onEdit = { editingModule = module },
                            onAttach = { attachingTo = module },
                            onDetach = { attachment ->
                                creationState.moduleAttachments.remove(attachment)
                                save()
                            },
                            onDelete = { deleting = module }
                        )
                    }
                    item { Spacer(Modifier.height(16.dp)) }
                }
            }
        }
    }

    if (creating) {
        ModuleEditorDialog(
            initial = null,
            onDismiss = { creating = false },
            onSave = { name, description, code, specs ->
                creationState.modules.add(
                    ModuleDefinition(
                        id = creationState.newId(),
                        name = name,
                        description = description,
                        code = code,
                        inputs = specs
                    )
                )
                creating = false
                save()
            }
        )
    }

    editingModule?.let { module ->
        ModuleEditorDialog(
            initial = module,
            onDismiss = { editingModule = null },
            onSave = { name, description, code, specs ->
                val index = creationState.modules.indexOfFirst { it.id == module.id }
                if (index >= 0) {
                    creationState.modules[index] = module.copy(
                        name = name,
                        description = description,
                        code = code,
                        inputs = specs
                    )
                }
                editingModule = null
                save()
            }
        )
    }

    attachingTo?.let { module ->
        AttachModuleDialog(
            creationState = creationState,
            module = module,
            onDismiss = { attachingTo = null },
            onAttach = { attachment ->
                creationState.moduleAttachments.add(attachment)
                attachingTo = null
                save()
            }
        )
    }

    deleting?.let { module ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            containerColor = DarkSurfaceAlt,
            title = { Text("Delete module?", color = TextPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "“${module.name}” and its ${creationState.moduleAttachments.count { it.moduleId == module.id }} attachment(s) will be removed.",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    creationState.moduleAttachments.removeAll { it.moduleId == module.id }
                    creationState.modules.removeAll { it.id == module.id }
                    deleting = null
                    save()
                }) { Text("Delete", color = AccentRed, fontWeight = FontWeight.Bold) }
            },
            dismissButton = {
                TextButton(onClick = { deleting = null }) { Text("Cancel", color = TextMuted) }
            }
        )
    }
}

private fun attachmentLabel(creationState: CreationState, attachment: ModuleAttachment): String =
    when (attachment.targetKind) {
        ModuleTargetKind.DAY -> creationState.days.find { it.id == attachment.targetId }?.name
        ModuleTargetKind.SESSION -> creationState.sessions.find { it.id == attachment.targetId }?.name
        ModuleTargetKind.EXERCISE -> creationState.exercises.find { it.id == attachment.targetId }?.name
        ModuleTargetKind.SET -> "set ${attachment.targetId}"
    } ?: "?"

@Composable
private fun ModuleCard(
    module: ModuleDefinition,
    attachments: List<ModuleAttachment>,
    attachmentLabel: (ModuleAttachment) -> String,
    onEdit: () -> Unit,
    onAttach: () -> Unit,
    onDetach: (ModuleAttachment) -> Unit,
    onDelete: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceAlt)
            .padding(12.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                MarqueeText(
                    text = module.name,
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold,
                    fontSize = 15.sp
                )
                if (module.description.isNotBlank()) {
                    Text(module.description, color = TextMuted, fontSize = 11.sp, maxLines = 2)
                }
                if (module.inputs.isNotEmpty()) {
                    Text(
                        "inputs: " + module.inputs.joinToString { "${it.name}:${it.type}" },
                        color = AccentOrange,
                        fontSize = 10.sp
                    )
                }
            }
            IconButton(onClick = onEdit) {
                Icon(Icons.Default.Edit, "edit", tint = AccentOrange)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Default.Delete, "delete", tint = AccentRed)
            }
        }

        attachments.forEach { attachment ->
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("→", color = AccentOrange, fontSize = 12.sp)
                Spacer(Modifier.width(6.dp))
                Text(
                    attachmentLabel(attachment),
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    "detach",
                    color = AccentRed,
                    fontSize = 11.sp,
                    modifier = Modifier
                        .clickable { onDetach(attachment) }
                        .padding(4.dp)
                )
            }
        }

        TextButton(onClick = onAttach, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Default.Add, null, tint = AccentOrange, modifier = Modifier.width(16.dp))
            Spacer(Modifier.width(6.dp))
            Text("Attach to a target", color = AccentOrange, fontSize = 12.sp)
        }
    }
}

@Composable
private fun ModuleEditorDialog(
    initial: ModuleDefinition?,
    onDismiss: () -> Unit,
    onSave: (String, String, String, List<ModuleInputSpec>) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name ?: "") }
    var description by remember { mutableStateOf(initial?.description ?: "") }
    var inputsText by remember {
        mutableStateOf(initial?.inputs?.joinToString(", ") { "${it.name}:${it.type}" } ?: "")
    }
    var code by remember { mutableStateOf(initial?.code ?: ModuleDefinition.DEFAULT_CODE) }
    var testOutput by remember { mutableStateOf<String?>(null) }

    val context = LocalContext.current

    fun parseSpecs(): List<ModuleInputSpec> =
        inputsText.split(',', '\n')
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { chunk ->
                val parts = chunk.split(':', limit = 2)
                ModuleInputSpec(
                    name = parts[0].trim(),
                    type = parts.getOrNull(1)?.trim()?.ifBlank { "int" } ?: "int"
                )
            }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(if (initial == null) "New module" else "Edit module", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = inputsText,
                    onValueChange = { inputsText = it },
                    label = { Text("Inputs, e.g. form:int, sleep:float") },
                    modifier = Modifier.fillMaxWidth(),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                OutlinedTextField(
                    value = code,
                    onValueChange = { code = it },
                    label = { Text("Python") },
                    modifier = Modifier.fillMaxWidth().heightIn(min = 180.dp),
                    textStyle = MaterialTheme.typography.bodySmall.copy(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp
                    ),
                    colors = fieldColors()
                )
                Spacer(Modifier.height(8.dp))
                TextButton(onClick = {
                    testOutput = try {
                        val preview = ModuleDefinition(
                            id = initial?.id ?: 0L,
                            name = name,
                            description = description,
                            code = code,
                            inputs = parseSpecs()
                        )
                        // Test bench runs the module on an empty context so syntax/exec errors surface.
                        val ctx = """{"date":"","program_id":0,"target":{"kind":"test","id":0,"name":"test"},"items":{},"sets":[]}"""
                        PythonModuleExecutor().run(preview, ctx)
                    } catch (t: Throwable) {
                        "ERROR: ${t.message ?: t::class.simpleName}"
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, null, tint = AccentOrange, modifier = Modifier.width(16.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("Test run", color = AccentOrange, fontSize = 12.sp)
                }
                testOutput?.let {
                    Text(it, color = if (it.startsWith("ERROR")) AccentRed else AccentGreen, fontSize = 10.sp)
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(name.trim(), description.trim(), code, parseSpecs()) },
                enabled = name.isNotBlank()
            ) {
                Text("Save", color = if (name.isNotBlank()) AccentOrange else TextMuted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    unfocusedBorderColor = TextMuted,
    focusedBorderColor = AccentOrange,
    unfocusedLabelColor = TextMuted,
    focusedLabelColor = AccentOrange,
    cursorColor = AccentOrange,
    focusedTextColor = TextPrimary,
    unfocusedTextColor = TextPrimary
)

/**
 * Attach a module to one target of one program: choose the program, the kind of target, the target
 * itself, then bind each declared input to an item of the target or one of its ancestors.
 */
@Composable
private fun AttachModuleDialog(
    creationState: CreationState,
    module: ModuleDefinition,
    onDismiss: () -> Unit,
    onAttach: (ModuleAttachment) -> Unit
) {
    var programIndex by remember { mutableStateOf(0) }
    var kind by remember { mutableStateOf(ModuleTargetKind.SESSION) }
    var targetId by remember { mutableStateOf<Long?>(null) }
    var bindings by remember { mutableStateOf<Map<String, Long>>(emptyMap()) }

    val programs = creationState.programs
    val program = programs.getOrNull(programIndex)

    val app = remember(creationState.dataVersion.value) { creationState.toAppState() }

    val targets: List<Pair<Long, String>> = remember(program?.id, kind, creationState.dataVersion.value) {
        if (program == null) emptyList() else programTargets(creationState, program.id, kind)
    }

    val candidates = remember(kind, targetId, creationState.dataVersion.value) {
        targetId?.let { moduleInputCandidates(app, kind, it) } ?: emptyList()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text("Attach “${module.name}”", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                Text("Program", color = TextMuted, fontSize = 11.sp)
                programs.forEachIndexed { index, p ->
                    Row(
                        Modifier.fillMaxWidth().clickable {
                            programIndex = index
                            targetId = null
                            bindings = emptyMap()
                        }.padding(vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (index == programIndex) "◉" else "○", color = AccentOrange)
                        Spacer(Modifier.width(8.dp))
                        Text(p.name, color = TextPrimary, fontSize = 13.sp)
                    }
                }

                Spacer(Modifier.height(10.dp))
                Text("Target", color = TextMuted, fontSize = 11.sp)
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ModuleTargetKind.entries.forEach { option ->
                        val selected = option == kind
                        Box(
                            Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (selected) AccentOrange else DarkSurface)
                                .clickable {
                                    kind = option
                                    targetId = null
                                    bindings = emptyMap()
                                }
                                .padding(horizontal = 10.dp, vertical = 6.dp)
                        ) {
                            Text(
                                option.name.lowercase(),
                                color = if (selected) DarkBackground else TextPrimary,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                if (targets.isEmpty()) {
                    Text("Nothing to attach to in this program.", color = TextMuted, fontSize = 11.sp)
                } else {
                    targets.forEach { (id, label) ->
                        val selected = targetId == id
                        Row(
                            Modifier.fillMaxWidth().clickable {
                                targetId = id
                                bindings = emptyMap()
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (selected) "◉" else "○", color = AccentOrange)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = if (selected) AccentOrange else TextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                if (targetId != null && module.inputs.isNotEmpty()) {
                    Spacer(Modifier.height(10.dp))
                    Text("Inputs (target itself or its parents)", color = TextMuted, fontSize = 11.sp)
                    module.inputs.forEach { spec ->
                        val boundId = bindings[spec.name]
                        Text(
                            "${spec.name} (${spec.type}) → " + (candidates.find { it.entityId == boundId }?.let { "${it.kindLabel}: ${it.name}" } ?: "not bound"),
                            color = if (boundId == null) AccentRed else AccentGreen,
                            fontSize = 11.sp,
                            modifier = Modifier.padding(top = 6.dp)
                        )
                        Row(
                            Modifier.fillMaxWidth().padding(vertical = 3.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            candidates.take(4).forEach { candidate ->
                                Box(
                                    Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .background(if (candidate.entityId == boundId) AccentOrange else DarkSurface)
                                        .clickable {
                                            bindings = bindings + (spec.name to candidate.entityId)
                                        }
                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                ) {
                                    Text(
                                        "${candidate.kindLabel}: ${candidate.name}".take(22),
                                        color = if (candidate.entityId == boundId) DarkBackground else TextPrimary,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            val ready = targetId != null && program != null &&
                module.inputs.all { bindings.containsKey(it.name) }
            TextButton(
                onClick = {
                    val id = targetId ?: return@TextButton
                    onAttach(
                        ModuleAttachment(
                            id = creationState.newId(),
                            moduleId = module.id,
                            programId = program!!.id,
                            targetKind = kind,
                            targetId = id,
                            bindings = module.inputs.mapNotNull { spec ->
                                bindings[spec.name]?.let { entityId ->
                                    ModuleInputBinding(
                                        specName = spec.name,
                                        entityId = entityId,
                                        entityLabel = candidates.find { it.entityId == entityId }?.name ?: ""
                                    )
                                }
                            }
                        )
                    )
                },
                enabled = ready
            ) {
                Text("Attach", color = if (ready) AccentOrange else TextMuted, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

/** Targets of one kind inside a program, as id → label. */
private fun programTargets(
    creationState: CreationState,
    programId: Long,
    kind: ModuleTargetKind
): List<Pair<Long, String>> {
    val activeIds = creationState.activePrograms.filter { it.programId == programId }.map { it.id }.toSet()
    val program = creationState.programs.find { it.id == programId } ?: return emptyList()
    val sessionIds = creationState.activePrograms
        .filter { it.programId == programId }
        .flatMap { it.sessionIds }
        .toSet()
    val sessions = creationState.sessions.filter { it.id in sessionIds }
    val exercises = sessions.flatMap { s -> s.exerciseIds.mapNotNull { id -> creationState.exercises.find { it.id == id } } }
        .distinctBy { it.id }

    return when (kind) {
        ModuleTargetKind.SESSION -> sessions.map { it.id to it.name }
        ModuleTargetKind.EXERCISE -> exercises.map { ex ->
            val movement = creationState.movements.find { it.id == ex.movementId }?.name ?: "?"
            ex.id to (if (ex.name.isBlank()) movement else "$movement ${ex.name}")
        }
        ModuleTargetKind.SET -> exercises.flatMap { ex ->
            ex.entries.mapIndexed { index, entry -> entry.id to "${ex.name.ifBlank { "set" }} · ${index + 1}" }
        }
        ModuleTargetKind.DAY -> {
            val dayIds = sessions.flatMap { s ->
                creationState.days.filter { day -> day.timedSessionIds.any { ts -> creationState.timedSessions.find { it.id == ts }?.sessionId == s.id } }
            }.distinctBy { it.id }
            dayIds.map { it.id to it.name }
        }
    }
}
