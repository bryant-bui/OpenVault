package io.openvault.app.ui.screens

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.PasswordOptions
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultCard
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultIdentity
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.VaultLogin
import io.openvault.app.core.crypto.VaultUri
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.LiquidGlassChip
import io.openvault.app.ui.theme.LocalOpenVaultColors

@Composable
fun ItemEditorScreen(
    initialItem: VaultItem?,
    folders: List<VaultFolder>,
    scannedTotpUri: String?,
    onConsumeScannedTotp: () -> Unit,
    onRequestQrScan: () -> Unit,
    onCancel: () -> Unit,
    onSave: (VaultItem) -> Unit,
    isSaving: Boolean,
) {
    val c = LocalOpenVaultColors.current
    val isEdit = initialItem != null && initialItem.id.isNotBlank()

    var type by remember(initialItem) { mutableStateOf(initialItem?.type ?: CipherType.LOGIN) }
    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var folderId by remember(initialItem) { mutableStateOf(initialItem?.folderId) }
    var favorite by remember(initialItem) { mutableStateOf(initialItem?.favorite ?: false) }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }

    // Login fields
    var username by remember(initialItem) { mutableStateOf(initialItem?.login?.username.orEmpty()) }
    var password by remember(initialItem) { mutableStateOf(initialItem?.login?.password.orEmpty()) }
    var uriText by remember(initialItem) {
        mutableStateOf(initialItem?.login?.uris?.joinToString("\n") { it.uri }.orEmpty())
    }
    var totp by remember(initialItem) { mutableStateOf(initialItem?.login?.totp.orEmpty()) }

    // Card fields
    var cardholder by remember(initialItem) { mutableStateOf(initialItem?.card?.cardholderName.orEmpty()) }
    var cardBrand by remember(initialItem) { mutableStateOf(initialItem?.card?.brand.orEmpty()) }
    var cardNumber by remember(initialItem) { mutableStateOf(initialItem?.card?.number.orEmpty()) }
    var expMonth by remember(initialItem) { mutableStateOf(initialItem?.card?.expMonth.orEmpty()) }
    var expYear by remember(initialItem) { mutableStateOf(initialItem?.card?.expYear.orEmpty()) }
    var cardCvv by remember(initialItem) { mutableStateOf(initialItem?.card?.code.orEmpty()) }

    // Identity fields
    var idFirstName by remember(initialItem) { mutableStateOf(initialItem?.identity?.firstName.orEmpty()) }
    var idLastName by remember(initialItem) { mutableStateOf(initialItem?.identity?.lastName.orEmpty()) }
    var idEmail by remember(initialItem) { mutableStateOf(initialItem?.identity?.email.orEmpty()) }
    var idPhone by remember(initialItem) { mutableStateOf(initialItem?.identity?.phone.orEmpty()) }
    var idCompany by remember(initialItem) { mutableStateOf(initialItem?.identity?.company.orEmpty()) }
    var idAddress by remember(initialItem) { mutableStateOf(initialItem?.identity?.address1.orEmpty()) }
    var idCity by remember(initialItem) { mutableStateOf(initialItem?.identity?.city.orEmpty()) }
    var idCountry by remember(initialItem) { mutableStateOf(initialItem?.identity?.country.orEmpty()) }
    var idPassport by remember(initialItem) { mutableStateOf(initialItem?.identity?.passportNumber.orEmpty()) }

    LaunchedEffect(scannedTotpUri) {
        if (!scannedTotpUri.isNullOrBlank()) {
            totp = scannedTotpUri
            if (name.isBlank()) {
                runCatching { TotpEngine.parseTotpInput(scannedTotpUri) }.getOrNull()?.let { cfg ->
                    name = cfg.issuer ?: cfg.label ?: ""
                    if (username.isBlank() && !cfg.label.isNullOrBlank()) {
                        username = cfg.label
                    }
                }
            }
            type = CipherType.LOGIN
            onConsumeScannedTotp()
        }
    }

    val fieldColors = OutlinedTextFieldDefaults.colors(
        focusedBorderColor = c.accent,
        unfocusedBorderColor = c.glassBorderTop.copy(alpha = 0.25f),
        focusedTextColor = c.textPrimary,
        unfocusedTextColor = c.textPrimary,
        focusedLabelColor = c.accent,
        unfocusedLabelColor = c.textSecondary,
        cursorColor = c.accent,
    )

    LiquidGlassBackground {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .navigationBarsPadding()
                .imePadding(),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onCancel) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, contentDescription = "Đóng", tint = c.textPrimary)
                    }
                    Text(
                        text = if (isEdit) "Chỉnh sửa mục" else "Thêm mục mới",
                        color = c.textPrimary,
                        fontSize = 19.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }

                Button(
                    onClick = {
                        val uris = uriText.lineSequence()
                            .map { it.trim() }
                            .filter { it.isNotEmpty() }
                            .map { VaultUri(it) }
                            .toList()

                        val built = VaultItem(
                            id = initialItem?.id.orEmpty(),
                            folderId = folderId,
                            type = type,
                            name = name.trim().ifBlank { "Không có tiêu đề" },
                            notes = notes,
                            favorite = favorite,
                            login = if (type == CipherType.LOGIN) {
                                VaultLogin(
                                    uris = uris,
                                    username = username.trim(),
                                    password = password,
                                    totp = totp.trim().takeIf { it.isNotBlank() },
                                    fido2Credentials = initialItem?.login?.fido2Credentials.orEmpty(),
                                )
                            } else {
                                null
                            },
                            card = if (type == CipherType.CARD) {
                                VaultCard(
                                    cardholderName = cardholder.trim(),
                                    brand = cardBrand.trim(),
                                    number = cardNumber.trim(),
                                    expMonth = expMonth.trim(),
                                    expYear = expYear.trim(),
                                    code = cardCvv.trim(),
                                )
                            } else {
                                null
                            },
                            identity = if (type == CipherType.IDENTITY) {
                                VaultIdentity(
                                    firstName = idFirstName.trim(),
                                    lastName = idLastName.trim(),
                                    email = idEmail.trim(),
                                    phone = idPhone.trim(),
                                    company = idCompany.trim(),
                                    address1 = idAddress.trim(),
                                    city = idCity.trim(),
                                    country = idCountry.trim(),
                                    passportNumber = idPassport.trim(),
                                )
                            } else {
                                null
                            },
                            fields = initialItem?.fields.orEmpty(),
                            passwordHistory = initialItem?.passwordHistory.orEmpty(),
                        )
                        onSave(built)
                    },
                    enabled = !isSaving && name.isNotBlank(),
                    shape = RoundedCornerShape(14.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = c.accent, contentColor = Color.White),
                    contentPadding = PaddingValues(horizontal = 18.dp, vertical = 8.dp),
                ) {
                    if (isSaving) {
                        CircularProgressIndicator(color = Color.White, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                    } else {
                        Text("Lưu", fontWeight = FontWeight.SemiBold)
                    }
                }
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp),
            ) {
                if (!isEdit) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        CipherType.entries.forEach { t ->
                            LiquidGlassChip(
                                label = t.labelVi,
                                selected = type == t,
                                onClick = { type = t },
                            )
                        }
                    }
                }

                // General Info Card
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp, elevated = true) {
                    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Tiêu đề *") },
                            placeholder = { Text("Ví dụ: GitHub, Vietcombank, Gmail…") },
                            singleLine = true,
                            shape = RoundedCornerShape(14.dp),
                            colors = fieldColors,
                            modifier = Modifier.fillMaxWidth(),
                        )

                        if (folders.isNotEmpty()) {
                            Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                Text("Thư mục", color = c.textSecondary, fontSize = 12.sp)
                                Row(
                                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    LiquidGlassChip(
                                        label = "Không phân loại",
                                        selected = folderId == null,
                                        onClick = { folderId = null },
                                    )
                                    folders.forEach { f ->
                                        LiquidGlassChip(
                                            label = f.name,
                                            selected = folderId == f.id,
                                            onClick = { folderId = f.id },
                                        )
                                    }
                                }
                            }
                        }

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text("Đánh dấu yêu thích", color = c.textPrimary, fontSize = 14.5.sp)
                            Switch(checked = favorite, onCheckedChange = { favorite = it })
                        }
                    }
                }

                // Type-specific fields
                when (type) {
                    CipherType.LOGIN -> {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = username,
                                    onValueChange = { username = it },
                                    label = { Text("Tên đăng nhập / Email") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = password,
                                    onValueChange = { password = it },
                                    label = { Text("Mật khẩu") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(
                                            onClick = {
                                                password = PasswordGenerator.generatePassword(
                                                    PasswordOptions(length = 20, symbols = true),
                                                )
                                            },
                                        ) {
                                            Icon(
                                                Icons.Rounded.AutoAwesome,
                                                contentDescription = "Tạo mật khẩu mạnh",
                                                tint = c.accent,
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = totp,
                                    onValueChange = { totp = it },
                                    label = { Text("Khóa xác thực 2FA (Secret / otpauth://)") },
                                    singleLine = true,
                                    trailingIcon = {
                                        IconButton(onClick = onRequestQrScan) {
                                            Icon(
                                                Icons.Rounded.QrCodeScanner,
                                                contentDescription = "Quét mã QR 2FA",
                                                tint = c.accent,
                                            )
                                        }
                                    },
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = uriText,
                                    onValueChange = { uriText = it },
                                    label = { Text("Trang web / Android App URI (mỗi dòng 1 URI)") },
                                    placeholder = { Text("https://github.com\nandroidapp://com.github.android") },
                                    minLines = 2,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    CipherType.CARD -> {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                OutlinedTextField(
                                    value = cardholder,
                                    onValueChange = { cardholder = it },
                                    label = { Text("Tên chủ thẻ") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = cardBrand,
                                    onValueChange = { cardBrand = it },
                                    label = { Text("Loại thẻ (Visa, Mastercard, JCB…)") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = cardNumber,
                                    onValueChange = { cardNumber = it },
                                    label = { Text("Số thẻ") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = expMonth,
                                        onValueChange = { expMonth = it },
                                        label = { Text("Tháng (MM)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = expYear,
                                        onValueChange = { expYear = it },
                                        label = { Text("Năm (YYYY)") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = cardCvv,
                                        onValueChange = { cardCvv = it },
                                        label = { Text("CVV") },
                                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                            }
                        }
                    }

                    CipherType.IDENTITY -> {
                        LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = idFirstName,
                                        onValueChange = { idFirstName = it },
                                        label = { Text("Tên") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = idLastName,
                                        onValueChange = { idLastName = it },
                                        label = { Text("Họ") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                OutlinedTextField(
                                    value = idEmail,
                                    onValueChange = { idEmail = it },
                                    label = { Text("Email") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = idPhone,
                                    onValueChange = { idPhone = it },
                                    label = { Text("Số điện thoại") },
                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone),
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = idCompany,
                                    onValueChange = { idCompany = it },
                                    label = { Text("Công ty") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                OutlinedTextField(
                                    value = idAddress,
                                    onValueChange = { idAddress = it },
                                    label = { Text("Địa chỉ") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                                    OutlinedTextField(
                                        value = idCity,
                                        onValueChange = { idCity = it },
                                        label = { Text("Thành phố") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                    OutlinedTextField(
                                        value = idCountry,
                                        onValueChange = { idCountry = it },
                                        label = { Text("Quốc gia") },
                                        singleLine = true,
                                        shape = RoundedCornerShape(14.dp),
                                        colors = fieldColors,
                                        modifier = Modifier.weight(1f),
                                    )
                                }
                                OutlinedTextField(
                                    value = idPassport,
                                    onValueChange = { idPassport = it },
                                    label = { Text("Số CCCD / Hộ chiếu") },
                                    singleLine = true,
                                    shape = RoundedCornerShape(14.dp),
                                    colors = fieldColors,
                                    modifier = Modifier.fillMaxWidth(),
                                )
                            }
                        }
                    }

                    CipherType.SECURE_NOTE -> Unit
                }

                // Notes
                LiquidGlassCard(modifier = Modifier.fillMaxWidth(), cornerRadius = 22.dp) {
                    OutlinedTextField(
                        value = notes,
                        onValueChange = { notes = it },
                        label = { Text("Ghi chú bảo mật") },
                        minLines = if (type == CipherType.SECURE_NOTE) 6 else 3,
                        shape = RoundedCornerShape(14.dp),
                        colors = fieldColors,
                        modifier = Modifier.fillMaxWidth(),
                    )
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }
}
