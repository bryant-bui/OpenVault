package io.openvault.app.ui.screens.safe

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.NavigateBefore
import androidx.compose.material.icons.automirrored.rounded.NavigateNext
import androidx.compose.material.icons.rounded.DeleteForever
import androidx.compose.material.icons.rounded.DeleteOutline
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.RestoreFromTrash
import androidx.compose.material.icons.rounded.Star
import androidx.compose.material.icons.rounded.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.SafeDocumentCategory
import io.openvault.app.data.DecryptedSafeItem
import io.openvault.app.data.SafeRepository
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun VaultPlusViewerScreen(
    item: DecryptedSafeItem,
    safeRepository: SafeRepository,
    onBack: () -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var loading by remember(item.id) { mutableStateOf(true) }
    var errorMsg by remember(item.id) { mutableStateOf<String?>(null) }
    var decryptedBytes by remember(item.id) { mutableStateOf<ByteArray?>(null) }

    var imageBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var imageCompose by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }

    var pdfTempFile by remember(item.id) { mutableStateOf<File?>(null) }
    var pdfPfd by remember(item.id) { mutableStateOf<ParcelFileDescriptor?>(null) }
    var pdfRenderer by remember(item.id) { mutableStateOf<PdfRenderer?>(null) }
    var pdfPageCount by remember(item.id) { mutableIntStateOf(0) }
    var pdfPageIndex by remember(item.id) { mutableIntStateOf(0) }
    var pdfBitmap by remember(item.id) { mutableStateOf<Bitmap?>(null) }
    var pdfCompose by remember(item.id) { mutableStateOf<ImageBitmap?>(null) }

    var zoomScale by remember { mutableFloatStateOf(1f) }
    var panOffset by remember { mutableStateOf(Offset.Zero) }

    var showExportWarn by remember { mutableStateOf(false) }
    var showEditDialog by remember { mutableStateOf(false) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val exportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument(item.metadata.mimeType),
    ) { uri ->
        val data = decryptedBytes ?: return@rememberLauncherForActivityResult
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { out ->
                        out.write(data)
                    } ?: error("Không thể mở tệp đích.")
                }
            }.onSuccess {
                onShowMessage(if (s.isVi) "Đã xuất bản sao ra thiết bị." else "Exported decrypted copy.")
            }.onFailure {
                onShowMessage(it.message ?: "Export failed")
            }
        }
    }

    BackHandler(onBack = onBack)

    // Load and decrypt OVSF bytes strictly in RAM
    LaunchedEffect(item.id) {
        loading = true
        errorMsg = null
        try {
            val bytes = safeRepository.loadDocumentBytes(item)
            decryptedBytes = bytes
            if (item.metadata.mimeType == "application/pdf") {
                val tmp = withContext(Dispatchers.IO) { safeRepository.createTempPdfFile(bytes) }
                val pfd = ParcelFileDescriptor.open(tmp, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(pfd)
                pdfTempFile = tmp
                pdfPfd = pfd
                pdfRenderer = renderer
                pdfPageCount = renderer.pageCount
                pdfPageIndex = 0
            } else {
                val bmp = withContext(Dispatchers.Default) { decodeBoundedBitmap(bytes, 2048) }
                if (bmp == null) {
                    errorMsg = if (s.isVi) "Không thể giải mã định dạng ảnh này." else "Unsupported image format."
                } else {
                    imageBitmap = bmp
                    imageCompose = bmp.asImageBitmap()
                }
            }
        } catch (ex: Exception) {
            errorMsg = ex.message ?: if (s.isVi) "Không thể mở tài liệu." else "Failed to decrypt document."
        } finally {
            loading = false
        }
    }

    // Render active PDF page when pageIndex changes
    LaunchedEffect(pdfRenderer, pdfPageIndex) {
        val renderer = pdfRenderer ?: return@LaunchedEffect
        if (pdfPageCount <= 0 || pdfPageIndex !in 0 until pdfPageCount) return@LaunchedEffect
        val rendered = withContext(Dispatchers.Default) {
            renderer.openPage(pdfPageIndex).use { page ->
                val targetW = 1440
                val ratio = targetW.toFloat() / page.width.coerceAtLeast(1)
                val w = targetW
                val h = (page.height * ratio).toInt().coerceIn(1, 4096)
                val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
                Canvas(bmp).drawColor(android.graphics.Color.WHITE)
                page.render(bmp, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bmp
            }
        }
        val prev = pdfBitmap
        pdfBitmap = rendered
        pdfCompose = rendered.asImageBitmap()
        prev?.recycle()
        zoomScale = 1f
        panOffset = Offset.Zero
    }

    // Strict memory zeroization and temp PDF shredding on exit
    DisposableEffect(item.id) {
        onDispose {
            decryptedBytes?.fill(0)
            decryptedBytes = null
            imageCompose = null
            imageBitmap?.recycle()
            imageBitmap = null
            pdfCompose = null
            pdfBitmap?.recycle()
            pdfBitmap = null
            runCatching { pdfRenderer?.close() }
            runCatching { pdfPfd?.close() }
            safeRepository.shredTempFile(pdfTempFile)
            pdfRenderer = null
            pdfPfd = null
            pdfTempFile = null
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding(),
    ) {
        // Viewer Top Bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary)
            }
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = item.metadata.title,
                    color = c.textPrimary,
                    fontSize = 17.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = "${categoryLabel(item.category, s.isVi)} · ${formatBytes(item.metadata.originalSize.toLong())}",
                    color = c.textSecondary,
                    fontSize = 12.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            IconButton(
                onClick = {
                    scope.launch {
                        runCatching { safeRepository.toggleFavorite(item) }
                            .onFailure { onShowMessage(it.message ?: "Error") }
                    }
                },
            ) {
                Icon(
                    imageVector = if (item.favorite) Icons.Rounded.Star else Icons.Rounded.StarOutline,
                    contentDescription = null,
                    tint = if (item.favorite) Color(0xFFF59E0B) else c.textSecondary,
                )
            }
            IconButton(onClick = { showEditDialog = true }) {
                Icon(Icons.Rounded.Edit, contentDescription = s.edit, tint = c.textPrimary)
            }
            if (decryptedBytes != null) {
                IconButton(onClick = { showExportWarn = true }) {
                    Icon(Icons.Rounded.Download, contentDescription = "Export", tint = c.textPrimary)
                }
            }
            if (item.deletedAt != null) {
                IconButton(
                    onClick = {
                        scope.launch {
                            runCatching { safeRepository.restoreItem(item.id) }
                                .onSuccess {
                                    onShowMessage(if (s.isVi) "Đã khôi phục tài liệu." else "Document restored.")
                                    onBack()
                                }
                                .onFailure { onShowMessage(it.message ?: "Error") }
                        }
                    },
                ) {
                    Icon(Icons.Rounded.RestoreFromTrash, contentDescription = s.restore, tint = c.accent)
                }
            }
            IconButton(onClick = { showDeleteConfirm = true }) {
                Icon(
                    imageVector = if (item.deletedAt != null) Icons.Rounded.DeleteForever else Icons.Rounded.DeleteOutline,
                    contentDescription = s.delete,
                    tint = c.danger,
                )
            }
        }

        // Main Decrypted Viewer Canvas
        Box(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .background(if (c.isDark) Color(0xFF0A0B0E) else Color(0xFFF3F4F6))
                .pointerInput(item.id) {
                    detectTransformGestures { _, pan, zoom, _ ->
                        safeRepository.session.touchActivity()
                        val nextScale = (zoomScale * zoom).coerceIn(1f, 4f)
                        zoomScale = nextScale
                        panOffset = if (nextScale > 1.01f) {
                            Offset(panOffset.x + pan.x, panOffset.y + pan.y)
                        } else {
                            Offset.Zero
                        }
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            when {
                loading -> CircularProgressIndicator(color = c.accent)
                errorMsg != null -> Text(
                    text = errorMsg.orEmpty(),
                    color = c.danger,
                    fontSize = 14.sp,
                    modifier = Modifier.padding(24.dp),
                )

                item.metadata.mimeType == "application/pdf" && pdfCompose != null -> {
                    Image(
                        bitmap = pdfCompose!!,
                        contentDescription = item.metadata.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y,
                            ),
                    )
                }

                imageCompose != null -> {
                    Image(
                        bitmap = imageCompose!!,
                        contentDescription = item.metadata.title,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier
                            .fillMaxSize()
                            .graphicsLayer(
                                scaleX = zoomScale,
                                scaleY = zoomScale,
                                translationX = panOffset.x,
                                translationY = panOffset.y,
                            ),
                    )
                }
            }
        }

        // Footer (PDF page navigation + notes/expiry strip)
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .background(c.surface)
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            if (pdfPageCount > 1) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = pdfPageIndex > 0,
                        onClick = { pdfPageIndex = (pdfPageIndex - 1).coerceAtLeast(0) },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateBefore, contentDescription = null)
                    }
                    Text(
                        text = if (s.isVi) "Trang ${pdfPageIndex + 1} / $pdfPageCount" else "Page ${pdfPageIndex + 1} / $pdfPageCount",
                        color = c.textPrimary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.SemiBold,
                    )
                    IconButton(
                        enabled = pdfPageIndex < pdfPageCount - 1,
                        onClick = { pdfPageIndex = (pdfPageIndex + 1).coerceAtMost(pdfPageCount - 1) },
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.NavigateNext, contentDescription = null)
                    }
                }
            }

            if (!item.metadata.expiresAt.isNullOrBlank() || !item.metadata.notes.isNullOrBlank()) {
                OnePassCard(
                    cornerRadius = 12.dp,
                    padding = PaddingValues(12.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        if (!item.metadata.expiresAt.isNullOrBlank()) {
                            Text(
                                text = (if (s.isVi) "Ngày hết hạn: " else "Expires: ") + item.metadata.expiresAt,
                                color = c.accent,
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                        if (!item.metadata.notes.isNullOrBlank()) {
                            Text(
                                text = item.metadata.notes,
                                color = c.textSecondary,
                                fontSize = 13.sp,
                            )
                        }
                    }
                }
            }
        }
    }

    if (showExportWarn) {
        AlertDialog(
            onDismissRequest = { showExportWarn = false },
            title = { Text(s.vaultPlusExportWarnTitle) },
            text = { Text(s.vaultPlusExportWarnMsg) },
            confirmButton = {
                Button(
                    onClick = {
                        showExportWarn = false
                        exportLauncher.launch(item.metadata.fileName)
                    },
                ) {
                    Text(if (s.isVi) "Xuất bản sao" else "Export")
                }
            },
            dismissButton = {
                TextButton(onClick = { showExportWarn = false }) { Text(s.cancel) }
            },
        )
    }

    if (showDeleteConfirm) {
        val permanent = item.deletedAt != null
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text(if (permanent) s.deleteConfirmPermTitle else s.deleteConfirmTrashTitle) },
            text = {
                Text(
                    if (permanent) {
                        if (s.isVi) "Tệp mã hóa trên Cloudflare R2 và dữ liệu cục bộ sẽ bị xóa vĩnh viễn." else "The encrypted file in Cloudflare R2 will be permanently deleted."
                    } else {
                        if (s.isVi) "Tài liệu sẽ chuyển vào Thùng rác Vault+ (tự xóa sau 30 ngày)." else "Document will move to Vault+ Trash (auto-purged after 30 days)."
                    },
                )
            },
            confirmButton = {
                Button(
                    colors = ButtonDefaults.buttonColors(containerColor = c.danger),
                    onClick = {
                        showDeleteConfirm = false
                        scope.launch {
                            runCatching {
                                if (permanent) safeRepository.hardDeleteItem(item.id)
                                else safeRepository.softDeleteItem(item.id)
                            }.onSuccess {
                                onShowMessage(if (permanent) (if (s.isVi) "Đã xóa vĩnh viễn." else "Permanently deleted.") else (if (s.isVi) "Đã chuyển vào Thùng rác." else "Moved to Trash."))
                                onBack()
                            }.onFailure {
                                onShowMessage(it.message ?: "Error")
                            }
                        }
                    },
                ) {
                    Text(if (permanent) s.deletePermanently else s.moveToTrash, color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text(s.cancel) }
            },
        )
    }

    if (showEditDialog) {
        SafeMetadataEditDialog(
            initialTitle = item.metadata.title,
            initialCategory = item.category,
            initialNotes = item.metadata.notes.orEmpty(),
            initialExpiresAt = item.metadata.expiresAt.orEmpty(),
            isVi = s.isVi,
            onDismiss = { showEditDialog = false },
            onSave = { title, cat, notes, exp ->
                showEditDialog = false
                scope.launch {
                    runCatching {
                        safeRepository.updateItemMetadata(item, title, cat, notes, exp)
                    }.onSuccess {
                        onShowMessage(if (s.isVi) "Đã cập nhật thông tin." else "Updated metadata.")
                    }.onFailure {
                        onShowMessage(it.message ?: "Error")
                    }
                }
            },
        )
    }
}

