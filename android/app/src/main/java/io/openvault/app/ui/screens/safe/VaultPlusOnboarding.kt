package io.openvault.app.ui.screens.safe

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.EnhancedEncryption
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Surface
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.data.SafeRepository
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

private enum class VaultPlusSetupStep {
    INTRO,
    PIN_SETUP,
}

/**
 * First-time Vault+ onboarding & setup flow:
 * 1) Interactive 4-slide flash carousel explaining isolated encrypted storage & SafeKey
 * 2) Final slide reveals the primary "Khởi tạo ngay" / "Set Up Now" button
 * 3) Dedicated 6-digit PIN creation & confirmation keypad (biometrics are configured
 *    inside Vault+ Settings after initialization).
 */
@Composable
fun SafeSetupFlow(
    safeRepository: SafeRepository,
    onSetupCreated: (String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    var step by remember { mutableStateOf(VaultPlusSetupStep.INTRO) }

    AnimatedContent(
        targetState = step,
        transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(180)) },
        label = "vault_plus_setup_step",
    ) { currentStep ->
        when (currentStep) {
            VaultPlusSetupStep.INTRO -> SafeFlashIntroCarousel(
                onStartSetup = { step = VaultPlusSetupStep.PIN_SETUP },
            )

            VaultPlusSetupStep.PIN_SETUP -> SafeSetupPinStepScreen(
                onBackToIntro = { step = VaultPlusSetupStep.INTRO },
                safeRepository = safeRepository,
                onSetupCreated = onSetupCreated,
                onShowMessage = onShowMessage,
            )
        }
    }
}

