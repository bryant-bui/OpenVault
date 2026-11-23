package io.openvault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Key
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.security.AppThemeMode
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.MainTab
import io.openvault.app.ui.components.OnePassBottomBar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassExtendedFab
import io.openvault.app.ui.components.OnePassNavigationRail
import io.openvault.app.ui.components.OpenVaultBrandCrest
import io.openvault.app.ui.screens.AuthScreen
import io.openvault.app.ui.screens.GeneratorScreen
import io.openvault.app.ui.screens.HomeScreen
import io.openvault.app.ui.screens.ItemDetailScreen
import io.openvault.app.ui.screens.ItemEditorScreen
import io.openvault.app.ui.screens.ItemsScreen
import io.openvault.app.ui.screens.NewItemSheet
import io.openvault.app.ui.screens.QrScannerScreen
import io.openvault.app.ui.screens.SettingsScreen
import io.openvault.app.ui.screens.TotpScreen
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.screens.WatchtowerTabScreen
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme
import io.openvault.app.ui.theme.ProvideFoldableLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val container = (application as OpenVaultApplication).container

        setContent {
            val settings by container.preferences.settings.collectAsState()
            val darkActive = when (settings.themeMode) {
                AppThemeMode.DARK -> true
                AppThemeMode.LIGHT -> false
                AppThemeMode.SYSTEM -> isSystemInDarkTheme()
            }

            LaunchedEffect(darkActive) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkActive) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                    navigationBarStyle = if (darkActive) {
                        SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.light(
                            android.graphics.Color.TRANSPARENT,
                            android.graphics.Color.TRANSPARENT,
                        )
                    },
                )
            }

            LaunchedEffect(settings.flagSecureEnabled) {
                if (settings.flagSecureEnabled) {
                    window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                } else {
                    window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
                }
            }

            OpenVaultTheme(themeMode = settings.themeMode) {
                ProvideFoldableLayout(activity = this@MainActivity) {
                    OpenVaultRootApp(
                        container = container,
                        activity = this@MainActivity,
                    )
                }
            }
        }
    }
}

private sealed interface OverlayRoute {
    data object None : OverlayRoute
    data object NewItemPicker : OverlayRoute
    data object Settings : OverlayRoute
    data object Generator : OverlayRoute
    data object TotpHub : OverlayRoute
    data class Detail(val itemId: String) : OverlayRoute
    data class Editor(val initialItem: VaultItem?, val initialType: CipherType = CipherType.LOGIN) : OverlayRoute
    data class QrScanner(val returnToEditor: Boolean) : OverlayRoute
}

