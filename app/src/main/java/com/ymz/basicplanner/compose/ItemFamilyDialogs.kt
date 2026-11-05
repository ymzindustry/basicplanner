package com.ymz.basicplanner.compose

import android.content.Context
import android.graphics.Bitmap
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.RectangleShape
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import com.ymz.basicplanner.R
import com.ymz.basicplanner.model.Item
import com.ymz.basicplanner.model.buildItemOverview
import com.ymz.basicplanner.model.ItemFamily
import com.ymz.basicplanner.model.ItemSort
import com.ymz.basicplanner.model.ItemType
import com.ymz.basicplanner.ui.*
import com.ymz.basicplanner.util.FileUtil
import kotlinx.coroutines.delay


enum class DialogMode { MANAGE_FAMILIES, HANG_ITEMS }
private enum class ManageFamiliesView { LIST, CREATE, EDIT }
private enum class HangItemsView { LIST, SELECT_FAMILY, CREATE_ITEM }


@Composable
fun ItemManagementDialog(
    mode: DialogMode,
    
    entityName: String = "",
    items: List<Item> = emptyList(),
    onHang: (ItemFamily, String) -> Unit = { _, _ -> },
    onUpdateItem: (Item, String) -> Unit = { _, _ -> },
    onDeleteItem: (Item) -> Unit = {},

    
    itemFamilies: List<ItemFamily>,
    onDismiss: () -> Unit,

    
    onCreateFamily: (name: String, description: String, itemType: ItemType, defaultValue: String) -> Unit = { _, _, _, _ -> },
    onUpdateFamily: (family: ItemFamily, name: String, description: String, itemType: ItemType, defaultValue: String) -> Unit = { _, _, _, _, _ -> },
    onDeleteFamily: (ItemFamily) -> Unit = {}
) {
    when (mode) {
        DialogMode.MANAGE_FAMILIES -> {
            ItemFamilyManagementContent(
                itemFamilies = itemFamilies,
                onDismiss = onDismiss,
                onCreate = onCreateFamily,
                onUpdate = onUpdateFamily,
                onDelete = onDeleteFamily
            )
        }
        DialogMode.HANG_ITEMS -> {
            HangItemContent(
                entityName = entityName,
                items = items,
                itemFamilies = itemFamilies,
                onDismiss = onDismiss,
                onHang = onHang,
                onUpdate = onUpdateItem,
                onDelete = onDeleteItem
            )
        }
    }
}






private fun isValueValid(value: String, type: ItemType): Boolean {
    
    if (value.isBlank()) return true
    return when (type) {
        ItemType.STRING, ItemType.IMAGE, ItemType.VIDEO -> true
        ItemType.INT -> value.toIntOrNull() != null
        ItemType.FLOAT -> value.toFloatOrNull() != null
        ItemType.BOOLEAN -> value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)
    }
}


