package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.Icon
import androidx.compose.ui.graphics.Color
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.DialogProperties
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.model.BlockEntity
import com.ymz.basicplanner.model.BaseEntity
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.DayEntity
import com.ymz.basicplanner.model.EntityKind
import com.ymz.basicplanner.model.ExerciseEntity
import com.ymz.basicplanner.model.ProgramEntity
import com.ymz.basicplanner.model.SessionEntity
import com.ymz.basicplanner.model.WeekEntity
import com.ymz.basicplanner.model.module.ModuleAttachment
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleTargetKind

// ---------------------------------------------------------------- helpers

/** The module target a tree entity maps to. Set entries have no row, so they're absent here. */
fun moduleTargetKindOf(kind: EntityKind): ModuleTargetKind? = when (kind) {
    EntityKind.PROGRAM -> ModuleTargetKind.PROGRAM
    EntityKind.BLOCK -> ModuleTargetKind.BLOCK
    EntityKind.WEEK -> ModuleTargetKind.WEEK
    EntityKind.DAY -> ModuleTargetKind.DAY
    EntityKind.SESSION -> ModuleTargetKind.SESSION
    EntityKind.EXERCISE -> ModuleTargetKind.EXERCISE
    else -> null
}

fun EntityKind.moduleLabel(): String = when (this) {
    EntityKind.PROGRAM -> "Program"
    EntityKind.BLOCK -> "Block"
    EntityKind.WEEK -> "Week"
    EntityKind.DAY -> "Day"
    EntityKind.SESSION -> "Session"
    EntityKind.EXERCISE -> "Exercise"
    else -> name.lowercase()
}

fun kindOfEntity(entity: BaseEntity): EntityKind? = when (entity) {
    is com.ymz.basicplanner.model.MovementEntity -> EntityKind.MOVEMENT
    is ExerciseEntity -> EntityKind.EXERCISE
    is SessionEntity -> EntityKind.SESSION
    is DayEntity -> EntityKind.DAY
    is WeekEntity -> EntityKind.WEEK
    is BlockEntity -> EntityKind.BLOCK
    is ProgramEntity -> EntityKind.PROGRAM
    else -> null
}

/**
 * A day's children are timed sessions, which wrap the session without being one. The tree shows the
 * session, so the walks here do the same.
 */
private fun childrenAsEntities(
    creationState: CreationState,
    entity: BaseEntity,
    kind: EntityKind
): List<Pair<BaseEntity, EntityKind>> {
    if (kind == EntityKind.EXERCISE) return emptyList()
    return creationState.childrenOf(kind, entity).mapNotNull { child ->
        when (child) {
            is com.ymz.basicplanner.model.TimedSessionEntity ->
                creationState.sessions.find { it.id == child.sessionId }?.let { it to EntityKind.SESSION }
            else -> kindOfEntity(child)?.let { child to it }
        }
    }
}

/**
 * Parent of every entity, built the same way the tree draws itself: `childrenOf` knows that a week
 * slot may hold a day, a session or a single exercise, so a session dropped straight into a week
 * still finds that week as its parent.
 */
private fun parentMap(creationState: CreationState): Map<Long, BaseEntity> {
    val parents = mutableMapOf<Long, BaseEntity>()
    fun walk(entity: BaseEntity, kind: EntityKind) {
        childrenAsEntities(creationState, entity, kind).forEach { (child, childKind) ->
            if (child.id <= 0 || child.id in parents) return@forEach
            parents[child.id] = entity
            walk(child, childKind)
        }
    }
    creationState.programs.forEach { walk(it, EntityKind.PROGRAM) }
    return parents
}

/** The program an entity lives in; a program is its own program. */
fun programOf(creationState: CreationState, entity: BaseEntity): ProgramEntity? {
    if (entity is ProgramEntity) return entity
    val parents = parentMap(creationState)
    var current: BaseEntity? = parents[entity.id]
    repeat(12) {
        when (val node = current) {
            null -> return null
            is ProgramEntity -> return node
            else -> current = parents[node.id]
        }
    }
    return null
}

/** The entity directly above [entity], if the tree has one. */
fun parentOf(creationState: CreationState, entity: BaseEntity): BaseEntity? =
    parentMap(creationState)[entity.id]

