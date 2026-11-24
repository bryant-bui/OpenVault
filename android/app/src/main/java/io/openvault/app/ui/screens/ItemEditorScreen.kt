package io.openvault.app.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.DeleteSweep
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.GridView
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.QrCodeScanner
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.RemoveCircleOutline
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.PassphraseOptions
import io.openvault.app.core.crypto.PasswordGenerator
import io.openvault.app.core.crypto.PasswordOptions
import io.openvault.app.core.crypto.RecoveryCodeUtils
import io.openvault.app.core.crypto.SecureNoteCategory
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultCard
import io.openvault.app.core.crypto.VaultFido2Credential
import io.openvault.app.core.crypto.VaultField
import io.openvault.app.core.crypto.VaultFolder
import io.openvault.app.core.crypto.VaultIdentity
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.VaultLogin
import io.openvault.app.core.crypto.VaultUri
import io.openvault.app.core.media.IconLoader
import io.openvault.app.suppressAutoLockForPicker
import io.openvault.app.ui.components.EditingStripedHeader
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import io.openvault.app.ui.theme.OpenVaultMonoFontFamily
import kotlinx.coroutines.launch

private data class EditableCustomField(
    val id: Long,
    val label: String,
    val value: String,
    val bitwardenType: Int, // 0 = Text, 1 = Hidden/Password
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isSecurityQuestion: Boolean = false,
    val isTemplateSeed: Boolean = false,
)

/**
 * Authentic 1Password 8 Android Item Editor:
 * - Purple Diagonal-Striped Editing Header + Cobalt Save button
 * - Split Secure Note categories (Software, Notes, Passport, ID Card) with optional templates
 * - Clickable website inputs without redundant "website" label
 * - 20-box 4-char Recovery Code Editor with Paste All & auto multi-code paste distribution
 * - Zero hardcoded PII placeholders (open-source i18n compliant)
 */
