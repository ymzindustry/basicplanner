package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
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
import androidx.compose.ui.text.TextStyle
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
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleInputSpec
import kotlinx.coroutines.launch

private val CODE_LINE_HEIGHT = 18.sp
private val CODE_LINE_BOX = 18.dp

/** `Deadlift form` → `deadlift_form.py`, so the module reads like a real file. */
fun moduleFileName(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "module" }
    return "$slug.py"
}

/**
 * Full-page module editor, presented as a Python file: file name in the header, a comment block
 * carrying the metadata, the code filling the page, and the run/save actions in the bottom bar.
 *
 * Format constraints (they come from the runtime, not from taste):
 * - the file must define `apply(ctx)`; saving is refused otherwise;
 * - inputs are declared as `name:type` and become the keys of `ctx["items"]`;
 * - one statement per line (Python), so the line gutter stays aligned.
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
    var inputsText by remember(moduleId) {
        mutableStateOf(module.inputs.joinToString(", ") { "${it.name}:${it.type}" })
    }
    var code by remember(moduleId) { mutableStateOf(module.code) }
    var result by remember(moduleId) { mutableStateOf<String?>(null) }

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

    val hasApply = Regex("""(?m)^\s*def\s+apply\s*\(""").containsMatchIn(code)
    val canSave = name.isNotBlank() && hasApply
    val fileName = moduleFileName(name)

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        creationState.modules[index] = creationState.modules[index].copy(
            name = name.trim(),
            description = description.trim(),
            code = code,
            inputs = parseSpecs()
        )
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(DarkBackground)
    ) {
        CommonHeader(
            centerContent = { ScrollingText(text = fileName) },
            startContent = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
                }
            }
        )

        // File metadata, shown like the header comment of a .py file.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "PY",
                color = DarkBackground,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .clip(RoundedCornerShape(4.dp))
                    .background(AccentOrange)
                    .padding(horizontal = 5.dp, vertical = 1.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "def apply(ctx) → list of set deltas",
                color = TextMuted,
                fontSize = 11.sp,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = if (hasApply) "valid" else "missing apply(ctx)",
                color = if (hasApply) AccentGreen else AccentRed,
                fontSize = 10.sp
            )
        }

        // Metadata block: name, description, declared inputs.
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(DarkSurfaceAlt)
                .padding(8.dp)
        ) {
            CodeField(
                label = "# name",
                value = name,
                onValueChange = { name = it },
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            CodeField(
                label = "# description",
                value = description,
                onValueChange = { description = it },
                singleLine = true
            )
            Spacer(Modifier.height(6.dp))
            CodeField(
                label = "# inputs (name:type, …)",
                value = inputsText,
                onValueChange = { inputsText = it },
                singleLine = true
            )
        }

        Spacer(Modifier.height(6.dp))

        // The code itself, with a line-number gutter.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 8.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(Color(0xFF111111))
                .border(1.dp, TextMuted.copy(alpha = 0.25f), RoundedCornerShape(10.dp))
        ) {
            val lineCount = code.count { it == '\n' } + 1
            val scroll = rememberScrollState()
            Column(
                modifier = Modifier
                    .background(Color(0xFF0B0B0B))
                    .padding(horizontal = 6.dp, vertical = 8.dp)
                    .verticalScroll(scroll)
            ) {
                repeat(lineCount) { index ->
                    Text(
                        text = "${index + 1}",
                        color = TextMuted.copy(alpha = 0.6f),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        lineHeight = CODE_LINE_HEIGHT,
                        modifier = Modifier.heightIn(min = CODE_LINE_BOX)
                    )
                }
            }

            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 8.dp, vertical = 8.dp),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = CODE_LINE_HEIGHT
                ),
                cursorBrush = SolidColor(AccentOrange),
                // Keep tabs literal and the caret visible while the keyboard is open.
                decorationBox = { inner ->
                    Box {
                        if (code.isEmpty()) {
                            Text(
                                "# write python here\n" + ModuleDefinition.DEFAULT_CODE,
                                color = TextMuted.copy(alpha = 0.5f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp
                            )
                        }
                        inner()
                    }
                }
            )
        }

        result?.let { output ->
            Text(
                text = output,
                color = if (output.startsWith("ERROR")) AccentRed else AccentGreen,
                fontFamily = FontFamily.Monospace,
                fontSize = 10.sp,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
                    .heightIn(max = 80.dp)
            )
        }

        CommonBottomBar(
            leftButtons = {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
                }
            },
            rightButtons = {
                IconButton(onClick = {
                    result = try {
                        PythonModuleExecutor().run(
                            ModuleDefinition(
                                id = moduleId,
                                name = name,
                                description = description,
                                code = code,
                                inputs = parseSpecs()
                            ),
                            """{"date":"","program_id":0,"target":{"kind":"test","id":0,"name":"test"},"items":{},"sets":[]}"""
                        )
                    } catch (t: Throwable) {
                        "ERROR: ${t.message ?: t::class.simpleName}"
                    }
                }) {
                    Icon(Icons.Default.PlayArrow, "run", tint = AccentOrange)
                }
                IconButton(onClick = { if (canSave) save() }) {
                    Icon(
                        Icons.Default.Save,
                        "save",
                        tint = if (canSave) AccentOrange else TextMuted
                    )
                }
            }
        )
    }
}

@Composable
private fun CodeField(
    label: String,
    value: String,
    onValueChange: (String) -> Unit,
    singleLine: Boolean
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Text(
            text = label,
            color = TextMuted,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            modifier = Modifier.width(150.dp)
        )
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = singleLine,
            modifier = Modifier
                .weight(1f)
                .clip(RoundedCornerShape(6.dp))
                .background(Color(0xFF1A1A1A))
                .padding(horizontal = 6.dp, vertical = 4.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            cursorBrush = SolidColor(AccentOrange)
        )
    }
}