/**
 * Everything an entity contains, itself included — the scope an input may be read from. The section
 * you add the module from decides which entity is passed here: a block row in the Block section, the
 * program row in the Program section.
 */
fun entitiesInSubtree(
    creationState: CreationState,
    root: BaseEntity
): List<Triple<Long, EntityKind, String>> {
    val out = mutableListOf<Triple<Long, EntityKind, String>>()

    fun label(entity: BaseEntity, kind: EntityKind): String = when (entity) {
        is ExerciseEntity -> {
            val movement = creationState.movements.find { it.id == entity.movementId }?.name ?: ""
            if (entity.name.isBlank()) movement else "$movement ${entity.name}"
        }
        else -> entity.name
    }

    fun walk(entity: BaseEntity, kind: EntityKind) {
        out += Triple(entity.id, kind, label(entity, kind))
        childrenAsEntities(creationState, entity, kind).forEach { (child, childKind) ->
            walk(child, childKind)
        }
    }

    kindOfEntity(root)?.let { walk(root, it) }
    return out.distinctBy { it.first }
}

/** Every entity of a program, with the label shown in the input picker. *//** Every entity of a program, with the label shown in the input picker. */
fun entitiesOfProgram(
    creationState: CreationState,
    program: ProgramEntity
): List<Triple<Long, EntityKind, String>> = entitiesInSubtree(creationState, program)

// ---------------------------------------------------------------- dialogs

/**
 * A rounded rectangle with a semicircular hole punched into its top edge, left side. It is the
 * module row's signature shape: the outline itself says "this is a module line".
 */
class ModuleRowShape(
    private val cornerDp: Float = 10f,
    private val holeDp: Float = 15f
) : androidx.compose.ui.graphics.Shape {
    override fun createOutline(
        size: androidx.compose.ui.geometry.Size,
        layoutDirection: androidx.compose.ui.unit.LayoutDirection,
        density: androidx.compose.ui.unit.Density
    ): androidx.compose.ui.graphics.Outline {
        val r = with(density) { cornerDp.dp.toPx() }
        val s = with(density) { holeDp.dp.toPx() }
        val h = size.height
        val holeCx = r + s
        val path = androidx.compose.ui.graphics.Path()
        path.moveTo(r, 0f)
        path.lineTo(holeCx - s, 0f)
        path.arcTo(androidx.compose.ui.geometry.Rect(holeCx - s, -s, holeCx + s, s), 180f, -180f, false)
        path.lineTo(size.width - r, 0f)
        path.quadraticTo(size.width, 0f, size.width, r)
        path.lineTo(size.width, h - r)
        path.quadraticTo(size.width, h, size.width - r, h)
        path.lineTo(r, h)
        path.quadraticTo(0f, h, 0f, h - r)
        path.lineTo(0f, r)
        path.quadraticTo(0f, 0f, r, 0f)
        path.close()
        return androidx.compose.ui.graphics.Outline.Generic(path)
    }
}

/**
 * The long-press dialog: the entity's name, the modules attached to it, and the way to add another.
 * Adjustments are not here — they live in the entity's own edit dialog.
 */
