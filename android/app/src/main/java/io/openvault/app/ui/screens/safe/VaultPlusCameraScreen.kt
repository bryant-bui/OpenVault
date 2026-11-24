package io.openvault.app.ui.screens.safe

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Cameraswitch
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
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
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.compose.ui.window.SecureFlagPolicy
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream

/**
 * Clean full-screen modal camera capture flow for Vault+.
 * Captures strictly in RAM (never writes plaintext JPEG to shared storage)
 * and offers a quick RAM review step before encrypting.
 */
@Composable
fun SafeCameraCaptureDialog(
    onCaptured: (ByteArray) -> Unit,
    onCancel: () -> Unit,
    onError: (String) -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val s = LocalStrings.current
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasPermission) permLauncher.launch(Manifest.permission.CAMERA)
    }

    var lensFacing by remember { mutableIntStateOf(CameraSelector.LENS_FACING_BACK) }
    var flashEnabled by remember { mutableStateOf(false) }
    var capturing by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }

    var previewBytes by remember { mutableStateOf<ByteArray?>(null) }
    var previewBitmap by remember { mutableStateOf<Bitmap?>(null) }
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
            implementationMode = PreviewView.ImplementationMode.COMPATIBLE
        }
    }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
            .build()
    }

    fun clearCapturedPreview(wipeBytes: Boolean = true) {
        if (wipeBytes) previewBytes?.fill(0)
        previewBytes = null
        previewImage = null
        previewBitmap?.recycle()
        previewBitmap = null
    }

    DisposableEffect(Unit) {
        onDispose {
            clearCapturedPreview(wipeBytes = true)
            runCatching { ProcessCameraProvider.getInstance(context).get().unbindAll() }
        }
    }

    LaunchedEffect(hasPermission, lensFacing, previewBytes != null) {
        if (!hasPermission || previewBytes != null) return@LaunchedEffect
        val provider = withContext(Dispatchers.Main) {
            runCatching {
                val future = ProcessCameraProvider.getInstance(context)
                future.get()
            }.getOrNull()
        } ?: return@LaunchedEffect

        val preview = Preview.Builder().build().also {
            it.surfaceProvider = previewView.surfaceProvider
        }
        val selector = CameraSelector.Builder()
            .requireLensFacing(lensFacing)
            .build()
        imageCapture.flashMode = if (flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK) {
            ImageCapture.FLASH_MODE_ON
        } else {
            ImageCapture.FLASH_MODE_OFF
        }
        runCatching {
            provider.unbindAll()
            boundCamera = provider.bindToLifecycle(
                lifecycleOwner,
                selector,
                preview,
                imageCapture,
            )
        }.onFailure { ex ->
            onError(ex.message ?: "Camera error")
        }
    }

    LaunchedEffect(flashEnabled, boundCamera, lensFacing) {
        val backFlash = flashEnabled && lensFacing == CameraSelector.LENS_FACING_BACK
        imageCapture.flashMode = if (backFlash) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
        if (boundCamera?.cameraInfo?.hasFlashUnit() == true) {
            runCatching { boundCamera?.cameraControl?.enableTorch(backFlash) }
        }
    }

    Dialog(
        onDismissRequest = {
            clearCapturedPreview(wipeBytes = true)
            onCancel()
        },
        properties = DialogProperties(
            usePlatformDefaultWidth = false,
            decorFitsSystemWindows = false,
            securePolicy = SecureFlagPolicy.Inherit,
        ),
    ) {
        if (!hasPermission) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .background(c.background)
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(20.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.Rounded.Close, contentDescription = s.close, tint = c.textPrimary)
                }

                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    padding = PaddingValues(24.dp),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(62.dp)
                                .clip(RoundedCornerShape(18.dp))
                                .background(c.accentSoft),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                imageVector = Icons.Rounded.CameraAlt,
                                contentDescription = null,
                                tint = c.accent,
                                modifier = Modifier.size(32.dp),
                            )
                        }
                        Text(
                            text = if (s.isVi) "Cần quyền Camera" else "Camera Permission Required",
                            color = c.textPrimary,
                            fontSize = 18.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = if (s.isVi) {
                                "OpenVault chụp tài liệu trực tiếp vào RAM để mã hóa, hoàn toàn không lưu ảnh gốc ra Thư viện."
                            } else {
                                "OpenVault captures documents straight into RAM for encryption without saving to your gallery."
                            },
                            color = c.textSecondary,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                        )
                        Spacer(Modifier.height(4.dp))
                        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                            OutlinedButton(
                                onClick = {
                                    val intent = Intent(
                                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                                        Uri.fromParts("package", context.packageName, null),
                                    )
                                    runCatching { context.startActivity(intent) }
                                },
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(if (s.isVi) "Mở Cài đặt" else "Settings")
                            }
                            Button(
                                onClick = { permLauncher.launch(Manifest.permission.CAMERA) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = c.accentButtonBg,
                                    contentColor = c.accentButtonFg,
                                ),
                                shape = RoundedCornerShape(12.dp),
                            ) {
                                Text(if (s.isVi) "Cấp quyền" else "Grant Access", fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                }

                Spacer(Modifier.size(1.dp))
            }
        } else if (previewBytes != null && previewImage != null) {
            // Quick RAM Review Step
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                Image(
                    bitmap = previewImage!!,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .fillMaxSize()
                        .statusBarsPadding()
                        .navigationBarsPadding()
                        .padding(bottom = 96.dp),
                )

                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = {
                            clearCapturedPreview(wipeBytes = true)
                            onCancel()
                        },
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = Color.White)
                    }
                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = formatBytes(previewBytes!!.size.toLong()),
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }
                    Spacer(Modifier.size(40.dp))
                }

                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .background(Color.Black.copy(alpha = 0.72f))
                        .navigationBarsPadding()
                        .padding(horizontal = 20.dp, vertical = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    OutlinedButton(
                        onClick = { clearCapturedPreview(wipeBytes = true) },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.White),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Icon(Icons.Rounded.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isVi) "Chụp lại" else "Retake", fontWeight = FontWeight.SemiBold)
                    }
                    Button(
                        onClick = {
                            val bytes = previewBytes ?: return@Button
                            previewBytes = null
                            clearCapturedPreview(wipeBytes = false)
                            onCaptured(bytes)
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accentButtonBg,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .weight(1f)
                            .height(50.dp),
                    ) {
                        Icon(Icons.Rounded.Check, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(if (s.isVi) "Dùng ảnh này" else "Use Photo", fontWeight = FontWeight.SemiBold)
                    }
                }
            }
        } else {
            // Simple Live Viewfinder
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(Color.Black),
            ) {
                AndroidView(
                    factory = { previewView },
                    modifier = Modifier.fillMaxSize(),
                )

                // Subtle corner framing guide
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 32.dp, vertical = 140.dp),
                ) {
                    Canvas(modifier = Modifier.fillMaxSize()) {
                        val cornerLen = 28.dp.toPx()
                        val stroke = 2.5.dp.toPx()
                        val guideColor = Color.White.copy(alpha = 0.55f)
                        val w = size.width
                        val h = size.height

                        // Top-left
                        drawLine(guideColor, Offset(0f, 0f), Offset(cornerLen, 0f), stroke, StrokeCap.Round)
                        drawLine(guideColor, Offset(0f, 0f), Offset(0f, cornerLen), stroke, StrokeCap.Round)
                        // Top-right
                        drawLine(guideColor, Offset(w - cornerLen, 0f), Offset(w, 0f), stroke, StrokeCap.Round)
                        drawLine(guideColor, Offset(w, 0f), Offset(w, cornerLen), stroke, StrokeCap.Round)
                        // Bottom-left
                        drawLine(guideColor, Offset(0f, h - cornerLen), Offset(0f, h), stroke, StrokeCap.Round)
                        drawLine(guideColor, Offset(0f, h), Offset(cornerLen, h), stroke, StrokeCap.Round)
                        // Bottom-right
                        drawLine(guideColor, Offset(w - cornerLen, h), Offset(w, h), stroke, StrokeCap.Round)
                        drawLine(guideColor, Offset(w, h - cornerLen), Offset(w, h), stroke, StrokeCap.Round)
                    }
                }

                // Minimal Top Controls
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = onCancel,
                        modifier = Modifier
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(Icons.Rounded.Close, contentDescription = s.close, tint = Color.White)
                    }

                    Surface(
                        shape = CircleShape,
                        color = Color.Black.copy(alpha = 0.55f),
                    ) {
                        Text(
                            text = if (s.isVi) "Chụp vào RAM" else "RAM Capture",
                            color = Color.White,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        )
                    }

                    if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                        IconButton(
                            onClick = { flashEnabled = !flashEnabled },
                            modifier = Modifier
                                .clip(CircleShape)
                                .background(Color.Black.copy(alpha = 0.55f)),
                        ) {
                            Icon(
                                imageVector = if (flashEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                                contentDescription = "Flash",
                                tint = if (flashEnabled) Color(0xFFFBBF24) else Color.White,
                            )
                        }
                    } else {
                        Spacer(Modifier.size(40.dp))
                    }
                }

                // Minimal Bottom Shutter Dock
                Row(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .padding(horizontal = 28.dp, vertical = 28.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        enabled = !capturing,
                        onClick = {
                            flashEnabled = false
                            lensFacing = if (lensFacing == CameraSelector.LENS_FACING_BACK) {
                                CameraSelector.LENS_FACING_FRONT
                            } else {
                                CameraSelector.LENS_FACING_BACK
                            }
                        },
                        modifier = Modifier
                            .size(48.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f)),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.Cameraswitch,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }

                    // Shutter ring
                    Box(
                        modifier = Modifier
                            .size(78.dp)
                            .clip(CircleShape)
                            .border(3.5.dp, Color.White, CircleShape)
                            .padding(6.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Surface(
                            enabled = !capturing,
                            onClick = {
                                if (capturing) return@Surface
                                capturing = true
                                imageCapture.takePicture(
                                    ContextCompat.getMainExecutor(context),
                                    object : ImageCapture.OnImageCapturedCallback() {
                                        override fun onCaptureSuccess(image: ImageProxy) {
                                            scope.launch {
                                                val result = withContext(Dispatchers.Default) {
                                                    try {
                                                        val bytes = extractBoundedJpegBytes(image)
                                                        val bmp = bytes?.let { decodeCameraPreviewBitmap(it) }
                                                        bytes to bmp
                                                    } finally {
                                                        image.close()
                                                    }
                                                }
                                                capturing = false
                                                val (bytes, bmp) = result
                                                if (bytes != null && bmp != null) {
                                                    previewBytes = bytes
                                                    previewBitmap = bmp
                                                    previewImage = bmp.asImageBitmap()
                                                } else {
                                                    bytes?.fill(0)
                                                    bmp?.recycle()
                                                    onError(
                                                        if (s.isVi) "Không thể xử lý ảnh chụp."
                                                        else "Failed to process captured photo.",
                                                    )
                                                }
                                            }
                                        }

                                        override fun onError(exception: ImageCaptureException) {
                                            capturing = false
                                            onError(exception.message ?: "Camera error")
                                        }
                                    },
                                )
                            },
                            shape = CircleShape,
                            color = Color.White,
                            modifier = Modifier.fillMaxSize(),
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                if (capturing) {
                                    CircularProgressIndicator(
                                        modifier = Modifier.size(26.dp),
                                        color = c.accentButtonBg,
                                        strokeWidth = 2.5.dp,
                                    )
                                }
                            }
                        }
                    }

                    Spacer(Modifier.size(48.dp))
                }
            }
        }
    }
}

