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
import androidx.compose.material.icons.rounded.DragHandle
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
import androidx.compose.ui.focus.onFocusChanged
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
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
import io.openvault.app.ui.components.EditingStripedHeader
import io.openvault.app.ui.components.ItemIconTile
import io.openvault.app.ui.components.OnePassAccountAvatar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassChip
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import kotlinx.coroutines.launch

private data class EditableCustomField(
    val id: Long,
    val label: String,
    val value: String,
    val bitwardenType: Int, // 0 = Text, 1 = Hidden/Password
    val keyboardType: KeyboardType = KeyboardType.Text,
    val isSecurityQuestion: Boolean = false,
)

/**
 * Authentic 1Password 8 Android Item Editor:
 * - Purple Diagonal-Striped Editing Header + Cobalt Save button
 * - Tappable Item Icon with gallery image picker (E2EE compressed 96x96 WebP stored in `_ov_icon`)
 * - Single-line websites with coral remove button & quick reorder
 * - Full Passkeys management with per-passkey removal & confirmation dialog
 * - Deep editable custom fields (both Title/Label & Value editable inline, blue focus border!)
 * - "+ add more ⌄" menu (Security Questions, Text, URL, Email, Address, Date, One-Time Password, Password, Phone)
 */
@Composable
fun ItemEditorScreen(
    initialItem: VaultItem?,
    initialType: CipherType = CipherType.LOGIN,
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
    val scope = rememberCoroutineScope()
    val isEdit = initialItem != null && initialItem.id.isNotBlank()

    var type by remember(initialItem, initialType) { mutableStateOf(initialItem?.type ?: initialType) }
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
                val b64 = IconLoader.compressPickedImageToBase64(context, uri, targetPx = 96, quality = 78)
                if (b64 != null) customIconBase64 = b64
            }
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
                if (f.label.isNotBlank() || f.value.isNotBlank()) {
                    add(
                        VaultField(
                            name = f.label.trim().ifBlank { "Field" },
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
            name = name.trim().ifBlank { if (s.isVi) "Không có tiêu đề" else "Untitled" },
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

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .navigationBarsPadding()
            .imePadding(),
    ) {
        EditingStripedHeader(
            title = if (isEdit) s.editing else "${s.createPrefix}${type.label(s.isVi)}",
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
                    OnePassAccountAvatar(email = email, size = 22.dp)
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
                        text = folders.firstOrNull { it.id == folderId }?.name ?: s.personalVault,
                        color = c.textSecondary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    )
                }

                // Hero [Tappable Custom Icon w/ ⌄ badge] [Bordered Title Input] (Requirement #7 + Image 3!)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Box {
                        ItemIconTile(
                            title = name.ifBlank { type.label(s.isVi) },
                            type = type,
                            size = 56.dp,
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
                                text = { Text(s.chooseIconImage, color = c.textPrimary, fontSize = 14.5.sp) },
                                leadingIcon = { Icon(Icons.Rounded.Image, contentDescription = null, tint = c.accent) },
                                onClick = {
                                    showIconMenu = false
                                    iconPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly),
                                    )
                                },
                            )
                            if (customIconBase64 != null) {
                                DropdownMenuItem(
                                    text = { Text(s.resetIconDefault, color = c.textSecondary, fontSize = 14.5.sp) },
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
                                    Text(s.titlePlaceholder, color = c.textMuted, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                                inner()
                            }
                        },
                        modifier = Modifier.weight(1f),
                    )
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
                        // Primary Credentials Box (username + password + optional TOTP)
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
                                    placeholder = if (s.isVi) "Nhập hoặc tạo mật khẩu mạnh" else "Enter or generate password",
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
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = s.passwordGenerator, tint = c.accent, modifier = Modifier.size(20.dp))
                                        }
                                    },
                                )
                                if (showTotpField || totp.isNotBlank()) {
                                    HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                    EditorFieldRow(
                                        label = s.oneTimePassword,
                                        value = totp,
                                        placeholder = "Base32 secret or otpauth://",
                                        onValueChange = { totp = it },
                                        trailing = {
                                            IconButton(onClick = onRequestQrScan, modifier = Modifier.size(34.dp)) {
                                                Icon(Icons.Rounded.QrCodeScanner, contentDescription = s.scanQr2fa, tint = c.accent, modifier = Modifier.size(20.dp))
                                            }
                                        },
                                    )
                                }
                            }
                        }

                        // Passkeys Management Card (Requirement #6 — Delete Passkey!)
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
                                            .padding(horizontal = 16.dp, vertical = 10.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    ) {
                                        Icon(Icons.Rounded.Key, contentDescription = null, tint = Color(0xFFA78BFA), modifier = Modifier.size(16.dp))
                                        Text(
                                            text = s.passkeys,
                                            color = Color(0xFFC4B5FD),
                                            fontSize = 13.sp,
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
                                                modifier = Modifier.size(32.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.RemoveCircleOutline,
                                                    contentDescription = s.remove,
                                                    tint = c.danger,
                                                    modifier = Modifier.size(20.dp),
                                                )
                                            }
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                                Text(
                                                    text = pk.rpName?.takeIf { it.isNotBlank() } ?: pk.rpId,
                                                    color = c.textPrimary,
                                                    fontSize = 15.sp,
                                                    fontWeight = FontWeight.Medium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                                Text(
                                                    text = pk.userName?.takeIf { it.isNotBlank() } ?: pk.credentialId.take(16),
                                                    color = c.textSecondary,
                                                    fontSize = 13.sp,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        // Single-Line Websites Card (Requirement #3 — Images 3 & 4!)
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
                                            .padding(start = 8.dp, end = 10.dp, top = 10.dp, bottom = 10.dp),
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
                                                contentDescription = s.remove,
                                                tint = c.danger.copy(alpha = 0.85f),
                                                modifier = Modifier.size(20.dp),
                                            )
                                        }
                                        Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                                            Text(
                                                text = if (idx == 0) s.website else "${s.website} #${idx + 1}",
                                                color = c.textSecondary,
                                                fontSize = 12.5.sp,
                                            )
                                            if (!focused && site.length > 34) {
                                                // Truncate cleanly to 1 line with "..." when unfocused (tap to edit full URL)
                                                Text(
                                                    text = site,
                                                    color = c.textPrimary,
                                                    fontSize = 15.sp,
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
                                                    textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                                                    cursorBrush = SolidColor(c.accent),
                                                    decorationBox = { inner ->
                                                        if (site.isEmpty()) {
                                                            Text("https://example.com", color = c.textMuted, fontSize = 15.sp, maxLines = 1)
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
                                                modifier = Modifier.size(30.dp),
                                            ) {
                                                Icon(
                                                    Icons.Rounded.DragHandle,
                                                    contentDescription = "Reorder",
                                                    tint = c.textMuted,
                                                    modifier = Modifier.size(18.dp),
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
                                    Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(20.dp))
                                    Text(
                                        text = s.addAnotherWebsite,
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
                                EditorFieldRow(if (s.isVi) "tên chủ thẻ" else "cardholder", cardholder, "JOHN DOE", onValueChange = { cardholder = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "loại thẻ" else "brand", cardBrand, "Visa / Mastercard", onValueChange = { cardBrand = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "số thẻ" else "number", cardNumber, "4242 •••• •••• 4242", keyboardType = KeyboardType.Number, onValueChange = { cardNumber = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow(if (s.isVi) "tháng" else "month", expMonth, "08", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expMonth = it })
                                    EditorFieldRow(if (s.isVi) "năm" else "year", expYear, "2029", keyboardType = KeyboardType.Number, modifier = Modifier.weight(1f), onValueChange = { expYear = it })
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
                                    EditorFieldRow(if (s.isVi) "tên" else "first name", idFirstName, "Hoang", modifier = Modifier.weight(1f), onValueChange = { idFirstName = it })
                                    EditorFieldRow(if (s.isVi) "họ" else "last name", idLastName, "Phi", modifier = Modifier.weight(1f), onValueChange = { idLastName = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow("email", idEmail, "email@example.com", keyboardType = KeyboardType.Email, onValueChange = { idEmail = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "số điện thoại" else "phone", idPhone, "+84...", keyboardType = KeyboardType.Phone, onValueChange = { idPhone = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "công ty" else "company", idCompany, "Company", onValueChange = { idCompany = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "địa chỉ" else "address", idAddress, "Street address", onValueChange = { idAddress = it })
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                Row {
                                    EditorFieldRow(if (s.isVi) "thành phố" else "city", idCity, "Da Nang", modifier = Modifier.weight(1f), onValueChange = { idCity = it })
                                    EditorFieldRow(if (s.isVi) "quốc gia" else "country", idCountry, "Vietnam", modifier = Modifier.weight(1f), onValueChange = { idCountry = it })
                                }
                                HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                                EditorFieldRow(if (s.isVi) "cccd / hộ chiếu" else "passport / id", idPassport, "ID number", onValueChange = { idPassport = it })
                            }
                        }
                    }

                    CipherType.SECURE_NOTE -> Unit
                }

                // Deep Editable Custom Fields (BOTH Title/Label AND Value editable inline — Requirement #3 Image 4!)
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
                                        modifier = Modifier.size(32.dp),
                                    ) {
                                        Icon(
                                            Icons.Rounded.RemoveCircleOutline,
                                            contentDescription = s.remove,
                                            tint = c.danger.copy(alpha = 0.85f),
                                            modifier = Modifier.size(20.dp),
                                        )
                                    }

                                    Column(
                                        modifier = Modifier.weight(1f),
                                        verticalArrangement = Arrangement.spacedBy(6.dp),
                                    ) {
                                        // Editable Field Title / Label
                                        BasicTextField(
                                            value = field.label,
                                            onValueChange = { updatedLabel ->
                                                customFields[idx] = field.copy(label = updatedLabel)
                                            },
                                            singleLine = true,
                                            textStyle = TextStyle(
                                                color = c.fieldLabel,
                                                fontSize = 13.sp,
                                                fontWeight = FontWeight.Medium,
                                            ),
                                            cursorBrush = SolidColor(c.accent),
                                            decorationBox = { inner ->
                                                if (field.label.isEmpty()) {
                                                    Text(s.fieldTitlePlaceholder, color = c.textMuted, fontSize = 13.sp)
                                                }
                                                inner()
                                            },
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .onFocusChanged { if (it.isFocused) isFocused = true },
                                        )

                                        HorizontalDivider(color = c.divider.copy(alpha = 0.65f), thickness = 0.7.dp)

                                        // Editable Field Value
                                        BasicTextField(
                                            value = field.value,
                                            onValueChange = { updatedVal ->
                                                customFields[idx] = field.copy(value = updatedVal)
                                            },
                                            singleLine = true,
                                            keyboardOptions = KeyboardOptions(keyboardType = field.keyboardType),
                                            textStyle = TextStyle(color = c.textPrimary, fontSize = 15.sp),
                                            cursorBrush = SolidColor(c.accent),
                                            decorationBox = { inner ->
                                                if (field.value.isEmpty()) {
                                                    Text(s.fieldValuePlaceholder, color = c.textMuted, fontSize = 15.sp)
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
                                                customFields[idx] = field.copy(value = generated)
                                            },
                                            modifier = Modifier.size(32.dp),
                                        ) {
                                            Icon(Icons.Rounded.AutoAwesome, contentDescription = "Generate", tint = c.accent, modifier = Modifier.size(18.dp))
                                        }
                                    }

                                    if (customFields.size > 1) {
                                        IconButton(
                                            onClick = {
                                                val target = if (idx > 0) idx - 1 else customFields.lastIndex
                                                val moved = customFields.removeAt(idx)
                                                customFields.add(target, moved)
                                            },
                                            modifier = Modifier.size(30.dp),
                                        ) {
                                            Icon(Icons.Rounded.DragHandle, contentDescription = "Reorder", tint = c.textMuted, modifier = Modifier.size(18.dp))
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // "+ add more ⌄" Card & Dropdown Menu (Requirement #4 — Exact Image 5 minus excluded items!)
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
                                Icon(Icons.Rounded.Add, contentDescription = null, tint = c.navActiveContent, modifier = Modifier.size(20.dp))
                                Text(
                                    text = s.addMore,
                                    color = c.navActiveContent,
                                    fontSize = 15.sp,
                                    fontWeight = FontWeight.Medium,
                                )
                            }
                            Icon(
                                imageVector = Icons.Rounded.KeyboardArrowDown,
                                contentDescription = null,
                                tint = c.textSecondary,
                                modifier = Modifier.size(20.dp),
                            )
                        }
                    }

                    DropdownMenu(
                        expanded = addMoreExpanded,
                        onDismissRequest = { addMoreExpanded = false },
                        shape = RoundedCornerShape(14.dp),
                        containerColor = c.surfaceElevated,
                        border = BorderStroke(1.dp, c.cardBorder),
                        modifier = Modifier.widthIn(min = 260.dp, max = 340.dp),
                    ) {
                        DropdownMenuItem(
                            text = {
                                Column(modifier = Modifier.padding(vertical = 2.dp)) {
                                    Text(s.addSecQuestion, color = c.textPrimary, fontSize = 15.sp, fontWeight = FontWeight.Medium)
                                    Text(s.addSecQuestionSub, color = c.textSecondary, fontSize = 12.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                }
                            },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addSecQuestion, bitwardenType = 1, isSecurityQuestion = true)
                            },
                        )
                        HorizontalDivider(color = c.divider, thickness = 0.8.dp)
                        DropdownMenuItem(
                            text = { Text(s.addText, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addText, bitwardenType = 0)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addUrl, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addUrl, bitwardenType = 0, keyboardType = KeyboardType.Uri)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addEmail, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addEmail, bitwardenType = 0, keyboardType = KeyboardType.Email)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addAddress, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addAddress, bitwardenType = 0)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addDate, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addDate, bitwardenType = 0)
                            },
                        )
                        if (type == CipherType.LOGIN) {
                            DropdownMenuItem(
                                text = { Text(s.addOtp, color = c.textPrimary, fontSize = 15.sp) },
                                onClick = {
                                    addMoreExpanded = false
                                    showTotpField = true
                                },
                            )
                        }
                        DropdownMenuItem(
                            text = { Text(s.addPassword, color = c.textPrimary, fontSize = 15.sp) },
                            onClick = {
                                addMoreExpanded = false
                                addCustomField(label = s.addPassword, bitwardenType = 1)
                            },
                        )
                        DropdownMenuItem(
                            text = { Text(s.addPhone, color = c.textPrimary, fontSize = 15.sp) },
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
                        Text(s.pinToFavorites, color = c.textPrimary, fontSize = 15.sp)
                        Switch(checked = favorite, onCheckedChange = { favorite = it })
                    }
                }

                Spacer(modifier = Modifier.height(24.dp))
            }
        }
    }

    // Passkey delete confirmation dialog
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
                        Text(text = placeholder, color = c.textMuted, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
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
