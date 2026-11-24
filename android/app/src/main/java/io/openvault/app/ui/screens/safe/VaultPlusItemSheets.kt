@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package io.openvault.app.ui.screens.safe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Chat
import androidx.compose.material.icons.automirrored.rounded.DriveFileMove
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Badge
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.CreateNewFolder
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.DirectionsCar
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.DriveFileRenameOutline
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material.icons.rounded.Flight
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.LocalHospital
import androidx.compose.material.icons.rounded.Payments
import androidx.compose.material.icons.rounded.PictureAsPdf
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalBottomSheetProperties
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.SecureFlagPolicy
import io.openvault.app.core.crypto.SafeDocumentCategory
import io.openvault.app.data.DecryptedSafeItem
import io.openvault.app.data.SafeRepository
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import io.openvault.app.ui.theme.formatLocalTimestamp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * Floating bottom-anchored Metadata Sheet used when uploading a new Vault+ file or editing an existing one.
 */
@Composable
fun SafeMetadataBottomSheet(
    isUploadMode: Boolean,
    fileName: String,
    mimeType: String,
    sizeBytes: Long,
    previewRawBytes: ByteArray? = null,
    initialTitle: String,
    initialCategory: SafeDocumentCategory,
    initialFolderName: String?,
    initialNotes: String,
    initialExpiresAt: String,
    availableFolders: List<String>,
    onCreateFolderRequest: ((String) -> Unit)? = null,
    onDismiss: () -> Unit,
    onSave: (title: String, category: SafeDocumentCategory, folderName: String?, notes: String?, expiresAt: String?) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    var title by remember { mutableStateOf(initialTitle) }
    var category by remember { mutableStateOf(initialCategory) }
    var selectedFolder by remember { mutableStateOf(initialFolderName?.takeIf { it.isNotBlank() }) }
    var expiresAt by remember { mutableStateOf(initialExpiresAt) }
    var notes by remember { mutableStateOf(initialNotes) }
    var showInlineFolderInput by remember { mutableStateOf(false) }
    var newFolderText by remember { mutableStateOf("") }

    var previewBitmap by remember(previewRawBytes) { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(previewRawBytes, mimeType) {
        if (previewRawBytes != null && mimeType.startsWith("image/")) {
            val bmp = withContext(Dispatchers.Default) {
                val opts = BitmapFactory.Options().apply { inSampleSize = 4 }
                BitmapFactory.decodeByteArray(previewRawBytes, 0, previewRawBytes.size, opts)?.asImageBitmap()
            }
            if (bmp != null) previewBitmap = bmp
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            // File preview + sheet header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp)
                    .padding(bottom = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(
                            if (mimeType == "application/pdf") {
                                if (c.isDark) Color(0xFF33181D) else Color(0xFFFEE2E2)
                            } else {
                                c.accentSoft
                            },
                        ),
                    contentAlignment = Alignment.Center,
                ) {
                    when {
                        previewBitmap != null -> Image(
                            bitmap = previewBitmap!!,
                            contentDescription = null,
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxSize(),
                        )

                        mimeType == "application/pdf" -> Icon(
                            imageVector = Icons.Rounded.PictureAsPdf,
                            contentDescription = null,
                            tint = Color(0xFFEF4444),
                            modifier = Modifier.size(26.dp),
                        )

                        else -> Icon(
                            imageVector = metaCategoryIcon(category),
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(25.dp),
                        )
                    }
                }

                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                    Text(
                        text = if (isUploadMode) {
                            if (s.isVi) "Thông Tin Tài Liệu Mới" else "New Document Details"
                        } else {
                            if (s.isVi) "Chỉnh Sửa Tài Liệu" else "Edit Document"
                        },
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = "${fileName} · ${formatBytes(sizeBytes)}",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }

                Surface(
                    onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                    shape = CircleShape,
                    color = c.surface,
                    modifier = Modifier.size(34.dp),
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textSecondary, modifier = Modifier.size(18.dp))
                    }
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (s.isVi) "Tên hiển thị" else "Display Name") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                // Visual Category Chips
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (s.isVi) "Phân loại" else "Category",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(SafeDocumentCategory.entries) { cat ->
                            val selected = category == cat
                            Surface(
                                onClick = { category = cat },
                                shape = CircleShape,
                                color = if (selected) c.accentButtonBg else c.surface,
                                border = if (selected) null else BorderStroke(0.9.dp, c.cardBorder),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                                ) {
                                    Icon(
                                        imageVector = metaCategoryIcon(cat),
                                        contentDescription = null,
                                        tint = if (selected) c.accentButtonFg else c.accent,
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = categoryLabel(cat, s.isVi),
                                        color = if (selected) c.accentButtonFg else c.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }
                }

                // Folder Chips + Inline Create New Folder
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (s.isVi) "Thư mục lưu trữ" else "Folder",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    val combinedFolders = remember(availableFolders, selectedFolder) {
                        if (selectedFolder != null && selectedFolder !in availableFolders) {
                            availableFolders + selectedFolder!!
                        } else {
                            availableFolders
                        }
                    }
                    LazyRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        item {
                            val rootSelected = selectedFolder == null
                            Surface(
                                onClick = { selectedFolder = null },
                                shape = CircleShape,
                                color = if (rootSelected) c.accentButtonBg else c.surface,
                                border = if (rootSelected) null else BorderStroke(0.9.dp, c.cardBorder),
                            ) {
                                Text(
                                    text = if (s.isVi) "Thư mục gốc" else "Root",
                                    color = if (rootSelected) c.accentButtonFg else c.textPrimary,
                                    fontSize = 12.5.sp,
                                    fontWeight = if (rootSelected) FontWeight.SemiBold else FontWeight.Medium,
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                )
                            }
                        }
                        items(combinedFolders) { folder ->
                            val active = selectedFolder == folder
                            Surface(
                                onClick = { selectedFolder = folder },
                                shape = CircleShape,
                                color = if (active) c.accentButtonBg else c.surface,
                                border = if (active) null else BorderStroke(0.9.dp, c.cardBorder),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                                ) {
                                    Icon(
                                        imageVector = Icons.Rounded.Folder,
                                        contentDescription = null,
                                        tint = if (active) c.accentButtonFg else Color(0xFFF59E0B),
                                        modifier = Modifier.size(16.dp),
                                    )
                                    Text(
                                        text = folder,
                                        color = if (active) c.accentButtonFg else c.textPrimary,
                                        fontSize = 12.5.sp,
                                        fontWeight = if (active) FontWeight.SemiBold else FontWeight.Medium,
                                    )
                                }
                            }
                        }
                        item {
                            Surface(
                                onClick = { showInlineFolderInput = !showInlineFolderInput },
                                shape = CircleShape,
                                color = c.accentSoft,
                                border = BorderStroke(0.9.dp, c.accent.copy(alpha = 0.25f)),
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 11.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.accent, modifier = Modifier.size(15.dp))
                                    Text(
                                        text = if (s.isVi) "Tạo mới" else "New",
                                        color = c.accent,
                                        fontSize = 12.5.sp,
                                        fontWeight = FontWeight.SemiBold,
                                    )
                                }
                            }
                        }
                    }

                    if (showInlineFolderInput) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            OutlinedTextField(
                                value = newFolderText,
                                onValueChange = { newFolderText = it },
                                placeholder = { Text(if (s.isVi) "Tên thư mục mới..." else "New folder name...") },
                                singleLine = true,
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.weight(1f),
                            )
                            Button(
                                enabled = newFolderText.trim().isNotEmpty(),
                                shape = RoundedCornerShape(12.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg),
                                onClick = {
                                    val clean = newFolderText.trim()
                                    onCreateFolderRequest?.invoke(clean)
                                    selectedFolder = clean
                                    newFolderText = ""
                                    showInlineFolderInput = false
                                },
                            ) {
                                Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                            }
                        }
                    }
                }

                OutlinedTextField(
                    value = expiresAt,
                    onValueChange = { expiresAt = it },
                    label = { Text(if (s.isVi) "Ngày hết hạn (YYYY-MM-DD, tùy chọn)" else "Expiry Date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (s.isVi) "Ghi chú bảo mật" else "Encrypted Notes") },
                    minLines = 2,
                    maxLines = 4,
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                )

                Button(
                    enabled = title.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accentButtonBg,
                        contentColor = c.accentButtonFg,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp),
                    onClick = {
                        val finalTitle = title.trim()
                        val finalFolder = selectedFolder
                        val finalNotes = notes
                        val finalExp = expiresAt
                        scope.launch {
                            sheetState.hide()
                            onSave(finalTitle, category, finalFolder, finalNotes, finalExp)
                        }
                    },
                ) {
                    Icon(Icons.Rounded.EnhancedEncryption, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = if (isUploadMode) {
                            if (s.isVi) "Mã Hóa & Lưu Vào Vault+" else "Encrypt & Save to Vault+"
                        } else {
                            if (s.isVi) "Lưu Thay Đổi" else "Save Changes"
                        },
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * OneDrive-inspired Item Action ModalBottomSheet triggered from "..." on any document item.
 */
@Composable
fun SafeItemActionsBottomSheet(
    item: DecryptedSafeItem,
    thumbBitmap: ImageBitmap?,
    isOfflineCached: Boolean,
    onDismiss: () -> Unit,
    onDeleteOrTrash: () -> Unit,
    onRestore: () -> Unit,
    onDownloadExport: () -> Unit,
    onToggleOffline: (Boolean) -> Unit,
    onToggleFavorite: () -> Unit,
    onRenameEdit: () -> Unit,
    onMoveFolder: () -> Unit,
    onOpenNotes: () -> Unit,
    onOpenDetails: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val inTrash = item.deletedAt != null

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            // Centered Preview Icon / Thumbnail + Title
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(c.surfaceElevated),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    thumbBitmap != null -> Image(
                        bitmap = thumbBitmap,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize(),
                    )

                    item.metadata.mimeType == "application/pdf" -> Icon(
                        imageVector = Icons.Rounded.PictureAsPdf,
                        contentDescription = null,
                        tint = Color(0xFFEF4444),
                        modifier = Modifier.size(34.dp),
                    )

                    else -> Icon(
                        imageVector = metaCategoryIcon(item.category),
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(34.dp),
                    )
                }
            }

            Spacer(Modifier.height(10.dp))

            Text(
                text = item.metadata.title,
                color = c.textPrimary,
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                textAlign = TextAlign.Center,
            )

            Spacer(Modifier.height(3.dp))

            Text(
                text = if (inTrash) {
                    if (s.isVi) "Trong thùng rác · ${formatBytes(item.metadata.originalSize.toLong())}"
                    else "In Trash · ${formatBytes(item.metadata.originalSize.toLong())}"
                } else {
                    if (s.isVi) "Đã mở khóa · ${formatBytes(item.metadata.originalSize.toLong())}"
                    else "Unlocked · ${formatBytes(item.metadata.originalSize.toLong())}"
                },
                color = c.textSecondary,
                fontSize = 12.5.sp,
            )

            Spacer(Modifier.height(16.dp))

            // 2 Prominent Quick Action Tiles: Xóa | Tải xuống (or Khôi phục)
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Surface(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onDeleteOrTrash()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = c.surfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .height(82.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (inTrash) Icons.Rounded.DeleteForever else Icons.Rounded.DeleteOutline,
                            contentDescription = null,
                            tint = if (inTrash) c.danger else c.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (inTrash) s.deletePermanently else s.delete,
                            color = if (inTrash) c.danger else c.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }

                Surface(
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            if (inTrash) onRestore() else onDownloadExport()
                        }
                    },
                    shape = RoundedCornerShape(16.dp),
                    color = c.surfaceElevated,
                    modifier = Modifier
                        .weight(1f)
                        .height(82.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxSize(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.Center,
                    ) {
                        Icon(
                            imageVector = if (inTrash) Icons.Rounded.RestoreFromTrash else Icons.Rounded.Download,
                            contentDescription = null,
                            tint = c.textPrimary,
                            modifier = Modifier.size(24.dp),
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            text = if (inTrash) s.restore else if (s.isVi) "Tải xuống" else "Download",
                            color = c.textPrimary,
                            fontSize = 13.5.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }

            Spacer(Modifier.height(10.dp))

            // Switch Row: Làm có sẵn ngoại tuyến
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable { onToggleOffline(!isOfflineCached) }
                    .padding(horizontal = 6.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = if (isOfflineCached) Icons.Rounded.CloudDone else Icons.Rounded.CloudDownload,
                    contentDescription = null,
                    tint = if (isOfflineCached) c.accent else c.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = if (s.isVi) "Làm có sẵn ngoại tuyến" else "Make available offline",
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = isOfflineCached,
                    onCheckedChange = onToggleOffline,
                )
            }

            // Switch Row: Yêu thích
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable(onClick = onToggleFavorite)
                    .padding(horizontal = 6.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(
                    imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = null,
                    tint = if (item.favorite) Color(0xFFF59E0B) else c.textPrimary,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    text = s.favorites,
                    color = c.textPrimary,
                    fontSize = 15.sp,
                    modifier = Modifier.weight(1f),
                )
                Switch(
                    checked = item.favorite,
                    onCheckedChange = { onToggleFavorite() },
                )
            }

            if (!inTrash) {
                SafeActionListRow(
                    icon = Icons.Rounded.DriveFileRenameOutline,
                    label = if (s.isVi) "Đổi tên" else "Rename",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onRenameEdit()
                        }
                    },
                )

                SafeActionListRow(
                    icon = Icons.AutoMirrored.Rounded.DriveFileMove,
                    label = if (s.isVi) "Di chuyển" else "Move to folder",
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onMoveFolder()
                        }
                    },
                )

                SafeActionListRow(
                    icon = Icons.AutoMirrored.Rounded.Chat,
                    label = s.notes,
                    onClick = {
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onOpenNotes()
                        }
                    },
                )
            }

            SafeActionListRow(
                icon = Icons.Rounded.Info,
                label = if (s.isVi) "Chi tiết" else "Details",
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                        onOpenDetails()
                    }
                },
            )
        }
    }
}