@Composable
fun SafeMetadataEditDialog(
    initialTitle: String,
    initialCategory: SafeDocumentCategory,
    initialNotes: String,
    initialExpiresAt: String,
    isVi: Boolean,
    onDismiss: () -> Unit,
    onSave: (String, SafeDocumentCategory, String?, String?) -> Unit,
) {
    var title by remember { mutableStateOf(initialTitle) }
    var category by remember { mutableStateOf(initialCategory) }
    var notes by remember { mutableStateOf(initialNotes) }
    var expiresAt by remember { mutableStateOf(initialExpiresAt) }
    var catMenuOpen by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (isVi) "Thông Tin Tài Liệu" else "Document Details") },
        text = {
            Column(
                modifier = Modifier.verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text(if (isVi) "Tên hiển thị" else "Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Box {
                    Surface(
                        onClick = { catMenuOpen = true },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(categoryLabel(category, isVi), fontWeight = FontWeight.Medium)
                            Text(if (isVi) "Đổi loại" else "Change", fontSize = 12.sp, color = Color(0xFF0572EC))
                        }
                    }
                    DropdownMenu(expanded = catMenuOpen, onDismissRequest = { catMenuOpen = false }) {
                        SafeDocumentCategory.entries.forEach { cat ->
                            DropdownMenuItem(
                                text = { Text(categoryLabel(cat, isVi)) },
                                onClick = {
                                    category = cat
                                    catMenuOpen = false
                                },
                            )
                        }
                    }
                }
                OutlinedTextField(
                    value = expiresAt,
                    onValueChange = { expiresAt = it },
                    label = { Text(if (isVi) "Ngày hết hạn (YYYY-MM-DD, tùy chọn)" else "Expiry Date (YYYY-MM-DD, optional)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = notes,
                    onValueChange = { notes = it },
                    label = { Text(if (isVi) "Ghi chú bảo mật" else "Notes") },
                    minLines = 2,
                    maxLines = 4,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            Button(
                enabled = title.trim().isNotEmpty(),
                onClick = { onSave(title.trim(), category, notes, expiresAt) },
            ) {
                Text(if (isVi) "Lưu" else "Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (isVi) "Hủy" else "Cancel") }
        },
    )
}

private fun decodeBoundedBitmap(bytes: ByteArray, maxDim: Int): Bitmap? {
    val opts = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    if (opts.outWidth <= 0 || opts.outHeight <= 0) return null
    var sample = 1
    while ((opts.outWidth / sample) > maxDim * 2 || (opts.outHeight / sample) > maxDim * 2) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

internal fun categoryLabel(category: SafeDocumentCategory, isVi: Boolean): String = when (category) {
    SafeDocumentCategory.ID_CARD -> if (isVi) "CCCD / CMND" else "ID Card"
    SafeDocumentCategory.PASSPORT -> if (isVi) "Hộ chiếu" else "Passport"
    SafeDocumentCategory.DRIVER_LICENSE -> if (isVi) "Bằng lái xe" else "Driver's License"
    SafeDocumentCategory.MEDICAL -> if (isVi) "Hồ sơ y tế" else "Medical"
    SafeDocumentCategory.FINANCIAL -> if (isVi) "Tài chính" else "Financial"
    SafeDocumentCategory.PHOTO -> if (isVi) "Ảnh riêng tư" else "Photos"
    SafeDocumentCategory.OTHER -> if (isVi) "Khác" else "Other"
}

internal fun formatBytes(bytes: Long): String {
    if (bytes < 1024L) return "$bytes B"
    val kb = bytes / 1024.0
    if (kb < 1024.0) return String.format("%.1f KB", kb)
    val mb = kb / 1024.0
    return String.format("%.2f MB", mb)
}
