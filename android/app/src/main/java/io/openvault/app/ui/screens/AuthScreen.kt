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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.security.AppPreferences
import io.openvault.app.data.VaultRepository
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.theme.LocalOpenVaultColors
import kotlinx.coroutines.launch

@Composable
fun AuthScreen(
    repository: VaultRepository,
    onAuthenticated: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    val scope = rememberCoroutineScope()

    var isRegister by remember { mutableStateOf(false) }
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
        unfocusedBorderColor = c.glassBorderTop.copy(alpha = 0.25f),
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accent,
        focusedContainerColor = c.glassSurface.copy(alpha = 0.45f),
        unfocusedContainerColor = c.glassSurface.copy(alpha = 0.30f),
    )

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(18.dp),
        ) {
            Spacer(modifier = Modifier.height(12.dp))
            OpenVaultBrandCrest(size = 74.dp)

            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "OpenVault",
                    color = c.textPrimary,
                    fontSize = 30.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = (-0.6).sp,
                )
                Text(
                    text = "Kho mật khẩu Zero-Knowledge chuẩn Bitwarden",
                    color = c.textSecondary,
                    fontSize = 13.5.sp,
                )
            }

            // Segmented Liquid Glass switcher
            LiquidGlassCard(
                cornerRadius = 18.dp,
                padding = PaddingValues(4.dp),
                modifier = Modifier.fillMaxWidth(),
            ) {
                Row(modifier = Modifier.fillMaxWidth()) {
                    listOf(false to "Đăng nhập", true to "Tạo tài khoản").forEach { (reg, label) ->
                        val active = isRegister == reg
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .clip(RoundedCornerShape(14.dp))
                                .background(if (active) c.accent else Color.Transparent)
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

            // Main frosted credentials card
            LiquidGlassCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 26.dp,
                elevated = true,
                padding = PaddingValues(20.dp),
            ) {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Server selector pill
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clip(RoundedCornerShape(12.dp))
                            .background(c.accentSoft)
                            .clickable { showServerEditor = !showServerEditor }
                            .padding(horizontal = 12.dp, vertical = 9.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Cloud,
                            contentDescription = null,
                            tint = c.accent,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = serverUrl.removePrefix("https://").removePrefix("http://"),
                            color = c.accent,
                            fontSize = 12.5.sp,
                            fontWeight = FontWeight.Medium,
                            modifier = Modifier.weight(1f),
                        )
                        Text(
                            text = if (showServerEditor) "Ẩn" else "Đổi máy chủ",
                            color = c.textSecondary,
                            fontSize = 11.5.sp,
                        )
                    }

                    AnimatedVisibility(visible = showServerEditor) {
                        OutlinedTextField(
                            value = serverUrl,
                            onValueChange = { serverUrl = it },
                            label = { Text("URL Cloudflare Worker / Bitwarden Server") },
                            placeholder = { Text(AppPreferences.DEFAULT_SERVER_URL) },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
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
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isRegister) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it },
                            label = { Text("Tên hiển thị (tùy chọn)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    OutlinedTextField(
                        value = password,
                        onValueChange = { password = it; error = null },
                        label = { Text("Mật khẩu chính (Master Password)") },
                        singleLine = true,
                        visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { showPassword = !showPassword }) {
                                Icon(
                                    imageVector = if (showPassword) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility,
                                    contentDescription = "Hiện/Ẩn mật khẩu",
                                    tint = c.textSecondary,
                                )
                            }
                        },
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )

                    if (isRegister) {
                        OutlinedTextField(
                            value = confirmPassword,
                            onValueChange = { confirmPassword = it; error = null },
                            label = { Text("Xác nhận mật khẩu chính") },
                            singleLine = true,
                            visualTransformation = if (showPassword) VisualTransformation.None else PasswordVisualTransformation(),
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                        OutlinedTextField(
                            value = hint,
                            onValueChange = { hint = it },
                            label = { Text("Gợi ý mật khẩu (tùy chọn)") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )
                    }

                    if (error != null) {
                        Text(
                            text = error!!,
                            color = c.danger,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                        )
                    }

                    Button(
                        onClick = {
                            val cleanEmail = email.trim()
                            if (cleanEmail.isBlank() || !cleanEmail.contains("@")) {
                                error = "Vui lòng nhập email hợp lệ."
                                return@Button
                            }
                            if (password.length < 8) {
                                error = "Mật khẩu chính cần tối thiểu 8 ký tự."
                                return@Button
                            }
                            if (isRegister && password != confirmPassword) {
                                error = "Mật khẩu xác nhận không khớp."
                                return@Button
                            }
                            loading = true
                            error = null
                            scope.launch {
                                runCatching {
                                    if (isRegister) {
                                        repository.register(
                                            serverUrl = serverUrl,
                                            email = cleanEmail,
                                            name = displayName,
                                            masterPassword = password,
                                            hint = hint,
                                        )
                                    } else {
                                        repository.login(
                                            serverUrl = serverUrl,
                                            email = cleanEmail,
                                            masterPassword = password,
                                        )
                                    }
                                }
                                    .onSuccess {
                                        loading = false
                                        onAuthenticated()
                                    }
                                    .onFailure { ex ->
                                        loading = false
                                        error = ex.message ?: "Không thể kết nối máy chủ OpenVault."
                                    }
                            }
                        },
                        enabled = !loading,
                        shape = RoundedCornerShape(16.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = c.accent,
                            contentColor = Color.White,
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(52.dp),
                    ) {
                        if (loading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                strokeWidth = 2.5.dp,
                                modifier = Modifier.size(20.dp),
                            )
                            Spacer(modifier = Modifier.size(10.dp))
                            Text("Đang dẫn xuất khóa Argon2id…", fontWeight = FontWeight.SemiBold)
                        } else {
                            Text(
                                text = if (isRegister) "Tạo kho Zero-Knowledge" else "Đăng nhập & Mở kho",
                                fontSize = 15.5.sp,
                                fontWeight = FontWeight.SemiBold,
                            )
                        }
                    }
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(
                    Icons.Rounded.Lock,
                    contentDescription = null,
                    tint = c.textMuted,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "Mã hóa AES-256-CBC + HMAC-SHA256 ngay trên thiết bị",
                    color = c.textMuted,
                    fontSize = 12.sp,
                )
            }
        }
    }
}