private fun decodeCameraPreviewBitmap(bytes: ByteArray): Bitmap? {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    BitmapFactory.decodeByteArray(bytes, 0, bytes.size, bounds)
    if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return null
    var sample = 1
    while ((bounds.outWidth / sample) > 2048 || (bounds.outHeight / sample) > 2048) {
        sample *= 2
    }
    return BitmapFactory.decodeByteArray(
        bytes,
        0,
        bytes.size,
        BitmapFactory.Options().apply { inSampleSize = sample },
    )
}

internal fun extractBoundedJpegBytes(image: ImageProxy): ByteArray? {
    val buffer = image.planes.firstOrNull()?.buffer ?: return null
    val raw = ByteArray(buffer.remaining())
    buffer.get(raw)
    val rotation = image.imageInfo.rotationDegrees
    if (rotation == 0 && raw.size <= SafeCrypto.SAFE_MAX_FILE_BYTES) {
        return raw
    }
    val decoded = BitmapFactory.decodeByteArray(raw, 0, raw.size)
        ?: return raw.takeIf { it.size <= SafeCrypto.SAFE_MAX_FILE_BYTES }
    val rotated = if (rotation != 0) {
        val m = Matrix().apply { postRotate(rotation.toFloat()) }
        Bitmap.createBitmap(decoded, 0, 0, decoded.width, decoded.height, m, true).also {
            if (it !== decoded) decoded.recycle()
        }
    } else {
        decoded
    }
    return try {
        var quality = 88
        var best: ByteArray
        do {
            val out = ByteArrayOutputStream()
            rotated.compress(Bitmap.CompressFormat.JPEG, quality, out)
            best = out.toByteArray()
            quality -= 12
        } while (best.size > SafeCrypto.SAFE_MAX_FILE_BYTES && quality >= 35)
        best.takeIf { it.size <= SafeCrypto.SAFE_MAX_FILE_BYTES }
    } finally {
        rotated.recycle()
    }
}
