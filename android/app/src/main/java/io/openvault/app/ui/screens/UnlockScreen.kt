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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.security.BiometricKeyManager
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Atmospheric Apple Liquid Glass Unlock Screen:
 * - Instant BiometricPrompt (StrongBox / Android Keystore) auto-trigger if enabled
 * - 3 + 3 rounded glass PIN boxes with instant auto-unlock on the last digit + shake feedback
 * - Seamless switch to Master Password unlock
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
            for (target in listOf(-18f, 18f, -12f, 12f, -6f, 0f)) {
                shakeOffset.animateTo(target, tween(42))
            }
        }
    }

    fun launchBiometricPrompt() {
        val host = activity ?: return
        val blob = repository.preferences.getBiometricBlob() ?: return
        val cipher = runCatching { BiometricKeyManager.createBiometricDecryptCipher(blob) }.getOrElse {
            repository.disableBiometric()
            errorMessage = "Khóa sinh trắc học đã thay đổi. Vui lòng mở bằng mật khẩu chính và bật lại."
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
            onError = { msg ->
                errorMessage = msg
            },
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
                if (outcome.pinWiped) {
                    useMasterMode = true
                }
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
        if (updated.length == targetLen) {
            submitPin(updated)
        }
    }

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding()
                .padding(horizontal = 22.dp, vertical = 20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween,
        ) {
            // Top Brand & Identity
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(10.dp),
                modifier = Modifier.padding(top = 18.dp),
            ) {
                OpenVaultBrandCrest(size = 66.dp)
                Text(
                    text = "Mở khóa OpenVault",
                    color = c.textPrimary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.5).sp,
                )
                Text(
                    text = account.email,
                    color = c.textSecondary,
                    fontSize = 13.5.sp,
                )
            }

            // Center: Either 3 + 3 Auto-Unlock PIN OR Master Password Card
            if (!useMasterMode && settings.pinEnabled) {
                val targetLen = settings.pinLength.coerceIn(4, 12)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(18.dp),
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Text(
                        text = if (busy) "Đang giải mã kho…" else "Nhập mã PIN ($targetLen số)",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )

                    // 3 + 3 Liquid Glass PIN boxes
                    Row(
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                    ) {
                        val splitAt = if (targetLen == 6) 3 else -1
                        for (i in 0 until targetLen) {
                            if (i == splitAt) {
                                Spacer(modifier = Modifier.width(16.dp))
                            } else if (i > 0) {
                                Spacer(modifier = Modifier.width(8.dp))
                            }
                            val filled = i < pinDigits.length
                            val active = i == pinDigits.length && !busy
                            val dotScale by animateFloatAsState(
                                targetValue = if (filled) 1f else 0f,
                                animationSpec = spring(
                                    dampingRatio = Spring.DampingRatioMediumBouncy,
                                    stiffness = Spring.StiffnessMedium,
                                ),
                                label = "pin_dot_$i",
                            )
                            val boxShape = RoundedCornerShape(15.dp)
                            Box(
                                modifier = Modifier
                                    .size(width = 44.dp, height = 54.dp)
                                    .clip(boxShape)
                                    .background(
                                        if (active) c.accentSoft else c.glassSurfaceElevated.copy(alpha = 0.72f),
                                    )
                                    .border(
                                        width = if (active) 1.8.dp else 1.dp,
                                        color = when {
                                            errorMessage != null -> c.danger
                                            active -> c.accent
                                            filled -> c.glassBorderTop
                                            else -> c.glassBorderTop.copy(alpha = 0.25f)
                                        },
                                        shape = boxShape,
                                    ),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (filled) {
                                    Box(
                                        modifier = Modifier
                                            .scale(dotScale)
                                            .size(12.dp)
                                            .clip(CircleShape)
                                            .background(c.textPrimary),
                                    )
                                } else if (active) {
                                    Box(
                                        modifier = Modifier
                                            .width(2.dp)
                                            .height(22.dp)
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
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                    } else {
                        Spacer(modifier = Modifier.height(18.dp))
                    }

                    // Tactile Liquid Glass Numeric Keypad
                    Column(
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        val rows = listOf(
                            listOf("1", "2", "3"),
                            listOf("4", "5", "6"),
                            listOf("7", "8", "9"),
                        )
                        for (row in rows) {
                            Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                                for (digit in row) {
                                    GlassKeypadButton(
                                        label = digit,
                                        enabled = !busy,
                                        onClick = { appendPinDigit(digit) },
                                    )
                                }
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(20.dp)) {
                            if (canUseBiometric) {
                                GlassKeypadIconButton(
                                    onClick = { launchBiometricPrompt() },
                                    tint = c.accent,
                                ) {
                                    Icon(
                                        Icons.Rounded.Fingerprint,
                                        contentDescription = "Vân tay",
                                        tint = c.accent,
                                        modifier = Modifier.size(30.dp),
                                    )
                                }
                            } else {
                                Spacer(modifier = Modifier.size(72.dp))
                            }

                            GlassKeypadButton(
                                label = "0",
                                enabled = !busy,
                                onClick = { appendPinDigit("0") },
                            )

                            GlassKeypadIconButton(
                                onClick = {
                                    if (!busy && pinDigits.isNotEmpty()) {
                                        pinDigits = pinDigits.dropLast(1)
                                    }
                                },
                                tint = c.textSecondary,
                            ) {
                                Icon(
                                    Icons.AutoMirrored.Rounded.Backspace,
                                    contentDescription = "Xóa",
                                    tint = c.textSecondary,
                                    modifier = Modifier.size(24.dp),
                                )
                            }
                        }
                    }
                }
            } else {
                // Master Password Liquid Glass Card
                LiquidGlassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 26.dp,
                    elevated = true,
                    padding = PaddingValues(20.dp),
                ) {
                    Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                        OutlinedTextField(
                            value = masterPassword,
                            onValueChange = { masterPassword = it; errorMessage = null },
                            label = { Text("Mật khẩu chính (Master Password)") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            trailingIcon = {
                                IconButton(onClick = { showPassword = !showPassword }) {
                                    Icon(
                                        imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                        contentDescription = "Hiện/Ẩn",
                                        tint = c.textSecondary,
                                    )
                                }
                            },
                            shape = RoundedCornerShape(14.dp),
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = c.accent,
                                unfocusedBorderColor = c.glassBorderTop.copy(alpha = 0.25f),
                                focusedTextColor = c.textPrimary,
                                unfocusedTextColor = c.textPrimary,
                                focusedLabelColor = c.accent,
                                unfocusedLabelColor = c.textSecondary,
                            ),
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (errorMessage != null) {
                            Text(
                                text = errorMessage!!,
                                color = c.danger,
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                            )
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
                                        errorMessage = "Mật khẩu chính không chính xác."
                                    }
                                }
                            },
                            enabled = !busy && masterPassword.isNotEmpty(),
                            shape = RoundedCornerShape(16.dp),
                            colors = ButtonDefaults.buttonColors(
                                containerColor = c.accent,
                                contentColor = Color.White,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(50.dp),
                        ) {
                            if (busy) {
                                CircularProgressIndicator(
                                    color = Color.White,
                                    strokeWidth = 2.5.dp,
                                    modifier = Modifier.size(20.dp),
                                )
                            } else {
                                Text("Mở khóa kho", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                            }
                        }

                        if (canUseBiometric) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(14.dp))
                                    .background(c.accentSoft)
                                    .clickable { launchBiometricPrompt() }
                                    .padding(vertical = 12.dp),
                                horizontalArrangement = Arrangement.Center,
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(
                                    Icons.Rounded.Fingerprint,
                                    contentDescription = null,
                                    tint = c.accent,
                                    modifier = Modifier.size(22.dp),
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Mở nhanh bằng sinh trắc học",
                                    color = c.accent,
                                    fontWeight = FontWeight.SemiBold,
                                    fontSize = 14.sp,
                                )
                            }
                        }
                    }
                }
            }

            // Bottom Actions: Switch PIN/Master + Logout
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (settings.pinEnabled) {
                    TextButton(onClick = {
                        useMasterMode = !useMasterMode
                        errorMessage = null
                        pinDigits = ""
                    }) {
                        Text(
                            text = if (useMasterMode) "Dùng mã PIN" else "Dùng mật khẩu chính",
                            color = c.accent,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
                if (allowLogout) {
                    TextButton(onClick = {
                        scope.launch { repository.logout(allDevices = false) }
                    }) {
                        Text(
                            text = "Đăng xuất",
                            color = c.textSecondary,
                            fontWeight = FontWeight.Medium,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun GlassKeypadButton(
    label: String,
    enabled: Boolean,
    onClick: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(
                Brush.verticalGradient(
                    listOf(c.glassSurfaceElevated.copy(alpha = 0.8f), c.glassSurface.copy(alpha = 0.5f)),
                ),
            )
            .border(1.dp, c.glassBorderTop.copy(alpha = 0.35f), CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = label,
            color = c.textPrimary,
            fontSize = 26.sp,
            fontWeight = FontWeight.Medium,
        )
    }
}

@Composable
private fun GlassKeypadIconButton(
    tint: Color,
    onClick: () -> Unit,
    content: @Composable () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .size(72.dp)
            .clip(CircleShape)
            .background(tint.copy(alpha = if (c.isDark) 0.12f else 0.08f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}