@Composable
private fun SafeActionListRow(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = c.textPrimary,
            modifier = Modifier.size(22.dp),
        )
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = 15.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}

/**
 * Floating bottom-anchored Create or Rename Folder sheet.
 */
@Composable
fun SafeCreateFolderBottomSheet(
    initialName: String = "",
    isRename: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var name by remember { mutableStateOf(initialName) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(42.dp)
                        .clip(RoundedCornerShape(13.dp))
                        .background(if (c.isDark) Color(0xFF2D2310) else Color(0xFFFEF3C7)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Rounded.CreateNewFolder,
                        contentDescription = null,
                        tint = Color(0xFFF59E0B),
                        modifier = Modifier.size(22.dp),
                    )
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (isRename) {
                            if (s.isVi) "Đổi tên thư mục" else "Rename Folder"
                        } else {
                            if (s.isVi) "Tạo thư mục mới" else "Create New Folder"
                        },
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Text(
                        text = if (s.isVi) "Sắp xếp tài liệu gọn gàng trong Vault+" else "Keep your Vault+ files organized",
                        color = c.textSecondary,
                        fontSize = 12.5.sp,
                    )
                }
            }

            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(if (s.isVi) "Tên thư mục" else "Folder Name") },
                singleLine = true,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedButton(
                    onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1f)
                        .height(48.dp),
                ) {
                    Text(s.cancel)
                }
                Button(
                    enabled = name.trim().isNotEmpty(),
                    colors = ButtonDefaults.buttonColors(
                        containerColor = c.accentButtonBg,
                        contentColor = c.accentButtonFg,
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .weight(1.3f)
                        .height(48.dp),
                    onClick = {
                        val clean = name.trim()
                        scope.launch {
                            sheetState.hide()
                            onDismiss()
                            onConfirm(clean)
                        }
                    },
                ) {
                    Text(
                        text = if (isRename) s.save else s.create,
                        fontWeight = FontWeight.SemiBold,
                    )
                }
            }
        }
    }
}

