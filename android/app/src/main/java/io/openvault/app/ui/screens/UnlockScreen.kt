package io.openvault.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Vault-Door Unlock Screen inspired by 1Password 8 Android (Image 19):
 * - Split metallic Top Door Plate & Bottom Door Plate joined by a horizontal seam
 * - Centered 94dp metallic Cobalt Keyhole Dial right on the seam
 * - On authentication (Biometric / 6-digit PIN / Master Password):
 *     1) Keyhole core rotates 90 degrees clockwise with tactile haptic feedback
 *     2) Top plate (+ keyhole) glides UP and Bottom plate glides DOWN to physically
 *        reveal the live unlocked OpenVault home screen underneath!
 * - Autofill & Passkey activities pass animateDoorReveal = false for instant zero-delay return.
 */
@Composable
fun UnlockScreen(
    repository: VaultRepository,
    activity: FragmentActivity?,
    autoTriggerBiometric: Boolean = true,
    allowLogout: Boolean = true,
    animateDoorReveal: Boolean = false,
    isUnlockedReveal: Boolean = false,
    onDoorAnimationFinished: () -> Unit = {},
    onUnlocked: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val settings by repository.preferences.settings.collectAsState()
    val account by repository.preferences.account.collectAsState()

    val canUseBiometric = settings.biometricEnabled &&
        BiometricKeyManager.isBiometricAvailable(context) &&
        activity != null

    var useMasterMode by remember(settings.pinEnabled) { mutableStateOf(!settings.pinEnabled) }
    var pinDigits by remember { mutableStateOf("") }
    var masterPassword by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var busy by remember { mutableStateOf(false) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    val shakeOffset = remember { Animatable(0f) }
    val keyholeRotation = remember { Animatable(0f) }
    val doorSplit = remember { Animatable(0f) }

    LaunchedEffect(isUnlockedReveal, animateDoorReveal) {
        if (isUnlockedReveal) {
            if (!animateDoorReveal) {
                onDoorAnimationFinished()
            } else {
                haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                keyholeRotation.animateTo(
                    targetValue = 90f,
                    animationSpec = tween(durationMillis = 270, easing = FastOutSlowInEasing),
                )
                delay(55L)
                doorSplit.animateTo(
                    targetValue = 1f,
                    animationSpec = tween(durationMillis = 360, easing = FastOutSlowInEasing),
                )
                onDoorAnimationFinished()
            }
        } else {
            keyholeRotation.snapTo(0f)
            doorSplit.snapTo(0f)
        }
    }

    fun triggerShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            for (target in listOf(-16f, 16f, -10f, 10f, -5f, 0f)) {
                shakeOffset.animateTo(target, tween(38))
            }
        }
    }

    fun launchBiometricPrompt() {
        if (isUnlockedReveal) return
        val host = activity ?: return
        val blob = repository.preferences.getBiometricBlob() ?: return
        val cipher = runCatching { BiometricKeyManager.createBiometricDecryptCipher(blob) }.getOrElse {
            repository.disableBiometric()
            errorMessage = if (s.isVi) {
                "Khóa sinh trắc học đã thay đổi. Vui lòng mở bằng mật khẩu chính."
            } else {
                "Biometric key changed. Please unlock with your master password."
            }
            return
        }
        BiometricKeyManager.showBiometricPrompt(
            activity = host,
            title = if (s.isVi) "Mở khóa OpenVault" else "Unlock OpenVault",
            subtitle = account.email,
            cipher = cipher,
            onSuccess = { authedCipher ->
                if (repository.unlockWithBiometricCipher(authedCipher)) {
                    onUnlocked()
                } else {
                    errorMessage = if (s.isVi) {
                        "Không thể giải mã khóa bằng sinh trắc học."
                    } else {
                        "Could not decrypt vault key with biometrics."
                    }
                }
            },
            onError = { msg -> errorMessage = msg },
        )
    }

    LaunchedEffect(canUseBiometric, autoTriggerBiometric, isUnlockedReveal) {
        if (canUseBiometric && autoTriggerBiometric && !isUnlockedReveal) {
            launchBiometricPrompt()
        }
    }

    fun submitMasterPassword() {
        if (masterPassword.isEmpty() || busy || isUnlockedReveal) return
        busy = true
        errorMessage = null
        scope.launch {
            val ok = repository.unlockWithMasterPassword(masterPassword)
            busy = false
            if (ok) {
                masterPassword = ""
                onUnlocked()
            } else {
                errorMessage = if (s.isVi) "Mật khẩu chính không đúng." else "Incorrect master password."
                triggerShake()
            }
        }
    }

    fun submitPin(code: String) {
        if (busy || isUnlockedReveal) return
        busy = true
        errorMessage = null
        scope.launch {
            val outcome = repository.unlockWithPin(code)
            busy = false
            if (outcome.success) {
                pinDigits = ""
                onUnlocked()
            } else {
                pinDigits = ""
                errorMessage = outcome.errorMessage
                triggerShake()
                if (outcome.pinWiped) useMasterMode = true
            }
        }
    }

    fun appendPinDigit(digit: String) {
        if (busy || !settings.pinEnabled || isUnlockedReveal) return
        val targetLen = settings.pinLength.coerceIn(4, 12)
        if (pinDigits.length >= targetLen) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorMessage = null
        val updated = pinDigits + digit
        pinDigits = updated
        if (updated.length == targetLen) submitPin(updated)
    }

    // Metallic vault door palette (dark & light)
    val topDoorBrush = if (c.isDark) {
        Brush.verticalGradient(listOf(Color(0xFF2E3034), Color(0xFF282A2E)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFE6ECF5), Color(0xFFDDE5F0)))
    }
    val bottomDoorBrush = if (c.isDark) {
        Brush.verticalGradient(listOf(Color(0xFF24262A), Color(0xFF1D1F23)))
    } else {
        Brush.verticalGradient(listOf(Color(0xFFF6F8FC), Color(0xFFEEF2F8)))
    }
    val seamDarkLine = if (c.isDark) Color(0xFF121316) else Color(0xFFCBD5E1)
    val seamHighlightLine = if (c.isDark) Color(0xFF3A3D44) else Color(0xFFFFFFFF)
    val inputBg = if (c.isDark) Color(0xFF1B1D21) else Color.White

    val pinSlotWidth = if (fold.isNarrowCover) 38.dp else 44.dp
    val pinSlotHeight = if (fold.isNarrowCover) 46.dp else 52.dp
    val pinGap = if (fold.isNarrowCover) 6.dp else 8.dp
    val pinSplitGap = if (fold.isNarrowCover) 12.dp else 16.dp
    val keySize = if (fold.isNarrowCover) 58.dp else 68.dp
    val keyHSpacing = if (fold.isNarrowCover) 16.dp else 22.dp

    val showingPin = !useMasterMode && settings.pinEnabled

    BoxWithConstraints(modifier = Modifier.fillMaxSize()) {
        val totalHeight = maxHeight
        val seamFraction = if (showingPin) 0.19f else 0.25f
        val topPlateHeight = (totalHeight * seamFraction).coerceAtLeast(132.dp)
        val bottomPlateHeight = totalHeight - topPlateHeight
        val dialSize = if (fold.isNarrowCover) 86.dp else 94.dp

        val topPlatePx = with(density) { topPlateHeight.toPx() }
        val bottomPlatePx = with(density) { bottomPlateHeight.toPx() }
        val dialHalfPx = with(density) { (dialSize / 2).toPx() }
        val split = doorSplit.value

        // ── TOP VAULT DOOR PLATE (slides UP when unlocked) ──────────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(topPlateHeight)
                .align(Alignment.TopCenter)
                .graphicsLayer {
                    translationY = -split * (topPlatePx + dialHalfPx + 48f)
                }
                .background(topDoorBrush),
        ) {
            // Subtle bottom bevel along the split seam
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.BottomCenter)
                    .background(seamDarkLine),
            )
        }

        // ── BOTTOM VAULT DOOR PLATE (slides DOWN when unlocked) ─────────────
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(bottomPlateHeight)
                .align(Alignment.BottomCenter)
                .graphicsLayer {
                    translationY = split * (bottomPlatePx + 48f)
                }
                .background(bottomDoorBrush),
        ) {
            // Top highlight lip along the split seam
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .align(Alignment.TopCenter)
                    .background(seamHighlightLine),
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .navigationBarsPadding()
                    .imePadding()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = if (fold.isNarrowCover) 16.dp else 24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                // Leave room for the lower half of the 94dp keyhole dial
                Spacer(modifier = Modifier.height(dialSize / 2 + if (showingPin) 14.dp else 24.dp))

                // User Avatar (custom photo or monogram badge)
                OnePassAccountAvatar(
                    email = account.email,
                    size = if (fold.isNarrowCover) 52.dp else 58.dp,
                )

                Spacer(modifier = Modifier.height(if (showingPin) 12.dp else 22.dp))

                if (showingPin) {
                    // ── 6-DIGIT INSTANT AUTO-UNLOCK PIN MODE ────────────────
                    val targetLen = settings.pinLength.coerceIn(4, 12)
                    Text(
                        text = if (busy) s.unlocking else "${s.enterPinPrefix} ($targetLen)",
                        color = c.textSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                    Spacer(modifier = Modifier.height(12.dp))

                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                    ) {
                        val splitAt = if (targetLen == 6) 3 else -1
                        for (i in 0 until targetLen) {
                            if (i == splitAt) Spacer(modifier = Modifier.width(pinSplitGap))
                            else if (i > 0) Spacer(modifier = Modifier.width(pinGap))

                            val filled = i < pinDigits.length
                            val active = i == pinDigits.length && !busy && !isUnlockedReveal
                            val dotScale by animateFloatAsState(
                                targetValue = if (filled) 1f else 0f,
                                animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                                label = "pin_dot_$i",
                            )
                            val boxShape = RoundedCornerShape(11.dp)
                            Box(
                                modifier = Modifier
                                    .size(width = pinSlotWidth, height = pinSlotHeight)
                                    .clip(boxShape)
                                    .background(inputBg)
                                    .border(
                                        width = if (active || errorMessage != null) 1.6.dp else 1.dp,
                                        color = when {
                                            errorMessage != null -> c.danger
                                            active -> c.accent
                                            else -> c.cardBorder
                                        },
                                        shape = boxShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (filled) {
                                    Box(
                                        modifier = Modifier
                                            .scale(dotScale)
                                            .size(11.dp)
                                            .clip(CircleShape)
                                            .background(c.textPrimary),
                                    )
                                } else if (active) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(19.dp)
                                            .clip(CircleShape)
                                            .background(c.accent),
                                    )
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = c.danger,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    } else {
                        Text(
                            text = account.email,
                            color = c.textMuted,
                            fontSize = 12.5.sp,
                            textAlign = TextAlign.Center,
                        )
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Numeric Keypad
                    Column(
                        verticalArrangement = Arrangement.spacedBy(if (fold.isNarrowCover) 9.dp else 11.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                        ).forEach { row ->
                            Row(horizontalArrangement = Arrangement.spacedBy(keyHSpacing)) {
                                row.forEach { digit ->
                                    VaultKeypadButton(
                                        label = digit,
                                        size = keySize,
                                        enabled = !busy && !isUnlockedReveal,
                                        bg = inputBg,
                                    ) { appendPinDigit(digit) }
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(keyHSpacing)) {
                            if (canUseBiometric) {
                                Box(
                                    modifier = Modifier
                                        .size(keySize)
                                        .clip(CircleShape)
                                        .background(c.accentSoft)
                                        .clickable(enabled = !isUnlockedReveal) { launchBiometricPrompt() },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        Icons.Rounded.Fingerprint,
                                        contentDescription = s.unlockWithBiometricsCta,
                                        tint = c.navActiveContent,
                                        modifier = Modifier.size(27.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(keySize))
                            }

                            VaultKeypadButton(
                                label = "0",
                                size = keySize,
                                enabled = !busy && !isUnlockedReveal,
                                bg = inputBg,
                            ) { appendPinDigit("0") }

                            Box(
                                modifier = Modifier
                                    .size(keySize)
                                    .clip(CircleShape)
                                    .clickable(enabled = !busy && !isUnlockedReveal) {
                                        if (pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = s.delete,
                                    tint = c.textSecondary,
                                    modifier = Modifier.size(22.dp),
                                )
                            }
                        }
                    }
                } else {
                    // ── MASTER PASSWORD MODE (Authentic Image 19 Layout) ────
                    Row(
                        modifier = Modifier
                            .widthIn(max = 380.dp)
                            .fillMaxWidth()
                            .height(54.dp)
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) }
                            .clip(RoundedCornerShape(10.dp))
                            .background(inputBg)
                            .border(
                                width = 1.6.dp,
                                color = if (errorMessage != null) c.danger else Color(0xFF1D6CE0),
                                shape = RoundedCornerShape(10.dp),
                            ),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .padding(start = 15.dp, end = 4.dp),
                            contentAlignment = Alignment.CenterStart,
                        ) {
                            if (masterPassword.isEmpty()) {
                                Text(
                                    text = if (s.isVi) "Nhập mật khẩu chính" else "Enter your password",
                                    color = c.textMuted,
                                    fontSize = 15.5.sp,
                                )
                            }
                            BasicTextField(
                                value = masterPassword,
                                onValueChange = {
                                    masterPassword = it
                                    errorMessage = null
                                },
                                singleLine = true,
                                enabled = !busy && !isUnlockedReveal,
                                visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                                textStyle = TextStyle(
                                    color = c.textPrimary,
                                    fontSize = 15.5.sp,
                                    fontWeight = FontWeight.Normal,
                                ),
                                cursorBrush = SolidColor(c.accent),
                                keyboardOptions = KeyboardOptions(
                                    keyboardType = KeyboardType.Password,
                                    imeAction = ImeAction.Done,
                                ),
                                keyboardActions = KeyboardActions(onDone = { submitMasterPassword() }),
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }

                        if (masterPassword.isNotEmpty()) {
                            IconButton(
                                onClick = { showPassword = !showPassword },
                                modifier = Modifier.size(36.dp),
                            ) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = c.textMuted,
                                    modifier = Modifier.size(18.dp),
                                )
                            }
                        }

                        // Subtle vertical divider inside the input bar
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(26.dp)
                                .background(if (c.isDark) Color(0xFF393C44) else c.divider),
                        )

                        // Trailing Action: Submit arrow if password typed, otherwise Biometric Fingerprint!
                        Box(
                            modifier = Modifier
                                .width(52.dp)
                                .height(54.dp)
                                .clickable(enabled = !busy && !isUnlockedReveal) {
                                    if (masterPassword.isNotEmpty()) {
                                        submitMasterPassword()
                                    } else if (canUseBiometric) {
                                        launchBiometricPrompt()
                                    }
                                },
                            contentAlignment = Alignment.Center,
                        ) {
                            when {
                                busy -> CircularProgressIndicator(
                                    color = c.accent,
                                    strokeWidth = 2.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                                masterPassword.isNotEmpty() -> Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = s.unlockButton,
                                    tint = c.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                                canUseBiometric -> Icon(
                                    Icons.Rounded.Fingerprint,
                                    contentDescription = s.unlockWithBiometricsCta,
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(26.dp),
                                )
                                else -> Icon(
                                    Icons.AutoMirrored.Rounded.ArrowForward,
                                    contentDescription = s.unlockButton,
                                    tint = c.textMuted,
                                    modifier = Modifier.size(21.dp),
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))

                    Text(
                        text = errorMessage ?: if (s.isVi) "OpenVault đang khóa." else "OpenVault is locked.",
                        color = if (errorMessage != null) c.danger else c.textSecondary,
                        fontSize = 13.5.sp,
                        fontWeight = if (errorMessage != null) FontWeight.Medium else FontWeight.Normal,
                        textAlign = TextAlign.Center,
                    )

                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = account.email,
                        color = c.textMuted,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Footer switches
                Row(
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.padding(bottom = 18.dp),
                ) {
                    if (settings.pinEnabled) {
                        TextButton(
                            onClick = {
                                useMasterMode = !useMasterMode
                                errorMessage = null
                                pinDigits = ""
                            },
                            enabled = !isUnlockedReveal,
                        ) {
                            Text(
                                if (useMasterMode) s.usePin else s.useMasterPassword,
                                color = c.accent,
                                fontWeight = FontWeight.Medium,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                    if (allowLogout) {
                        TextButton(
                            onClick = { scope.launch { repository.logout(allDevices = false) } },
                            enabled = !isUnlockedReveal,
                        ) {
                            Text(
                                if (s.isVi) "Đăng xuất" else "Sign out",
                                color = c.textSecondary,
                                fontSize = 13.5.sp,
                            )
                        }
                    }
                }
            }
        }

        // ── CENTERED ROTATING KEYHOLE DIAL RIGHT ON THE SEAM ────────────────
        Box(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .offset(y = topPlateHeight - dialSize / 2)
                .graphicsLayer {
                    translationY = -split * (topPlatePx + dialHalfPx + 48f)
                }
                .size(dialSize),
            contentAlignment = Alignment.Center,
        ) {
            VaultKeyholeDial(
                rotationDegrees = keyholeRotation.value,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

/**
 * Metallic circular vault dial matching Image 19:
 * - Outer silver/white beveled ring
 * - Cobalt-to-sky gradient ring
 * - Soft recessed silver center disc
 * - Rotating notched dark-navy keyhole bolt (rotates 0° -> 90° on unlock!)
 */
@Composable
private fun VaultKeyholeDial(
    rotationDegrees: Float,
    modifier: Modifier = Modifier,
) {
    Canvas(modifier = modifier) {
        val r = size.minDimension / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        // Subtle outer drop shadow
        drawCircle(
            brush = Brush.radialGradient(
                colors = listOf(Color.Black.copy(alpha = 0.42f), Color.Transparent),
                center = Offset(center.x, center.y + r * 0.08f),
                radius = r * 1.08f,
            ),
            radius = r * 1.06f,
            center = Offset(center.x, center.y + r * 0.06f),
        )

        // Outer polished silver/white ring
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFF8FAFC), Color(0xFFCBD5E1), Color(0xFFE2E8F0)),
                start = Offset(0f, 0f),
                end = Offset(size.width, size.height),
            ),
            radius = r * 0.96f,
            center = center,
        )

        // Inner Cobalt / Sapphire ring
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFF1A56DB), Color(0xFF2563EB), Color(0xFF60A5FA)),
                start = Offset(center.x, center.y - r),
                end = Offset(center.x, center.y + r),
            ),
            radius = r * 0.80f,
            center = center,
        )

        // Subtle light-blue halo ring inside Cobalt
        drawCircle(
            color = Color(0xFF93C5FD).copy(alpha = 0.45f),
            radius = r * 0.66f,
            center = center,
            style = Stroke(width = r * 0.06f),
        )

        // Recessed bright center cylinder disc
        drawCircle(
            brush = Brush.linearGradient(
                colors = listOf(Color(0xFFFFFFFF), Color(0xFFE2E8F0)),
                start = Offset(center.x - r * 0.4f, center.y - r * 0.4f),
                end = Offset(center.x + r * 0.4f, center.y + r * 0.4f),
            ),
            radius = r * 0.56f,
            center = center,
        )

        // Rotating notched keyhole cylinder (0° -> 90° clockwise on unlock!)
        rotate(degrees = rotationDegrees, pivot = center) {
            val slotW = r * 0.25f
            val slotH = r * 0.72f
            val left = center.x - slotW / 2f
            val top = center.y - slotH / 2f

            drawRoundRect(
                color = Color(0xFF101828),
                topLeft = Offset(left, top),
                size = Size(slotW, slotH),
                cornerRadius = CornerRadius(slotW * 0.28f, slotW * 0.28f),
            )

            // Signature triangular inner lock notches (upper-left & lower-right)
            val notchPath = Path().apply {
                // Upper-left triangular notch
                moveTo(left - 1f, center.y - slotH * 0.14f)
                lineTo(left + slotW * 0.42f, center.y - slotH * 0.04f)
                lineTo(left - 1f, center.y + slotH * 0.04f)
                close()
                // Lower-right triangular notch
                moveTo(left + slotW + 1f, center.y + slotH * 0.02f)
                lineTo(left + slotW * 0.58f, center.y + slotH * 0.12f)
                lineTo(left + slotW + 1f, center.y + slotH * 0.22f)
                close()
            }
            drawPath(notchPath, color = Color(0xFFEAF0F7))
        }
    }
}

@Composable
private fun VaultKeypadButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    bg: Color,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(bg)
            .border(0.9.dp, c.cardBorder.copy(alpha = 0.75f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = if (size < 64.dp) 22.sp else 25.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