@Composable
fun EntityActionDialog(
    entityLabel: String,
    kindLabel: String,
    attachments: List<Pair<ModuleAttachment, ModuleDefinition>>,
    onDelete: (ModuleAttachment) -> Unit,
    onAddModule: () -> Unit,
    onDismiss: () -> Unit
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        confirmButton = {},
        dismissButton = {},
        title = {
            Column {
                Text(entityLabel, color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                Text(kindLabel, color = TextMuted, fontSize = 10.sp)
            }
        },
        text = {
            Column {
                Text("modules :", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                Spacer(Modifier.height(8.dp))

                if (attachments.isEmpty()) {
                    Text("vide", color = TextMuted.copy(alpha = 0.55f), fontSize = 12.sp)
                } else {
                    attachments.forEach { (attachment, module) ->
                        val open = expandedId == attachment.id
                        Column(Modifier.fillMaxWidth().padding(bottom = 10.dp)) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(56.dp)
                                    .background(androidx.compose.ui.graphics.Color.Transparent, ModuleRowShape())
                                    .border(1.dp, AccentOrange.copy(alpha = 0.6f), ModuleRowShape())
                                    .padding(start = 14.dp, end = 6.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = highlightPython(
                                        "def " + ModuleDefinition.pythonFunctionName(module.name) +
                                            "(" + ModuleDefinition.pythonParameterName(module.itemName) + ")"
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 12.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    Icons.Default.DeleteOutline,
                                    contentDescription = "remove module",
                                    tint = AccentRed.copy(alpha = 0.8f),
                                    modifier = Modifier
                                        .size(18.dp)
                                        .clickable { onDelete(attachment) }
                                )
                                Spacer(Modifier.width(8.dp))
                                Icon(
                                    imageVector = if (open) Icons.Default.ArrowDropDown
                                    else Icons.AutoMirrored.Filled.ArrowRight,
                                    contentDescription = "unfold",
                                    tint = TextMuted,
                                    modifier = Modifier
                                        .size(20.dp)
                                        .clickable { expandedId = if (open) null else attachment.id }
                                )
                            }
                            if (open) {
                                Column(Modifier.fillMaxWidth().padding(start = 14.dp, top = 6.dp)) {
                                    moduleFunctionBody(module.code).lines().forEach { line ->
                                        Text(
                                            text = highlightPython(line.ifEmpty { " " }),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    Text(
                        text = "Add module",
                        color = AccentOrange,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier
                            .clickable(onClick = onAddModule)
                            .padding(vertical = 8.dp, horizontal = 2.dp)
                    )
                }
            }
        }
    )
}

@Composable
private fun ActionRow(label: String, onClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .background(DarkSurface)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, color = AccentOrange, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

/**
 * Module chooser: bare `def …(…)` lines in the code colours, chevron to unfold the body — the same
 * shape as the Modules tab, without filled cells.
 */
@Composable
fun ModulePickerDialog(
    entityLabel: String,
    modules: List<ModuleDefinition>,
    onPick: (ModuleDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    var expandedId by remember { mutableStateOf<Long?>(null) }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
        title = {
            Column {
                Text("Add module", color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(entityLabel, color = TextMuted, fontSize = 11.sp)
            }
        },
        text = {
            if (modules.isEmpty()) {
                Text("No module yet — create one from the Modules section.", color = TextMuted, fontSize = 12.sp)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    modules.forEach { module ->
                        val open = expandedId == module.id
                        Column(Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { onPick(module) }
                                    .padding(horizontal = 2.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = highlightPython(
                                        "def " + ModuleDefinition.pythonFunctionName(module.name) +
                                            "(" + ModuleDefinition.pythonParameterName(module.itemName) + "):"
                                    ),
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                Icon(
                                    imageVector = if (open) Icons.Default.ArrowDropDown
                                    else Icons.AutoMirrored.Filled.ArrowRight,
                                    contentDescription = null,
                                    tint = TextMuted,
                                    modifier = Modifier
                                        .size(22.dp)
                                        .clickable { expandedId = if (open) null else module.id }
                                )
                            }
                            if (open) {
                                Column(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(start = 10.dp, bottom = 8.dp)
                                ) {
                                    moduleFunctionBody(module.code).lines().forEach { line ->
                                        Text(
                                            text = highlightPython(line.ifEmpty { " " }),
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 10.sp,
                                            lineHeight = 15.sp,
                                            maxLines = 1,
                                            softWrap = false,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    )
}

/**
 * The input chooser, drawn like the creation tree: same rows, same chevrons, same nesting — tapping
 * a row selects it instead of opening it for edition.
 */
@Composable
fun InputEntityTreeDialog(
    creationState: CreationState,
    scopeRoot: BaseEntity,
    programName: String,
    initialId: Long?,
    onPick: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initialId) }
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }

    fun label(entity: BaseEntity): String = when (entity) {
        is ExerciseEntity -> {
            val movement = creationState.movements.find { it.id == entity.movementId }?.name ?: ""
            if (entity.name.isBlank()) movement else "$movement ${entity.name}"
        }
        else -> entity.name
    }

    @Composable
    fun row(entity: BaseEntity, kind: EntityKind, depth: Int) {
        val children = if (kind == EntityKind.EXERCISE) emptyList()
        else creationState.childrenOf(kind, entity).filter { it.id > 0 }
        val isOpen = expanded[entity.id] == true
        val isSelected = entity.id == selected

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(8.dp))
                .background(if (isSelected) AccentOrange.copy(alpha = 0.16f) else Color.Transparent)
                .clickable { selected = entity.id }
                .padding(start = (depth * 14).dp, top = 7.dp, bottom = 7.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(22.dp)
                    .clickable(enabled = children.isNotEmpty()) {
                        expanded[entity.id] = !isOpen
                    },
                contentAlignment = Alignment.Center
            ) {
                if (children.isNotEmpty()) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.ArrowDropDown
                        else Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = kind.moduleLabel(),
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(46.dp)
            )
            Text(
                text = label(entity),
                color = if (isSelected) AccentOrange else TextPrimary,
                fontSize = 13.sp,
                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            if (isSelected) {
                Box(
                    Modifier
                        .size(7.dp)
                        .clip(RoundedCornerShape(50))
                        .background(AccentOrange)
                )
            }
        }
        if (isOpen) {
            children.forEach { child ->
                kindOfEntity(child)?.let { childKind -> row(child, childKind, depth + 1) }
            }
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = {
                    val id = selected
                    if (id != null) {
                        val label = entitiesInSubtree(creationState, scopeRoot)
                            .find { it.first == id }?.third ?: ""
                        onPick(id, label)
                    }
                }
            ) {
                Text(
                    "OK",
                    color = if (selected != null) AccentOrange else TextMuted.copy(alpha = 0.5f),
                    fontWeight = FontWeight.Bold
                )
            }
        },
        title = { Text("Input", color = TextPrimary) },
        text = {
            Column {
                Text(
                    text = "Scoped to $programName — pick where the item is read.",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 420.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    kindOfEntity(scopeRoot)?.let { rootKind ->
                        expanded[scopeRoot.id] = true
                        row(scopeRoot, rootKind, 0)
                    }
                }
            }
        }
    )
}

/**
 * The tree used to pick the input, drawn inline (not as a dialog) so the creation screen can dim
 * everything around it and let the parent entity stand out.
 */
@Composable
fun InputPickerTree(
    creationState: CreationState,
    scopeRoot: BaseEntity,
    onSelect: (Long, String) -> Unit,
    modifier: Modifier = Modifier
) {
    val expanded = remember { mutableStateMapOf<Long, Boolean>() }
    expanded[scopeRoot.id] = true

    fun label(entity: BaseEntity): String = when (entity) {
        is ExerciseEntity -> {
            val movement = creationState.movements.find { it.id == entity.movementId }?.name ?: ""
            if (entity.name.isBlank()) movement else "$movement ${entity.name}"
        }
        else -> entity.name
    }

    @Composable
    fun row(entity: BaseEntity, kind: EntityKind, depth: Int) {
        val children = childrenAsEntities(creationState, entity, kind)
        val isOpen = expanded[entity.id] == true

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable { onSelect(entity.id, label(entity)) }
                .padding(start = (depth * 14).dp, top = 8.dp, bottom = 8.dp, end = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .clickable(enabled = children.isNotEmpty()) { expanded[entity.id] = !isOpen },
                contentAlignment = Alignment.Center
            ) {
                if (children.isNotEmpty()) {
                    Icon(
                        imageVector = if (isOpen) Icons.Default.ArrowDropDown
                        else Icons.AutoMirrored.Filled.ArrowRight,
                        contentDescription = null,
                        tint = AccentOrange,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
            Text(
                text = kind.moduleLabel(),
                color = TextMuted,
                fontSize = 8.sp,
                fontFamily = FontFamily.Monospace,
                modifier = Modifier.width(48.dp)
            )
            Text(
                text = label(entity),
                color = TextPrimary,
                fontSize = 14.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
        }
        if (isOpen) {
            children.forEach { (child, childKind) -> row(child, childKind, depth + 1) }
        }
    }

    Column(modifier = modifier.verticalScroll(rememberScrollState())) {
        kindOfEntity(scopeRoot)?.let { row(scopeRoot, it, 0) }
    }
}

/** Builds the attachment once both choices are made. */
fun buildModuleAttachment(
    id: Long,
    module: ModuleDefinition,
    program: ProgramEntity,
    targetKind: ModuleTargetKind,
    targetId: Long,
    inputEntityId: Long,
    inputLabel: String
) = ModuleAttachment(
    id = id,
    moduleId = module.id,
    programId = program.id,
    targetKind = targetKind,
    targetId = targetId,
    inputEntityId = inputEntityId,
    inputEntityLabel = inputLabel
)