/**
 * Floating bottom-anchored Folder actions sheet for "..." on a folder item.
 */
@Composable
fun SafeFolderActionsBottomSheet(
    folderName: String,
    itemCount: Int,
    onDismiss: () -> Unit,
    onOpen: () -> Unit,
    onRename: () -> Unit,
    onDeleteFolderOnly: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 22.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Box(
                modifier = Modifier
                    .size(58.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(if (c.isDark) Color(0xFF2D2310) else Color(0xFFFEF3C7)),
                contentAlignment = Alignment.Center,
            ) {
                Icon(Icons.Rounded.Folder, contentDescription = null, tint = Color(0xFFF59E0B), modifier = Modifier.size(34.dp))
            }
            Spacer(Modifier.height(10.dp))
            Text(folderName, color = c.textPrimary, fontSize = 17.sp, fontWeight = FontWeight.Bold)
            Text(
                text = if (s.isVi) "$itemCount tài liệu" else "$itemCount documents",
                color = c.textSecondary,
                fontSize = 12.5.sp,
            )
            Spacer(Modifier.height(14.dp))

            SafeActionListRow(
                icon = Icons.Rounded.Folder,
                label = if (s.isVi) "Mở thư mục" else "Open folder",
                onClick = {
                    scope.launch { sheetState.hide(); onDismiss(); onOpen() }
                },
            )
            SafeActionListRow(
                icon = Icons.Rounded.DriveFileRenameOutline,
                label = if (s.isVi) "Đổi tên thư mục" else "Rename folder",
                onClick = {
                    scope.launch { sheetState.hide(); onDismiss(); onRename() }
                },
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(12.dp))
                    .clickable {
                        scope.launch { sheetState.hide(); onDismiss(); onDeleteFolderOnly() }
                    }
                    .padding(horizontal = 6.dp, vertical = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                Icon(Icons.Rounded.DeleteOutline, contentDescription = null, tint = c.danger, modifier = Modifier.size(22.dp))
                Column {
                    Text(
                        text = if (s.isVi) "Xóa thư mục" else "Delete folder",
                        color = c.danger,
                        fontSize = 15.sp,
                    )
                    Text(
                        text = if (s.isVi) "Các tệp bên trong vẫn được giữ an toàn ở thư mục gốc" else "Files inside are kept safely in Root",
                        color = c.textSecondary,
                        fontSize = 12.sp,
                    )
                }
            }
        }
    }
}

