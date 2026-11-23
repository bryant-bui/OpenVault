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
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * Clean flat OLED Dark / Light Unlock Screen:
 * - Instant BiometricPrompt auto-trigger
 * - 3 + 3 rounded PIN slots with instant auto-unlock on the last digit + crisp matte keypad
 * - Seamless toggle to Master Password
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .padding(horizontal = 22.dp, vertical = 20.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.padding(top = 18.dp),
        ) {
            OpenVaultBrandCrest(size = 58.dp)
            Text(
                text = "Mở khóa OpenVault",
                color = c.textPrimary,
                fontSize = 23.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = (-0.4).sp,
            )
            Text(text = account.email, color = c.textSecondary, fontSize = 13.5.sp)
        }

        if (!useMasterMode && settings.pinEnabled) {
            val targetLen = settings.pinLength.coerceIn(4, 12)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(18.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = if (busy) "Đang mở khóa…" else "Nhập mã PIN ($targetLen số)",
                    color = c.textSecondary,
                    fontSize = 14.sp,
                )

                // Clean 3 + 3 PIN Boxes
                Row(
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .offset { IntOffset(shakeOffset.value.roundToInt(), 0) },
                ) {
                    val splitAt = if (targetLen == 6) 3 else -1
                    for (i in 0 until targetLen) {
                        if (i == splitAt) Spacer(modifier = Modifier.width(16.dp))
                        else if (i > 0) Spacer(modifier = Modifier.width(8.dp))

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
                                .size(width = 44.dp, height = 52.dp)
                                .clip(boxShape)
                                .background(c.surface)
                                .border(
                                    width = if (active || errorMessage != null) 1.6.dp else 1.dp,
                                    color = when {
                                        errorMessage != null -> c.danger
                                        active -> c.accent
                                        else -> c.divider
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
                    Spacer(modifier = Modifier.height(16.dp))
                }

                // Clean Matte Numeric Keypad
                Column(
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    listOf(
                        listOf("1", "2", "3"),
                        listOf("4", "5", "6"),
                        listOf("7", "8", "9"),
                    ).forEach { row ->
                        Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                            row.forEach { digit ->
                                FlatKeypadButton(digit, enabled = !busy) { appendPinDigit(digit) }
                            }
                        }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(22.dp)) {
                        if (canUseBiometric) {
                            Box(
                                modifier = Modifier
                                    .size(70.dp)
                                    .clip(CircleShape)
                                    .background(c.accentSoft)
                                    .clickable { launchBiometricPrompt() },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Rounded.Fingerprint, contentDescription = "Vân tay", tint = c.accent, modifier = Modifier.size(28.dp))
                            }
                        } else {
                            Spacer(modifier = Modifier.size(70.dp))
                        }

                        FlatKeypadButton("0", enabled = !busy) { appendPinDigit("0") }

                        Box(
                            modifier = Modifier
                                .size(70.dp)
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
        } else {
            OnePassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                padding = PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    OutlinedTextField(
                        value = masterPassword,
                        onValueChange = { masterPassword = it; errorMessage = null },
                        label = { Text("Mật khẩu chính") },
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
                            unfocusedBorderColor = c.divider,
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
                        shape = RoundedCornerShape(10.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (busy) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                        } else {
                            Text("Mở khóa", fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        }
                    }

                    if (canUseBiometric) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.accentSoft)
                                .clickable { launchBiometricPrompt() }
                                .padding(vertical = 12.dp),
                            horizontalArrangement = Arrangement.Center,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(Icons.Rounded.Fingerprint, contentDescription = null, tint = c.accent, modifier = Modifier.size(21.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Mở nhanh bằng sinh trắc học", color = c.accent, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
                        }
                    }
                }
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (settings.pinEnabled) {
                TextButton(onClick = {
                    useMasterMode = !useMasterMode
                    errorMessage = null
                    pinDigits = ""
                }) {
                    Text(if (useMasterMode) "Dùng mã PIN" else "Dùng mật khẩu chính", color = c.accent, fontWeight = FontWeight.Medium)
                }
            }
            if (allowLogout) {
                TextButton(onClick = { scope.launch { repository.logout(allDevices = false) } }) {
                    Text("Đăng xuất", color = c.textSecondary)
                }
            }
        }
    }
}

@Composable
private fun FlatKeypadButton(label: String, enabled: Boolean, onClick: () -> Unit) {
    val c = LocalOpenVaultColors.current
    Box(
        modifier = Modifier
            .size(70.dp)
            .clip(CircleShape)
            .background(c.surface)
            .border(if (c.isDark) 0.dp else 0.8.dp, if (c.isDark) Color.Transparent else c.cardBorder, CircleShape)
            .clickable(enabled = enabled, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Text(text = label, color = c.textPrimary, fontSize = 26.sp, fontWeight = FontWeight.Normal)
    }
}
