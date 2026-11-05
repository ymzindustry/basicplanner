package com.ymz.basicplanner.compose

import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.BaselineShift
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.em
import com.ymz.basicplanner.model.BaseEntity
import com.ymz.basicplanner.model.MovementEntity
import androidx.compose.ui.text.AnnotatedString
import com.ymz.basicplanner.ui.AccentOrange

fun BaseEntity.getFormatName(movements: List<MovementEntity>, maxLength: Int = 20, strUnknown: String = "", strRestDay: String = ""): AnnotatedString {

    val rawName = this.computeNameString(movements,strUnknown, maxLength, strRestDay)

    return buildAnnotatedString {
        if (rawName.contains("^")) {
            val parts = rawName.split("^")
            append(parts[0])


            withStyle(
                SpanStyle(
                    baselineShift = BaselineShift.Superscript,
                    fontSize = 0.7.em,
                    color = AccentOrange
                )
            ) {
                append(parts.getOrElse(1) { "" })
            }
        } else {
            append(rawName)
        }
    }
}