@Composable
fun ItemEditorScreen(
    initialItem: VaultItem?,
    initialType: CipherType = CipherType.LOGIN,
    initialSecureNoteType: Int = 0,
    email: String = "",
    prefillUsername: Boolean = true,
    folders: List<VaultFolder>,
    scannedTotpUri: String?,
    onConsumeScannedTotp: () -> Unit,
    onRequestQrScan: () -> Unit,
    onCancel: () -> Unit,
    onSave: (VaultItem) -> Unit,
    isSaving: Boolean,
) {
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current
    val fold = LocalFoldableLayout.current
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val isEdit = initialItem != null && initialItem.id.isNotBlank()

    var type by remember(initialItem, initialType) { mutableStateOf(initialItem?.type ?: initialType) }
    var secureNoteType by remember(initialItem, initialSecureNoteType) {
        mutableStateOf(initialItem?.secureNoteType ?: initialSecureNoteType)
    }
    var name by remember(initialItem) { mutableStateOf(initialItem?.name.orEmpty()) }
    var folderId by remember(initialItem) { mutableStateOf(initialItem?.folderId) }
    var favorite by remember(initialItem) { mutableStateOf(initialItem?.favorite ?: false) }
    var notes by remember(initialItem) { mutableStateOf(initialItem?.notes.orEmpty()) }
    var customIconBase64 by remember(initialItem) { mutableStateOf(initialItem?.customIconBase64) }
    var showIconMenu by remember { mutableStateOf(false) }

    var username by remember(initialItem) {
        mutableStateOf(
            initialItem?.login?.username ?: if (!isEdit && prefillUsername && type == CipherType.LOGIN) email else "",
        )
    }
    var password by remember(initialItem) { mutableStateOf(initialItem?.login?.password.orEmpty()) }
    val websiteList = remember(initialItem) {
        val initial = initialItem?.login?.uris?.map { it.uri }.orEmpty()
        mutableStateListOf<String>().apply {
            if (initial.isEmpty()) add("") else addAll(initial)
        }
    }
    var totp by remember(initialItem) { mutableStateOf(initialItem?.login?.totp.orEmpty()) }
    var showTotpField by remember(initialItem) { mutableStateOf(!initialItem?.login?.totp.isNullOrBlank()) }

    val passkeys = remember(initialItem) {
        mutableStateListOf<VaultFido2Credential>().apply {
            addAll(initialItem?.login?.fido2Credentials.orEmpty())
        }
    }
    var pendingDeletePasskey by remember { mutableStateOf<VaultFido2Credential?>(null) }

    var nextFieldId by remember { mutableStateOf(100L) }
    val customFields = remember(initialItem) {
        mutableStateListOf<EditableCustomField>().apply {
            initialItem?.visibleFields.orEmpty().forEachIndexed { idx, f ->
                add(
                    EditableCustomField(
                        id = idx.toLong() + 1L,
                        label = f.name,
                        value = f.value,
                        bitwardenType = f.type,
                    ),
                )
            }
        }
    }
    var addMoreExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(type, secureNoteType) {
        if (!isEdit && type == CipherType.SECURE_NOTE) {
            val untouched = customFields.all { it.isTemplateSeed && it.value.isBlank() }
            if (untouched) {
                customFields.clear()
                val seeds = when (SecureNoteCategory.fromCode(secureNoteType)) {
                    SecureNoteCategory.SOFTWARE -> listOf(
                        (if (s.isVi) "Mã Bản Quyền (License Key)" else "License Key") to 1,
                        (if (s.isVi) "Phiên Bản" else "Version") to 0,
                        "Email" to 0,
                    )
                    SecureNoteCategory.PASSPORT -> listOf(
                        (if (s.isVi) "Số Hộ Chiếu" else "Passport Number") to 1,
                        (if (s.isVi) "Họ Và Tên" else "Full Name") to 0,
                        (if (s.isVi) "Quốc Tịch" else "Nationality") to 0,
                        (if (s.isVi) "Ngày Hết Hạn" else "Expiry Date") to 0,
                    )
                    SecureNoteCategory.ID_CARD -> listOf(
                        (if (s.isVi) "Số CCCD / CMND" else "ID Number") to 1,
                        (if (s.isVi) "Họ Và Tên" else "Full Name") to 0,
                        (if (s.isVi) "Ngày Sinh" else "Date Of Birth") to 0,
                        (if (s.isVi) "Ngày Cấp" else "Issue Date") to 0,
                    )
                    SecureNoteCategory.NOTE -> emptyList()
                }
                seeds.forEach { (lbl, bType) ->
                    nextFieldId += 1L
                    customFields.add(
                        EditableCustomField(
                            id = nextFieldId,
                            label = lbl,
                            value = "",
                            bitwardenType = bType,
                            isTemplateSeed = true,
                        ),
                    )
                }
            }
        }
    }

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

    val iconPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = IconLoader.compressPickedImageToBase64(context, uri, targetPx = 128, quality = 80)
                if (b64 != null) customIconBase64 = b64
            }
        }
    }

    val iconFallbackPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val b64 = IconLoader.compressPickedImageToBase64(context, uri, targetPx = 128, quality = 80)
                if (b64 != null) customIconBase64 = b64
            }
        }
    }

    fun launchIconPicker() {
        context.suppressAutoLockForPicker()
        val usePhotoPicker = runCatching {
            ActivityResultContracts.PickVisualMedia.isPhotoPickerAvailable(context)
        }.getOrDefault(false)
        val launched = if (usePhotoPicker) {
            runCatching {
                iconPicker.launch(
                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                )
            }.isSuccess
        } else {
            false
        }
        if (!launched) {
            runCatching { iconFallbackPicker.launch("image/*") }
        }
    }

    LaunchedEffect(scannedTotpUri) {
        if (!scannedTotpUri.isNullOrBlank()) {
            totp = scannedTotpUri
            showTotpField = true
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

    fun addCustomField(
        label: String,
        bitwardenType: Int = 0,
        keyboardType: KeyboardType = KeyboardType.Text,
        isSecurityQuestion: Boolean = false,
    ) {
        nextFieldId += 1L
        customFields.add(
            EditableCustomField(
                id = nextFieldId,
                label = label,
                value = "",
                bitwardenType = bitwardenType,
                keyboardType = keyboardType,
                isSecurityQuestion = isSecurityQuestion,
            ),
        )
    }

    fun submitSave() {
        val uris = websiteList
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { VaultUri(it) }

        val finalFields = buildList {
            customFields.forEach { f ->
                if ((!f.isTemplateSeed && f.label.isNotBlank()) || f.value.isNotBlank()) {
                    add(
                        VaultField(
                            name = f.label.trim().ifBlank { if (s.isVi) "Trường" else "Field" },
                            value = f.value,
                            type = f.bitwardenType,
                        ),
                    )
                }
            }
            customIconBase64?.takeIf { it.isNotBlank() }?.let { iconB64 ->
                add(
                    VaultField(
                        name = VaultItem.CUSTOM_ICON_FIELD_NAME,
                        value = iconB64,
                        type = 1,
                    ),
                )
            }
        }

        val built = VaultItem(
            id = initialItem?.id.orEmpty(),
            folderId = folderId,
            type = type,
            secureNoteType = if (type == CipherType.SECURE_NOTE) secureNoteType else 0,
            name = name.trim().ifBlank { if (s.isVi) "Không Có Tiêu Đề" else "Untitled" },
            notes = notes,
            favorite = favorite,
            login = if (type == CipherType.LOGIN) {
                VaultLogin(
                    uris = uris,
                    username = username.trim(),
                    password = password,
                    totp = totp.trim().takeIf { it.isNotBlank() },
                    fido2Credentials = passkeys.toList(),
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
            fields = finalFields,
            passwordHistory = initialItem?.passwordHistory.orEmpty(),
        )
        onSave(built)
    }

    val headerCreateSuffix = if (type == CipherType.SECURE_NOTE) {
        SecureNoteCategory.fromCode(secureNoteType).label(s.isVi)
    } else {
        type.label(s.isVi)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        EditingStripedHeader(
            title = if (isEdit) s.editing else "${s.createPrefix}$headerCreateSuffix",
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
                // Context Vault Row
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    OnePassAccountAvatar(email = email, size = 24.dp)
                    Text(ownerName, color = c.textSecondary, fontSize = 14.5.sp, fontWeight = FontWeight.Medium)
                    Text("|", color = c.divider, fontSize = 14.5.sp)
                    Box(
                        modifier = Modifier
                            .size(22.dp)
                            .clip(CircleShape)
                            .background(Color(0xFF475569)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.Shield, contentDescription = null, tint = Color(0xFFCBD5E1), modifier = Modifier.size(13.dp))
                    }
                    Text(
                        text = folders.firstOrNull { it.id == folderId }?.name ?: s.personalVault,
                        color = c.textSecondary,
                        fontSize = 14.5.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Hero [Tappable Custom Icon w/ badge] [Bordered Title Input]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box {
                        ItemIconTile(
                            title = name.ifBlank { headerCreateSuffix },
                            type = type,
                            size = 58.dp,
                            secureNoteType = secureNoteType,
                            customIconBase64 = customIconBase64,
                            websiteUrl = websiteList.firstOrNull { it.isNotBlank() },
                            showDropdownBadge = true,
                            onClick = { showIconMenu = true },
                        )

                        DropdownMenu(
                            expanded = showIconMenu,
                            onDismissRequest = { showIconMenu = false },
                            shape = RoundedCornerShape(14.dp),
                            containerColor = c.surfaceElevated,
                            border = BorderStroke(1.dp, c.cardBorder),
                        ) {
                            DropdownMenuItem(
                                text = { Text(s.chooseIconImage, color = c.textPrimary, fontSize = 15.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, tint = c.accent) },
                                onClick = {
                                    showIconMenu = false
                                    launchIconPicker()
                                },
                            )
                            if (customIconBase64 != null) {
                                DropdownMenuItem(
                                    text = { Text(s.resetIconDefault, color = c.textSecondary, fontSize = 15.5.sp) },
                                    leadingIcon = { Icon(Icons.Rounded.Refresh, contentDescription = null, tint = c.textSecondary) },
                                    onClick = {
                                        showIconMenu = false
                                        customIconBase64 = null
                                    },
                                )
                            }
                        }
                    }

                    val titleShape = RoundedCornerShape(10.dp)
                    BasicTextField(
                        value = name,
                        onValueChange = { name = it },
                        singleLine = true,
                        textStyle = TextStyle(
                            color = c.textPrimary,
                            fontSize = 20.sp,
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
                                    Text(s.titlePlaceholder, color = c.textMuted, fontSize = 16.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
                }

                // Split Secure Note Category Chips (Software, Notes, Passport, ID Card)
                if (type == CipherType.SECURE_NOTE) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        SecureNoteCategory.entries.forEach { cat ->
                            OnePassChip(
                                label = cat.label(s.isVi),
                                selected = SecureNoteCategory.fromCode(secureNoteType) == cat,
                            ) {
                                secureNoteType = cat.typeCode
                            }
                        }
                    }
                }

                // Folder chips
                if (folders.isNotEmpty()) {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        OnePassChip(label = s.personalVault, selected = folderId == null) { folderId = null }
                        folders.forEach { f ->
                            OnePassChip(label = f.name, selected = folderId == f.id) { folderId = f.id }
                        }
                    }
                }

                // Type-specific fields
                when (type) {
                    CipherType.LOGIN -> {
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            borderColor = c.cardBorder,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                EditorFieldRow(
                                    label = s.username,
                                    value = username,
                                    placeholder = "email@example.com",
                                    onValueChange = { username = it },
                                )
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(
                                    label = s.password,
                                    value = password,
                                    placeholder = if (s.isVi) "Nhập Hoặc Tạo Mật Khẩu Mạnh" else "Enter Or Generate Password",
                                    onValueChange = { password = it },
                                    trailing = {
                                        IconButton(
                                            onClick = {
                                                password = PasswordGenerator.generatePassword(
                                                    PasswordOptions(length = 20, symbols = true),
                                                )
                                            },
                                            modifier = Modifier.size(36.dp),
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = s.passwordGenerator, tint = c.accent, modifier = Modifier.size(21.dp))
                                        }
                                    },
                                )
                                if (showTotpField || totp.isNotBlank()) {
                                    HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    EditorFieldRow(
                                        label = s.oneTimePassword,
                                        value = totp,
                                        placeholder = "Base32 Or otpauth://",
                                        onValueChange = { totp = it },
                                        trailing = {
                                            IconButton(onClick = onRequestQrScan, modifier = Modifier.size(36.dp)) {
                                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = s.scanQr2fa, tint = c.accent, modifier = Modifier.size(21.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        if (passkeys.isNotEmpty()) {
                            OnePassCard(
                                modifier = Modifier.fillMaxWidth(),
                                cornerRadius = 12.dp,
                                backgroundColor = c.surface,
                                borderColor = Color(0xFF7253ED).copy(alpha = 0.45f),
                                padding = PaddingValues(0.dp),
                            ) {
                                Column {
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 16.dp, vertical = 11.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(18.dp))
                                        Text(
                                            text = s.passkeys,
                                            color = Color(0xFFC4B5FD),
                                            fontSize = 14.sp,
                                            fontWeight = FontWeight.SemiBold,
                                        )
                                    }
                                    passkeys.forEach { pk ->
                                        HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(start = 8.dp, end = 14.dp, top = 10.dp, bottom = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            IconButton(
                                                onClick = { pendingDeletePasskey = pk },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.RemoveCircleOutline,
                                                    contentDescription = s.remove,
                                                    tint = c.danger,
                                                    modifier = Modifier.size(21.dp),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = pk.rpName?.takeIf { it.isNotBlank() } ?: pk.rpId,
                                                    color = c.textPrimary,
                                                    fontSize = 16.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = pk.userName?.takeIf { it.isNotBlank() } ?: pk.credentialId.take(16),
                                                    color = c.textSecondary,
                                                    fontSize = 14.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Single-Line Websites Card (URL links ONLY, no redundant "website" label)
                        OnePassCard(
                            modifier = Modifier.fillMaxWidth(),
                            cornerRadius = 12.dp,
                            backgroundColor = c.surface,
                            padding = PaddingValues(0.dp),
                        ) {
                            Column {
                                websiteList.forEachIndexed { idx, site ->
                                    if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    var focused by remember(idx) { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .let {
                                                if (focused) it.border(1.5.dp, c.accentButtonBg, RoundedCornerShape(8.dp)) else it
                                            }
                                            .padding(start = 8.dp, end = 10.dp, top = 12.dp, bottom = 12.dp),
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
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.RemoveCircleOutline,
                                                contentDescription = s.remove,
                                                tint = c.danger.copy(alpha = 0.85f),
                                                modifier = Modifier.size(21.dp),
                                            )
                                        }
                                        Box(modifier = Modifier.weight(1f)) {
                                            if (!focused && site.length > 34) {
                                                Text(
                                                    text = site,
                                                    color = c.accent,
                                                    fontSize = 16.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .clickable { focused = true },
                                                )
                                            } else {
                                                BasicTextField(
                                                    value = site,
                                                    onValueChange = { websiteList[idx] = it },
                                                    singleLine = true,
                                                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri),
                                                    textStyle = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                                                    cursorBrush = SolidColor(c.accent),
                                                    decorationBox = { inner ->
                                                        if (site.isEmpty()) {
                                                            Text("https://example.com", color = c.textMuted, fontSize = 16.sp, maxLines = 1)
                                                        }
                                                        inner()
                                                    },
                                                    modifier = Modifier
                                                        .fillMaxWidth()
                                                        .onFocusChanged { focused = it.isFocused },
                                                )
                                            }
                                        }
                                        if (websiteList.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val target = if (idx > 0) idx - 1 else websiteList.lastIndex
                                                    val moved = websiteList.removeAt(idx)
                                                    websiteList.add(target, moved)
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DragHandle,
                                                    contentDescription = "Reorder",
                                                    tint = c.textMuted,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
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
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(22.dp))
                                    Text(
                                        text = s.addAnotherWebsite,
                                        color = c.navActiveContent,
                                        fontSize = 16.sp,
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
                                val holderLabel = if (s.isVi) "Tên Chủ Thẻ" else "Cardholder"
                                val brandLabel = if (s.isVi) "Loại Thẻ" else "Brand"
                                val numLabel = if (s.isVi) "Số Thẻ" else "Card Number"
                                val monthLabel = if (s.isVi) "Tháng" else "Month"
                                val yearLabel = if (s.isVi) "Năm" else "Year"
                                EditorFieldRow(holderLabel, cardholder, holderLabel, onValueChange = { cardholder = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(brandLabel, cardBrand, "Visa / Mastercard", onValueChange = { cardBrand = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(numLabel, cardNumber, "•••• •••• •••• ••••", keyboardType = KeyboardType.Number, onValueChange = { cardNumber = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow(monthLabel, expMonth, "MM", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expMonth = it })
                                    EditorFieldRow(yearLabel, expYear, "YYYY", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expYear = it })
                                    EditorFieldRow("CVV", cardCvv, "•••", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { cardCvv = it })
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
                                    EditorFieldRow(s.firstNamePlaceholder, idFirstName, s.firstNamePlaceholder, modifier = Modifier.weight(1f), onValueChange = { idFirstName = it })
                                    EditorFieldRow(s.lastNamePlaceholder, idLastName, s.lastNamePlaceholder, modifier = Modifier.weight(1f), onValueChange = { idLastName = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("Email", idEmail, "email@example.com", keyboardType = KeyboardType.Email, onValueChange = { idEmail = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(s.phonePlaceholder, idPhone, s.phonePlaceholder, keyboardType = KeyboardType.Phone, onValueChange = { idPhone = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                val companyLabel = if (s.isVi) "Công Ty" else "Company"
                                val addrLabel = if (s.isVi) "Địa Chỉ" else "Address"
                                val passportLabel = if (s.isVi) "CCCD / Hộ Chiếu" else "Passport / ID"
                                EditorFieldRow(companyLabel, idCompany, companyLabel, onValueChange = { idCompany = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(addrLabel, idAddress, addrLabel, onValueChange = { idAddress = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow(s.cityPlaceholder, idCity, s.cityPlaceholder, modifier = Modifier.weight(1f), onValueChange = { idCity = it })
                                    EditorFieldRow(s.countryPlaceholder, idCountry, s.countryPlaceholder, modifier = Modifier.weight(1f), onValueChange = { idCountry = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(passportLabel, idPassport, passportLabel, onValueChange = { idPassport = it })
                            }
                        }
                    }

                    CipherType.SECURE_NOTE -> Unit
                }

                // Deep Editable Custom Fields + 20-Box Recovery Codes Editor
                if (customFields.isNotEmpty()) {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        padding = PaddingValues(0.dp),
                    ) {
                        Column {
                            customFields.forEachIndexed { idx, field ->
                                if (idx > 0) HorizontalDivider(color = c.divider, thickness = 0.8.dp)

                                if (RecoveryCodeUtils.isRecoveryCodesField(field.label)) {
                                    var minSlotCount by remember(field.id) { mutableStateOf(RecoveryCodeUtils.RECOVERY_DEFAULT_SLOTS) }
                                    val slots = RecoveryCodeUtils.parseRecoveryCodesToSlots(field.value, minSlotCount)
                                    val filledCount = RecoveryCodeUtils.countFilledRecoveryCodes(slots)
                                    val cols = 2
                                    val focusRequesters = remember(field.id) {
                                        List(RecoveryCodeUtils.RECOVERY_MAX_SLOTS) { FocusRequester() }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(horizontal = 10.dp, vertical = 12.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp),
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            IconButton(
                                                onClick = { customFields.removeAt(idx) },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.RemoveCircleOutline,
                                                    contentDescription = s.remove,
                                                    tint = c.danger.copy(alpha = 0.85f),
                                                    modifier = Modifier.size(21.dp),
                                                )
                                            }
                                            Icon(
                                                Icons.Rounded.GridView,
                                                contentDescription = null,
                                                tint = c.accent,
                                                modifier = Modifier.size(19.dp),
                                            )
                                            BasicTextField(
                                                value = field.label,
                                                onValueChange = { updatedLabel ->
                                                    customFields[idx] = field.copy(label = updatedLabel, isTemplateSeed = false)
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = c.fieldLabel,
                                                    fontSize = 14.5.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                ),
                                                cursorBrush = SolidColor(c.accent),
                                                modifier = Modifier.weight(1f),
                                            )
                                            Text(
                                                text = "$filledCount/${slots.size}",
                                                color = c.accent,
                                                fontSize = 12.sp,
                                                fontWeight = FontWeight.SemiBold,
                                                modifier = Modifier
                                                    .clip(RoundedCornerShape(6.dp))
                                                    .background(c.accentSoft)
                                                    .padding(horizontal = 7.dp, vertical = 2.dp),
                                            )
                                            IconButton(
                                                onClick = {
                                                    val clip = clipboard.getText()?.text.orEmpty()
                                                    if (clip.isNotBlank()) {
                                                        val (nextSlots, _) = RecoveryCodeUtils.applyRecoveryCodesPaste(
                                                            currentSlots = slots,
                                                            targetIndex = 0,
                                                            rawText = clip,
                                                            forceFromStart = true,
                                                        )
                                                        if (nextSlots.size > minSlotCount) {
                                                            minSlotCount = nextSlots.size
                                                        }
                                                        customFields[idx] = field.copy(
                                                            value = RecoveryCodeUtils.serializeRecoveryCodesSlots(nextSlots),
                                                            isTemplateSeed = false,
                                                        )
                                                    }
                                                },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.ContentPaste,
                                                    contentDescription = s.pasteAll,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                            IconButton(
                                                onClick = {
                                                    minSlotCount = RecoveryCodeUtils.RECOVERY_DEFAULT_SLOTS
                                                    customFields[idx] = field.copy(value = "", isTemplateSeed = false)
                                                },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DeleteSweep,
                                                    contentDescription = s.clearAll,
                                                    tint = c.textSecondary,
                                                    modifier = Modifier.size(19.dp),
                                                )
                                            }
                                        }

                                        Column(
                                            modifier = Modifier.fillMaxWidth(),
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                        ) {
                                            slots.chunked(cols).forEachIndexed { rowIdx, rowCells ->
                                                Row(
                                                    modifier = Modifier.fillMaxWidth(),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                ) {
                                                    rowCells.forEachIndexed { colIdx, slotVal ->
                                                        val slotIdx = rowIdx * cols + colIdx
                                                        var slotFocused by remember(field.id, slotIdx) { mutableStateOf(false) }
                                                        Row(
                                                            modifier = Modifier
                                                                .weight(1f)
                                                                .height(44.dp)
                                                                .clip(RoundedCornerShape(8.dp))
                                                                .background(if (c.isDark) Color(0xFF14171D) else c.sheetBackground)
                                                                .border(
                                                                    width = if (slotFocused) 1.5.dp else 1.dp,
                                                                    color = if (slotFocused) c.accentButtonBg else c.cardBorder,
                                                                    shape = RoundedCornerShape(8.dp),
                                                                )
                                                                .padding(horizontal = 8.dp),
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            horizontalArrangement = Arrangement.spacedBy(5.dp),
                                                        ) {
                                                            Text(
                                                                text = "%02d".format(slotIdx + 1),
                                                                color = c.textMuted,
                                                                fontFamily = OpenVaultMonoFontFamily,
                                                                fontSize = 11.5.sp,
                                                            )
                                                            BasicTextField(
                                                                value = slotVal,
                                                                onValueChange = { rawInput ->
                                                                    val hasMultiDelimiter = rawInput.length > RecoveryCodeUtils.RECOVERY_MAX_LEN ||
                                                                        rawInput.contains('\n') || rawInput.contains('\t') ||
                                                                        rawInput.contains(',') || rawInput.contains(';')
                                                                    if (hasMultiDelimiter && rawInput.length > 1) {
                                                                        val (nextSlots, nextFocus) = RecoveryCodeUtils.applyRecoveryCodesPaste(
                                                                            currentSlots = slots,
                                                                            targetIndex = slotIdx,
                                                                            rawText = rawInput,
                                                                        )
                                                                        if (nextSlots.size > minSlotCount) {
                                                                            minSlotCount = nextSlots.size
                                                                        }
                                                                        customFields[idx] = field.copy(
                                                                            value = RecoveryCodeUtils.serializeRecoveryCodesSlots(nextSlots),
                                                                            isTemplateSeed = false,
                                                                        )
                                                                        if (nextFocus != slotIdx && nextFocus < focusRequesters.size) {
                                                                            runCatching { focusRequesters[nextFocus].requestFocus() }
                                                                        }
                                                                    } else {
                                                                        val clean = rawInput
                                                                            .filter { it.isLetterOrDigit() || it == ' ' || it == '-' }
                                                                            .replace(Regex("\\s+"), " ")
                                                                            .take(RecoveryCodeUtils.RECOVERY_MAX_LEN)
                                                                        val updated = slots.toMutableList()
                                                                        updated[slotIdx] = clean
                                                                        customFields[idx] = field.copy(
                                                                            value = RecoveryCodeUtils.serializeRecoveryCodesSlots(updated),
                                                                            isTemplateSeed = false,
                                                                        )
                                                                    }
                                                                },
                                                                singleLine = true,
                                                                textStyle = TextStyle(
                                                                    color = c.textPrimary,
                                                                    fontFamily = OpenVaultMonoFontFamily,
                                                                    fontSize = 14.sp,
                                                                    fontWeight = FontWeight.SemiBold,
                                                                ),
                                                                cursorBrush = SolidColor(c.accent),
                                                                decorationBox = { inner ->
                                                                    if (slotVal.isEmpty()) {
                                                                        Text(
                                                                            text = "123456",
                                                                            color = c.textMuted.copy(alpha = 0.4f),
                                                                            fontFamily = OpenVaultMonoFontFamily,
                                                                            fontSize = 14.sp,
                                                                        )
                                                                    }
                                                                    inner()
                                                                },
                                                                modifier = Modifier
                                                                    .weight(1f)
                                                                    .focusRequester(focusRequesters[slotIdx])
                                                                    .onFocusChanged { slotFocused = it.isFocused },
                                                            )
                                                        }
                                                    }
                                                }
                                            }
                                        }

                                        if (slots.size < RecoveryCodeUtils.RECOVERY_MAX_SLOTS) {
                                            TextButton(
                                                onClick = {
                                                    minSlotCount = minOf(RecoveryCodeUtils.RECOVERY_MAX_SLOTS, slots.size + 2)
                                                },
                                                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 4.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.Add,
                                                    contentDescription = null,
                                                    tint = c.accent,
                                                    modifier = Modifier.size(16.dp),
                                                )
                                                Spacer(Modifier.size(4.dp))
                                                Text(
                                                    text = if (s.isVi) "Thêm 2 ô mã" else "Add 2 slots",
                                                    color = c.accent,
                                                    fontSize = 13.sp,
                                                    fontWeight = FontWeight.SemiBold,
                                                )
                                            }
                                        }
                                    }
                                } else {
                                    var isFocused by remember(field.id) { mutableStateOf(false) }
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .let {
                                                if (isFocused) it.border(1.5.dp, c.accentButtonBg, RoundedCornerShape(8.dp)) else it
                                            }
                                            .padding(start = 8.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        IconButton(
                                            onClick = { customFields.removeAt(idx) },
                                            modifier = Modifier.size(34.dp),
                                        ) {
                                            Icon(
                                                Icons.Rounded.RemoveCircleOutline,
                                                contentDescription = s.remove,
                                                tint = c.danger.copy(alpha = 0.85f),
                                                modifier = Modifier.size(21.dp),
                                            )
                                        }

                                        Column(
                                            modifier = Modifier.weight(1f),
                                            verticalArrangement = Arrangement.spacedBy(6.dp),
                                        ) {
                                            BasicTextField(
                                                value = field.label,
                                                onValueChange = { updatedLabel ->
                                                    customFields[idx] = field.copy(label = updatedLabel, isTemplateSeed = false)
                                                },
                                                singleLine = true,
                                                textStyle = TextStyle(
                                                    color = c.fieldLabel,
                                                    fontSize = 14.sp,
                                                    fontWeight = FontWeight.Medium,
                                                ),
                                                cursorBrush = SolidColor(c.accent),
                                                decorationBox = { inner ->
                                                    if (field.label.isEmpty()) {
                                                        Text(s.fieldTitlePlaceholder, color = c.textMuted, fontSize = 14.sp)
                                                    }
                                                    inner()
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onFocusChanged { if (it.isFocused) isFocused = true },
                                            )

                                            HorizontalDivider(color = c.divider.copy(alpha = 0.65f), thickness = 0.7.dp)

                                            BasicTextField(
                                                value = field.value,
                                                onValueChange = { updatedVal ->
                                                    customFields[idx] = field.copy(value = updatedVal, isTemplateSeed = false)
                                                },
                                                singleLine = true,
                                                keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                                                textStyle = TextStyle(color = c.textPrimary, fontSize = 16.sp),
                                                cursorBrush = SolidColor(c.accent),
                                                decorationBox = { inner ->
                                                    if (field.value.isEmpty()) {
                                                        Text(s.fieldValuePlaceholder, color = c.textMuted, fontSize = 16.sp)
                                                    }
                                                    inner()
                                                },
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .onFocusChanged { isFocused = it.isFocused },
                                            )
                                        }

                                        if (field.bitwardenType == 1 || field.isSecurityQuestion) {
                                            IconButton(
                                                onClick = {
                                                    val generated = if (field.isSecurityQuestion) {
                                                        PasswordGenerator.generatePassphrase(
                                                            PassphraseOptions(wordCount = 3, separator = "-", capitalize = true, includeNumber = true),
                                                        )
                                                    } else {
                                                        PasswordGenerator.generatePassword(PasswordOptions(length = 18, symbols = true))
                                                    }
                                                    customFields[idx] = field.copy(value = generated, isTemplateSeed = false)
                                                },
                                                modifier = Modifier.size(34.dp),
                                            ) {
                                                Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate", tint = c.accent, modifier = Modifier.size(20.dp))
                                            }
                                        }

                                        if (customFields.size > 1) {
                                            IconButton(
                                                onClick = {
                                                    val target = if (idx > 0) idx - 1 else customFields.lastIndex
                                                    val moved = customFields.removeAt(idx)
                                                    customFields.add(target, moved)
                                                },
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder", tint = c.textMuted, modifier = Modifier.size(20.dp))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // "+ Add More" Card & Dropdown Menu (now includes Recovery Codes)
                Box(modifier = Modifier.fillMaxWidth()) {
                    OnePassCard(
                        modifier = Modifier.fillMaxWidth(),
                        cornerRadius = 12.dp,
                        backgroundColor = c.surface,
                        padding = PaddingValues(horizontal = 16.dp, vertical = 14.dp),
                        onClick = { addMoreExpanded = true },
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween,
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                            ) {
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(22.dp))
                                Text(
                                    text = s.addMore,
                                    color = c.navActiveContent,
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = addMoreExpanded,
                        onDismissRequest = { addMoreExpanded = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceElevated,
                        border = BorderStroke(1.dp, c.cardBorder),
                        modifier = Modifier.widthIn(min = 270.dp, max = 350.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(s.addRecoveryCodes, color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                    Text(s.addRecoveryCodesSub, color = c.textSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            leadingIcon = { Icon(Icons.Rounded.GridView, contentDescription = null, tint = c.accent, modifier = Modifier.size(20.dp)) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.recoveryCodesTitle, bitwardenType = 1)
                            },
                        )
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(s.addSecQuestion, color = c.textPrimary, fontSize = 15.5.sp, fontWeight = FontWeight.Medium)
                                    Text(s.addSecQuestionSub, color = c.textSecondary, fontSize = 12.5.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addSecQuestion, bitwardenType = 1, isSecurityQuestion = true)
                            },
                        )
                        HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                        DropdownMenuItem(
                            text = { Text(s.addText, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addText, bitwardenType = 0)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addUrl, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addUrl, bitwardenType = 0, keyboardType = KeyboardType.Uri)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addEmail, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addEmail, bitwardenType = 0, keyboardType = KeyboardType.Email)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addAddress, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addAddress, bitwardenType = 0)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addDate, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addDate, bitwardenType = 0)
                            },
                        )
                        if (type == CipherType.LOGIN) {
                            DropdownMenuItem(
                                text = { Text(s.addOtp, color = c.textPrimary, fontSize = 15.5.sp) },
                                onClick = {
                                    addMoreExpanded = false
                                    showTotpField = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(s.addPassword, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addPassword, bitwardenType = 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addPhone, color = c.textPrimary, fontSize = 15.5.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addPhone, bitwardenType = 0, keyboardType = KeyboardType.Phone)
                            },
                        )
                    }
                }

                // Notes Card
                OnePassCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 12.dp,
                    backgroundColor = c.surface,
                    padding = PaddingValues(0.dp),
                ) {
                    EditorFieldRow(
                        label = s.notes,
                        value = notes,
                        placeholder = s.notesPlaceholder,
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
                        Text(s.pinToFavorites, color = c.textPrimary, fontSize = 16.sp)
                        Switch(checked = favorite, onCheckedChange = { favorite = it })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    pendingDeletePasskey?.let { pk ->
        AlertDialog(
            onDismissRequest = { pendingDeletePasskey = null },
            containerColor = c.surfaceElevated,
            title = { Text(s.removePasskeyTitle, color = c.textPrimary, fontWeight = FontWeight.Bold) },
            text = {
                Text(
                    "${pk.rpName ?: pk.rpId} (${pk.userName ?: pk.credentialId.take(12)})\n\n${s.removePasskeyDesc}",
                    color = c.textSecondary,
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        passkeys.remove(pk)
                        pendingDeletePasskey = null
                    },
                ) {
                    Text(s.remove, color = c.danger, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeletePasskey = null }) {
                    Text(s.cancel, color = c.textSecondary)
                }
            },
        )
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
            .padding(horizontal = 16.dp, vertical = 13.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(text = label, color = c.fieldLabel, fontSize = 13.5.sp, fontWeight = FontWeight.Medium)
            BasicTextField(
                value = value,
                onValueChange = onValueChange,
                singleLine = singleLine,
                minLines = minLines,
                keyboardOptions = KeyboardOptions(keyboardType = keyboardType),
                textStyle = TextStyle(color = c.textPrimary, fontSize = 16.5.sp),
                cursorBrush = SolidColor(c.accent),
                decorationBox = { inner ->
                    if (value.isEmpty()) {
                        Text(text = placeholder, color = c.textMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
