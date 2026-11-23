package io.openvault.app.ui.screens

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
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
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.theme.LocalOpenVaultColors

/**
 * Clean 1Password 8 Item Editor (matching Image 2-left):
 * - Top bar: Hủy | Mục mới / Chỉnh sửa | Lưu
 * - Inline [Icon] [Title input]
 * - Grouped matte card with label-above-input rows separated by thin hairlines
 */
@Composable
fun ItemEditorScreen(
    initialItem: VaultItem?,
    initialType: CipherType = CipherType.LOGIN,
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

    var type by remember(initialItem, initialType) { mutableStateOf(initialItem?.type ?: initialType) }
    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var folderId by remember(initialItem) { mutableStateOf(initialItem?.folderId) }
    var favorite by remember(initialItem) { mutableStateOf(initialItem?.favorite ?: false) }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }

    var username by remember(initialItem) { mutableStateOf(initialItem?.login?.username.orEmpty()) }
    var password by remember(initialItem) { mutableStateOf(initialItem?.login?.password.orEmpty()) }
    var uriText by remember(initialItem) {
        mutableStateOf(initialItem?.login?.uris?.joinToString("\n") { it.uri }.orEmpty())
    }
    var totp by remember(initialItem) { mutableStateOf(initialItem?.login?.totp.orEmpty()) }

    var cardholder by remember(initialItem) { mutableStateOf(initialItem?.card?.cardholderName.orEmpty()) }
    var cardBrand by remember(initialItem) { mutableStateOf(initialItem?.card?.brand.orEmpty()) }
    var cardNumber by remember(initialItem) { mutableStateOf(initialItem?.card?.number.orEmpty()) }
    var expMonth by remember(initialItem) { mutableStateOf(initialItem?.card?.expMonth.orEmpty()) }
    var expYear by remember(initialItem) { mutableStateOf(initialItem?.card?.expYear.orEmpty()) }
    var cardCvv by remember(initialItem) { mutableStateOf(initialItem?.card?.code.orEmpty()) }

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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Header: Hủy | Title | Lưu
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            TextButton(onClick = onCancel) {
                Text("Hủy", color = c.accent, fontSize = 15.5.sp)
            }
            Text(
                text = if (isEdit) "Chỉnh sửa" else type.labelVi,
                color = c.textPrimary,
                fontSize = 16.5.sp,
                fontWeight = FontWeight.SemiBold,
            )
            TextButton(
                enabled = !isSaving && name.isNotBlank(),
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
                        } else null,
                        card = if (type == CipherType.CARD) {
                            VaultCard(
                                cardholderName = cardholder.trim(),
                                brand = cardBrand.trim(),
                                number = cardNumber.trim(),
                                expMonth = expMonth.trim(),
                                expYear = expYear.trim(),
                                code = cardCvv.trim(),
                            )
                        } else null,
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
                        } else null,
                        fields = initialItem?.fields.orEmpty(),
                        passwordHistory = initialItem?.passwordHistory.orEmpty(),
                    )
                    onSave(built)
                },
            ) {
                if (isSaving) {
                    CircularProgressIndicator(color = c.accent, strokeWidth = 2.dp, modifier = Modifier.size(16.dp))
                } else {
                    Text(
                        text = "Lưu",
                        color = if (name.isNotBlank()) c.accent else c.textMuted,
                        fontSize = 15.5.sp,
                        fontWeight = FontWeight.Bold,
                    )
                }
            }
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Inline [IconTile] [Title Box] (exact Image 2-left!)
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                ItemIconTile(title = name.ifBlank { type.labelVi }, type = type, size = 48.dp)
                BasicTextField(
                    value = name,
                    onValueChange = { name = it },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = c.textPrimary,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.SemiBold,
                    ),
                    cursorBrush = SolidColor(c.accent),
                    decorationBox = { inner ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(10.dp))
                                .background(c.surface)
                                .padding(horizontal = 14.dp, vertical = 13.dp),
                        ) {
                            if (name.isEmpty()) {
                                Text("Tiêu đề (VD: GitHub, Gmail…)", color = c.textMuted, fontSize = 16.sp)
                            }
                            inner()
                        }
                    },
                    modifier = Modifier.weight(1f),
                )
            }

            // Folder picker strip
            if (folders.isNotEmpty()) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnePassChip(label = "Personal", selected = folderId == null) { folderId = null }
                    folders.forEach { f ->
                        OnePassChip(label = f.name, selected = folderId == f.id) { folderId = f.id }
                    }
                }
            }

            // Type-specific grouped matte card
            when (type) {
                CipherType.LOGIN -> {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            EditorFieldRow(
                                label = "tên đăng nhập",
                                value = username,
                                placeholder = "email@example.com",
                                onValueChange = { username = it },
                            )
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow(
                                label = "mật khẩu",
                                value = password,
                                placeholder = "Nhập hoặc tạo mật khẩu mạnh",
                                onValueChange = { password = it },
                                trailing = {
                                    IconButton(
                                        onClick = {
                                            password = PasswordGenerator.generatePassword(
                                                PasswordOptions(length = 20, symbols = true),
                                            )
                                        },
                                        modifier = Modifier.size(34.dp),
                                    ) {
                                        Icon(Icons.Rounded.AutoAwesome, contentDescription = "Tạo mật khẩu", tint = c.accent, modifier = Modifier.size(19.dp))
                                    }
                                },
                            )
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow(
                                label = "mã xác thực một lần (2FA)",
                                value = totp,
                                placeholder = "Secret Base32 hoặc otpauth://",
                                onValueChange = { totp = it },
                                trailing = {
                                    IconButton(onClick = onRequestQrScan, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Quét QR", tint = c.accent, modifier = Modifier.size(19.dp))
                                    }
                                },
                            )
                        }
                    }

                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        EditorFieldRow(
                            label = "trang web / ứng dụng (mỗi dòng 1 URI)",
                            value = uriText,
                            placeholder = "https://example.com",
                            singleLine = false,
                            onValueChange = { uriText = it },
                        )
                    }
                }

                CipherType.CARD -> {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            EditorFieldRow("tên chủ thẻ", cardholder, "NGUYEN VAN A", onValueChange = { cardholder = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("loại thẻ", cardBrand, "Visa / Mastercard", onValueChange = { cardBrand = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("số thẻ", cardNumber, "4242 •••• •••• 4242", keyboardType = KeyboardType.Number, onValueChange = { cardNumber = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            Row {
                                EditorFieldRow("tháng (MM)", expMonth, "08", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expMonth = it })
                                EditorFieldRow("năm (YYYY)", expYear, "2029", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expYear = it })
                                EditorFieldRow("cvv", cardCvv, "123", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { cardCvv = it })
                            }
                        }
                    }
                }

                CipherType.IDENTITY -> {
                    OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                        Column {
                            Row {
                                EditorFieldRow("tên", idFirstName, "Phi", modifier = Modifier.weight(1f), onValueChange = { idFirstName = it })
                                EditorFieldRow("họ", idLastName, "Hoàng", modifier = Modifier.weight(1f), onValueChange = { idLastName = it })
                            }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("email", idEmail, "email@example.com", keyboardType = KeyboardType.Email, onValueChange = { idEmail = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("số điện thoại", idPhone, "+84...", keyboardType = KeyboardType.Phone, onValueChange = { idPhone = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("công ty", idCompany, "Tên công ty", onValueChange = { idCompany = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("địa chỉ", idAddress, "Số nhà, đường", onValueChange = { idAddress = it })
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            Row {
                                EditorFieldRow("thành phố", idCity, "TP. HCM", modifier = Modifier.weight(1f), onValueChange = { idCity = it })
                                EditorFieldRow("quốc gia", idCountry, "Việt Nam", modifier = Modifier.weight(1f), onValueChange = { idCountry = it })
                            }
                            HorizontalDivider(color = c.divider, thickness = 0.7.dp)
                            EditorFieldRow("cccd / hộ chiếu", idPassport, "Số định danh", onValueChange = { idPassport = it })
                        }
                    }
                }

                CipherType.SECURE_NOTE -> Unit
            }

            // Notes Card
            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                EditorFieldRow(
                    label = "ghi chú",
                    value = notes,
                    placeholder = "Thêm ghi chú bảo mật…",
                    singleLine = false,
                    minLines = if (type == CipherType.SECURE_NOTE) 6 else 3,
                    onValueChange = { notes = it },
                )
            }

            // Favorite Switch
            OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(horizontal = 15.dp, vertical = 8.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text("Ghim vào Yêu thích", color = c.textPrimary, fontSize = 15.sp)
                    Switch(checked = favorite, onCheckedChange = { favorite = it })
                }
            }

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun EditorFieldRow(
    label: String,
    value: String,
    placeholder: String,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    trailing: (@Composable () -> Unit)? = null,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 15.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 12.sp, fontWeight = FontWeight.Medium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = c.textMuted, fontSize = 15.sp)
                    }
                    inner()
                },
                modifier = Modifier.fillMaxWidth(),
            )
        }
        if (trailing != null) {
            trailing()
        }
    }
}
