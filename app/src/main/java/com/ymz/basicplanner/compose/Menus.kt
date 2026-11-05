package com.ymz.basicplanner.compose

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.border
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.ymz.basicplanner.ui.AccentOrange
import com.ymz.basicplanner.ui.DarkSurfaceAlt
import com.ymz.basicplanner.ui.TextPrimary
import com.ymz.basicplanner.model.truncate


@Composable
fun <T> UnifiedSelectionMenu(
    expanded: Boolean,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier,
    headerTitle: String? = null,
    isHeaderSelected: Boolean = false,
    onHeaderClick: (() -> Unit)? = null,
    items: List<T>,
    itemLabel: @Composable (T) -> String = { it.toString() },
    selectedItem: T?,
    onItemClick: (T) -> Unit
) {
    DropdownMenu(
        expanded = expanded,
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        modifier = modifier.border(
            BorderStroke(1.dp, AccentOrange),
            RoundedCornerShape(8.dp)
        )
    ) {

        if (headerTitle != null && onHeaderClick != null) {
            DropdownMenuItem(
                text = {
                    MarqueeText(
                        text = headerTitle,
                        color = if (isHeaderSelected) AccentOrange else TextPrimary
                    )
                },
                onClick = {
                    onHeaderClick()
                    onDismiss()
                }
            )
        }

        items.forEach { item ->
            val isSelected = item == selectedItem
            DropdownMenuItem(
                text = {
                    MarqueeText(
                        text = itemLabel(item),
                        color = if (isSelected) AccentOrange else TextPrimary
                    )
                },
                onClick = {
                    onItemClick(item)
                    onDismiss()
                }
            )
        }
    }
}