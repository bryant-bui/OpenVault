package io.openvault.app.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Cloud
import androidx.compose.material.icons.rounded.Lock
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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.security.AppPreferences
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    repository: VaultRepository,
    initialRegister: Boolean = false,
    onBackToOnboarding: (() -> Unit)? = null,
    onAuthenticated: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val scope = rememberCoroutineScope()

    var isRegister by remember(initialRegister) { mutableStateOf(initialRegister) }
    var serverUrl by remember { mutableStateOf(repository.preferences.getServerUrl()) }
    var showServerEditor by remember { mutableStateOf(false) }
    var email by remember { mutableStateOf("") }
    var displayName by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var confirmPassword by remember { mutableStateOf("") }
    var hint by remember { mutableStateOf("") }
    var showPassword by remember { mutableStateOf(false) }
    var loading by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf<String?>(null) }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.cardBorder,
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accent,
    )

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding()
            .verticalScroll(rememberScrollState()),
        contentAlignment = Alignment.TopCenter,
    ) {
        if (onBackToOnboarding != null) {
            IconButton(
                onClick = onBackToOnboarding,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp),
            ) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = s.back, tint = c.textPrimary)
            }
        }

        Column(
            modifier = Modifier
                .widthIn(max = 460.dp)
                .fillMaxWidth()
                .padding(horizontal = if (fold.isNarrowCover) 14.dp else 22.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.height(if (onBackToOnboarding != null) 22.dp else 6.dp))
            OpenVaultBrandCrest(size = 62.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "OpenVault",
                    color = c.textPrimary,
                    fontSize = 27.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    text = if (s.isVi) "Kho mật khẩu Zero-Knowledge chuẩn Bitwarden" else "Zero-Knowledge Vault · Bitwarden Compatible",
                    color = c.textSecondary,
                    fontSize = 13.5.sp,
                    textAlign = TextAlign.Center,
                )
            }

            // Clean Segmented Switcher
            OnePassCard(
                cornerRadius = 12.dp,
                padding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(
                        false to (if (s.isVi) "Đăng nhập" else "Sign In"),
                        true to (if (s.isVi) "Tạo tài khoản" else "Create Account"),
                    ).forEach { (reg, label) ->
                        val active = isRegister == reg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(9.dp))
                                .background(if (active) c.accentButtonBg else Color.Transparent)
                                .clickable {
                                    isRegister = reg
                                    error = null
                                }
                                .padding(vertical = 10.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                text = label,
                                color = if (active) Color.White else c.textSecondary,
                                fontWeight = FontWeight.SemiBold,
                                fontSize = 14.sp,
                            )
                        }
                    }
                }
            }

            OnePassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 16.dp,
                borderColor = c.cardBorder,
                padding = PaddingValues(18.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(13.dp)) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(10.dp))
                            .background(c.searchBackground)
                            .clickable { showServerEditor = !showServerEditor }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(Icons.Rounded.Cloud, contentDescription = null, tint = c.accent, modifier = Modifier.size(16.dp))
                        Text(
                            text = if (s.isVi) "Máy chủ đám mây OpenVault" else "OpenVault Cloud Server",
                            color = c.textPrimary,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (showServerEditor) (if (s.isVi) "Ẩn" else "Hide") else (if (s.isVi) "Tùy chỉnh" else "Custom"),
                            color = c.accent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.SemiBold,
                        )
                    }

                    AnimatedVisibility(visible = showServerEditor) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text(if (s.isVi) "URL Máy chủ" else "Server URL") },
                            placeholder = { Text(AppPreferences.DEFAULT_SERVER_URL) },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = email,
                        onValueChange = { email = it; error = null },
                        label = { Text("Email") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isRegister) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text(if (s.isVi) "Tên hiển thị (tùy chọn)" else "Display name (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text(s.masterPassword) },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = null,
                                    tint = c.textSecondary,
                                )
                            }
                        },
                        shape = RoundedCornerShape(10.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isRegister) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; error = null },
                            label = { Text(if (s.isVi) "Xác nhận mật khẩu chính" else "Confirm master password") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = hint,
                            onValueChange = { hint = it },
                            label = { Text(if (s.isVi) "Gợi ý mật khẩu (tùy chọn)" else "Password hint (optional)") },
                            singleLine = true,
                            shape = RoundedCornerShape(10.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (error != null) {
                        Text(text = error!!, color = c.danger, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }

                    Button(
                        onClick = {
                            val cleanEmail = email.trim()
                            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                error = if (s.isVi) "Vui lòng nhập email hợp lệ." else "Please enter a valid email address."
                                return@Button
                            }
                            if (password.length < 8) {
                                error = if (s.isVi) "Mật khẩu chính cần tối thiểu 8 ký tự." else "Master password must be at least 8 characters."
                                return@Button
                            }
                            if (isRegister && password != confirmPassword) {
                                error = if (s.isVi) "Mật khẩu xác nhận không khớp." else "Master passwords do not match."
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                runCatching {
                                    if (isRegister) {
                                        repository.register(serverUrl, cleanEmail, displayName, password, hint)
                                    } else {
                                        repository.login(serverUrl, cleanEmail, password)
                                    }
                                }
                                    .onSuccess {
                                        loading = false
                                        onAuthenticated()
                                    }
                                    .onFailure { ex ->
                                        loading = false
                                        error = ex.message ?: if (s.isVi) "Không thể kết nối máy chủ." else "Could not reach server."
                                    }
                            }
                        },
                        enabled = !loading,
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(19.dp))
                            Spacer(modifier = Modifier.size(10.dp))
                            Text(
                                if (s.isVi) "Đang dẫn xuất khóa Argon2id…" else "Deriving Argon2id key…",
                                fontWeight = FontWeight.SemiBold,
                            )
                        } else {
                            Text(
                                text = if (isRegister) {
                                    if (s.isVi) "Tạo kho Zero-Knowledge" else "Create Zero-Knowledge Vault"
                                } else {
                                    if (s.isVi) "Đăng nhập" else "Sign In"
                                },
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Icon(Icons.Rounded.Lock, contentDescription = null, tint = c.textMuted, modifier = Modifier.size(14.dp))
                Text(
                    if (s.isVi) "Mã hóa AES-256-CBC + HMAC-SHA256 hoàn toàn cục bộ" else "End-to-end AES-256-CBC + HMAC-SHA256 encryption",
                    color = c.textMuted,
                    fontSize = 12.sp,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}