@Composable
private fun SafeFlashIntroCarousel(
    onStartSetup: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()
    val pagerState = rememberPagerState(pageCount = { 4 })

    val bottomBarInset = if (fold.isTwoPane) 16.dp else 86.dp

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Subtle top brand pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = fold.horizontalPadding, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Surface(
                shape = CircleShape,
                color = c.accentSoft,
                border = BorderStroke(1.dp, c.accent.copy(alpha = 0.25f)),
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Icon(
                        imageVector = Icons.Rounded.EnhancedEncryption,
                        contentDescription = null,
                        tint = c.accent,
                        modifier = Modifier.size(16.dp),
                    )
                    Text(
                        text = s.tabVaultPlus,
                        color = c.accent,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }

            if (pagerState.currentPage < 3) {
                TextButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(3) }
                    },
                ) {
                    Text(
                        text = if (s.isVi) "Bỏ qua" else "Skip",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }
            } else {
                Spacer(modifier = Modifier.height(36.dp))
            }
        }

        HorizontalPager(
            state = pagerState,
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth(),
        ) { page ->
            val artSize = if (fold.isNarrowCover) 176.dp else 220.dp
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (fold.isNarrowCover) 20.dp else 30.dp, vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    modifier = Modifier.widthIn(max = 440.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(22.dp),
                ) {
                    when (page) {
                        0 -> SafeIntroDocumentArt(Modifier.size(artSize), c.accent)
                        1 -> SafeIntroKeyArt(Modifier.size(artSize), c.accent)
                        2 -> SafeIntroShieldArt(Modifier.size(artSize), c.accent)
                        else -> SafeIntroCloudArt(Modifier.size(artSize), c.accent)
                    }

                    val (title, body) = when (page) {
                        0 -> if (s.isVi) {
                            "Không Gian Két Cá Nhân Độc Lập" to
                                "Nơi lưu giữ riêng tư những tài liệu quan trọng như CCCD, hộ chiếu, bằng lái xe, hồ sơ y tế, tệp PDF và ảnh cá nhân (tối đa 5 MB/tệp)."
                        } else {
                            "Isolated Personal Safe" to
                                "Store your most sensitive documents—national IDs, passports, driver's licenses, medical PDFs, and private photos (up to 5 MB each)."
                        }

                        1 -> if (s.isVi) {
                            "Khóa SafeKey Riêng Biệt" to
                                "Vault+ sử dụng khóa SafeKey 64-byte độc lập hoàn toàn với kho mật khẩu chính. Mọi tệp đều được mã hóa AES-256-GCM trước khi rời thiết bị."
                        } else {
                            "Independent SafeKey Architecture" to
                                "Powered by a standalone 64-byte SafeKey isolated from your main vault. Every file is encrypted with AES-256-GCM before upload."
                        }

                        2 -> if (s.isVi) {
                            "Bảo Vệ Chủ Động Đa Lớp" to
                                "Chống chụp trộm màn hình, tự động khóa thông minh, khóa 15 phút sau 5 lần sai PIN và khôi phục an toàn qua Recovery Key."
                        } else {
                            "Multi-Layer Active Defense" to
                                "Anti-screenshot protection, smart auto-lock, 15-minute cooldown after 5 wrong PINs, and offline Recovery Key recovery."
                        }

                        else -> if (s.isVi) {
                            "Sẵn Sàng Khởi Tạo Vault+" to
                                "Thiết lập mã PIN 6 chữ số riêng ngay bây giờ. Bạn có thể bật thêm Mở khoá Sinh Trắc Học trong Cài đặt sau khi hoàn tất."
                        } else {
                            "Ready to Create Your Vault+" to
                                "Set up your dedicated 6-digit PIN now. You can enable Biometric Unlock inside Vault+ Settings right after."
                        }
                    }

                    Column(
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        Text(
                            text = title,
                            color = c.textPrimary,
                            fontSize = if (fold.isNarrowCover) 22.sp else 25.sp,
                            fontWeight = FontWeight.Bold,
                            lineHeight = 30.sp,
                            textAlign = TextAlign.Center,
                        )
                        Text(
                            text = body,
                            color = c.textSecondary,
                            fontSize = 14.sp,
                            lineHeight = 20.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
        }

        // Animated page indicator pills
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.padding(vertical = 12.dp),
        ) {
            repeat(4) { idx ->
                val active = pagerState.currentPage == idx
                val dotWidth by animateDpAsState(
                    targetValue = if (active) 22.dp else 8.dp,
                    animationSpec = tween(220),
                    label = "safe_intro_dot_$idx",
                )
                Box(
                    modifier = Modifier
                        .size(width = dotWidth, height = 8.dp)
                        .clip(CircleShape)
                        .background(if (active) c.accent else c.textMuted.copy(alpha = 0.35f)),
                )
            }
        }

        // Clean CTA button directly on canvas without outer card wrapper
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(bottom = bottomBarInset)
                .padding(horizontal = fold.horizontalPadding),
            contentAlignment = Alignment.Center,
        ) {
            Box(
                modifier = Modifier
                    .widthIn(max = 460.dp)
                    .fillMaxWidth(),
            ) {
                if (pagerState.currentPage < 3) {
                    Button(
                        onClick = {
                            scope.launch {
                                pagerState.animateScrollToPage((pagerState.currentPage + 1).coerceAtMost(3))
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accentButtonBg,
                            contentColor = c.accentButtonFg,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Text(
                            text = if (s.isVi) "Tiếp theo" else "Next",
                            fontSize = 15.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Spacer(Modifier.width(8.dp))
                        Icon(
                            imageVector = Icons.AutoMirrored.Rounded.ArrowForward,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                } else {
                    Button(
                        onClick = onStartSetup,
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accentButtonBg,
                            contentColor = c.accentButtonFg,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Rounded.EnhancedEncryption,
                            contentDescription = null,
                            modifier = Modifier.size(19.dp),
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = s.vaultPlusStartSetupCta,
                            fontSize = 16.sp,
                            fontWeight = FontWeight.Bold,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SafeSetupPinStepScreen(
    onBackToIntro: () -> Unit,
    safeRepository: SafeRepository,
    onSetupCreated: (String) -> Unit,
    onShowMessage: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    val haptic = LocalHapticFeedback.current

    var isConfirmStep by remember { mutableStateOf(false) }
    var firstPin by remember { mutableStateOf("") }
    var confirmPin by remember { mutableStateOf("") }
    var busy by remember { mutableStateOf(false) }
    var errorMsg by remember { mutableStateOf<String?>(null) }
    val shakeOffset = remember { Animatable(0f) }

    val activePin = if (isConfirmStep) confirmPin else firstPin
    val bottomPadding = if (fold.isTwoPane) 18.dp else 86.dp

    fun handleBack() {
        if (busy) return
        if (isConfirmStep) {
            firstPin = ""
            confirmPin = ""
            errorMsg = null
            isConfirmStep = false
        } else {
            firstPin = ""
            confirmPin = ""
            errorMsg = null
            onBackToIntro()
        }
    }

    BackHandler(onBack = ::handleBack)

    fun triggerMismatchShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            val px = with(density) { 10.dp.toPx() }
            for (target in listOf(-px, px, -px * 0.6f, px * 0.6f, 0f)) {
                shakeOffset.animateTo(target, tween(40))
            }
        }
    }

    fun handlePinComplete(completed: String) {
        if (!isConfirmStep) {
            firstPin = completed
            confirmPin = ""
            errorMsg = null
            isConfirmStep = true
            return
        }
        if (completed != firstPin) {
            confirmPin = ""
            errorMsg = if (s.isVi) "Mã PIN xác nhận không khớp. Vui lòng nhập lại." else "PINs do not match. Please try again."
            triggerMismatchShake()
            return
        }
        busy = true
        errorMsg = null
        scope.launch {
            runCatching { safeRepository.setupVaultPlus(completed, biometricEncryptCipher = null) }
                .onSuccess { recoveryKey ->
                    busy = false
                    firstPin = ""
                    confirmPin = ""
                    onSetupCreated(recoveryKey)
                }
                .onFailure { ex ->
                    busy = false
                    confirmPin = ""
                    val msg = ex.message ?: if (s.isVi) "Không thể khởi tạo Vault+." else "Could not create Vault+."
                    errorMsg = msg
                    onShowMessage(msg)
                }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(bottom = bottomPadding),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        // Top navigation & step badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            IconButton(
                enabled = !busy,
                onClick = ::handleBack,
            ) {
                Icon(
                    imageVector = Icons.AutoMirrored.Rounded.ArrowBack,
                    contentDescription = s.back,
                    tint = c.textPrimary,
                )
            }

            AnimatedContent(
                targetState = isConfirmStep,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "safe_setup_step_badge",
            ) { confirm ->
                Surface(
                    shape = CircleShape,
                    color = c.accentSoft,
                ) {
                    Text(
                        text = if (s.isVi) {
                            if (confirm) "Bước 2 / 2" else "Bước 1 / 2"
                        } else {
                            if (confirm) "Step 2 / 2" else "Step 1 / 2"
                        },
                        color = c.accent,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                    )
                }
            }

            Spacer(modifier = Modifier.size(48.dp))
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .widthIn(max = 420.dp)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Box(
                modifier = Modifier
                    .size(64.dp)
                    .clip(RoundedCornerShape(18.dp))
                    .background(c.accentSoft),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Rounded.EnhancedEncryption,
                    contentDescription = null,
                    tint = c.accent,
                    modifier = Modifier.size(32.dp),
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            AnimatedContent(
                targetState = isConfirmStep,
                transitionSpec = { fadeIn(tween(180)) togetherWith fadeOut(tween(140)) },
                label = "safe_setup_step_heading",
            ) { confirm ->
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = if (confirm) {
                            s.vaultPlusPinConfirmLabel
                        } else {
                            s.vaultPlusPinLabel
                        },
                        color = c.textPrimary,
                        fontSize = 21.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                    )

                    Text(
                        text = if (confirm) {
                            if (s.isVi) "Nhập lại chính xác 6 chữ số để khóa két bảo mật." else "Re-enter the exact 6 digits to lock your safe."
                        } else {
                            if (s.isVi) "Mã PIN 6 số riêng biệt cho Vault+. Sinh trắc học có thể bật sau trong Cài đặt."
                            else "Dedicated 6-digit Vault+ PIN. Biometrics can be enabled later in Settings."
                        },
                        color = c.textSecondary,
                        fontSize = 13.5.sp,
                        lineHeight = 19.sp,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(22.dp))

            SafePinIndicatorRow(
                pinLength = activePin.length,
                targetLength = SafeCrypto.SAFE_PIN_LENGTH,
                isError = errorMsg != null,
                enabled = !busy,
                shakeOffsetPx = shakeOffset.value,
            )

            Spacer(modifier = Modifier.height(12.dp))

            Box(
                modifier = Modifier.height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                when {
                    busy -> CircularProgressIndicator(
                        modifier = Modifier.size(20.dp),
                        color = c.accent,
                        strokeWidth = 2.dp,
                    )

                    errorMsg != null -> Text(
                        text = errorMsg.orEmpty(),
                        color = c.danger,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            SafeNumericKeypad(
                enabled = !busy,
                showBiometric = false,
                onDigit = { d ->
                    errorMsg = null
                    if (activePin.length < SafeCrypto.SAFE_PIN_LENGTH) {
                        val next = activePin + d
                        if (isConfirmStep) confirmPin = next else firstPin = next
                        if (next.length == SafeCrypto.SAFE_PIN_LENGTH) {
                            handlePinComplete(next)
                        }
                    }
                },
                onBackspace = {
                    errorMsg = null
                    if (activePin.isNotEmpty()) {
                        if (isConfirmStep) confirmPin = confirmPin.dropLast(1)
                        else firstPin = firstPin.dropLast(1)
                    }
                },
            )
        }
    }
}

// ── Custom Canvas Artwork for Vault+ Flash Intro Carousel ────────────────────

@Composable
private fun SafeIntroDocumentArt(modifier: Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy),
        )

        // Back card tilted
        rotate(-10f, pivot = Offset(cx, cy)) {
            drawRoundRect(
                color = Color(0xFF1E3A5F),
                topLeft = Offset(w * 0.16f, h * 0.22f),
                size = Size(w * 0.62f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.35f),
                topLeft = Offset(w * 0.16f, h * 0.22f),
                size = Size(w * 0.62f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Stroke(width = w * 0.012f),
            )
        }

        // Front document card
        rotate(4f, pivot = Offset(cx, cy)) {
            drawRoundRect(
                brush = Brush.linearGradient(
                    listOf(Color(0xFF121824), Color(0xFF1D293D)),
                ),
                topLeft = Offset(w * 0.22f, h * 0.28f),
                size = Size(w * 0.62f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
            )
            drawRoundRect(
                color = accent.copy(alpha = 0.7f),
                topLeft = Offset(w * 0.22f, h * 0.28f),
                size = Size(w * 0.62f, h * 0.48f),
                cornerRadius = CornerRadius(w * 0.08f, w * 0.08f),
                style = Stroke(width = w * 0.016f),
            )
            // ID badge photo square + lines
            drawRoundRect(
                color = accent.copy(alpha = 0.26f),
                topLeft = Offset(w * 0.28f, h * 0.36f),
                size = Size(w * 0.17f, w * 0.20f),
                cornerRadius = CornerRadius(w * 0.03f, w * 0.03f),
            )
            drawRoundRect(
                color = accent,
                topLeft = Offset(w * 0.50f, h * 0.38f),
                size = Size(w * 0.26f, h * 0.045f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = Color(0xFF64748B),
                topLeft = Offset(w * 0.50f, h * 0.46f),
                size = Size(w * 0.20f, h * 0.04f),
                cornerRadius = CornerRadius(10f, 10f),
            )
            drawRoundRect(
                color = Color(0xFF475569),
                topLeft = Offset(w * 0.28f, h * 0.62f),
                size = Size(w * 0.48f, h * 0.045f),
                cornerRadius = CornerRadius(10f, 10f),
            )
        }
    }
}

@Composable
private fun SafeIntroKeyArt(modifier: Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.24f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy),
        )

        drawCircle(
            color = accent.copy(alpha = 0.22f),
            radius = w * 0.36f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.018f),
        )
        drawCircle(
            color = Color(0xFF38BDF8).copy(alpha = 0.45f),
            radius = w * 0.28f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.012f),
        )

        // Key ring + blade
        drawCircle(
            color = accent,
            radius = w * 0.13f,
            center = Offset(cx - w * 0.12f, cy),
            style = Stroke(width = w * 0.045f),
        )
        drawCircle(
            color = Color(0xFF111827),
            radius = w * 0.06f,
            center = Offset(cx - w * 0.12f, cy),
        )
        drawLine(
            color = accent,
            start = Offset(cx, cy),
            end = Offset(cx + w * 0.25f, cy),
            strokeWidth = w * 0.045f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(cx + w * 0.14f, cy),
            end = Offset(cx + w * 0.14f, cy + h * 0.09f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round,
        )
        drawLine(
            color = accent,
            start = Offset(cx + w * 0.23f, cy),
            end = Offset(cx + w * 0.23f, cy + h * 0.12f),
            strokeWidth = w * 0.04f,
            cap = StrokeCap.Round,
        )
    }
}

@Composable
private fun SafeIntroShieldArt(modifier: Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(Color(0xFF22C55E).copy(alpha = 0.20f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy),
        )

        val shield = Path().apply {
            moveTo(cx, h * 0.16f)
            lineTo(w * 0.78f, h * 0.28f)
            lineTo(w * 0.75f, h * 0.56f)
            quadraticTo(w * 0.68f, h * 0.78f, cx, h * 0.86f)
            quadraticTo(w * 0.32f, h * 0.78f, w * 0.25f, h * 0.56f)
            lineTo(w * 0.22f, h * 0.28f)
            close()
        }
        drawPath(
            path = shield,
            brush = Brush.verticalGradient(
                listOf(accent.copy(alpha = 0.28f), Color(0xFF111827).copy(alpha = 0.9f)),
            ),
        )
        drawPath(
            path = shield,
            color = accent,
            style = Stroke(width = w * 0.025f, join = StrokeJoin.Round),
        )

        // Padlock body inside shield
        drawArc(
            color = Color.White,
            startAngle = 180f,
            sweepAngle = 180f,
            useCenter = false,
            topLeft = Offset(cx - w * 0.08f, h * 0.35f),
            size = Size(w * 0.16f, h * 0.14f),
            style = Stroke(width = w * 0.03f, cap = StrokeCap.Round),
        )
        drawRoundRect(
            color = Color(0xFF4ADE80),
            topLeft = Offset(cx - w * 0.12f, h * 0.43f),
            size = Size(w * 0.24f, h * 0.19f),
            cornerRadius = CornerRadius(w * 0.04f, w * 0.04f),
        )
    }
}

@Composable
private fun SafeIntroCloudArt(modifier: Modifier, accent: Color) {
    Canvas(modifier = modifier) {
        val w = size.width
        val h = size.height
        val cx = w / 2f
        val cy = h / 2f

        drawCircle(
            brush = Brush.radialGradient(
                listOf(accent.copy(alpha = 0.26f), Color.Transparent),
                center = Offset(cx, cy),
                radius = w * 0.48f,
            ),
            radius = w * 0.48f,
            center = Offset(cx, cy),
        )

        // Vault door circle
        drawCircle(
            color = Color(0xFF172030),
            radius = w * 0.31f,
            center = Offset(cx, cy),
        )
        drawCircle(
            color = accent,
            radius = w * 0.31f,
            center = Offset(cx, cy),
            style = Stroke(width = w * 0.025f),
        )
        // Checkmark badge in center
        val check = Path().apply {
            moveTo(cx - w * 0.11f, cy + h * 0.01f)
            lineTo(cx - w * 0.02f, cy + h * 0.10f)
            lineTo(cx + w * 0.13f, cy - h * 0.08f)
        }
        drawPath(
            path = check,
            color = Color(0xFF4ADE80),
            style = Stroke(width = w * 0.038f, cap = StrokeCap.Round, join = StrokeJoin.Round),
        )
    }
}
