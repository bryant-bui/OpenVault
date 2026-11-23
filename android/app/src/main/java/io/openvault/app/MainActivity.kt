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
import androidx.compose.foundation.background
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.Text
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
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
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme
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
                OpenVaultRootApp(
                    container = container,
                    activity = this@MainActivity,
                )
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
    val lockState by container.sessionManager.lockState.collectAsState()
    val account by container.preferences.account.collectAsState()
    val items by container.repository.items.collectAsState()
    val folders by container.repository.folders.collectAsState()
    val watchtower by container.repository.watchtower.collectAsState()
    val isSyncing by container.repository.isSyncing.collectAsState()

    var selectedTab by remember { mutableStateOf(MainTab.HOME) }
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

    BackHandler(enabled = overlay != OverlayRoute.None) {
        overlay = when (val current = overlay) {
            is OverlayRoute.QrScanner -> if (current.returnToEditor) OverlayRoute.Editor(editingReturnItem) else OverlayRoute.None
            else -> OverlayRoute.None
        }
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
                    OverlayRoute.NewItemPicker -> {
                        NewItemSheet(
                            onCreateType = { type ->
                                editingReturnItem = null
                                overlay = OverlayRoute.Editor(initialItem = null, initialType = type)
                            },
                            onOpenGenerator = { overlay = OverlayRoute.Generator },
                            onOpenQrScanner = {
                                editingReturnItem = null
                                overlay = OverlayRoute.QrScanner(returnToEditor = false)
                            },
                            onDismiss = { overlay = OverlayRoute.None },
                        )
                    }

                    OverlayRoute.Settings -> {
                        SettingsScreen(
                            repository = container.repository,
                            activity = activity,
                            onBack = { overlay = OverlayRoute.None },
                            onShowMessage = ::showToast,
                            onLock = {
                                overlay = OverlayRoute.None
                                container.sessionManager.lock()
                            },
                        )
                    }

                    OverlayRoute.Generator -> {
                        GeneratorScreen(
                            onBack = { overlay = OverlayRoute.None },
                            onCopy = { pwd -> copyToClipboard("mật khẩu mới", pwd, true) },
                        )
                    }

                    OverlayRoute.TotpHub -> {
                        TotpScreen(
                            items = items,
                            onBack = { overlay = OverlayRoute.None },
                            onScanQrToAdd = {
                                editingReturnItem = null
                                overlay = OverlayRoute.QrScanner(returnToEditor = false)
                            },
                            onOpenItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                            onCopyCode = { title, code -> copyToClipboard("mã 2FA $title", code, true) },
                        )
                    }

                    is OverlayRoute.Detail -> {
                        val liveItem = items.firstOrNull { it.id == currentOverlay.itemId }
                        if (liveItem == null) {
                            overlay = OverlayRoute.None
                        } else {
                            val folder = folders.firstOrNull { it.id == liveItem.folderId }
                            ItemDetailScreen(
                                item = liveItem,
                                folder = folder,
                                onBack = { overlay = OverlayRoute.None },
                                onEdit = {
                                    editingReturnItem = liveItem
                                    overlay = OverlayRoute.Editor(liveItem, liveItem.type)
                                },
                                onToggleFavorite = {
                                    scope.launch {
                                        runCatching { container.repository.toggleFavorite(liveItem) }
                                            .onFailure { showToast("Lỗi cập nhật: ${it.message}") }
                                    }
                                },
                                onSoftDelete = {
                                    scope.launch {
                                        runCatching { container.repository.softDeleteItem(liveItem.id) }
                                            .onSuccess {
                                                overlay = OverlayRoute.None
                                                showToast("Đã chuyển \"${liveItem.name}\" vào Đã xóa gần đây.")
                                            }
                                            .onFailure { showToast("Lỗi xóa: ${it.message}") }
                                    }
                                },
                                onRestore = {
                                    scope.launch {
                                        runCatching { container.repository.restoreItem(liveItem.id) }
                                            .onSuccess { showToast("Đã khôi phục \"${liveItem.name}\".") }
                                            .onFailure { showToast("Lỗi khôi phục: ${it.message}") }
                                    }
                                },
                                onHardDelete = {
                                    scope.launch {
                                        runCatching { container.repository.hardDeleteItem(liveItem.id) }
                                            .onSuccess {
                                                overlay = OverlayRoute.None
                                                showToast("Đã xóa vĩnh viễn \"${liveItem.name}\".")
                                            }
                                            .onFailure { showToast("Lỗi xóa: ${it.message}") }
                                    }
                                },
                                onCopy = ::copyToClipboard,
                            )
                        }
                    }

                    is OverlayRoute.Editor -> {
                        ItemEditorScreen(
                            initialItem = currentOverlay.initialItem,
                            initialType = currentOverlay.initialType,
                            folders = folders,
                            scannedTotpUri = pendingScannedTotp,
                            onConsumeScannedTotp = { pendingScannedTotp = null },
                            onRequestQrScan = {
                                editingReturnItem = currentOverlay.initialItem
                                overlay = OverlayRoute.QrScanner(returnToEditor = true)
                            },
                            onCancel = { overlay = OverlayRoute.None },
                            isSaving = isSavingItem,
                            onSave = { built ->
                                isSavingItem = true
                                scope.launch {
                                    runCatching { container.repository.saveItem(built) }
                                        .onSuccess { saved ->
                                            isSavingItem = false
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

                    OverlayRoute.None -> {
                        val c = LocalOpenVaultColors.current
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(c.background),
                        ) {
                            when (selectedTab) {
                                MainTab.HOME -> HomeScreen(
                                    email = account.email,
                                    items = items,
                                    isSyncing = isSyncing,
                                    onOpenSettings = { overlay = OverlayRoute.Settings },
                                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                                    onOpenNewItem = { overlay = OverlayRoute.NewItemPicker },
                                    onSync = {
                                        scope.launch {
                                            runCatching { container.repository.syncNow(force = true) }
                                                .onSuccess { showToast("Đã đồng bộ kho thành công!") }
                                                .onFailure { showToast("Lỗi đồng bộ: ${it.message}") }
                                        }
                                    },
                                    onLock = { container.sessionManager.lock() },
                                    onSelectItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                                    onCopyTotp = { title, code -> copyToClipboard("mã 2FA $title", code, true) },
                                )

                                MainTab.ITEMS -> ItemsScreen(
                                    email = account.email,
                                    items = items,
                                    folders = folders,
                                    isSyncing = isSyncing,
                                    onOpenSettings = { overlay = OverlayRoute.Settings },
                                    onOpenNewItem = { overlay = OverlayRoute.NewItemPicker },
                                    onOpenTotpHub = { overlay = OverlayRoute.TotpHub },
                                    onSync = {
                                        scope.launch {
                                            runCatching { container.repository.syncNow(force = true) }
                                                .onSuccess { showToast("Đã đồng bộ kho thành công!") }
                                                .onFailure { showToast("Lỗi đồng bộ: ${it.message}") }
                                        }
                                    },
                                    onSelectItem = { item -> overlay = OverlayRoute.Detail(item.id) },
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
                                    onOpenSettings = { overlay = OverlayRoute.Settings },
                                    onOpenNewItem = { overlay = OverlayRoute.NewItemPicker },
                                    onOpenItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                                )
                            }

                            OnePassBottomBar(
                                selectedTab = selectedTab,
                                onSelect = { selectedTab = it },
                                modifier = Modifier.align(Alignment.BottomCenter),
                            )
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
            val c = LocalOpenVaultColors.current
            OnePassCard(
                cornerRadius = 12.dp,
                backgroundColor = if (c.isDark) Color(0xFF2C2C2E) else Color(0xFF1F2937),
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
