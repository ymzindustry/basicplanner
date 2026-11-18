package com.ymz.basicplanner.ui

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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
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
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.data.PythonModuleExecutor
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModulePrescription
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json

private val CODE_BG = Color(0xFF0B0C0E)
private val CODE_LINE = 19.sp

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
 * Full-page module editor, built on the dashboard's structure: section labels, one card per block,
 * orange chips, big values.
 *
 * The module reads exactly one item (chosen here) and returns `sets, reps, percent, time`.
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
    var description by remember(moduleId) { mutableStateOf(module.description) }
    var itemFamilyId by remember(moduleId) { mutableStateOf(module.itemFamilyId) }
    var code by remember(moduleId) { mutableStateOf(module.code) }
    var output by remember(moduleId) { mutableStateOf<String?>(null) }
    var returned by remember(moduleId) { mutableStateOf<ModulePrescription?>(null) }

    val hasApply = Regex("""(?m)^\s*def\s+apply\s*\(""").containsMatchIn(code)
    val canSave = name.isNotBlank() && itemFamilyId != null && hasApply
    val fileName = moduleFileName(name)
    val families = creationState.itemFamilies

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        val chosen = families.find { it.id == itemFamilyId }
        creationState.modules[index] = creationState.modules[index].copy(
            name = name.trim(),
            description = description.trim(),
            code = code,
            itemFamilyId = itemFamilyId,
            itemName = chosen?.name ?: ""
        )
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        // Header, dashboard-style: back, title block, state.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 4.dp, end = 12.dp, top = 4.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
            }
            Column(Modifier.weight(1f)) {
                Text(
                    text = name.ifBlank { "untitled module" },
                    color = AccentOrange,
                    fontSize = 20.sp,
                    fontWeight = FontWeight.ExtraBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    text = fileName,
                    color = TextMuted,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 1
                )
            }
            Text(
                text = if (canSave) "ready" else "incomplete",
                color = if (canSave) AccentGreen else TextMuted,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold
            )
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            SectionLabel("INPUT")

            if (families.isEmpty()) {
                InfoCard {
                    Text(
                        "No item exists yet. Create one in the Items section — a module reads exactly one.",
                        color = TextMuted,
                        fontSize = 12.sp
                    )
                }
            } else {
                val chosen = families.find { it.id == itemFamilyId }
                StatCard {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(
                                text = chosen?.name ?: "pick an item",
                                color = if (chosen != null) TextPrimary else TextMuted,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = chosen?.let { it.itemType.name.lowercase() + " · ctx[\"item\"]" }
                                    ?: "the function reads this one value",
                                color = TextMuted,
                                fontSize = 10.sp,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                        Box(
                            Modifier
                                .width(8.dp)
                                .height(8.dp)
                                .clip(CircleShape)
                                .background(if (chosen != null) AccentOrange else TextMuted.copy(alpha = 0.4f))
                        )
                    }
                    Spacer(Modifier.height(10.dp))
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        families.chunked(3).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                row.forEach { family ->
                                    Chip(
                                        label = family.name,
                                        selected = family.id == itemFamilyId,
                                        onClick = { itemFamilyId = family.id }
                                    )
                                }
                            }
                        }
                    }
                }
            }

            SectionLabel("CODE")
            // The code scrolls sideways instead of wrapping: a wrapped line at column 0 reads like
            // broken indentation, which is exactly what Python must never look like.
            val measurer = rememberTextMeasurer()
            val density = LocalDensity.current
            val codeStyle = remember {
                TextStyle(fontSize = 11.sp, lineHeight = CODE_LINE, fontFamily = FontFamily.Monospace)
            }
            val codeWidth = remember(code) {
                val widest = code.lines().maxOfOrNull { line ->
                    measurer.measure(AnnotatedString(line.ifEmpty { " " }), codeStyle, softWrap = false).size.width
                } ?: 0
                with(density) { widest.toDp() + 24.dp }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(16.dp))
                    .background(CODE_BG)
                    .border(1.dp, TextMuted.copy(alpha = 0.12f), RoundedCornerShape(16.dp))
                    .horizontalScroll(rememberScrollState())
                    .padding(12.dp)
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier
                        .widthIn(min = codeWidth)
                        .heightIn(min = 300.dp),
                    textStyle = codeStyle.copy(color = TextPrimary),
                    cursorBrush = SolidColor(AccentOrange),
                    visualTransformation = PythonHighlightTransformation
                )
            }

            SectionLabel("RETURNS")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                ReturnStat("sets", returned?.sets?.toString(), Modifier.weight(1f))
                ReturnStat("reps", returned?.reps?.toString(), Modifier.weight(1f))
                ReturnStat(
                    "percent",
                    returned?.percent?.let { if (it % 1.0 == 0.0) it.toInt().toString() else it.toString() },
                    Modifier.weight(1f)
                )
                ReturnStat("time", returned?.time?.let { "${it}s" }, Modifier.weight(1f))
            }
            Text(
                text = "return {\"sets\": n, \"reps\": n, \"percent\": n, \"time\": n}  ·  None = leave the plan as is",
                color = TextMuted.copy(alpha = 0.75f),
                fontFamily = FontFamily.Monospace,
                fontSize = 9.sp,
                modifier = Modifier.padding(top = 2.dp)
            )

            output?.let { text ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(10.dp))
                        .background(DarkSurfaceAlt)
                        .padding(horizontal = 10.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = text,
                        color = if (text.startsWith("ERROR")) AccentRed else AccentGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 4,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { output = null }) {
                        Text("hide", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }

            SectionLabel("ABOUT")
            StatCard {
                FieldLabel("# name")
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold
                    ),
                    cursorBrush = SolidColor(AccentOrange),
                    modifier = Modifier.fillMaxWidth()
                )
                Spacer(Modifier.height(10.dp))
                FieldLabel("# description")
                BasicTextField(
                    value = description,
                    onValueChange = { description = it },
                    singleLine = true,
                    textStyle = TextStyle(color = TextMuted, fontSize = 12.sp),
                    cursorBrush = SolidColor(AccentOrange),
                    decorationBox = { inner ->
                        Box {
                            if (description.isEmpty()) {
                                Text(
                                    "what it changes, in one line",
                                    color = TextMuted.copy(alpha = 0.5f),
                                    fontSize = 12.sp
                                )
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )
            }

            Spacer(Modifier.height(8.dp))
        }

        CommonBottomBar(
            leftButtons = {
                TextButton(onClick = {
                    val chosen = families.find { it.id == itemFamilyId }
                    val candidate = ModuleDefinition(
                        id = moduleId,
                        name = name,
                        description = description,
                        code = code,
                        itemFamilyId = itemFamilyId,
                        itemName = chosen?.name ?: ""
                    )
                    val sampleValue = (chosen?.defaultValue ?: "").replace("\"", "\\\"")
                    val sampleName = (chosen?.name ?: "").replace("\"", "\\\"")
                    val json = """{"date":"${java.time.LocalDate.now()}","program_id":0,""" +
                        """"target":{"kind":"test","id":0,"name":"test"},""" +
                        """"item":{"name":"$sampleName","value":"$sampleValue"},""" +
                        """"items":{},"sets":[{"id":1,"exerciseId":0,"exercise":"test","movement":"test",""" +
                        """"reps":5,"intensity":80,"rest":120,"weight_kg":100.0}]}"""
                    output = try {
                        PythonModuleExecutor().run(candidate, json)
                    } catch (t: Throwable) {
                        "ERROR: ${t.message ?: t::class.simpleName}"
                    }
                    returned = try {
                        Json { ignoreUnknownKeys = true }
                            .decodeFromString(ModulePrescription.serializer(), output.orEmpty())
                    } catch (t: Throwable) {
                        null
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

@Composable
private fun SectionLabel(text: String) {
    Text(
        text = text,
        color = TextMuted,
        fontSize = 10.sp,
        fontWeight = FontWeight.Bold,
        modifier = Modifier.padding(start = 4.dp, top = 10.dp, bottom = 2.dp)
    )
}

@Composable
private fun StatCard(content: @Composable () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceAlt)
            .padding(12.dp)
    ) {
        content()
    }
}

@Composable
private fun InfoCard(content: @Composable () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(16.dp))
            .background(DarkSurfaceAlt)
            .padding(14.dp)
    ) {
        content()
    }
}

@Composable
private fun FieldLabel(text: String) {
    Text(
        text = text,
        color = TextMuted.copy(alpha = 0.8f),
        fontFamily = FontFamily.Monospace,
        fontSize = 9.sp,
        modifier = Modifier.padding(bottom = 2.dp)
    )
}

@Composable
private fun Chip(label: String, selected: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) AccentOrange else DarkSurface)
            .border(
                width = 1.dp,
                color = if (selected) AccentOrange else TextMuted.copy(alpha = 0.3f),
                shape = RoundedCornerShape(50)
            )
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Text(
            text = label,
            color = if (selected) DarkBackground else TextMuted,
            fontSize = 11.sp,
            fontWeight = if (selected) FontWeight.Bold else FontWeight.Normal,
            maxLines = 1
        )
    }
}

/** One of the four returned values, dashboard stat-card style. */
@Composable
private fun ReturnStat(label: String, value: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurfaceAlt)
            .padding(horizontal = 10.dp, vertical = 10.dp)
    ) {
        Text(label, color = TextMuted, fontSize = 9.sp, maxLines = 1)
        Spacer(Modifier.height(2.dp))
        Text(
            text = value ?: "—",
            color = if (value != null) AccentOrange else TextMuted.copy(alpha = 0.5f),
            fontSize = 17.sp,
            fontWeight = FontWeight.ExtraBold,
            maxLines = 1
        )
    }
}
