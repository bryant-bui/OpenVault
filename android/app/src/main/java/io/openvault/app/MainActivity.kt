package io.openvault.app

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.LiquidGlassBackground
import io.openvault.app.ui.components.LiquidGlassCard
import io.openvault.app.ui.components.LiquidGlassFloatingDock
import io.openvault.app.ui.components.MainTab
import io.openvault.app.ui.screens.AuthScreen
import io.openvault.app.ui.screens.GeneratorScreen
import io.openvault.app.ui.screens.ItemDetailScreen
import io.openvault.app.ui.screens.ItemEditorScreen
import io.openvault.app.ui.screens.QrScannerScreen
import io.openvault.app.ui.screens.SettingsScreen
import io.openvault.app.ui.screens.TotpScreen
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.screens.VaultScreen
import io.openvault.app.ui.screens.WatchtowerScreen
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

class MainActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OpenVaultApplication).container

        setContent {
            val settings by container.preferences.settings.collectAsState()

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
    data class Detail(val itemId: String) : OverlayRoute
    data class Editor(val initialItem: VaultItem?) : OverlayRoute
    data object Watchtower : OverlayRoute
    data class QrScanner(val returnToEditor: Boolean) : OverlayRoute
}

@Composable
private fun OpenVaultRootApp(
    container: AppContainer,
    activity: FragmentActivity,
) {
    val scope = rememberCoroutineScope()
    val lockState by container.sessionManager.lockState.collectAsState()
    val items by container.repository.items.collectAsState()
    val folders by container.repository.folders.collectAsState()
    val watchtower by container.repository.watchtower.collectAsState()
    val isSyncing by container.repository.isSyncing.collectAsState()

    var selectedTab by remember { mutableStateOf(MainTab.VAULT) }
    var overlay by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
    var pendingScannedTotp by remember { mutableStateOf<String?>(null) }
    var editingReturnItem by remember { mutableStateOf<VaultItem?>(null) }
    var isSavingItem by remember { mutableStateOf(false) }
    var toastMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2600L)
            toastMessage = null
        }
    }

    // Automatically trigger background delta sync right after unlocking
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
                    onUnlocked = { /* State flow switches automatically */ },
                )
            }

            VaultLockState.UNLOCKED -> {
                when (val currentOverlay = overlay) {
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
                                    overlay = OverlayRoute.Editor(liveItem)
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
                                                showToast("Đã chuyển \"${liveItem.name}\" vào thùng rác.")
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

                    is OverlayRoute.Watchtower -> {
                        WatchtowerScreen(
                            report = watchtower,
                            onBack = { overlay = OverlayRoute.None },
                            onOpenItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                        )
                    }

                    is OverlayRoute.QrScanner -> {
                        QrScannerScreen(
                            onResult = { scanned ->
                                pendingScannedTotp = scanned
                                if (currentOverlay.returnToEditor) {
                                    overlay = OverlayRoute.Editor(editingReturnItem)
                                } else {
                                    editingReturnItem = null
                                    overlay = OverlayRoute.Editor(null)
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
                        LiquidGlassBackground {
                            Box(modifier = Modifier.fillMaxSize()) {
                                when (selectedTab) {
                                    MainTab.VAULT -> VaultScreen(
                                        items = items,
                                        folders = folders,
                                        watchtower = watchtower,
                                        isSyncing = isSyncing,
                                        onSync = {
                                            scope.launch {
                                                runCatching { container.repository.syncNow(force = true) }
                                                    .onSuccess { showToast("Đã đồng bộ kho thành công!") }
                                                    .onFailure { showToast("Không thể đồng bộ: ${it.message}") }
                                            }
                                        },
                                        onLock = { container.sessionManager.lock() },
                                        onOpenWatchtower = { overlay = OverlayRoute.Watchtower },
                                        onSelectItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                                        onQuickCopy = { item ->
                                            when (item.type) {
                                                CipherType.LOGIN -> {
                                                    val pwd = item.login?.password.orEmpty()
                                                    if (pwd.isNotBlank()) copyToClipboard("mật khẩu", pwd, true)
                                                    else copyToClipboard("tên đăng nhập", item.login?.username.orEmpty(), false)
                                                }
                                                CipherType.CARD -> copyToClipboard("số thẻ", item.card?.number.orEmpty(), true)
                                                CipherType.IDENTITY -> copyToClipboard("email", item.identity?.email.orEmpty().ifBlank { item.identity?.fullName.orEmpty() }, false)
                                                CipherType.SECURE_NOTE -> copyToClipboard("ghi chú", item.notes, true)
                                            }
                                        },
                                        onAddNew = {
                                            editingReturnItem = null
                                            overlay = OverlayRoute.Editor(null)
                                        },
                                        onCreateFolder = { name ->
                                            scope.launch {
                                                runCatching { container.repository.createFolder(name) }
                                                    .onSuccess { showToast("Đã tạo thư mục \"$name\"") }
                                                    .onFailure { showToast("Lỗi tạo thư mục: ${it.message}") }
                                            }
                                        },
                                    )

                                    MainTab.TOTP -> TotpScreen(
                                        items = items,
                                        onScanQrToAdd = {
                                            editingReturnItem = null
                                            overlay = OverlayRoute.QrScanner(returnToEditor = false)
                                        },
                                        onOpenItem = { item -> overlay = OverlayRoute.Detail(item.id) },
                                        onCopyCode = { title, code ->
                                            copyToClipboard("mã 2FA $title", code, true)
                                        },
                                    )

                                    MainTab.GENERATOR -> GeneratorScreen(
                                        onCopy = { pwd -> copyToClipboard("mật khẩu mới", pwd, true) },
                                    )

                                    MainTab.SETTINGS -> SettingsScreen(
                                        repository = container.repository,
                                        activity = activity,
                                        onShowMessage = ::showToast,
                                        onLock = { container.sessionManager.lock() },
                                    )
                                }

                                LiquidGlassFloatingDock(
                                    selectedTab = selectedTab,
                                    onSelect = { selectedTab = it },
                                    modifier = Modifier.align(Alignment.BottomCenter),
                                )
                            }
                        }
                    }
                }
            }
        }

        // Top Floating Liquid Glass Toast Notification
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
            LiquidGlassCard(
                cornerRadius = 18.dp,
                elevated = true,
                padding = PaddingValues(horizontal = 16.dp, vertical = 11.dp),
            ) {
                Text(
                    text = toastMessage.orEmpty(),
                    color = c.textPrimary,
                    fontSize = 13.5.sp,
                    fontWeight = FontWeight.SemiBold,
                )
            }
        }
    }
}