@Composable
private fun OpenVaultRootApp(
    container: AppContainer,
    activity: FragmentActivity,
) {
    val scope = rememberCoroutineScope()
    val fold = LocalFoldableLayout.current
    val c = LocalOpenVaultColors.current

    val lockState by container.sessionManager.lockState.collectAsState()
    val account by container.preferences.account.collectAsState()
    val items by container.repository.items.collectAsState()
    val folders by container.repository.folders.collectAsState()
    val watchtower by container.repository.watchtower.collectAsState()
    val isSyncing by container.repository.isSyncing.collectAsState()

    var selectedTabName by rememberSaveable { mutableStateOf(MainTab.HOME.name) }
    val selectedTab = remember(selectedTabName) {
        runCatching { MainTab.valueOf(selectedTabName) }.getOrDefault(MainTab.HOME)
    }
    var selectedItemId by rememberSaveable { mutableStateOf<String?>(null) }

    var overlay by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var pendingScannedTotp by remember { mutableStateOf<String?>(null) }
    var editingReturnItem by remember { mutableStateOf<VaultItem?>(null) }
    var isSavingItem by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500L)
            toastMessage = null
        }
    }

    LaunchedEffect(lockState) {
        if (lockState == VaultLockState.UNLOCKED) {
            runCatching { container.repository.syncNow(force = false) }
        } else {
            overlay = OverlayRoute.None
        }
    }

    fun showToast(msg: String) {
        toastMessage = msg
    }

    fun copyToClipboard(label: String, value: String, sensitive: Boolean) {
        container.clipboard.copy(label, value, isSensitive = sensitive)
        val clearSec = container.preferences.settings.value.clipboardClearSeconds
        val suffix = if (sensitive && clearSec > 0) " (tự xóa sau ${clearSec}s)" else ""
        showToast("Đã sao chép $label$suffix")
    }

    fun triggerSync() {
        scope.launch {
            runCatching { container.repository.syncNow(force = true) }
                .onSuccess { showToast("Đã đồng bộ kho thành công!") }
                .onFailure { showToast("Lỗi đồng bộ: ${it.message}") }
        }
    }

    fun openItem(item: VaultItem) {
        selectedItemId = item.id
        overlay = OverlayRoute.Detail(item.id)
    }

    fun openQrScanner(returnToEditor: Boolean) {
        if (!returnToEditor) editingReturnItem = null
        overlay = OverlayRoute.QrScanner(returnToEditor = returnToEditor)
    }

    // Smart BackHandler: handles overlays on compact cover screen & secondary pane overlays on unfolded Z Fold 3
    val hasClosableOverlay = if (fold.isTwoPane) {
        overlay != OverlayRoute.None && overlay !is OverlayRoute.Detail
    } else {
        overlay != OverlayRoute.None
    }

    BackHandler(enabled = hasClosableOverlay) {
        overlay = when (val current = overlay) {
            is OverlayRoute.QrScanner -> if (current.returnToEditor) OverlayRoute.Editor(editingReturnItem) else OverlayRoute.None
            is OverlayRoute.Editor -> selectedItemId?.let { OverlayRoute.Detail(it) } ?: OverlayRoute.None
            else -> OverlayRoute.None
        }
    }

    @Composable
    fun ActiveTabContent(modifier: Modifier = Modifier) {
        Box(modifier = modifier) {
            when (selectedTab) {
                MainTab.HOME -> HomeScreen(
                    email = account.email,
                    items = items,
                    isSyncing = isSyncing,
                    selectedItemId = selectedItemId,
                    onOpenSettings = { overlay = OverlayRoute.Settings },
                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                    onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                    onOpenNewItem = { overlay = OverlayRoute.NewItemPicker },
                    onSync = ::triggerSync,
                    onLock = { container.sessionManager.lock() },
                    onSelectItem = ::openItem,
                    onCopyTotp = { title, code -> copyToClipboard("mã 2FA $title", code, true) },
                )

                MainTab.ITEMS -> ItemsScreen(
                    email = account.email,
                    items = items,
                    folders = folders,
                    isSyncing = isSyncing,
                    selectedItemId = selectedItemId,
                    onOpenSettings = { overlay = OverlayRoute.Settings },
                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                    onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                    onOpenTotpHub = { overlay = OverlayRoute.TotpHub },
                    onSync = ::triggerSync,
                    onLock = { container.sessionManager.lock() },
                    onSelectItem = ::openItem,
                    onCreateFolder = { name ->
                        scope.launch {
                            runCatching { container.repository.createFolder(name) }
                                .onSuccess { showToast("Đã tạo thư mục \"$name\"") }
                                .onFailure { showToast("Lỗi tạo thư mục: ${it.message}") }
                        }
                    },
                )

                MainTab.WATCHTOWER -> WatchtowerTabScreen(
                    email = account.email,
                    report = watchtower,
                    isSyncing = isSyncing,
                    selectedItemId = selectedItemId,
                    onOpenSettings = { overlay = OverlayRoute.Settings },
                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                    onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                    onSync = ::triggerSync,
                    onLock = { container.sessionManager.lock() },
                    onOpenItem = ::openItem,
                )
            }
        }
    }

    @Composable
    fun RenderItemDetail(item: VaultItem, showBackButton: Boolean) {
        val folder = folders.firstOrNull { it.id == item.folderId }
        ItemDetailScreen(
            item = item,
            email = account.email,
            folder = folder,
            showBackButton = showBackButton,
            onBack = { overlay = OverlayRoute.None },
            onEdit = {
                editingReturnItem = item
                overlay = OverlayRoute.Editor(item, item.type)
            },
            onToggleFavorite = {
                scope.launch {
                    runCatching { container.repository.toggleFavorite(item) }
                        .onFailure { showToast("Lỗi cập nhật: ${it.message}") }
                }
            },
            onSoftDelete = {
                scope.launch {
                    runCatching { container.repository.softDeleteItem(item.id) }
                        .onSuccess {
                            if (selectedItemId == item.id) selectedItemId = null
                            overlay = OverlayRoute.None
                            showToast("Đã chuyển \"${item.name}\" vào Đã xóa gần đây.")
                        }
                        .onFailure { showToast("Lỗi xóa: ${it.message}") }
                }
            },
            onRestore = {
                scope.launch {
                    runCatching { container.repository.restoreItem(item.id) }
                        .onSuccess { showToast("Đã khôi phục \"${item.name}\".") }
                        .onFailure { showToast("Lỗi khôi phục: ${it.message}") }
                }
            },
            onHardDelete = {
                scope.launch {
                    runCatching { container.repository.hardDeleteItem(item.id) }
                        .onSuccess {
                            if (selectedItemId == item.id) selectedItemId = null
                            overlay = OverlayRoute.None
                            showToast("Đã xóa vĩnh viễn \"${item.name}\".")
                        }
                        .onFailure { showToast("Lỗi xóa: ${it.message}") }
                }
            },
            onCopy = ::copyToClipboard,
        )
    }

    @Composable
    fun RenderItemEditor(editor: OverlayRoute.Editor) {
        ItemEditorScreen(
            initialItem = editor.initialItem,
            initialType = editor.initialType,
            email = account.email,
            folders = folders,
            scannedTotpUri = pendingScannedTotp,
            onConsumeScannedTotp = { pendingScannedTotp = null },
            onRequestQrScan = {
                editingReturnItem = editor.initialItem
                overlay = OverlayRoute.QrScanner(returnToEditor = true)
            },
            onCancel = {
                overlay = selectedItemId?.let { OverlayRoute.Detail(it) } ?: OverlayRoute.None
            },
            isSaving = isSavingItem,
            onSave = { built ->
                isSavingItem = true
                scope.launch {
                    runCatching { container.repository.saveItem(built) }
                        .onSuccess { saved ->
                            isSavingItem = false
                            selectedItemId = saved.id
                            overlay = OverlayRoute.Detail(saved.id)
                            showToast("Đã mã hóa & lưu \"${saved.name}\"!")
                        }
                        .onFailure { ex ->
                            isSavingItem = false
                            showToast("Không thể lưu: ${ex.message}")
                        }
                }
            },
        )
    }

    Box(modifier = Modifier.fillMaxSize()) {
        when (lockState) {
            VaultLockState.LOGGED_OUT -> {
                AuthScreen(
                    repository = container.repository,
                    onAuthenticated = { showToast("Đăng nhập thành công & đã đồng bộ kho!") },
                )
            }

            VaultLockState.LOCKED -> {
                UnlockScreen(
                    repository = container.repository,
                    activity = activity,
                    autoTriggerBiometric = true,
                    allowLogout = true,
                    onUnlocked = { },
                )
            }

            VaultLockState.UNLOCKED -> {
                when (val currentOverlay = overlay) {
                    is OverlayRoute.QrScanner -> {
                        QrScannerScreen(
                            onResult = { scanned ->
                                pendingScannedTotp = scanned
                                overlay = if (currentOverlay.returnToEditor) {
                                    OverlayRoute.Editor(editingReturnItem)
                                } else {
                                    editingReturnItem = null
                                    OverlayRoute.Editor(null, CipherType.LOGIN)
                                }
                                showToast("Đã quét mã QR 2FA thành công!")
                            },
                            onCancel = {
                                overlay = if (currentOverlay.returnToEditor) {
                                    OverlayRoute.Editor(editingReturnItem)
                                } else {
                                    OverlayRoute.None
                                }
                            },
                        )
                    }

                    OverlayRoute.NewItemPicker -> {
                        NewItemSheet(
                            onCreateType = { type ->
                                editingReturnItem = null
                                overlay = OverlayRoute.Editor(initialItem = null, initialType = type)
                            },
                            onOpenGenerator = { overlay = OverlayRoute.Generator },
                            onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                            onDismiss = { overlay = OverlayRoute.None },
                        )
                    }

                    else -> {
                        if (fold.isTwoPane) {
                            // ================================================================
                            // UNFOLDED GALAXY Z FOLD 3 / TABLET: 3-COLUMN ADAPTIVE TWO-PANE UI
                            // [Left Navigation Rail] | [Master List Pane] | [Hinge] | [Detail Pane]
                            // ================================================================
                            Row(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .background(c.background),
                            ) {
                                OnePassNavigationRail(
                                    selectedTab = selectedTab,
                                    isSyncing = isSyncing,
                                    onSelect = { tab -> selectedTabName = tab.name },
                                    onNewItem = { overlay = OverlayRoute.NewItemPicker },
                                    onSync = ::triggerSync,
                                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                                    onOpenSettings = { overlay = OverlayRoute.Settings },
                                    onLock = { container.sessionManager.lock() },
                                )

                                ActiveTabContent(
                                    modifier = Modifier
                                        .weight(fold.leftPaneWeight)
                                        .fillMaxHeight(),
                                )

                                // Respect Z Fold 3 physical crease / separating hinge
                                if (fold.hingeWidthDp > 0.dp) {
                                    Box(
                                        modifier = Modifier
                                            .width(fold.hingeWidthDp)
                                            .fillMaxHeight()
                                            .background(c.background),
                                    )
                                } else {
                                    Box(
                                        modifier = Modifier
                                            .width(0.9.dp)
                                            .fillMaxHeight()
                                            .background(c.divider),
                                    )
                                }

                                // Right Detail / Editor / Settings / Generator Pane
                                Box(
                                    modifier = Modifier
                                        .weight(1f - fold.leftPaneWeight)
                                        .fillMaxHeight()
                                        .background(c.sheetBackground),
                                ) {
                                    when (currentOverlay) {
                                        OverlayRoute.Settings -> SettingsScreen(
                                            repository = container.repository,
                                            activity = activity,
                                            showCloseButton = true,
                                            onBack = { overlay = OverlayRoute.None },
                                            onShowMessage = ::showToast,
                                            onLock = {
                                                overlay = OverlayRoute.None
                                                container.sessionManager.lock()
                                            },
                                        )

                                        OverlayRoute.Generator -> GeneratorScreen(
                                            onBack = { overlay = OverlayRoute.None },
                                            onCopy = { pwd -> copyToClipboard("mật khẩu mới", pwd, true) },
                                        )

                                        OverlayRoute.TotpHub -> TotpScreen(
                                            items = items,
                                            onBack = { overlay = OverlayRoute.None },
                                            onScanQrToAdd = { openQrScanner(returnToEditor = false) },
                                            onOpenItem = ::openItem,
                                            onCopyCode = { title, code -> copyToClipboard("mã 2FA $title", code, true) },
                                        )

                                        is OverlayRoute.Editor -> RenderItemEditor(currentOverlay)

                                        else -> {
                                            val activeId = (currentOverlay as? OverlayRoute.Detail)?.itemId ?: selectedItemId
                                            val liveItem = items.firstOrNull { it.id == activeId }
                                                ?: items.firstOrNull { !it.isDeleted }
                                            if (liveItem != null) {
                                                if (selectedItemId != liveItem.id) {
                                                    LaunchedEffect(liveItem.id) { selectedItemId = liveItem.id }
                                                }
                                                RenderItemDetail(item = liveItem, showBackButton = false)
                                            } else {
                                                TwoPaneEmptyDetailState(
                                                    totalItems = items.count { !it.isDeleted },
                                                    onNewItem = { overlay = OverlayRoute.NewItemPicker },
                                                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        } else {
                            // ================================================================
                            // FOLDED Z FOLD 3 COVER SCREEN (320dp) / STANDARD PHONE SINGLE-PANE
                            // ================================================================
                            when (currentOverlay) {
                                OverlayRoute.Settings -> SettingsScreen(
                                    repository = container.repository,
                                    activity = activity,
                                    showCloseButton = true,
                                    onBack = { overlay = OverlayRoute.None },
                                    onShowMessage = ::showToast,
                                    onLock = {
                                        overlay = OverlayRoute.None
                                        container.sessionManager.lock()
                                    },
                                )

                                OverlayRoute.Generator -> GeneratorScreen(
                                    onBack = { overlay = OverlayRoute.None },
                                    onCopy = { pwd -> copyToClipboard("mật khẩu mới", pwd, true) },
                                )

                                OverlayRoute.TotpHub -> TotpScreen(
                                    items = items,
                                    onBack = { overlay = OverlayRoute.None },
                                    onScanQrToAdd = { openQrScanner(returnToEditor = false) },
                                    onOpenItem = ::openItem,
                                    onCopyCode = { title, code -> copyToClipboard("mã 2FA $title", code, true) },
                                )

                                is OverlayRoute.Detail -> {
                                    val liveItem = items.firstOrNull { it.id == currentOverlay.itemId }
                                    if (liveItem == null) {
                                        LaunchedEffect(Unit) { overlay = OverlayRoute.None }
                                    } else {
                                        RenderItemDetail(item = liveItem, showBackButton = true)
                                    }
                                }

                                is OverlayRoute.Editor -> RenderItemEditor(currentOverlay)

                                else -> {
                                    Box(
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .background(c.background),
                                    ) {
                                        ActiveTabContent(modifier = Modifier.fillMaxSize())

                                        // Signature 1Password Android Cobalt "+ Mục mới" Extended FAB above BottomBar
                                        if (selectedTab != MainTab.WATCHTOWER) {
                                            OnePassExtendedFab(
                                                text = "Mục mới",
                                                onClick = { overlay = OverlayRoute.NewItemPicker },
                                                modifier = Modifier
                                                    .align(Alignment.BottomEnd)
                                                    .navigationBarsPadding()
                                                    .padding(end = fold.horizontalPadding, bottom = 84.dp),
                                            )
                                        }

                                        OnePassBottomBar(
                                            selectedTab = selectedTab,
                                            onSelect = { tab -> selectedTabName = tab.name },
                                            modifier = Modifier.align(Alignment.BottomCenter),
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // Clean solid toast notification
        AnimatedVisibility(
            visible = toastMessage != null,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier
                .align(Alignment.TopCenter)
                .statusBarsPadding()
                .padding(horizontal = 20.dp, vertical = 12.dp),
        ) {
            OnePassCard(
                cornerRadius = 12.dp,
                backgroundColor = if (c.isDark) Color(0xFF282B30) else Color(0xFF1F2937),
                borderColor = c.cardBorder,
                padding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = toastMessage.orEmpty(),
                    color = Color.White,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun TwoPaneEmptyDetailState(
    totalItems: Int,
    onNewItem: () -> Unit,
    onOpenGenerator: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.sheetBackground)
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        OpenVaultBrandCrest(size = 64.dp)
        Spacer(modifier = Modifier.height(16.dp))
        Text(
            text = "OpenVault",
            color = c.textPrimary,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
        )
        Spacer(modifier = Modifier.height(6.dp))
        Text(
            text = if (totalItems > 0) {
                "Chọn một mục ở danh sách bên trái để xem chi tiết ($totalItems mục trong kho)."
            } else {
                "Kho của bạn chưa có mục nào. Tạo mục đầu tiên hoặc tạo mật khẩu mạnh ngay."
            },
            color = c.textSecondary,
            fontSize = 14.sp,
            textAlign = TextAlign.Center,
        )
        Spacer(modifier = Modifier.height(22.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Button(
                onClick = onNewItem,
                shape = RoundedCornerShape(12.dp),
                colors = ButtonDefaults.buttonColors(containerColor = c.accentButtonBg, contentColor = Color.White),
            ) {
                Icon(Icons.Rounded.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Mục mới", fontWeight = FontWeight.SemiBold)
            }
            OutlinedButton(
                onClick = onOpenGenerator,
                shape = RoundedCornerShape(12.dp),
                border = BorderStroke(1.dp, c.cardBorder),
            ) {
                Icon(Icons.Rounded.Key, contentDescription = null, tint = c.accent, modifier = Modifier.size(17.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text("Tạo mật khẩu", color = c.textPrimary)
            }
        }
    }
}
