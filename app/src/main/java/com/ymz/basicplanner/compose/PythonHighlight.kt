package com.ymz.basicplanner.compose

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

/**
 * Python syntax colouring for the module editor.
 *
 * Only styles are applied, never characters, so offsets stay identical and the transformation can
 * use [androidx.compose.ui.text.input.OffsetMapping.Identity].
 */
object PythonColors {
    val keyword = Color(0xFFFF9E64)   // def, return, if …
    val string = Color(0xFF7EE787)
    val comment = Color(0xFF6B7280)
    val number = Color(0xFF79C0FF)
    val function = Color(0xFFD2A8FF)  // the name after def
    val builtin = Color(0xFFFFC777)   // int, len, range …
    val plain = Color(0xFFE6E6E6)
}

private val keywords = setOf(
    "def", "return", "if", "elif", "else", "for", "while", "in", "not", "and", "or",
    "None", "True", "False", "import", "from", "as", "class", "try", "except", "finally",
    "raise", "with", "pass", "break", "continue", "is", "lambda", "global", "assert", "del"
)

private val builtins = setOf(
    "int", "float", "str", "bool", "len", "min", "max", "abs", "round", "sum", "range",
    "sorted", "list", "dict", "set", "tuple", "print", "enumerate", "zip", "any", "all"
)

// comment | string | number | word
private val token = Regex(
    """(?<comment>#[^\n]*)|(?<string>"(?:[^"\\]|\\.)*"|'(?:[^'\\]|\\.)*')|(?<number>\b\d+(?:\.\d+)?\b)|(?<word>[A-Za-z_][A-Za-z0-9_]*)"""
)

/** Colours [text] like Python source. Unknown characters keep [PythonColors.plain]. */
fun highlightPython(text: String): AnnotatedString = buildAnnotatedString {
    append(text)
    addStyle(SpanStyle(color = PythonColors.plain), 0, text.length)

    var previousWord: String? = null
    token.findAll(text).forEach { match ->
        val value = match.value
        val range = match.range
        val style = when {
            match.groups["comment"] != null -> SpanStyle(color = PythonColors.comment)
            match.groups["string"] != null -> SpanStyle(color = PythonColors.string)
            match.groups["number"] != null -> SpanStyle(color = PythonColors.number)
            value in keywords -> SpanStyle(color = PythonColors.keyword, fontWeight = FontWeight.Bold)
            previousWord == "def" -> SpanStyle(color = PythonColors.function)
            previousWord == "class" -> SpanStyle(color = PythonColors.function)
            value in builtins -> SpanStyle(color = PythonColors.builtin)
            else -> null
        }
        if (style != null) addStyle(style, range.first, range.last + 1)
        if (match.groups["word"] != null) previousWord = value
    }
}