/**
 * Floating bottom-anchored Move Item To Folder sheet.
 */
@Composable
fun SafeMoveFolderBottomSheet(
    itemTitle: String,
    currentFolder: String?,
    folders: List<String>,
    onDismiss: () -> Unit,
    onCreateAndMove: (String) -> Unit,
    onSelectFolder: (String?) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var showNewInput by remember { mutableStateOf(false) }
    var newName by remember { mutableStateOf("") }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = if (s.isVi) "Di chuyển \"$itemTitle\"" else "Move \"$itemTitle\"",
                color = c.textPrimary,
                fontSize = 17.5.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = if (s.isVi) "Chọn thư mục đích" else "Choose destination folder",
                color = c.textSecondary,
                fontSize = 12.5.sp,
            )
            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            // Root Folder option
            SafeMoveTargetRow(
                label = if (s.isVi) "Thư mục gốc (Vault+)" else "Root (Vault+)",
                icon = Icons.Rounded.EnhancedEncryption,
                iconTint = c.accent,
                selected = currentFolder.isNullOrBlank(),
                onClick = {
                    scope.launch { sheetState.hide(); onDismiss(); onSelectFolder(null) }
                },
            )

            folders.forEach { folder ->
                SafeMoveTargetRow(
                    label = folder,
                    icon = Icons.Rounded.Folder,
                    iconTint = Color(0xFFF59E0B),
                    selected = currentFolder == folder,
                    onClick = {
                        scope.launch { sheetState.hide(); onDismiss(); onSelectFolder(folder) }
                    },
                )
            }

            if (showNewInput) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        placeholder = { Text(if (s.isVi) "Tên thư mục mới..." else "New folder name...") },
                        singleLine = true,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.weight(1f),
                    )
                    Button(
                        enabled = newName.trim().isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg),
                        shape = RoundedCornerShape(12.dp),
                        onClick = {
                            val clean = newName.trim()
                            scope.launch { sheetState.hide(); onDismiss(); onCreateAndMove(clean) }
                        },
                    ) {
                        Text(s.create)
                    }
                }
            } else {
                OutlinedButton(
                    onClick = { showNewInput = true },
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Icon(Icons.Rounded.CreateNewFolder, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text(if (s.isVi) "Tạo thư mục mới & Di chuyển" else "New Folder & Move")
                }
            }
        }
    }
}

