package com.ymz.basicplanner.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.AlertDialog
import androidx.compose.ui.window.DialogProperties
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.R
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.CommonHeader
import com.ymz.basicplanner.compose.MarqueeText
import com.ymz.basicplanner.compose.ScrollingText
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.data.PythonModuleExecutor
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.compose.itemTypeColor
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleTargetKind
import kotlinx.coroutines.launch

/**
 * Modules section: the Python programs that make a plan dynamic, and where each of them is plugged
 * in. A module is written and tested here; the session screen only displays its effect.
 */
@Composable
fun ModulesScreen(
    creationState: CreationState,
    onBack: () -> Unit,
    initiallyExpandedModuleId: Long? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()


    var editingModuleId by remember { mutableStateOf<Long?>(null) }
    var editingReadOnly by remember { mutableStateOf(false) }

    // The editor is a full page, like opening a .py file — not a dialog.
    if (editingModuleId != null) {
        ModuleEditorScreen(
            creationState = creationState,
            moduleId = editingModuleId!!,
            readOnly = editingReadOnly,
            onBack = { editingModuleId = null }
        )
        return
    }
    var creating by remember { mutableStateOf(false) }
    var attachingTo by remember { mutableStateOf<ModuleDefinition?>(null) }
    var editingModule by remember { mutableStateOf<ModuleDefinition?>(null) }
    var renaming by remember { mutableStateOf<ModuleDefinition?>(null) }
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
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
                    }
                },
                rightButtons = {
                    IconButton(onClick = { creating = true }, modifier = Modifier.size(40.dp)) {
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
            // Same header geometry as the editor: equal slots left/right so the title is centred.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "MODULES",
                    color = TextPrimary,
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    modifier = Modifier
                        .weight(1f)
                        .padding(start = 16.dp)
                )
            }

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
                            initiallyExpanded = module.id == initiallyExpandedModuleId,
                            attachments = attachments,
                            attachmentLabel = { attachment ->
                                val target = attachmentLabel(creationState, attachment)
                                val program = creationState.programs.find { it.id == attachment.programId }?.name
                                listOfNotNull(program, target).joinToString(" · ")
                            },
                            itemType = creationState.itemFamilies
                                .find { it.id == module.itemFamilyId }?.itemType,
                            onEdit = { editingModule = module },
                            onEditCode = {
                                editingReadOnly = false
                                editingModuleId = module.id
                            },
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
        ModuleFormDialog(
            creationState = creationState,
            editing = null,
            onDismiss = { creating = false },
            onSubmit = { name, family ->
                val newModule = ModuleDefinition(
                    id = creationState.newId(),
                    name = name,
                    code = ModuleDefinition.defaultCodeFor(name, family.name, family.itemType),
                    itemFamilyId = family.id,
                    itemName = family.name
                )
                creationState.modules.add(newModule)
                save()
                creating = false
                editingModuleId = newModule.id
            }
        )
    }

    editingModule?.let { module ->
        ModuleFormDialog(
            creationState = creationState,
            editing = module,
            onDismiss = { editingModule = null },
            onSubmit = { name, family ->
                val index = creationState.modules.indexOfFirst { it.id == module.id }
                if (index >= 0) {
                    // The signature follows the new name and input; the body is kept untouched.
                    val signature = ModuleDefinition.signature(name, family.name, family.itemType)
                    creationState.modules[index] = module.copy(
                        name = name,
                        itemFamilyId = family.id,
                        itemName = family.name,
                        code = signature + "\n" + moduleFunctionBody(module.code) + "\n"
                    )
                    save()
                }
                editingModule = null
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
    } ?: when (attachment.targetKind) {
        ModuleTargetKind.DAY -> "Day (missing)"
        ModuleTargetKind.SESSION -> "Session (missing)"
        ModuleTargetKind.EXERCISE -> "Exercise (missing)"
        ModuleTargetKind.SET -> "Set (missing)"
    }

@Composable
private fun ModuleCard(
    module: ModuleDefinition,
    itemType: ItemType?,
    initiallyExpanded: Boolean = false,
    attachments: List<ModuleAttachment>,
    attachmentLabel: (ModuleAttachment) -> String,
    onEdit: () -> Unit,
    onEditCode: () -> Unit,
    onAttach: () -> Unit,
    onDetach: (ModuleAttachment) -> Unit,
    onDelete: () -> Unit
) {
    // One line per module — the signature — and tapping it unfolds the code, exactly like an
    // exercise row in the session screen unfolds its sets.
    var expanded by remember(module.id) { mutableStateOf(initiallyExpanded) }
    val signature = ModuleDefinition.signature(module.name, module.itemName, itemType)
    val body = moduleFunctionBody(module.code)

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceAlt)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { expanded = !expanded }
                .padding(start = 14.dp, end = 4.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = highlightPython(signature),
                fontFamily = FontFamily.Monospace,
                fontSize = 14.sp,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Icon(
                imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                contentDescription = if (expanded) "hide code" else "show code",
                tint = TextMuted
            )

            var menuOpen by remember { mutableStateOf(false) }
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Default.MoreVert, "module actions", tint = TextMuted)
                }
                // Exactly the menu the creation screen shows for an entity cell: default Material
                // dropdown (light panel), plain labels, Delete in red.
                DropdownMenu(
                    expanded = menuOpen,
                    onDismissRequest = { menuOpen = false }
                ) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        onClick = {
                            menuOpen = false
                            onEdit()
                        }
                    )
                    if (attachments.isNotEmpty()) {
                        DropdownMenuItem(
                            text = { Text("Remove", color = AccentRed) },
                            onClick = {
                                menuOpen = false
                                attachments.forEach(onDetach)
                            }
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete", color = AccentRed) },
                        onClick = {
                            menuOpen = false
                            onDelete()
                        }
                    )
                }
            }
        }

        androidx.compose.animation.AnimatedVisibility(
            visible = expanded,
            enter = androidx.compose.animation.expandVertically(androidx.compose.animation.core.tween(220)) +
                androidx.compose.animation.fadeIn(androidx.compose.animation.core.tween(180)),
            exit = androidx.compose.animation.shrinkVertically(androidx.compose.animation.core.tween(180)) +
                androidx.compose.animation.fadeOut(androidx.compose.animation.core.tween(120))
        ) {
            Column {
            Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.10f)))
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable(onClick = onEditCode)
                    .heightIn(max = 260.dp)
                    .verticalScroll(rememberScrollState())
                    .padding(start = 14.dp, end = 14.dp, top = 10.dp, bottom = 12.dp)
            ) {
                // One Text per line: a wrapped line loses its indentation and reads like a syntax
                // error, so a too-long line ends with an ellipsis instead.
                body.ifBlank { "    pass" }.lines().forEach { line ->
                    Text(
                        text = highlightPython(line.ifEmpty { " " }),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = 17.sp,
                        maxLines = 1,
                        softWrap = false,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            attachments.forEach { attachment ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.10f)))
                Row(
                    modifier = Modifier.fillMaxWidth().padding(start = 14.dp, end = 14.dp, top = 8.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("→", color = AccentOrange, fontSize = 12.sp)
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = attachmentLabel(attachment),
                        color = TextMuted,
                        fontSize = 11.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                }
            }
            }
        }
    }
}

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

    val programs = creationState.programs
    val program = programs.getOrNull(programIndex)

    val targets: List<Pair<Long, String>> = remember(program?.id, kind, creationState.dataVersion.value) {
        if (program == null) emptyList() else programTargets(creationState, program.id, kind)
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
                            }.padding(vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(if (selected) "◉" else "○", color = AccentOrange)
                            Spacer(Modifier.width(8.dp))
                            Text(label, color = if (selected) AccentOrange else TextPrimary, fontSize = 12.sp)
                        }
                    }
                }

                if (targetId != null) {
                    Spacer(Modifier.height(10.dp))
                    Text(
                        text = if (module.itemName.isBlank()) {
                            "No item yet — open the module and pick one."
                        } else {
                            "reads “${module.itemName}” on the target or its closest parent"
                        },
                        color = if (module.itemName.isBlank()) AccentRed else TextMuted,
                        fontSize = 11.sp
                    )
                }
            }
        },
        confirmButton = {
            val ready = targetId != null && program != null && module.itemName.isNotBlank()
            TextButton(
                onClick = {
                    val id = targetId ?: return@TextButton
                    onAttach(
                        ModuleAttachment(
                            id = creationState.newId(),
                            moduleId = module.id,
                            programId = program!!.id,
                            targetKind = kind,
                            targetId = id
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

/** Change the input of an existing module — the only place it is editable after creation. */
@Composable
private fun ChangeInputDialog(
    creationState: CreationState,
    module: ModuleDefinition,
    onDismiss: () -> Unit,
    onPick: (ItemFamily) -> Unit
) {
    val selectable = creationState.itemFamilies.filter { it.itemType != ItemType.IMAGE }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text("Change input", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column(Modifier.heightIn(max = 380.dp).verticalScroll(rememberScrollState())) {
                Text(
                    "The file's parameter is renamed to match, e.g. apply(form) → apply(sleep).",
                    color = TextMuted,
                    fontSize = 11.sp,
                    modifier = Modifier.padding(bottom = 8.dp)
                )
                selectable.forEach { family ->
                    val selected = family.id == module.itemFamilyId
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { onPick(family) }
                            .padding(vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(if (selected) "◉" else "○", color = AccentOrange)
                        Spacer(Modifier.width(10.dp))
                        Text(
                            family.name,
                            color = if (selected) AccentOrange else TextPrimary,
                            fontSize = 13.sp,
                            modifier = Modifier.weight(1f)
                        )
                        Text(
                            family.itemType.name.lowercase(),
                            color = TextMuted,
                            fontSize = 9.sp,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

@Composable
private fun RenameModuleDialog(
    module: ModuleDefinition,
    onDismiss: () -> Unit,
    onRename: (String) -> Unit
) {
    var text by remember { mutableStateOf(module.name) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text("Rename module", color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = true,
                colors = OutlinedTextFieldDefaults.colors(
                    focusedBorderColor = AccentOrange,
                    unfocusedBorderColor = TextMuted.copy(alpha = 0.3f),
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    cursorColor = AccentOrange
                ),
                modifier = Modifier.fillMaxWidth()
            )
        },
        confirmButton = {
            TextButton(onClick = { if (text.isNotBlank()) onRename(text.trim()) }) {
                Text("Rename", color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } }
    )
}

/**
 * Creating a module asks the two things the function needs: a name, and the single item it takes as
 * input (every item except images). Everything after that happens in the editor.
 */
@Composable
private fun ModuleFormDialog(
    creationState: CreationState,
    editing: ModuleDefinition?,
    onDismiss: () -> Unit,
    onSubmit: (String, ItemFamily) -> Unit
) {
    var name by remember { mutableStateOf(editing?.name ?: "") }
    var familyId by remember { mutableStateOf(editing?.itemFamilyId) }

    val selectable = creationState.itemFamilies.filter { it.itemType != ItemType.IMAGE }
    val chosen = selectable.find { it.id == familyId }
    val ready = name.isNotBlank() && chosen != null
    val preview = chosen?.let {
        ModuleDefinition.signature(name.ifBlank { "module" }, it.name, it.itemType)
    } ?: "def module(item: int):"

    // Same chrome as the movement edit dialog in the creation screen.
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Cancel", color = TextMuted)
            }
        },
        confirmButton = {
            TextButton(enabled = ready, onClick = { if (ready) onSubmit(name.trim(), chosen!!) }) {
                Text(
                    text = "OK",
                    color = if (ready) AccentOrange else TextMuted.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        title = {
            Text(
                text = if (editing == null) "New module" else "Edit",
                color = TextPrimary
            )
        },
        text = {
            Column(Modifier.fillMaxWidth()) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text("Name") },
                    placeholder = { Text("Deadlift form", color = TextMuted) },
                    singleLine = true,
                    textStyle = MaterialTheme.typography.bodyMedium.copy(color = TextPrimary),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = TextMuted,
                        focusedBorderColor = AccentOrange,
                        unfocusedLabelColor = TextMuted,
                        focusedLabelColor = AccentOrange,
                        cursorColor = AccentOrange,
                        unfocusedTextColor = TextPrimary,
                        focusedTextColor = TextPrimary
                    ),
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(16.dp))

                Text(text = "Inputs", color = TextMuted, fontSize = 11.sp)
                Spacer(Modifier.height(8.dp))

                // Items, in the coloured bubbles used everywhere else in the app.
                if (selectable.isEmpty()) {
                    Text("No item available — images cannot feed a module.", color = AccentRed, fontSize = 12.sp)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        selectable.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                row.forEach { family ->
                                    val color = itemTypeColor(family.itemType)
                                    val selected = family.id == familyId
                                    Box(
                                        modifier = Modifier
                                            .height(40.dp)
                                            .widthIn(max = 168.dp)
                                            .clip(RoundedCornerShape(14.dp))
                                            .background(color.copy(alpha = if (selected) 0.28f else 0.12f))
                                            .border(
                                                width = 1.dp,
                                                color = color.copy(alpha = if (selected) 0.95f else 0.5f),
                                                shape = RoundedCornerShape(14.dp)
                                            )
                                            .clickable {
                                                familyId = family.id
                                                if (name.isBlank()) name = family.name
                                            }
                                            .padding(horizontal = 10.dp),
                                        contentAlignment = Alignment.CenterStart
                                    ) {
                                        Text(
                                            text = family.name,
                                            color = TextPrimary,
                                            fontSize = 13.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(14.dp))
                Text(
                    text = preview,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp
                )
            }
        }
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
