package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.data.PythonModuleExecutor
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleDefinition
import com.ymz.basicplanner.model.module.ModuleInputSpec
import kotlinx.coroutines.launch

private val EDITOR_BG = Color(0xFF0D0D0D)
private val EDITOR_FIELD = Color(0xFF161616)
private val CODE_LINE_HEIGHT = 19.sp

/** `Deadlift form` → `deadlift_form.py`, so the module reads like a real file. */
fun moduleFileName(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "module" }
    return "$slug.py"
}

/**
 * Full-page module editor, shaped like a Python file: file name centred in the header, one card
 * holding the file's metadata then its code, and run/save at the bottom.
 *
 * Format constraints (they come from the runtime, not from taste):
 * - the file must define `apply(ctx)`, otherwise saving is refused;
 * - inputs are declared `name:type` and become the keys of `ctx["items"]`.
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
            .background(EDITOR_BG)
    ) {
        // Header: equal 48dp slots on both sides so the file name sits exactly in the middle.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 4.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                IconButton(onClick = onBack) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "back",
                        tint = AccentOrange
                    )
                }
            }
            Text(
                text = fileName,
                color = AccentOrange,
                fontFamily = FontFamily.Monospace,
                fontSize = 15.sp,
                fontWeight = FontWeight.Bold,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f)
            )
            Box(Modifier.width(48.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier
                        .size(8.dp)
                        .clip(CircleShape)
                        .background(if (hasApply) AccentGreen else AccentRed)
                )
            }
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)          // keep room for the bottom bar instead of eating the screen
                .padding(horizontal = 12.dp)
                .clip(RoundedCornerShape(14.dp))
                .background(EDITOR_FIELD)
        ) {
            // File strip: language, contract, validity.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PY",
                    color = EDITOR_BG,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.ExtraBold,
                    modifier = Modifier
                        .clip(RoundedCornerShape(4.dp))
                        .background(AccentOrange)
                        .padding(horizontal = 5.dp, vertical = 2.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    text = if (hasApply) "apply(ctx) → set deltas" else "missing def apply(ctx)",
                    color = if (hasApply) TextMuted else AccentRed,
                    fontSize = 11.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            DividerLine()

            // Metadata, written the way it would be at the top of the file.
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 10.dp)) {
                CodeInput(
                    label = "# name",
                    value = name,
                    onValueChange = { name = it }
                )
                Spacer(Modifier.height(10.dp))
                CodeInput(
                    label = "# description",
                    value = description,
                    onValueChange = { description = it }
                )
                Spacer(Modifier.height(10.dp))
                CodeInput(
                    label = "# inputs   (form:int, sleep:float, …)",
                    value = inputsText,
                    onValueChange = { inputsText = it }
                )
            }

            DividerLine()

            // Code.
            BasicTextField(
                value = code,
                onValueChange = { code = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(horizontal = 12.dp, vertical = 10.dp),
                textStyle = TextStyle(
                    color = TextPrimary,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 12.sp,
                    lineHeight = CODE_LINE_HEIGHT
                ),
                cursorBrush = SolidColor(AccentOrange),
                decorationBox = { inner ->
                    Box {
                        if (code.isEmpty()) {
                            Text(
                                text = ModuleDefinition.DEFAULT_CODE,
                                color = TextMuted.copy(alpha = 0.55f),
                                fontFamily = FontFamily.Monospace,
                                fontSize = 12.sp,
                                lineHeight = CODE_LINE_HEIGHT
                            )
                        }
                        inner()
                    }
                }
            )

            result?.let { output ->
                DividerLine()
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = output,
                        color = if (output.startsWith("ERROR")) AccentRed else AccentGreen,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 3,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    TextButton(onClick = { result = null }) {
                        Text("hide", color = TextMuted, fontSize = 10.sp)
                    }
                }
            }
        }

        CommonBottomBar(
            leftButtons = {
                TextButton(onClick = {
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
private fun DividerLine() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(TextMuted.copy(alpha = 0.15f))
    )
}

/** One metadata line: a comment-style label above a monospace field. */
@Composable
private fun CodeInput(
    label: String,
    value: String,
    onValueChange: (String) -> Unit
) {
    Column {
        Text(
            text = label,
            color = TextMuted.copy(alpha = 0.8f),
            fontFamily = FontFamily.Monospace,
            fontSize = 10.sp
        )
        Spacer(Modifier.height(3.dp))
        BasicTextField(
            value = value,
            onValueChange = onValueChange,
            singleLine = true,
            modifier = Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(6.dp))
                .background(EDITOR_BG)
                .padding(horizontal = 8.dp, vertical = 6.dp),
            textStyle = TextStyle(
                color = TextPrimary,
                fontFamily = FontFamily.Monospace,
                fontSize = 12.sp
            ),
            cursorBrush = SolidColor(AccentOrange),
            decorationBox = { inner ->
                Box {
                    if (value.isEmpty()) {
                        Text(
                            text = "…",
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
}
