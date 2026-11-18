package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.data.PythonModuleExecutor
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.model.module.ModuleDefinition
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray

private val CODE_BG = Color(0xFF0B0C0E)
private val CODE_LINE = 19.sp
private val CODE_FONT = 12.sp

/** Applies [highlightPython] to the editable text without touching offsets. */
private object PythonHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightPython(text.text), OffsetMapping.Identity)
}

/** `Deadlift form` → `deadlift_form.py`, so the module reads like a real file. */
fun moduleFileName(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "module" }
    return "$slug.py"
}

/**
 * The module editor, as a file: a one-line header naming the file and its item, then the code
 * filling the whole screen — no card, no sections, nothing else competing for the space.
 *
 * `def apply(item)` receives the chosen item's value, already typed, and returns four deltas:
 * `sets, reps, percent, time`, added to the plan from a zero baseline.
 */
@Composable
fun ModuleEditorScreen(
    creationState: CreationState,
    moduleId: Long,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val module = creationState.modules.find { it.id == moduleId }

    if (module == null) {
        onBack()
        return
    }

    var name by remember(moduleId) { mutableStateOf(module.name) }
    var itemFamilyId by remember(moduleId) { mutableStateOf(module.itemFamilyId) }
    var code by remember(moduleId) { mutableStateOf(module.code) }
    var output by remember(moduleId) { mutableStateOf<String?>(null) }

    val hasApply = Regex("""(?m)^\s*def\s+apply\s*\(""").containsMatchIn(code)
    val canSave = name.isNotBlank() && itemFamilyId != null && hasApply
    val families = creationState.itemFamilies
    val selectable = families.filter { it.itemType != ItemType.IMAGE }
    val chosen = selectable.find { it.id == itemFamilyId }
    val fileName = moduleFileName(name)

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        creationState.modules[index] = creationState.modules[index].copy(
            name = name.trim(),
            code = code,
            itemFamilyId = itemFamilyId,
            itemName = chosen?.name ?: ""
        )
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(CODE_BG)
    ) {
        // File header: name (editable), file name, state.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 2.dp, end = 10.dp, top = 2.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
            }
            Column(Modifier.weight(1f)) {
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = AccentOrange,
                        fontSize = 17.sp,
                        fontWeight = FontWeight.ExtraBold
                    ),
                    cursorBrush = SolidColor(AccentOrange),
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = fileName + if (hasApply) "" else "  · missing def apply(item)",
                    color = if (hasApply) TextMuted else AccentRed,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Box(
                Modifier
                    .width(7.dp)
                    .height(7.dp)
                    .clip(CircleShape)
                    .background(if (canSave) AccentGreen else TextMuted.copy(alpha = 0.4f))
            )
        }

        // Item picker + contract reminder, one line each so the code keeps the screen.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            var expanded by remember { mutableStateOf(false) }
            Box {
                Row(
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(DarkSurfaceAlt)
                        .border(
                            1.dp,
                            if (chosen != null) AccentOrange.copy(alpha = 0.5f) else TextMuted.copy(alpha = 0.25f),
                            RoundedCornerShape(50)
                        )
                        .clickable(enabled = selectable.isNotEmpty()) { expanded = true }
                        .padding(start = 10.dp, end = 6.dp, top = 5.dp, bottom = 5.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = chosen?.let { it.name } ?: "choose an item",
                        color = if (chosen != null) TextPrimary else TextMuted,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Icon(
                        Icons.Default.ArrowDropDown,
                        contentDescription = "choose item",
                        tint = AccentOrange,
                        modifier = Modifier.width(18.dp)
                    )
                }

                DropdownMenu(
                    expanded = expanded,
                    onDismissRequest = { expanded = false },
                    modifier = Modifier.background(DarkSurfaceAlt)
                ) {
                    selectable.forEach { family ->
                        DropdownMenuItem(
                            text = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = family.name,
                                        color = if (family.id == itemFamilyId) AccentOrange else TextPrimary,
                                        fontSize = 13.sp,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Text(
                                        text = family.itemType.name.lowercase(),
                                        color = TextMuted,
                                        fontSize = 9.sp,
                                        fontFamily = FontFamily.Monospace
                                    )
                                }
                            },
                            onClick = {
                                itemFamilyId = family.id
                                expanded = false
                            }
                        )
                    }
                }
            }

            Spacer(Modifier.weight(1f))

            Text(
                text = if (selectable.isEmpty()) "images cannot be inputs" else "item → apply(item)",
                color = if (selectable.isEmpty()) AccentRed else TextMuted.copy(alpha = 0.7f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp
            )
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(TextMuted.copy(alpha = 0.12f))
        )

        // The file itself: full-bleed code, sideways-scrolling so indentation never collapses.
        val measurer = rememberTextMeasurer()
        val density = LocalDensity.current
        val codeStyle = remember {
            TextStyle(fontSize = CODE_FONT, lineHeight = CODE_LINE, fontFamily = FontFamily.Monospace)
        }
        val codeWidth = remember(code) {
            val widest = code.lines().maxOfOrNull { line ->
                measurer.measure(AnnotatedString(line.ifEmpty { " " }), codeStyle, softWrap = false).size.width
            } ?: 0
            with(density) { widest.toDp() + 20.dp }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .horizontalScroll(rememberScrollState())
        ) {
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier
                    .widthIn(min = codeWidth)
                    .fillMaxHeight()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                textStyle = codeStyle.copy(color = TextPrimary),
                cursorBrush = SolidColor(AccentOrange),
                visualTransformation = PythonHighlightTransformation
            )
        }

        output?.let { text ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(DarkSurface)
                    .padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "→ " + text,
                    color = if (text.startsWith("ERROR")) AccentRed else AccentGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                TextButton(onClick = { output = null }) {
                    Text("hide", color = TextMuted, fontSize = 9.sp)
                }
            }
        }

        CommonBottomBar(
            leftButtons = {
                TextButton(onClick = {
                    val candidate = ModuleDefinition(
                        id = moduleId,
                        name = name,
                        code = code,
                        itemFamilyId = itemFamilyId,
                        itemName = chosen?.name ?: ""
                    )
                    val sampleValue = (chosen?.defaultValue ?: "").replace("\"", "\\\"")
                    val sampleName = (chosen?.name ?: "").replace("\"", "\\\"")
                    val sampleType = chosen?.itemType?.name?.lowercase() ?: "string"
                    val json = """{"date":"${java.time.LocalDate.now()}","program_id":0,""" +
                        """"target":{"kind":"test","id":0,"name":"test"},""" +
                        """"item":{"name":"$sampleName","type":"$sampleType","value":"$sampleValue"},""" +
                        """"items":{},"sets":[{"id":1,"exerciseId":0,"exercise":"test","movement":"test",""" +
                        """"reps":5,"intensity":80,"rest":120,"weight_kg":100.0}]}"""
                    output = try {
                        PythonModuleExecutor().run(candidate, json)
                    } catch (t: Throwable) {
                        "ERROR: ${t.message ?: t::class.simpleName}"
                    }
                }) {
                    Text("run", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                }
            },
            rightButtons = {
                TextButton(onClick = { if (canSave) save() }, enabled = canSave) {
                    Text(
                        text = "save",
                        color = if (canSave) AccentOrange else TextMuted,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )
                }
            }
        )
    }
}
