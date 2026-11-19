package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.CommonBottomBar
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.data.AppStateStore
import com.ymz.basicplanner.model.CreationState
import com.ymz.basicplanner.model.module.ModuleDefinition
import kotlinx.coroutines.launch

private val CODE_LINE = 19.sp

private object PythonTransform : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText =
        TransformedText(highlightPython(text.text), OffsetMapping.Identity)
}

/** `Deadlift form` → `deadlift_form.py`, used by the modules list. */
fun moduleFileName(name: String): String {
    val slug = name.trim().lowercase()
        .replace(Regex("[^a-z0-9]+"), "_")
        .trim('_')
        .ifBlank { "module" }
    return "$slug.py"
}

/** The code without its `def …:` line. */
internal fun moduleFunctionBody(code: String): String {
    val lines = code.lines()
    val signature = lines.indexOfFirst { line ->
        line.trimStart().startsWith("def ") && line.trimEnd().endsWith(":")
    }
    return if (signature >= 0) lines.drop(signature + 1).joinToString("\n").trimEnd() else code.trimEnd()
}

/** The signature as a title: white `def`, orange function name, white argument list. */
private fun signatureTitle(signature: String): AnnotatedString {
    val open = signature.indexOf('(')
    val head = if (open > 0) signature.substring(0, open) else signature
    val rest = if (open > 0) signature.substring(open) else ""
    val space = head.indexOf(' ')
    val keyword = if (space > 0) head.substring(0, space + 1) else "def "
    val fn = if (space > 0) head.substring(space + 1) else head
    return buildAnnotatedString {
        withStyle(SpanStyle(color = TextPrimary)) { append(keyword) }
        withStyle(SpanStyle(color = AccentOrange)) { append(fn) }
        withStyle(SpanStyle(color = TextPrimary)) { append(rest) }
    }
}

/**
 * The edit-code screen, on the session screen's model: the signature as the title, the body in a
 * card underneath, the standard bottom bar with back and save.
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

    val family = creationState.itemFamilies.find { it.id == module.itemFamilyId }
    val signature = ModuleDefinition.signature(module.name, module.itemName, family?.itemType)
    var body by remember(moduleId) { mutableStateOf(moduleFunctionBody(module.code)) }

    fun save() {
        val index = creationState.modules.indexOfFirst { it.id == moduleId }
        if (index < 0) return
        creationState.modules[index] = creationState.modules[index]
            .copy(code = signature + "\n" + body.trimEnd() + "\n")
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
            // Title, where the session screen puts "DEADLIFT Medium".
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = signatureTitle(signature),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
            }

            // The body: no card, just a rule on the left saying "this belongs to the function".
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(start = 16.dp, end = 10.dp, bottom = 10.dp)
            ) {
                Box(
                    Modifier
                        .width(2.dp)
                        .fillMaxHeight()
                        .background(AccentOrange.copy(alpha = 0.45f))
                )
                BasicTextField(
                    value = body,
                    onValueChange = { if (!readOnly) body = it },
                    readOnly = readOnly,
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxHeight()
                        .verticalScroll(rememberScrollState())
                        .padding(start = 14.dp, top = 4.dp),
                    textStyle = TextStyle(
                        color = TextPrimary,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        lineHeight = CODE_LINE
                    ),
                    cursorBrush = SolidColor(AccentOrange),
                    visualTransformation = PythonTransform
                )
            }
        }
    }
}
