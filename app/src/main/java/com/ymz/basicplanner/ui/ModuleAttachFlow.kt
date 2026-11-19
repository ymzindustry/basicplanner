package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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

/** The program an entity lives in; a program is its own program. */
fun programOf(creationState: CreationState, entity: BaseEntity): ProgramEntity? {
    if (entity is ProgramEntity) return entity
    var current: BaseEntity = entity
    repeat(8) {
        current = parentOf(creationState, current) ?: return@repeat
        if (current is ProgramEntity) return current
    }
    return null
}

/** The entity directly above [entity], if the tree has one. */
fun parentOf(creationState: CreationState, entity: BaseEntity): BaseEntity? = when (entity) {
    is ExerciseEntity -> creationState.sessions.find { entity.id in it.exerciseIds }
    is SessionEntity -> creationState.days.find { day ->
        day.timedSessionIds.any { ts -> creationState.timedSessions.find { it.id == ts }?.sessionId == entity.id }
    }
    is DayEntity -> creationState.weeks.find { entity.id in it.dayIds }
    is WeekEntity -> creationState.blocks.find { entity.id in it.weekIds }
    is BlockEntity -> creationState.blocks.find { entity.id in it.subBlockIds }
        ?: creationState.programs.find { entity.id in it.blockIds }
    else -> null
}

/** Every entity of a program, with the label shown in the input picker. */
fun entitiesOfProgram(
    creationState: CreationState,
    program: ProgramEntity
): List<Triple<Long, EntityKind, String>> {
    val out = mutableListOf<Triple<Long, EntityKind, String>>()
    out += Triple(program.id, EntityKind.PROGRAM, program.name)

    fun walkBlock(blockId: Long) {
        val block = creationState.blocks.find { it.id == blockId } ?: return
        out += Triple(block.id, EntityKind.BLOCK, block.name)
        block.subBlockIds.forEach { walkBlock(it) }
        block.weekIds.forEach { weekId ->
            val week = creationState.weeks.find { it.id == weekId } ?: return@forEach
            out += Triple(week.id, EntityKind.WEEK, week.name)
            week.dayIds.forEach { dayId ->
                val day = creationState.days.find { it.id == dayId } ?: return@forEach
                out += Triple(day.id, EntityKind.DAY, day.name)
                day.timedSessionIds.forEach { tsId ->
                    val sessionId = creationState.timedSessions.find { it.id == tsId }?.sessionId
                        ?: return@forEach
                    val session = creationState.sessions.find { it.id == sessionId } ?: return@forEach
                    out += Triple(session.id, EntityKind.SESSION, session.name)
                }
            }
        }
    }
    program.blockIds.forEach { walkBlock(it) }
    return out.distinctBy { it.first }
}

// ---------------------------------------------------------------- dialogs

/** The two things that can be hung on an entity. */
@Composable
fun EntityActionDialog(
    entityLabel: String,
    kindLabel: String,
    onAdjustment: () -> Unit,
    onModule: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
        title = {
            Column {
                Text(entityLabel, color = TextPrimary, fontWeight = FontWeight.Bold)
                Text(kindLabel, color = TextMuted, fontSize = 11.sp)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                ActionRow("Add adjustment", onAdjustment)
                ActionRow("Add module", onModule)
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

/** Compact module chooser: one `def …(…)` line per module, same shape as the modules list. */
@Composable
fun ModulePickerDialog(
    modules: List<ModuleDefinition>,
    onPick: (ModuleDefinition) -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        confirmButton = {},
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
        title = { Text("Add module", color = TextPrimary) },
        text = {
            if (modules.isEmpty()) {
                Text("No module yet — create one from the Modules section.", color = TextMuted, fontSize = 12.sp)
            } else {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    modules.forEach { module ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(DarkSurface)
                                .clickable { onPick(module) }
                                .padding(horizontal = 12.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = highlightPython(
                                    "def " + ModuleDefinition.pythonFunctionName(module.name) +
                                        "(" + ModuleDefinition.pythonParameterName(module.itemName) + "):"
                                ),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
}

/** Which entity feeds the module's item — restricted to the same program. */
@Composable
fun InputEntityPickerDialog(
    program: ProgramEntity,
    candidates: List<Triple<Long, EntityKind, String>>,
    initialId: Long?,
    onPick: (Long, String) -> Unit,
    onDismiss: () -> Unit
) {
    var selected by remember { mutableStateOf(initialId) }
    val selectedLabel = candidates.find { it.first == selected }?.third ?: ""

    AlertDialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(),
        containerColor = DarkSurfaceAlt,
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel", color = TextMuted) } },
        confirmButton = {
            TextButton(
                enabled = selected != null,
                onClick = { selected?.let { onPick(it, selectedLabel) } }
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
                    text = "Reads its item from this entity, in ${program.name}:",
                    color = TextMuted,
                    fontSize = 11.sp
                )
                Spacer(Modifier.height(8.dp))
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 360.dp)
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    candidates.forEach { (id, kind, label) ->
                        val isSelected = id == selected
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(if (isSelected) AccentOrange.copy(alpha = 0.18f) else DarkSurface)
                                .clickable { selected = id }
                                .padding(horizontal = 12.dp, vertical = 9.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = kind.moduleLabel(),
                                color = if (isSelected) AccentOrange else TextMuted,
                                fontSize = 9.sp,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier.width(62.dp)
                            )
                            Text(
                                text = label,
                                color = if (isSelected) AccentOrange else TextPrimary,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }
        }
    )
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
