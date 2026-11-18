package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleDefinition
import kotlinx.coroutines.launch

private val CODE_LINE = 19.sp

/** Applies [highlightPython] to the editable text without touching offsets. */
private object PythonHighlightTransformation : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightPython(text.text), OffsetMapping.Identity)
}

/** `Deadlift form` → `deadlift_form.py`, kept for the modules list. */
fun moduleFileName(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "module" }
    return "$slug.py"
}

/**
 * The module file: the function, on black, taking the whole screen. Only the module name sits above
 * it; the chrome — back, run, save — lives in the bottom bar like the session screen.
 *
 * The input is read-only here (chosen at creation, changed from the ⋯ menu); the code wraps.
 */
@Composable
fun ModuleEditorScreen(
    creationState: CreationState,
    moduleId: Long,
    onBack: () -> Unit,
    readOnly: Boolean = false
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val module = creationState.modules.find { it.id == moduleId }

    if (module == null) {
        onBack()
        return
    }

    var code by remember(moduleId) { mutableStateOf(module.code) }

    val hasApply = Regex("""(?m)^\s*def\s+[A-Za-z_]\w*\s*\(""").containsMatchIn(code)

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        creationState.modules[index] = creationState.modules[index].copy(code = code)
        scope.launch { AppStateStore.save(context, creationState.toAppState()) }
    }

    // Modules written before the function took the module's name still say `def apply(...)`.
    androidx.compose.runtime.LaunchedEffect(moduleId, readOnly) {
        if (readOnly) return@LaunchedEffect
        val signature = Regex("""(?m)^(\s*def\s+)([A-Za-z_]\w*)\s*\(""").find(module.code)
        val target = ModuleDefinition.pythonFunctionName(module.name)
        if (signature != null && signature.groupValues[2] == "apply" && target != "apply") {
            val range = signature.groups[2]!!.range
            code = module.code.substring(0, range.first) + target + module.code.substring(range.last + 1)
            save()
        }
    }
    val param = ModuleDefinition.pythonParameterName(module.itemName)


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
                    if (readOnly) {
                        TextButton(onClick = onBack) {
                            Text("Close", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        }
                    } else {
                        TextButton(onClick = { save() }) {
                            Text("Save", color = AccentOrange, fontWeight = FontWeight.Bold, fontSize = 16.sp)
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
            // Code directly on the black background, filling everything left.
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .verticalScroll(rememberScrollState())
                    .statusBarsPadding()
                    .padding(top = 46.dp)   // same first-line height as the session screen
            ) {
                BasicTextField(
                    value = code,
                    onValueChange = { if (!readOnly) code = it },
                    readOnly = readOnly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = 14.dp, end = 14.dp, top = 6.dp, bottom = 12.dp),
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

        }
    }
}