@Composable
private fun SafeMoveTargetRow(
    label: String,
    icon: ImageVector,
    iconTint: Color,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) c.accentSoft else c.surface,
        border = BorderStroke(1.dp, if (selected) c.accent.copy(alpha = 0.45f) else c.cardBorder),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(icon, contentDescription = null, tint = iconTint, modifier = Modifier.size(21.dp))
            Text(
                text = label,
                color = c.textPrimary,
                fontSize = 14.5.sp,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal,
                modifier = Modifier.weight(1f),
            )
            if (selected) {
                Icon(Icons.Rounded.Check, contentDescription = null, tint = c.accent, modifier = Modifier.size(19.dp))
            }
        }
    }
}

/**
 * Floating bottom-anchored Quick Note viewer/editor sheet.
 */
@Composable
fun SafeQuickNoteBottomSheet(
    item: DecryptedSafeItem,
    onDismiss: () -> Unit,
    onSaveNotes: (String?) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var notesText by remember { mutableStateOf(item.metadata.notes.orEmpty()) }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Text(
                text = "${s.notes} · ${item.metadata.title}",
                color = c.textPrimary,
                fontSize = 17.sp,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            OutlinedTextField(
                value = notesText,
                onValueChange = { notesText = it },
                label = { Text(if (s.isVi) "Nội dung ghi chú (đã mã hóa)" else "Encrypted Note Content") },
                minLines = 3,
                maxLines = 7,
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            )
            Button(
                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = c.accentButtonFg),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(48.dp),
                onClick = {
                    val clean = notesText
                    scope.launch { sheetState.hide(); onDismiss(); onSaveNotes(clean) }
                },
            ) {
                Text(s.save, fontWeight = FontWeight.SemiBold)
            }
        }
    }
}

