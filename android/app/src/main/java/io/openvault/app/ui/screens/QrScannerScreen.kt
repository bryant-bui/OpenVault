package io.openvault.app.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.FlashOff
import androidx.compose.material.icons.rounded.FlashOn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.DecodeHintType
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.theme.LocalOpenVaultColors
import java.util.concurrent.Executors
import java.util.concurrent.atomic.AtomicBoolean

/**
 * 100% offline CameraX + ZXing QR Code Scanner for otpauth://totp/... URIs.
 */
@Composable
fun QrScannerScreen(
    onResult: (String) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    var hasCameraPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
                PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted -> hasCameraPermission = granted }

    LaunchedEffect(Unit) {
        if (!hasCameraPermission) {
            permissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    var torchEnabled by remember { mutableStateOf(false) }
    var boundCamera by remember { mutableStateOf<Camera?>(null) }
    var scanHint by remember { mutableStateOf("Đưa mã QR 2FA (otpauth://) vào khung hình") }
    val delivered = remember { AtomicBoolean(false) }
    val analyzerExecutor = remember { Executors.newSingleThreadExecutor() }

    DisposableEffect(Unit) {
        onDispose {
            analyzerExecutor.shutdown()
        }
    }

    if (!hasCameraPermission) {
        LiquidGlassBackground {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding()
                    .navigationBarsPadding()
                    .padding(24.dp),
                verticalArrangement = Arrangement.SpaceBetween,
            ) {
                IconButton(onClick = onCancel) {
                    Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Quay lại", tint = c.textPrimary)
                }
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevated = true,
                    padding = PaddingValues(24.dp),
                ) {
                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(14.dp),
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.CameraAlt, contentDescription = null, tint = c.accent, modifier = Modifier.size(48.dp))
                        Text("Cần quyền Camera để quét QR 2FA", color = c.textPrimary, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                        Text(
                            "OpenVault xử lý mã QR hoàn toàn offline trên thiết bị bằng ZXing — không gửi ảnh lên mạng.",
                            color = c.textSecondary,
                            fontSize = 13.5.sp,
                            textAlign = TextAlign.Center,
                        )
                        Button(
                            onClick = { permissionLauncher.launch(Manifest.permission.CAMERA) },
                            shape = RoundedCornerShape(14.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        ) {
                            Text("Cấp quyền Camera", fontWeight = FontWeight.SemiBold)
                        }
                    }
                }
                Box(Modifier.size(1.dp))
            }
        }
        return
    }

    Box(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        AndroidView(
            factory = { ctx ->
                val previewView = PreviewView(ctx)
                val cameraProviderFuture = ProcessCameraProvider.getInstance(ctx)
                cameraProviderFuture.addListener(
                    {
                        val provider = cameraProviderFuture.get()
                        val preview = Preview.Builder().build().also {
                            it.surfaceProvider = previewView.surfaceProvider
                        }
                        val reader = MultiFormatReader().apply {
                            setHints(
                                mapOf(
                                    DecodeHintType.POSSIBLE_FORMATS to listOf(BarcodeFormat.QR_CODE),
                                    DecodeHintType.TRY_HARDER to true,
                                ),
                            )
                        }
                        val analysis = ImageAnalysis.Builder()
                            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                            .build()
                            .also { imageAnalysis ->
                                imageAnalysis.setAnalyzer(analyzerExecutor) { proxy ->
                                    decodeQrFromImageProxy(reader, proxy)?.let { raw ->
                                        val isValidTotp = runCatching {
                                            TotpEngine.parseTotpInput(raw)
                                        }.isSuccess
                                        if (isValidTotp && delivered.compareAndSet(false, true)) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                onResult(raw)
                                            }
                                        } else if (!isValidTotp) {
                                            ContextCompat.getMainExecutor(ctx).execute {
                                                scanHint = "Mã QR này không phải chuẩn otpauth://totp"
                                            }
                                        }
                                    }
                                    proxy.close()
                                }
                            }
                        runCatching {
                            provider.unbindAll()
                            boundCamera = provider.bindToLifecycle(
                                lifecycleOwner,
                                CameraSelector.DEFAULT_BACK_CAMERA,
                                preview,
                                analysis,
                            )
                        }
                    },
                    ContextCompat.getMainExecutor(ctx),
                )
                previewView
            },
            modifier = Modifier.fillMaxSize(),
        )

        // Top Liquid Glass Overlay Controls
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
                    .background(Color(0x880B1120)),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Đóng", tint = Color.White)
            }

            IconButton(
                onClick = {
                    val next = !torchEnabled
                    torchEnabled = next
                    boundCamera?.cameraControl?.enableTorch(next)
                },
                modifier = Modifier
                    .clip(CircleShape)
                    .background(Color(0x880B1120)),
            ) {
                Icon(
                    imageVector = if (torchEnabled) Icons.Rounded.FlashOn else Icons.Rounded.FlashOff,
                    contentDescription = "Đèn pin",
                    tint = if (torchEnabled) Color(0xFFFBBF24) else Color.White,
                )
            }
        }

        // Center Frosted Reticle
        Box(
            modifier = Modifier
                .align(Alignment.Center)
                .size(250.dp)
                .clip(RoundedCornerShape(28.dp))
                .border(2.5.dp, Color(0xFF3B8BFF), RoundedCornerShape(28.dp))
                .background(Color(0x143B8BFF)),
        )

        // Bottom Frosted Instruction Pill
        LiquidGlassCard(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(horizontal = 24.dp, vertical = 28.dp),
            cornerRadius = 20.dp,
            padding = PaddingValues(horizontal = 18.dp, vertical = 14.dp),
        ) {
            Text(
                text = scanHint,
                color = Color.White,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

private fun decodeQrFromImageProxy(reader: MultiFormatReader, proxy: ImageProxy): String? {
    val plane = proxy.planes.firstOrNull() ?: return null
    val buffer = plane.buffer
    val bytes = ByteArray(buffer.remaining())
    buffer.get(bytes)

    return runCatching {
        val source = PlanarYUVLuminanceSource(
            bytes,
            plane.rowStride,
            proxy.height,
            0,
            0,
            proxy.width,
            proxy.height,
            false,
        )
        val bitmap = BinaryBitmap(HybridBinarizer(source))
        reader.decodeWithState(bitmap).text
    }.getOrNull().also {
        reader.reset()
    }
}