@Composable
private fun ItemFamilyManagementContent(
    itemFamilies: List<ItemFamily>,
    onDismiss: () -> Unit,
    onCreate: (name: String, description: String, itemType: ItemType, defaultValue: String) -> Unit,
    onUpdate: (family: ItemFamily, name: String, description: String, itemType: ItemType, defaultValue: String) -> Unit,
    onDelete: (ItemFamily) -> Unit
) {
    var currentView by remember { mutableStateOf(ManageFamiliesView.LIST) }
    var editingFamily by remember { mutableStateOf<ItemFamily?>(null) }
    var infoFamily by remember { mutableStateOf<ItemFamily?>(null) }
    var selectedFilter by remember { mutableStateOf<ItemType?>(null) }

    var formName by remember { mutableStateOf("") }
    var formDescription by remember { mutableStateOf("") }
    var formType by remember { mutableStateOf(ItemType.STRING) }
    var formDefaultValue by remember { mutableStateOf("") }

    val strTitleManage = stringResource(id = R.string.manage_item_families)
    val strTitleCreate = stringResource(id = R.string.create_item_family)
    val strTitleEdit = stringResource(id = R.string.edit_item_family)

    val filteredFamilies = if (selectedFilter == null) itemFamilies
    else itemFamilies.filter { it.itemType == selectedFilter }

    val isSaveEnabled = formName.isNotBlank() && isValueValid(formDefaultValue, formType)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = {
            Text(
                text = when (currentView) {
                    ManageFamiliesView.LIST -> strTitleManage
                    ManageFamiliesView.CREATE -> strTitleCreate
                    ManageFamiliesView.EDIT -> strTitleEdit
                },
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (currentView) {
                ManageFamiliesView.LIST -> FamilyListContent(
                    filteredFamilies = filteredFamilies,
                    selectedFilter = selectedFilter,
                    onFilterChanged = { selectedFilter = it },
                    onShowInfo = { infoFamily = it },
                    onEditRequest = { familyToEdit ->
                        editingFamily = familyToEdit
                        formName = familyToEdit.name
                        formDescription = familyToEdit.description
                        formType = familyToEdit.itemType
                        formDefaultValue = familyToEdit.defaultValue
                        currentView = ManageFamiliesView.EDIT
                    },
                    onDeleteRequest = { onDelete(it) }
                )
                ManageFamiliesView.CREATE, ManageFamiliesView.EDIT -> FamilyFormContent(
                    name = formName,
                    onNameChange = { formName = it },
                    description = formDescription,
                    onDescriptionChange = { formDescription = it },
                    type = formType,
                    onTypeChange = { formType = it },
                    defaultValue = formDefaultValue,
                    onDefaultValueChange = { formDefaultValue = it },
                    isEditMode = currentView == ManageFamiliesView.EDIT
                )
            }
        },
        confirmButton = {
            when (currentView) {
                ManageFamiliesView.LIST -> {
                    TextButton(onClick = {
                        formName = ""
                        formDescription = ""
                        formDefaultValue = ""
                        formType = ItemType.STRING
                        editingFamily = null
                        currentView = ManageFamiliesView.CREATE
                    }) {
                        Text(stringResource(R.string.create), color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
                ManageFamiliesView.CREATE, ManageFamiliesView.EDIT -> {
                    TextButton(
                        onClick = {
                            if (currentView == ManageFamiliesView.CREATE) {
                                onCreate(formName, formDescription, formType, formDefaultValue)
                            } else {
                                editingFamily?.let { onUpdate(it, formName, formDescription, formType, formDefaultValue) }
                            }
                            currentView = ManageFamiliesView.LIST
                            editingFamily = null
                        },
                        enabled = isSaveEnabled
                    ) {
                        Text(
                            text = stringResource(R.string.dialog_save),
                            color = if (isSaveEnabled) AccentOrange else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        },
        dismissButton = {
            TextButton(
                onClick = {
                    if (currentView == ManageFamiliesView.LIST) {
                        onDismiss()
                    } else {
                        currentView = ManageFamiliesView.LIST
                        editingFamily = null
                    }
                }
            ) {
                Text(
                    text = if (currentView == ManageFamiliesView.LIST) stringResource(R.string.close) else stringResource(R.string.dialog_cancel),
                    color = TextMuted,
                    fontWeight = if (currentView == ManageFamiliesView.LIST) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    )

    if (infoFamily != null) {
        DescriptionInfoDialog(
            family = infoFamily!!,
            onDismiss = { infoFamily = null }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun FamilyListContent(
    filteredFamilies: List<ItemFamily>,
    selectedFilter: ItemType?,
    onFilterChanged: (ItemType?) -> Unit,
    onShowInfo: (ItemFamily) -> Unit,
    onEditRequest: (ItemFamily) -> Unit,
    onDeleteRequest: (ItemFamily) -> Unit
) {
    var sortByType by rememberSaveable { mutableStateOf(false) }
    var menuFamily by remember { mutableStateOf<ItemFamily?>(null) }
    var pendingFamilyDeletion by remember { mutableStateOf<ItemFamily?>(null) }

    val ordered = if (sortByType) {
        filteredFamilies.sortedWith(
            compareBy({ ItemType.entries.indexOf(it.itemType) }, { it.name.lowercase() })
        )
    } else {
        filteredFamilies.sortedBy { it.name.lowercase() }
    }

    Column {
        ItemsToolbar(
            sortByType = sortByType,
            onToggleSort = { sortByType = !sortByType },
            selectedFilter = selectedFilter,
            onFilterChanged = onFilterChanged
        )
        Spacer(modifier = Modifier.height(10.dp))

        if (ordered.isEmpty()) {
            Text(
                text = stringResource(R.string.items_none_families),
                color = TextMuted,
                fontSize = 12.sp
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                ordered.forEach { family ->
                    key(family.id) {
                        Box {
                            FamilyBubble(
                                family = family,
                                onClick = { onEditRequest(family) },
                                onLongClick = { menuFamily = family }
                            )
                            // Same item styling as the creation screen's three-dot menu: default
                            // text colour on the themed menu surface, red only for deleting.
                            DropdownMenu(
                                expanded = menuFamily?.id == family.id,
                                onDismissRequest = { menuFamily = null }
                            ) {
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.edit)) },
                                    onClick = {
                                        menuFamily = null
                                        onEditRequest(family)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.info)) },
                                    onClick = {
                                        menuFamily = null
                                        onShowInfo(family)
                                    }
                                )
                                DropdownMenuItem(
                                    text = { Text(stringResource(R.string.delete), color = AccentRed) },
                                    onClick = {
                                        menuFamily = null
                                        pendingFamilyDeletion = family
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    pendingFamilyDeletion?.let { family ->
        ConfirmActionDialog(
            title = stringResource(R.string.confirm_delete_title),
            message = stringResource(R.string.confirm_delete_message),
            confirmLabel = stringResource(R.string.delete),
            onDismiss = { pendingFamilyDeletion = null },
            onConfirm = {
                pendingFamilyDeletion = null
                onDeleteRequest(family)
            }
        )
    }
}

@Composable
private fun FamilyFormContent(
    name: String,
    onNameChange: (String) -> Unit,
    description: String,
    onDescriptionChange: (String) -> Unit,
    type: ItemType,
    onTypeChange: (ItemType) -> Unit,
    defaultValue: String,
    onDefaultValueChange: (String) -> Unit,
    isEditMode: Boolean
) {
    var typeMenuExpanded by remember { mutableStateOf(false) }
    val isDefaultValueValid = isValueValid(defaultValue, type)

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.dialog_name)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                cursorColor = AccentOrange,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        OutlinedTextField(
            value = description,
            onValueChange = onDescriptionChange,
            label = { Text(stringResource(R.string.description)) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            colors = OutlinedTextFieldDefaults.colors(
                unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                cursorColor = AccentOrange,
                focusedTextColor = TextPrimary,
                unfocusedTextColor = TextPrimary
            )
        )

        
        if (type != ItemType.IMAGE && type != ItemType.VIDEO && type != ItemType.BOOLEAN) {
            OutlinedTextField(
                value = defaultValue,
                onValueChange = onDefaultValueChange,
                label = { Text(stringResource(R.string.default_value)) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true,
                isError = !isDefaultValueValid,
                keyboardOptions = KeyboardOptions(
                    keyboardType = when (type) {
                        ItemType.INT -> KeyboardType.Number
                        ItemType.FLOAT -> KeyboardType.Decimal
                        else -> KeyboardType.Text
                    }
                ),
                colors = OutlinedTextFieldDefaults.colors(
                    unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                    unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                    cursorColor = AccentOrange,
                    focusedTextColor = TextPrimary,
                    unfocusedTextColor = TextPrimary,
                    errorBorderColor = AccentRed,
                    errorLabelColor = AccentRed,
                    
                    errorTextColor = TextPrimary
                )
            )
        }

        
        if (type == ItemType.BOOLEAN) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(MaterialTheme.shapes.small)
                    .clickable {
                        val currentValue = defaultValue.toBoolean()
                        onDefaultValueChange((!currentValue).toString())
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Checkbox(
                    checked = defaultValue.toBoolean(),
                    onCheckedChange = { onDefaultValueChange(it.toString()) },
                    colors = CheckboxDefaults.colors(
                        checkedColor = AccentOrange,
                        uncheckedColor = TextMuted,
                        checkmarkColor = DarkBackground
                    )
                )
                Spacer(Modifier.width(8.dp))
                Text(text = stringResource(R.string.default_value), color = TextPrimary)
            }
        }

        Box {
            TextButton(
                onClick = { typeMenuExpanded = true },
                enabled = !isEditMode
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = type.name,
                        color = if (isEditMode) TextMuted else AccentOrange,
                        fontWeight = FontWeight.Bold,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Icon(
                        imageVector = Icons.Default.ArrowDropDown,
                        contentDescription = stringResource(R.string.type),
                        tint = if (isEditMode) TextMuted else AccentOrange,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
            if (!isEditMode) {
                UnifiedSelectionMenu(
                    expanded = typeMenuExpanded,
                    onDismiss = { typeMenuExpanded = false },
                    items = ItemType.entries.toList(),
                    itemLabel = { it.name },
                    selectedItem = type,
                    onItemClick = { selectedType ->
                        onTypeChange(selectedType)
                        
                        onDefaultValueChange(if (selectedType == ItemType.BOOLEAN) "false" else "")
                        typeMenuExpanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DescriptionInfoDialog(family: ItemFamily, onDismiss: () -> Unit) {
    val strDefaultValueLabel = stringResource(id = R.string.default_value)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = { Text(family.name, color = TextPrimary, fontWeight = FontWeight.Bold) },
        text = {
            Column {
                
                Text(
                    text = family.description,
                    color = TextPrimary,
                    fontSize = 16.sp
                )

                
                if (family.defaultValue.isNotBlank()) {
                    Spacer(modifier = Modifier.height(16.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "$strDefaultValueLabel: ",
                            color = TextMuted,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp
                        )
                        Text(
                            text = family.defaultValue,
                            color = AccentOrange,
                            fontWeight = FontWeight.SemiBold,
                            fontSize = 14.sp
                        )
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.dialog_ok), color = AccentOrange, fontWeight = FontWeight.Bold)
            }
        }
    )
}







private fun isHangedValueValid(value: String, type: ItemType): Boolean {
    if (value.isBlank()) {
        return type == ItemType.STRING
    }
    return when (type) {
        ItemType.STRING, ItemType.IMAGE, ItemType.VIDEO -> true
        ItemType.INT -> value.toIntOrNull() != null
        ItemType.FLOAT -> value.toFloatOrNull() != null
        ItemType.BOOLEAN -> value.equals("true", ignoreCase = true) || value.equals("false", ignoreCase = true)
    }
}


@Composable
private fun HangItemContent(
    entityName: String,
    items: List<Item>,
    itemFamilies: List<ItemFamily>,
    onDismiss: () -> Unit,
    onHang: (ItemFamily, String) -> Unit,
    onUpdate: (Item, String) -> Unit,
    onDelete: (Item) -> Unit
) {
    var currentView by remember { mutableStateOf(HangItemsView.LIST) }
    var selectedFilter by remember { mutableStateOf<ItemType?>(null) }
    var selectedFamily by remember { mutableStateOf<ItemFamily?>(null) }
    var editingItem by remember { mutableStateOf<Item?>(null) }

    var formValue by remember { mutableStateOf("") }

    var viewingImageUri by remember { mutableStateOf<Uri?>(null) }
    var playingVideoUri by remember { mutableStateOf<Uri?>(null) }

    val strTitle = stringResource(R.string.hanged_items_on, entityName)
    val strHangNew = stringResource(R.string.hang_new_item)
    val strCreateItem = stringResource(R.string.create_item)

    val filteredItems = if (selectedFilter == null) items
    else {
        val familyIds = itemFamilies.filter { it.itemType == selectedFilter }.map { it.id }.toSet()
        items.filter { it.itemFamilyId in familyIds }
    }

    val context = LocalContext.current

    val imagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                FileUtil.copyUriToInternalStorage(context, it)?.let { newFileUri ->
                    formValue = newFileUri.toString()
                }
            }
        }
    )

    val videoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
        onResult = { uri: Uri? ->
            uri?.let {
                FileUtil.copyUriToInternalStorage(context, it)?.let { newFileUri ->
                    formValue = newFileUri.toString()
                }
            }
        }
    )

    val isSaveEnabled = selectedFamily?.let {
        isHangedValueValid(formValue, it.itemType)
    } ?: false


    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = DarkSurfaceAlt,
        title = {
            Text(
                text = when (currentView) {
                    HangItemsView.LIST -> strTitle
                    HangItemsView.SELECT_FAMILY -> strHangNew
                    HangItemsView.CREATE_ITEM -> strCreateItem
                },
                color = TextPrimary,
                fontWeight = FontWeight.Bold
            )
        },
        text = {
            when (currentView) {
                HangItemsView.LIST -> ItemListContent(
                    items = filteredItems,
                    itemFamilies = itemFamilies,
                    selectedFilter = selectedFilter,
                    onFilterChanged = { selectedFilter = it },
                    onEditRequest = { item ->
                        editingItem = item
                        selectedFamily = itemFamilies.find { it.id == item.itemFamilyId }
                        formValue = item.value
                        currentView = HangItemsView.CREATE_ITEM
                    },
                    onDeleteRequest = onDelete,
                    onViewImageRequest = { uriString -> viewingImageUri = Uri.parse(uriString) },
                    onPlayVideoRequest = { uriString -> playingVideoUri = Uri.parse(uriString) },
                    onAttachRequest = { family ->
                        selectedFamily = family
                        editingItem = null
                        formValue = if (family.itemType == ItemType.BOOLEAN) {
                            family.defaultValue.takeIf { it.isNotBlank() } ?: "false"
                        } else {
                            family.defaultValue
                        }
                        currentView = HangItemsView.CREATE_ITEM
                    }
                )
                HangItemsView.SELECT_FAMILY -> {
                    val existingFamilyIds = items.map { it.itemFamilyId }.toSet()
                    val availableFamilies = itemFamilies.filter { it.id !in existingFamilyIds }
                    SelectFamilyContent(
                        families = availableFamilies,
                        onFamilySelected = { family ->
                            selectedFamily = family
                            editingItem = null
                            formValue = if (family.itemType == ItemType.BOOLEAN) {
                                family.defaultValue.takeIf { it.isNotBlank() } ?: "false"
                            } else {
                                family.defaultValue
                            }
                            currentView = HangItemsView.CREATE_ITEM
                        }
                    )
                }
                HangItemsView.CREATE_ITEM -> CreateItemContent(
                    family = selectedFamily!!,
                    value = formValue,
                    onValueChange = { formValue = it },
                    onImagePickerClick = { imagePickerLauncher.launch("image/*") },
                    onVideoPickerClick = { videoPickerLauncher.launch("video/*") }
                )
            }
        },
        confirmButton = {
            when (currentView) {
                HangItemsView.LIST -> {
                    TextButton(onClick = onDismiss) {
                        Text(stringResource(R.string.close), color = AccentOrange, fontWeight = FontWeight.Bold)
                    }
                }
                HangItemsView.CREATE_ITEM -> {
                    TextButton(
                        onClick = {
                            if (editingItem != null) {
                                onUpdate(editingItem!!, formValue)
                            } else {
                                onHang(selectedFamily!!, formValue)
                            }
                            currentView = HangItemsView.LIST
                        },
                        enabled = isSaveEnabled
                    ) {
                        Text(
                            stringResource(R.string.dialog_save),
                            color = if (isSaveEnabled) AccentOrange else TextMuted,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
                HangItemsView.SELECT_FAMILY -> {}
            }
        },
        dismissButton = {
            TextButton(onClick = {
                if (currentView == HangItemsView.LIST) {
                    onDismiss()
                } else {
                    currentView = HangItemsView.LIST
                }
            }) {
                Text(
                    text = if (currentView == HangItemsView.LIST) stringResource(R.string.close) else stringResource(R.string.dialog_cancel),
                    color = TextMuted,
                    fontWeight = if (currentView == HangItemsView.LIST) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    )

    viewingImageUri?.let { uri ->
        ImagePreviewDialog(uri = uri, onDismiss = { viewingImageUri = null })
    }

    playingVideoUri?.let { uri ->
        VideoPlayerDialog(uri = uri, onDismiss = { playingVideoUri = null })
    }
}

/** Bubble colour per item type: the type is recognisable at a glance without reading a label. */
private fun itemTypeColor(type: ItemType): Color = when (type) {
    ItemType.STRING -> Color(0xFF64B5F6)  // blue
    ItemType.INT -> Color(0xFFFFB74D)     // orange
    ItemType.FLOAT -> Color(0xFF81C784)   // green
    ItemType.BOOLEAN -> Color(0xFFBA68C8) // purple
    ItemType.IMAGE -> Color(0xFF4DD0E1)   // cyan
    ItemType.VIDEO -> Color(0xFFE57373)   // red
}

/** Shared toolbar: sort order (A-Z / by type) on the left, type filter on the right. */
@Composable
private fun ItemsToolbar(
    sortByType: Boolean,
    onToggleSort: () -> Unit,
    selectedFilter: ItemType?,
    onFilterChanged: (ItemType?) -> Unit
) {
    var filterMenuExpanded by remember { mutableStateOf(false) }
    val strAll = stringResource(id = R.string.all)
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Row(
            modifier = Modifier
                .clip(RoundedCornerShape(12.dp))
                .clickable(onClick = onToggleSort)
                .padding(horizontal = 6.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = Icons.Default.Sort,
                contentDescription = null,
                tint = if (sortByType) AccentOrange else TextMuted,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(4.dp))
            Text(
                text = stringResource(if (sortByType) R.string.items_sort_type else R.string.items_sort_alpha),
                fontSize = 13.sp,
                color = if (sortByType) AccentOrange else TextMuted
            )
        }
        Box {
            Row(
                modifier = Modifier.clickable { filterMenuExpanded = true },
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.FilterList,
                    contentDescription = strAll,
                    tint = if (selectedFilter == null) TextMuted else AccentOrange
                )
                Spacer(modifier = Modifier.width(4.dp))
                Text(
                    text = selectedFilter?.name ?: strAll,
                    fontSize = 14.sp,
                    color = if (selectedFilter == null) TextMuted else AccentOrange
                )
            }
            UnifiedSelectionMenu(
                expanded = filterMenuExpanded,
                onDismiss = { filterMenuExpanded = false },
                headerTitle = strAll,
                isHeaderSelected = selectedFilter == null,
                onHeaderClick = { onFilterChanged(null) },
                items = ItemType.entries.toList(),
                itemLabel = { it.name },
                selectedItem = selectedFilter,
                onItemClick = { type -> onFilterChanged(type) }
            )
        }
    }
}

/** Dotted rule separating the attached items from the ones still free. */
@Composable
private fun DottedDivider() {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 10.dp)
            .height(1.dp)
    ) {
        drawLine(
            color = TextMuted.copy(alpha = 0.45f),
            start = Offset(0f, size.height / 2f),
            end = Offset(size.width, size.height / 2f),
            strokeWidth = 1.5f,
            pathEffect = PathEffect.dashPathEffect(floatArrayOf(7f, 6f), 0f)
        )
    }
}

@OptIn(ExperimentalFoundationApi::class, ExperimentalLayoutApi::class)
@Composable
private fun ItemListContent(
    items: List<Item>,
    itemFamilies: List<ItemFamily>,
    selectedFilter: ItemType?,
    onFilterChanged: (ItemType?) -> Unit,
    onEditRequest: (Item) -> Unit,
    onDeleteRequest: (Item) -> Unit,
    onViewImageRequest: (String) -> Unit,
    onPlayVideoRequest: (String) -> Unit,
    onAttachRequest: (ItemFamily) -> Unit
) {
    var pendingRemoval by remember { mutableStateOf<Item?>(null) }
    var sortByType by rememberSaveable { mutableStateOf(false) }

    val sort = if (sortByType) ItemSort.BY_TYPE else ItemSort.ALPHABETICAL
    val overview = buildItemOverview(items = items, families = itemFamilies, filter = selectedFilter, sort = sort)
    val attached = overview.attached
    val available = overview.available

    Column {
        ItemsToolbar(
            sortByType = sortByType,
            onToggleSort = { sortByType = !sortByType },
            selectedFilter = selectedFilter,
            onFilterChanged = onFilterChanged
        )

        Spacer(modifier = Modifier.height(10.dp))

        if (attached.isEmpty()) {
            Text(
                text = stringResource(R.string.items_none_attached),
                color = TextMuted,
                fontSize = 12.sp
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                attached.forEach { (family, item) ->
                    key(item.id) {
                        AttachedItemBubble(
                            item = item,
                            family = family,
                            onEdit = { onEditRequest(item) },
                            onViewMedia = {
                                if (family.itemType == ItemType.IMAGE) onViewImageRequest(item.value)
                                else onPlayVideoRequest(item.value)
                            },
                            onRemove = { pendingRemoval = item }
                        )
                    }
                }
            }
        }

        DottedDivider()

        if (available.isEmpty()) {
            Text(
                text = stringResource(
                    if (itemFamilies.isEmpty()) R.string.items_none_families else R.string.items_none_available
                ),
                color = TextMuted,
                fontSize = 12.sp
            )
        } else {
            FlowRow(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                maxItemsInEachRow = 3
            ) {
                available.forEach { family ->
                    key(family.id) {
                        AvailableItemBubble(family = family, onClick = { onAttachRequest(family) })
                    }
                }
            }
        }
    }

    pendingRemoval?.let { item ->
        val family = itemFamilies.find { it.id == item.itemFamilyId }
        AlertDialog(
            onDismissRequest = { pendingRemoval = null },
            containerColor = DarkSurfaceAlt,
            title = {
                Text(
                    text = stringResource(R.string.items_remove_title),
                    color = TextPrimary,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(
                    text = "${family?.name ?: item.value} — ${stringResource(R.string.items_remove_message)}",
                    color = TextMuted
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingRemoval = null
                    onDeleteRequest(item)
                }) {
                    Text(stringResource(R.string.delete), color = AccentRed, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingRemoval = null }) {
                    Text(stringResource(R.string.dialog_cancel), color = TextMuted)
                }
            }
        )
    }
}

/** Attached item on one line — "name : value" — scrolling when the value is long. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AttachedItemBubble(
    item: Item,
    family: ItemFamily,
    onEdit: () -> Unit,
    onViewMedia: () -> Unit,
    onRemove: () -> Unit
) {
    val color = itemTypeColor(family.itemType)
    val isMedia = family.itemType == ItemType.IMAGE || family.itemType == ItemType.VIDEO
    val label = buildAnnotatedString {
        withStyle(SpanStyle(color = color, fontWeight = FontWeight.SemiBold)) { append(family.name) }
        append(" : ")
        withStyle(SpanStyle(color = TextPrimary, fontWeight = FontWeight.Bold)) {
            append(item.value.ifBlank { "—" })
        }
    }

    Row(
        modifier = Modifier
            .widthIn(max = 168.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.16f))
            .border(1.dp, color.copy(alpha = 0.55f), RoundedCornerShape(14.dp))
            .combinedClickable(
                onClick = { if (isMedia) onViewMedia() else onEdit() },
                onLongClick = onRemove
            )
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (isMedia) {
            MarqueeText(
                text = AnnotatedString(family.name),
                style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
                modifier = Modifier.widthIn(max = 140.dp)
            )
            Spacer(modifier = Modifier.width(6.dp))
            MediaThumbnail(family = family, value = item.value, modifier = Modifier.size(26.dp))
        } else {
            MarqueeText(
                text = label,
                style = TextStyle(fontSize = 13.sp),
                modifier = Modifier.widthIn(max = 140.dp)
            )
        }
    }
}

/** Attached media item: a small thumbnail in the bubble instead of the raw URI. */
@Composable
private fun MediaThumbnail(family: ItemFamily, value: String, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(6.dp))
            .background(DarkSurface)
    ) {
        if (family.itemType == ItemType.IMAGE) {
            AsyncImage(
                model = Uri.parse(value),
                contentDescription = family.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
        } else {
            val bitmap by remember(value) { mutableStateOf(getVideoThumbnail(context, Uri.parse(value))) }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = family.name,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxSize()
                )
            }
        }
    }
}

/** Item that is not attached yet: only its name, same footprint as an attached bubble. */
@Composable
private fun AvailableItemBubble(family: ItemFamily, onClick: () -> Unit) {
    val color = itemTypeColor(family.itemType)
    Row(
        modifier = Modifier
            .widthIn(max = 168.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(DarkSurface)
            .border(1.dp, color.copy(alpha = 0.35f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarqueeText(
            text = AnnotatedString(family.name),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
}

/** Family row in the "Items" manager: name only, tap = edit, long-press = info/delete menu. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun FamilyBubble(
    family: ItemFamily,
    onClick: () -> Unit,
    onLongClick: () -> Unit
) {
    val color = itemTypeColor(family.itemType)
    Row(
        modifier = Modifier
            .widthIn(max = 168.dp)
            .height(40.dp)
            .clip(RoundedCornerShape(14.dp))
            .background(color.copy(alpha = 0.12f))
            .border(1.dp, color.copy(alpha = 0.5f), RoundedCornerShape(14.dp))
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .padding(horizontal = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        MarqueeText(
            text = AnnotatedString(family.name),
            style = TextStyle(fontSize = 13.sp, fontWeight = FontWeight.SemiBold, color = TextPrimary),
            modifier = Modifier.widthIn(max = 140.dp)
        )
    }
}

@Composable
private fun HangedItemRow(
    item: Item,
    family: ItemFamily,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onViewImageRequest: () -> Unit,
    onPlayVideoRequest: () -> Unit
) {
    val context = LocalContext.current

    when (family.itemType) {
        ItemType.IMAGE, ItemType.VIDEO -> {
            Box(
                modifier = Modifier
                    .width(120.dp)
                    .height(64.dp)
                    .padding(horizontal = 4.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(DarkSurface)
                    .clickable(onClick = if (family.itemType == ItemType.IMAGE) onViewImageRequest else onPlayVideoRequest)
            ) {
                if (family.itemType == ItemType.IMAGE) {
                    AsyncImage(
                        model = Uri.parse(item.value),
                        contentDescription = family.name,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize()
                    )
                } else { 
                    val bitmap by remember(item.value) {
                        mutableStateOf(getVideoThumbnail(context, Uri.parse(item.value)))
                    }
                    bitmap?.let {
                        Image(
                            bitmap = it.asImageBitmap(),
                            contentDescription = family.name,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize()
                        )
                    }
                }
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(
                            brush = Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.8f)),
                            )
                        )
                )
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .padding(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = if (family.itemType == ItemType.IMAGE) Icons.Default.RemoveRedEye else Icons.Default.PlayCircle,
                        contentDescription = null,
                        tint = Color.White.copy(alpha = 0.9f),
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    MarqueeText(
                        text = family.name,
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 12.sp
                    )
                }
                Box(modifier = Modifier.align(Alignment.TopEnd)) {
                    var menuExpanded by remember { mutableStateOf(false) }
                    IconButton(onClick = { menuExpanded = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.MoreVert, stringResource(R.string.entity_row_options_desc), tint = Color.White, modifier = Modifier.size(18.dp))
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { onEdit(); menuExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = AccentRed) }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
        }
        else -> {
            StyledItemChip(
                onEdit = onEdit,
                onDelete = onDelete,
                onClick = {} 
            ) {
                Text(
                    text = family.name,
                    color = TextPrimary,
                    style = MaterialTheme.typography.labelLarge,
                )
                Text(
                    text = ": ${item.value}",
                    color = TextMuted,
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}


@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun SelectFamilyContent(
    families: List<ItemFamily>,
    onFamilySelected: (ItemFamily) -> Unit
) {
    val groupedFamilies = families.groupBy { it.itemType }.toSortedMap(compareBy { it.name })

    LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
        groupedFamilies.forEach { (itemType, families) ->
            stickyHeader {
                Text(
                    text = itemType.name,
                    fontWeight = FontWeight.Bold,
                    color = AccentOrange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(DarkSurfaceAlt)
                        .padding(vertical = 8.dp)
                )
            }
            item {
                LazyRow(
                    modifier = Modifier.fillMaxWidth(),
                    contentPadding = PaddingValues(vertical = 4.dp)
                ) {
                    items(families, key = { it.id }) { family ->
                        ItemFamilyChip(
                            family = family,
                            onShowInfo = { onFamilySelected(family) },
                            onEdit = {},
                            onDelete = {},
                            showMenu = false
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateItemContent(
    family: ItemFamily,
    value: String,
    onValueChange: (String) -> Unit,
    onImagePickerClick: () -> Unit,
    onVideoPickerClick: () -> Unit
) {
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text(text = family.description, color = TextMuted, fontSize = 14.sp)

        when (family.itemType) {
            ItemType.STRING, ItemType.INT, ItemType.FLOAT -> {
                OutlinedTextField(
                    value = value,
                    onValueChange = onValueChange,
                    label = { Text(family.name) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                    isError = !isHangedValueValid(value, family.itemType),
                    keyboardOptions = KeyboardOptions(
                        keyboardType = when (family.itemType) {
                            ItemType.INT -> KeyboardType.Number
                            ItemType.FLOAT -> KeyboardType.Decimal
                            else -> KeyboardType.Text
                        }
                    ),
                    colors = OutlinedTextFieldDefaults.colors(
                        unfocusedBorderColor = TextMuted, focusedBorderColor = AccentOrange,
                        unfocusedLabelColor = TextMuted, focusedLabelColor = AccentOrange,
                        cursorColor = AccentOrange,
                        focusedTextColor = TextPrimary,
                        unfocusedTextColor = TextPrimary,
                        errorBorderColor = AccentRed,
                        errorLabelColor = AccentRed,
                        
                        errorTextColor = TextPrimary
                    )
                )
            }
            ItemType.BOOLEAN -> {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(MaterialTheme.shapes.small)
                        .clickable { onValueChange((!value.toBoolean()).toString()) }
                        .padding(vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Checkbox(
                        checked = value.toBoolean(),
                        onCheckedChange = { onValueChange(it.toString()) },
                        colors = CheckboxDefaults.colors(
                            checkedColor = AccentOrange,
                            uncheckedColor = TextMuted,
                            checkmarkColor = DarkBackground
                        )
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(text = family.name, color = TextPrimary)
                }
            }
            ItemType.IMAGE -> {
                MediaPickerButton(
                    value = value,
                    buttonText = stringResource(id = R.string.select_image),
                    onClick = onImagePickerClick
                )
            }
            ItemType.VIDEO -> {
                MediaPickerButton(
                    value = value,
                    buttonText = stringResource(id = R.string.select_video),
                    onClick = onVideoPickerClick
                )
            }
        }
    }
}


private fun getVideoThumbnail(context: Context, uri: Uri): Bitmap? {
    return try {
        val retriever = MediaMetadataRetriever()
        retriever.setDataSource(context, uri)
        val bitmap = retriever.getFrameAtTime(1_000_000, MediaMetadataRetriever.OPTION_CLOSEST_SYNC)
        retriever.release()
        bitmap
    } catch (e: Exception) {
        e.printStackTrace()
        null
    }
}






@Composable
private fun ItemFamilyChip(
    family: ItemFamily,
    onShowInfo: () -> Unit,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    showMenu: Boolean = true
) {
    StyledItemChip(
        onEdit = onEdit,
        onDelete = onDelete,
        onClick = onShowInfo,
        showMenu = showMenu
    ) {
        Text(
            text = family.name,
            color = TextPrimary,
            style = MaterialTheme.typography.labelLarge,
        )
    }
}

@Composable
private fun StyledItemChip(
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    onClick: () -> Unit,
    showMenu: Boolean = true,
    content: @Composable RowScope.() -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = DarkSurface,
        border = BorderStroke(1.dp, AccentOrange),
        modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp)
    ) {
        Row(
            modifier = Modifier.clickable(onClick = onClick),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                modifier = Modifier
                    .padding(start = 12.dp, top = 8.dp, bottom = 8.dp)
                    .padding(end = if (showMenu) 6.dp else 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                content = content
            )

            if (showMenu) {
                Box {
                    IconButton(
                        onClick = { menuExpanded = true },
                        modifier = Modifier.size(24.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            stringResource(R.string.entity_row_options_desc),
                            tint = TextMuted,
                            modifier = Modifier.size(18.dp)
                        )
                    }
                    DropdownMenu(
                        expanded = menuExpanded,
                        onDismissRequest = { menuExpanded = false },
                    ) {
                        DropdownMenuItem(text = { Text(stringResource(R.string.edit)) }, onClick = { onEdit(); menuExpanded = false })
                        DropdownMenuItem(text = { Text(stringResource(R.string.delete), color = AccentRed) }, onClick = { onDelete(); menuExpanded = false })
                    }
                }
            }
        }
    }
}

@Composable
private fun MediaPickerButton(
    value: String,
    buttonText: String,
    onClick: () -> Unit
) {
    val strNoFile = stringResource(id = R.string.no_file_selected)

    Column {
        MarqueeText(
            text = value.takeIf { it.isNotBlank() }
                ?.let { Uri.parse(it).lastPathSegment ?: strNoFile }
                ?: strNoFile,
            color = TextMuted,
            fontSize = 12.sp
        )
        Spacer(modifier = Modifier.heightIn(4.dp))
        Button(
            onClick = onClick,
            colors = ButtonDefaults.buttonColors(containerColor = AccentOrange)
        ) {
            Icon(
                imageVector = Icons.Default.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(18.dp)
            )
            Spacer(modifier = Modifier.width(8.dp))
            Text(buttonText)
        }
    }
}

@Composable
private fun ImagePreviewDialog(uri: Uri, onDismiss: () -> Unit) {
    Dialog(onDismissRequest = onDismiss) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.8f))
                .clickable { onDismiss() },
            contentAlignment = Alignment.Center
        ) {
            AsyncImage(
                model = uri,
                contentDescription = stringResource(R.string.view_image),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .clip(RoundedCornerShape(12.dp))
            )
        }
    }
}

@Composable
private fun VideoPlayerDialog(uri: Uri, onDismiss: () -> Unit) {
    val context = LocalContext.current

    val exoPlayer = remember {
        ExoPlayer.Builder(context).build().apply {
            val mediaItem = MediaItem.fromUri(uri)
            setMediaItem(mediaItem)
            playWhenReady = true
            prepare()
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(onDismissRequest = onDismiss) {
        Box(modifier = Modifier.background(Color.Black)) {
            AndroidView(
                factory = {
                    PlayerView(it).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(16 / 9f)
            )
            IconButton(onClick = onDismiss, modifier = Modifier.align(Alignment.TopEnd)) {
                Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.White)
            }
        }
    }
}
