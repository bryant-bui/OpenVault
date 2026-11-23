package io.openvault.app.ui.screens

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Backspace
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
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
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Clean flat OLED Dark / Light Unlock Screen optimized for Galaxy Z Fold 3:
 * - Cover screen (320dp): scaled 3+3 PIN slots (38dp) & 62dp keypad circles with zero overflow
 * - Unfolded inner display (>=580dp): spacious split 2-column PIN unlock or centered 440dp Master Password card
 */
@Composable
fun UnlockScreen(
    repository: VaultRepository,
    activity: FragmentActivity?,
    autoTriggerBiometric: Boolean = true,
    allowLogout: Boolean = true,
    onUnlocked: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    val scope = rememberCoroutineScope()

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

    fun triggerShake() {
        haptic.performHapticFeedback(HapticFeedbackType.LongPress)
        scope.launch {
            for (target in listOf(-16f, 16f, -10f, 10f, -5f, 0f)) {
                shakeOffset.animateTo(target, tween(40))
            }
        }
    }

    fun launchBiometricPrompt() {
        val host = activity ?: return
        val blob = repository.preferences.getBiometricBlob() ?: return
        val cipher = runCatching { BiometricKeyManager.createBiometricDecryptCipher(blob) }.getOrElse {
            repository.disableBiometric()
            errorMessage = "Khóa sinh trắc học đã thay đổi. Vui lòng mở bằng mật khẩu chính."
            return
        }
        BiometricKeyManager.showBiometricPrompt(
            activity = host,
            title = "Mở khóa OpenVault",
            subtitle = account.email,
            cipher = cipher,
            onSuccess = { authedCipher ->
                if (repository.unlockWithBiometricCipher(authedCipher)) {
                    onUnlocked()
                } else {
                    errorMessage = "Không thể giải mã khóa bằng sinh trắc học."
                }
            },
            onError = { msg -> errorMessage = msg },
        )
    }

    LaunchedEffect(canUseBiometric, autoTriggerBiometric) {
        if (canUseBiometric && autoTriggerBiometric) {
            launchBiometricPrompt()
        }
    }

    fun submitPin(code: String) {
        if (busy) return
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
        if (busy || !settings.pinEnabled) return
        val targetLen = settings.pinLength.coerceIn(4, 12)
        if (pinDigits.length >= targetLen) return
        haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        errorMessage = null
        val updated = pinDigits + digit
        pinDigits = updated
        if (updated.length == targetLen) submitPin(updated)
    }

    val pinSlotWidth = if (fold.isNarrowCover) 38.dp else 44.dp
    val pinSlotHeight = if (fold.isNarrowCover) 48.dp else 54.dp
    val pinGap = if (fold.isNarrowCover) 6.dp else 8.dp
    val pinSplitGap = if (fold.isNarrowCover) 12.dp else 16.dp
    val keySize = if (fold.isNarrowCover) 62.dp else 72.dp
    val keyHSpacing = if (fold.isNarrowCover) 16.dp else 22.dp

    @Composable
    fun BrandHeader(modifier: Modifier = Modifier) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = modifier,
        ) {
            OpenVaultBrandCrest(size = if (fold.isNarrowCover) 52.dp else 62.dp)
            Text(
                text = s.unlockTitle,
                color = c.textPrimary,
                fontSize = if (fold.isNarrowCover) 21.sp else 24.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
                textAlign = TextAlign.Center,
            )
            Text(
                text = account.email,
                color = c.textSecondary,
                fontSize = 13.5.sp,
                textAlign = TextAlign.Center,
            )
        }
    }

    @Composable
    fun FooterActions() {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (settings.pinEnabled) {
                TextButton(onClick = {
                    useMasterMode = !useMasterMode
                    errorMessage = null
                    pinDigits = ""
                }) {
                    Text(
                        if (useMasterMode) s.usePin else s.useMasterPassword,
                        color = c.accent,
                        fontWeight = FontWeight.Medium,
                    )
                }
            }
            if (allowLogout) {
                TextButton(onClick = { scope.launch { repository.logout(allDevices = false) } }) {
                    Text(if (s.isVi) "Đăng xuất" else "Sign out", color = c.textSecondary)
                }
            }
        }
    }

    @Composable
    fun PinPadBlock(modifier: Modifier = Modifier) {
        val targetLen = settings.pinLength.coerceIn(4, 12)
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(if (fold.isNarrowCover) 14.dp else 18.dp),
            modifier = modifier,
        ) {
            Text(
                text = if (busy) s.unlocking else "${s.enterPinPrefix} ($targetLen)",
                color = c.textSecondary,
                fontSize = 14.sp,
            )

            // Responsive 3 + 3 PIN Boxes
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
                    val active = i == pinDigits.length && !busy
                    val dotScale by animateFloatAsState(
                        targetValue = if (filled) 1f else 0f,
                        animationSpec = spring(Spring.DampingRatioMediumBouncy, Spring.StiffnessMedium),
                        label = "pin_dot_$i",
                    )
                    val boxShape = RoundedCornerShape(12.dp)
                    Box(
                        modifier = Modifier
                            .size(width = pinSlotWidth, height = pinSlotHeight)
                            .clip(boxShape)
                            .background(c.surface)
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
                                    .height(20.dp)
                                    .clip(CircleShape)
                                    .background(c.accent),
                            )
                        }
                    }
                }
            }

            if (errorMessage != null) {
                Text(
                    text = errorMessage!!,
                    color = c.danger,
                    fontSize = 13.sp,
                    textAlign = TextAlign.Center,
                )
            } else {
                Spacer(modifier = Modifier.height(14.dp))
            }

            // Responsive Numeric Keypad
            Column(
                verticalArrangement = Arrangement.spacedBy(if (fold.isNarrowCover) 10.dp else 12.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                listOf(
                    listOf("1", "2", "3"),
                    listOf("4", "5", "6"),
                    listOf("7", "8", "9"),
                ).forEach { row ->
                    Row(horizontalArrangement = Arrangement.spacedBy(keyHSpacing)) {
                        row.forEach { digit ->
                            FlatKeypadButton(digit, size = keySize, enabled = !busy) { appendPinDigit(digit) }
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
                                .clickable { launchBiometricPrompt() },
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = "Vân tay", tint = c.navActiveContent, modifier = Modifier.size(28.dp))
                        }
                    } else {
                        Spacer(modifier = Modifier.size(keySize))
                    }

                    FlatKeypadButton("0", size = keySize, enabled = !busy) { appendPinDigit("0") }

                    Box(
                        modifier = Modifier
                            .size(keySize)
                            .clip(CircleShape)
                            .clickable {
                                if (!busy && pinDigits.isNotEmpty()) pinDigits = pinDigits.dropLast(1)
                            },
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.Backspace, contentDescription = "Xóa", tint = c.textSecondary, modifier = Modifier.size(23.dp))
                    }
                }
            }
        }
    }

    @Composable
    fun MasterPasswordCard() {
        OnePassCard(
            modifier = Modifier
                .widthIn(max = 440.dp)
                .fillMaxWidth(),
            cornerRadius = 16.dp,
            borderColor = c.cardBorder,
            padding = PaddingValues(18.dp),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                OutlinedTextField(
                    value = masterPassword,
                    onValueChange = { masterPassword = it; errorMessage = null },
                    label = { Text(s.masterPassword) },
                    singleLine = true,
                    visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = {
                        IconButton(onClick = { showPassword = !showPassword }) {
                            Icon(
                                if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                contentDescription = "Hiện/Ẩn",
                                tint = c.textSecondary,
                            )
                        }
                    },
                    shape = RoundedCornerShape(10.dp),
                    colors = OutlinedTextFieldDefaults.colors(
                        focusedBorderColor = c.accent,
                        unfocusedBorderColor = c.cardBorder,
                        focusedTextColor = c.textPrimary,
                        unfocusedTextColor = c.textPrimary,
                        focusedLabelColor = c.accent,
                        unfocusedLabelColor = c.textSecondary,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )

                if (errorMessage != null) {
                    Text(errorMessage!!, color = c.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }

                Button(
                    onClick = {
                        if (masterPassword.isEmpty() || busy) return@Button
                        busy = true
                        errorMessage = null
                        scope.launch {
                            val ok = repository.unlockWithMasterPassword(masterPassword)
                            busy = false
                            if (ok) {
                                masterPassword = ""
                                onUnlocked()
                            } else {
                                errorMessage = "Mật khẩu chính không đúng."
                            }
                        }
                    },
                    enabled = !busy && masterPassword.isNotEmpty(),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                    modifier = Modifier.fillMaxWidth().height(48.dp),
                ) {
                    if (busy) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                    } else {
                        Text(s.unlockButton, fontWeight = FontWeight.SemiBold, fontSize = 15.5.sp)
                    }
                }

                if (canUseBiometric) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accentSoft)
                            .clickable { launchBiometricPrompt() }
                            .padding(vertical = 12.dp),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(21.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(s.unlockWithBiometricsCta, color = c.navActiveContent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                    }
                }
            }
        }
    }

    // Adaptive Layout: Split 2-column on unfolded Z Fold 3 PIN mode, centered vertical otherwise
    if (fold.isTwoPane && !useMasterMode && settings.pinEnabled) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .padding(horizontal = 32.dp, vertical = 24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(if (fold.hingeWidthDp > 0.dp) fold.hingeWidthDp + 24.dp else 32.dp),
        ) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxHeight(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) {
                BrandHeader()
                Spacer(modifier = Modifier.height(24.dp))
                FooterActions()
            }
            Box(
                modifier = Modifier.weight(1f),
                contentAlignment = Alignment.Center,
            ) {
                PinPadBlock()
            }
        }
    } else {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(c.background)
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = if (fold.isNarrowCover) 14.dp else 22.dp, vertical = 18.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            BrandHeader(modifier = Modifier.padding(top = if (fold.isNarrowCover) 8.dp else 18.dp))

            if (!useMasterMode && settings.pinEnabled) {
                PinPadBlock(modifier = Modifier.fillMaxWidth())
            } else {
                MasterPasswordCard()
            }

            FooterActions()
        }
    }
}

@Composable
private fun FlatKeypadButton(
    label: String,
    size: Dp,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .size(size)
            .clip(CircleShape)
            .background(c.surface)
            .border(if (c.isDark) 0.dp else 0.8.dp, if (c.isDark) Color.Transparent else c.cardBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = if (size < 66.dp) 23.sp else 26.sp,
            fontWeight = FontWeight.Normal,
        )
    }
}