/**
 * Floating bottom-anchored Item Cryptographic & Metadata Details sheet.
 */
@Composable
fun SafeItemDetailsBottomSheet(
    item: DecryptedSafeItem,
    isOfflineCached: Boolean,
    onDismiss: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
        containerColor = c.sheetBackground,
        scrimColor = Color.Black.copy(alpha = 0.62f),
        dragHandle = { BottomSheetDefaults.DragHandle(color = c.textMuted.copy(alpha = 0.45f)) },
        contentWindowInsets = { WindowInsets(0, 0, 0, 0) },
        properties = ModalBottomSheetProperties(
            securePolicy = SecureFlagPolicy.Inherit,
            shouldDismissOnBackPress = true,
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = if (s.isVi) "Chi Tiết Tài Liệu" else "Document Details",
                    color = c.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Surface(shape = CircleShape, color = c.accentSoft) {
                    Text(
                        text = "OVSF v1 · AES-256-GCM",
                        color = c.accent,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    )
                }
            }

            HorizontalDivider(color = c.divider.copy(alpha = 0.7f))

            Surface(
                shape = RoundedCornerShape(16.dp),
                color = c.surface,
                border = BorderStroke(0.9.dp, c.cardBorder),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    DetailFieldRow(if (s.isVi) "Tên hiển thị" else "Title", item.metadata.title)
                    DetailFieldRow(if (s.isVi) "Tên tệp gốc" else "Original File", item.metadata.fileName)
                    DetailFieldRow(if (s.isVi) "Loại tài liệu" else "Category", categoryLabel(item.category, s.isVi))
                    DetailFieldRow(
                        if (s.isVi) "Thư mục" else "Folder",
                        item.metadata.folderName?.takeIf { it.isNotBlank() }
                            ?: if (s.isVi) "Thư mục gốc" else "Root",
                    )
                    DetailFieldRow(
                        if (s.isVi) "Dung lượng gốc" else "Original Size",
                        formatBytes(item.metadata.originalSize.toLong()),
                    )
                    DetailFieldRow("MIME Type", item.metadata.mimeType)
                    DetailFieldRow(
                        if (s.isVi) "Ngoại tuyến" else "Offline Cache",
                        if (isOfflineCached) {
                            if (s.isVi) "Đã tải sẵn trên máy (đã mã hóa)" else "Cached offline (encrypted)"
                        } else {
                            if (s.isVi) "Chỉ lưu trên đám mây" else "Cloud only"
                        },
                    )
                    if (!item.metadata.expiresAt.isNullOrBlank()) {
                        DetailFieldRow(if (s.isVi) "Ngày hết hạn" else "Expires", item.metadata.expiresAt)
                    }
                    if (!item.createdAt.isNullOrBlank()) {
                        DetailFieldRow(if (s.isVi) "Ngày tạo" else "Created", formatLocalTimestamp(item.createdAt, s.isVi))
                    }
                    if (!item.updatedAt.isNullOrBlank()) {
                        DetailFieldRow(if (s.isVi) "Cập nhật" else "Modified", formatLocalTimestamp(item.updatedAt, s.isVi))
                    }
                    Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                        Text("SHA-256", color = c.textMuted, fontSize = 11.5.sp, fontWeight = FontWeight.Medium)
                        Text(
                            text = item.metadata.originalSha256,
                            color = c.textSecondary,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                        )
                    }
                }
            }

            OutlinedButton(
                onClick = { scope.launch { sheetState.hide(); onDismiss() } },
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(s.close)
            }
        }
    }
}

@Composable
private fun DetailFieldRow(label: String, value: String) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top,
    ) {
        Text(label, color = c.textSecondary, fontSize = 12.5.sp, modifier = Modifier.weight(0.38f))
        Text(
            text = value,
            color = c.textPrimary,
            fontSize = 13.sp,
            fontWeight = FontWeight.Medium,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(0.62f),
        )
    }
}

internal fun metaCategoryIcon(category: SafeDocumentCategory): ImageVector = when (category) {
    SafeDocumentCategory.ID_CARD -> Icons.Rounded.Badge
    SafeDocumentCategory.PASSPORT -> Icons.Rounded.Flight
    SafeDocumentCategory.DRIVER_LICENSE -> Icons.Rounded.DirectionsCar
    SafeDocumentCategory.MEDICAL -> Icons.Rounded.LocalHospital
    SafeDocumentCategory.FINANCIAL -> Icons.Rounded.Payments
    SafeDocumentCategory.PHOTO -> Icons.Rounded.Image
    SafeDocumentCategory.OTHER -> Icons.Rounded.Description
}
