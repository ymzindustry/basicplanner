package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
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
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
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
import kotlinx.coroutines.launch

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
 * The module file screen, built on the session screen's chrome: same header band with the title
 * vertically centred, same black bottom bar with the orange rule, same card holding the content.
 *
 * The input is read-only here — it is chosen once at creation and changed from the module's ⋯ menu.
 * The code wraps: this is a phone, not an IDE.
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
    var code by remember(moduleId) { mutableStateOf(module.code) }
    var output by remember(moduleId) { mutableStateOf<String?>(null) }

    val hasApply = Regex("""(?m)^\s*def\s+apply\s*\(""").containsMatchIn(code)
    val param = ModuleDefinition.pythonParameterName(module.itemName)
    val fileName = moduleFileName(name)

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        creationState.modules[index] = creationState.modules[index].copy(name = name.trim(), code = code)
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    fun run() {
        val sampleValue = (creationState.itemFamilies.find { it.id == module.itemFamilyId }?.defaultValue ?: "")
            .replace("\"", "\\\"")
        val sampleName = module.itemName.replace("\"", "\\\"")
        val sampleType = creationState.itemFamilies.find { it.id == module.itemFamilyId }
            ?.itemType?.name?.lowercase() ?: "string"
        val json = """{"date":"${java.time.LocalDate.now()}","program_id":0,""" +
            """"target":{"kind":"test","id":0,"name":"test"},""" +
            """"item":{"name":"$sampleName","type":"$sampleType","value":"$sampleValue"},""" +
            """"items":{},"sets":[{"id":1,"exerciseId":0,"exercise":"test","movement":"test",""" +
            """"reps":5,"intensity":80,"rest":120,"weight_kg":100.0}]}"""
        output = try {
            PythonModuleExecutor().run(module.copy(code = code), json)
        } catch (t: Throwable) {
            "ERROR: ${t.message ?: t::class.simpleName}"
        }
    }

    Scaffold(
        containerColor = DarkBackground,
        contentColor = TextPrimary,
        topBar = {
            Box(modifier = Modifier.statusBarsPadding()) {
                // Same band as the session screen: the title sits in the middle of the row, and the
                // back arrow shares that vertical centre instead of hugging the status bar.
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(40.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, "back", tint = AccentOrange)
                    }
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = name.ifBlank { "module" },
                        style = MaterialTheme.typography.titleLarge,
                        color = TextPrimary,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f)
                    )
                    Text(
                        text = if (hasApply) "ready" else "incomplete",
                        color = if (hasApply) AccentGreen else AccentRed,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 9.sp,
                        modifier = Modifier.padding(start = 8.dp)
                    )
                }
            }
        },
        bottomBar = {
            CommonBottomBar(
                leftButtons = {
                    IconButton(onClick = { run() }) {
                        Icon(Icons.Default.PlayArrow, "run module", tint = AccentOrange)
                    }
                },
                rightButtons = {
                    TextButton(onClick = { save() }) {
                        Text("Save", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 8.dp, vertical = 6.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(DarkSurfaceAlt)
        ) {
            // Card header: file name, its input, and the module's colour accent.
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(Modifier.weight(1f)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = fileName,
                            color = TextPrimary,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(Modifier.width(6.dp))
                        Box(Modifier.size(6.dp).clip(CircleShape).background(AccentOrange))
                    }
                    Text(
                        text = "input: " + module.itemName.ifBlank { "none" } + "  ·  apply(" + param + ")",
                        color = TextMuted,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 10.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                Text(
                    text = "${code.lines().size} lines",
                    color = TextMuted.copy(alpha = 0.6f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.12f)))

            // The code wraps: on a phone, sideways scrolling hides half of every long line.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { code = it },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = CODE_LINE
                    ),
                    cursorBrush = SolidColor(AccentOrange),
                    visualTransformation = PythonHighlightTransformation
                )
            }

            output?.let { text ->
                Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.12f)))
                Text(
                    text = "→ " + text,
                    color = if (text.startsWith("ERROR")) AccentRed else AccentGreen,
                    fontFamily = FontFamily.Monospace,
                    fontSize = 10.sp,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp)
                )
            }

            Box(Modifier.fillMaxWidth().height(1.dp).background(TextMuted.copy(alpha = 0.12f)))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = "return sets, reps, percent, time",
                    color = TextMuted.copy(alpha = 0.7f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
                Text(
                    text = "deltas from 0",
                    color = TextMuted.copy(alpha = 0.5f),
                    fontFamily = FontFamily.Monospace,
                    fontSize = 9.sp
                )
            }
        }
    }
}
