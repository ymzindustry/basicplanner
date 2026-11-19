package com.ymz.basicplanner.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowRight
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.ymz.basicplanner.compose.highlightPython
import com.ymz.basicplanner.model.module.ModuleDefinition

private val previewModules = listOf(
    ModuleDefinition(id = 1L, name = "Deadlift form", itemName = "form"),
    ModuleDefinition(id = 2L, name = "Deload week", itemName = "week")
)

@Composable
private fun PreviewShell(holeCenterDp: Float) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .background(DarkSurfaceAlt, androidx.compose.foundation.shape.RoundedCornerShape(24.dp))
            .padding(horizontal = 18.dp, vertical = 16.dp)
    ) {
        Text("Mon : DEADLIFT", color = TextPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(14.dp))
        Text("modules :", color = TextMuted, fontSize = 10.sp, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
            previewModules.forEach { module ->
                val shape = ModuleRowShape(cornerDp = 4f, holeDp = 15f, holeCenterDp = holeCenterDp)
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(56.dp)
                        .background(Color.Transparent, shape)
                        .border(1.dp, AccentOrange.copy(alpha = 0.6f), shape)
                        .padding(start = 14.dp, end = 6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = highlightPython(
                            "def " + ModuleDefinition.pythonFunctionName(module.name) +
                                "(" + ModuleDefinition.pythonParameterName(module.itemName) + ")"
                        ),
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp,
                        maxLines = 1,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(Icons.Default.DeleteOutline, null, tint = AccentRed.copy(alpha = 0.8f), modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Icon(Icons.AutoMirrored.Filled.ArrowRight, null, tint = TextMuted, modifier = Modifier.size(20.dp))
                }
            }
        }
        Spacer(Modifier.height(4.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
            Text(
                "Add module",
                color = AccentOrange,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 6.dp)
            )
        }
    }
}

/** A · hole just inside the corner (current). */
@Composable
fun HoleVariantA() = PreviewShell(holeCenterDp = 25f)

/** B · hole pushed left, close to the edge. */
@Composable
fun HoleVariantB() = PreviewShell(holeCenterDp = 12f)

/** C · hole tangent to the left edge: the corner itself is bitten. */
@Composable
fun HoleVariantC() = PreviewShell(holeCenterDp = 19f)
