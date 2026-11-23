package io.openvault.app.ui.screens

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
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
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import io.openvault.app.ui.components.EditingStripedHeader
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors

/**
 * Authentic 1Password 8 Android Item Editor:
 * - Signature Purple Diagonal-Striped "Đang sửa" / "Tạo mới" Header with Cobalt "Lưu" pill button
 * - Elevated matte charcoal (#1C1D1F) canvas, context Vault row, [Icon] [Bordered Title Input]
 * - Hairline-outlined primary credentials box, structured Website rows with remove/add buttons
 * - Centered comfortable width on unfolded Galaxy Z Fold 3
 */
@Composable
fun ItemEditorScreen(
    initialItem: VaultItem?,
    initialType: CipherType = CipherType.LOGIN,
    email: String = "",
    folders: List<VaultFolder>,
    scannedTotpUri: String?,
    onConsumeScannedTotp: () -> Unit,
    onRequestQrScan: () -> Unit,
    onCancel: () -> Unit,
    onSave: (VaultItem) -> Unit,
    isSaving: Boolean,
) {
    val c = LocalOpenVaultColors.current
    val fold = LocalFoldableLayout.current
    val isEdit = initialItem != null && initialItem.id.isNotBlank()

    var type by remember(initialItem, initialType) { mutableStateOf(initialItem?.type ?: initialType) }
    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var folderId by remember(initialItem) { mutableStateOf(initialItem?.folderId) }
    var favorite by remember(initialItem) { mutableStateOf(initialItem?.favorite ?: false) }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }

    var username by remember(initialItem) { mutableStateOf(initialItem?.login?.username.orEmpty()) }
    var password by remember(initialItem) { mutableStateOf(initialItem?.login?.password.orEmpty()) }
    val websiteList = remember(initialItem) {
        val initial = initialItem?.login?.uris?.map { it.uri }.orEmpty()
        mutableStateListOf<String>().apply {
            if (initial.isEmpty()) add("") else addAll(initial)
        }
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

    val ownerName = remember(email) {
        val local = email.substringBefore("@").trim()
        if (local.isNotBlank()) local.replaceFirstChar { it.uppercaseChar() } else "OpenVault"
    }

    fun submitSave() {
        val uris = websiteList
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { VaultUri(it) }

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
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        // Signature 1Password Android Purple Diagonal-Striped Edit Banner
        EditingStripedHeader(
            title = if (isEdit) "Đang sửa" else "Tạo ${type.labelVi.lowercase()}",
            saveEnabled = name.isNotBlank(),
            isSaving = isSaving,
            onCancel = onCancel,
            onSave = ::submitSave,
        )

        Box(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState()),
            contentAlignment = Alignment.TopCenter,
        ) {
            Column(
                modifier = Modifier
                    .widthIn(max = 620.dp)
                    .fillMaxWidth()
                    .padding(horizontal = if (fold.isNarrowCover) 14.dp else 20.dp, vertical = 14.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Context row: [Avatar] account | [Vault] Personal
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnePassAccountAvatar(email = email, onClick = { }, size = 22.dp)
                    Text(ownerName, color = c.textSecondary, fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    Text("|", color = c.divider, fontSize = 14.sp)
                    Box(
                        modifier = Modifier
                            .size(20.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(12.dp))
                    }
                    Text(
                        text = folders.firstOrNull { it.id == folderId }?.name ?: "Cá nhân",
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Inline [52dp IconTile] [Bordered Title Input Box] (Exact 1Password Android Editor!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    ItemIconTile(title = name.ifBlank { type.labelVi }, type = type, size = 54.dp)
                    val titleShape = RoundedCornerShape(10.dp)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = 19.sp,
                            fontWeight = FontWeight.SemiBold,
                        ),
                        cursorBrush = SolidColor(c.accent),
                        decorationBox = { inner ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(titleShape)
                                    .background(c.surface)
                                    .border(1.dp, c.cardBorder, titleShape)
                                    .padding(horizontal = 14.dp, vertical = 14.dp),
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

                // Folder selector chips
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OnePassChip(label = "Cá nhân", selected = folderId == null) { folderId = null }
                        folders.forEach { f ->
                            OnePassChip(label = f.name, selected = folderId == f.id) { folderId = f.id }
                        }
                    }
                }

                // Type-specific fields
                when (type) {
                    CipherType.LOGIN -> {
                        // Primary Credentials Outlined Card
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = c.cardBorder,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                EditorFieldRow(
                                    label = "tên đăng nhập",
                                    value = username,
                                    placeholder = "email@example.com",
                                    onValueChange = { username = it },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
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
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Tạo mật khẩu", tint = c.accent, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(
                                    label = "mã xác thực một lần (2FA)",
                                    value = totp,
                                    placeholder = "Secret Base32 hoặc otpauth://",
                                    onValueChange = { totp = it },
                                    trailing = {
                                        IconButton(onClick = onRequestQrScan, modifier = Modifier.size(34.dp)) {
                                            Icon(Icons.Rounded.QrCodeScanner, contentDescription = "Quét QR", tint = c.accent, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                )
                            }
                        }

                        // Websites Card with minus-remove circle + "+ thêm trang web" (Exact 1Password Android!)
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                websiteList.forEachIndexed { idx, site ->
                                    if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(start = 10.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        IconButton(
                                            onClick = {
                                                if (websiteList.size > 1) {
                                                    websiteList.removeAt(idx)
                                                } else {
                                                    websiteList[0] = ""
                                                }
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.RemoveCircleOutline,
                                                contentDescription = "Xóa trang web",
                                                tint = c.danger.copy(alpha = 0.85f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                            Text(
                                                text = if (idx == 0) "trang web" else "trang web #${idx + 1}",
                                                color = c.textSecondary,
                                                fontSize = 12.5.sp,
                                            )
                                            BasicTextField(
                                                value = site,
                                                onValueChange = { websiteList[idx] = it },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                                textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                                                cursorBrush = SolidColor(c.accent),
                                                decorationBox = { inner ->
                                                    if (site.isEmpty()) {
                                                        Text("https://example.com", color = c.textMuted, fontSize = 15.sp)
                                                    }
                                                    inner()
                                                },
                                                modifier = Modifier.fillMaxWidth(),
                                            )
                                        }
                                    }
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { websiteList.add("") }
                                        .padding(horizontal = 16.dp, vertical = 14.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                                ) {
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = "thêm trang web khác",
                                        color = c.navActiveContent,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Medium,
                                    )
                                }
                            }
                        }
                    }

                    CipherType.CARD -> {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = c.cardBorder,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                EditorFieldRow("tên chủ thẻ", cardholder, "NGUYEN VAN A", onValueChange = { cardholder = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("loại thẻ", cardBrand, "Visa / Mastercard", onValueChange = { cardBrand = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("số thẻ", cardNumber, "4242 •••• •••• 4242", keyboardType = KeyboardType.Number, onValueChange = { cardNumber = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow("tháng", expMonth, "08", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expMonth = it })
                                    EditorFieldRow("năm", expYear, "2029", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expYear = it })
                                    EditorFieldRow("cvv", cardCvv, "123", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { cardCvv = it })
                                }
                            }
                        }
                    }

                    CipherType.IDENTITY -> {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = c.cardBorder,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                Row {
                                    EditorFieldRow("tên", idFirstName, "Phi", modifier = Modifier.weight(1f), onValueChange = { idFirstName = it })
                                    EditorFieldRow("họ", idLastName, "Hoàng", modifier = Modifier.weight(1f), onValueChange = { idLastName = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("email", idEmail, "email@example.com", keyboardType = KeyboardType.Email, onValueChange = { idEmail = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("số điện thoại", idPhone, "+84...", keyboardType = KeyboardType.Phone, onValueChange = { idPhone = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("công ty", idCompany, "Tên công ty", onValueChange = { idCompany = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("địa chỉ", idAddress, "Số nhà, đường", onValueChange = { idAddress = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow("thành phố", idCity, "TP. HCM", modifier = Modifier.weight(1f), onValueChange = { idCity = it })
                                    EditorFieldRow("quốc gia", idCountry, "Việt Nam", modifier = Modifier.weight(1f), onValueChange = { idCountry = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("cccd / hộ chiếu", idPassport, "Số định danh", onValueChange = { idPassport = it })
                            }
                        }
                    }

                    CipherType.SECURE_NOTE -> Unit
                }

                // Notes Card
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    backgroundColor = c.surface,
                    padding = PaddingValues(0.dp),
                ) {
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
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    backgroundColor = c.surface,
                    padding = PaddingValues(horizontal = 16.dp, vertical = 10.dp),
                ) {
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
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 12.5.sp, fontWeight = FontWeight.Medium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(color = c.textPrimary, fontSize = 15.5.sp),
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
