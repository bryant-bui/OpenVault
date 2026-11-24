package io.openvault.app

import android.Manifest
import android.os.Build
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.SystemBarStyle
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.systemBars
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.security.AppThemeMode
import io.openvault.app.core.security.ClipboardNotificationHelper
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.MainTab
import io.openvault.app.ui.components.OnePassBottomBar
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassExtendedFab
import io.openvault.app.ui.components.OnePassNavigationRail
import io.openvault.app.ui.screens.AuthScreen
import io.openvault.app.ui.screens.CustomizeHomeScreen
import io.openvault.app.ui.screens.GeneratorScreen
import io.openvault.app.ui.screens.HomeScreen
import io.openvault.app.ui.screens.ItemDetailScreen
import io.openvault.app.ui.screens.ItemEditorScreen
import io.openvault.app.ui.screens.ItemsScreen
import io.openvault.app.ui.screens.NewItemSheet
import io.openvault.app.ui.screens.OnboardingScreen
import io.openvault.app.ui.screens.ProfileScreen
import io.openvault.app.ui.screens.QrScannerScreen
import io.openvault.app.ui.screens.SettingsScreen
import io.openvault.app.ui.screens.TotpScreen
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.screens.WatchtowerTabScreen
import io.openvault.app.ui.screens.safe.VaultPlusScreen
import io.openvault.app.ui.theme.LocalFoldableLayout
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.LocalStrings
import io.openvault.app.ui.theme.OpenVaultTheme
import io.openvault.app.ui.theme.ProvideFoldableLayout
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
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

            OpenVaultTheme(
                themeMode = settings.themeMode,
                language = settings.language,
                showWebsiteIcons = settings.showWebsiteIcons,
                accountAvatarBase64 = settings.accountAvatarBase64,
            ) {
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
    data object Profile : OverlayRoute
    data object CustomizeHome : OverlayRoute
    data object Settings : OverlayRoute
    data object Generator : OverlayRoute
    data object TotpHub : OverlayRoute
    data object WelcomeTour : OverlayRoute
    data class Detail(val itemId: String) : OverlayRoute
    data class Editor(
        val initialItem: VaultItem?,
        val initialType: CipherType = CipherType.LOGIN,
        val initialSecureNoteType: Int = 0,
    ) : OverlayRoute
    data class QrScanner(val returnToEditor: Boolean) : OverlayRoute
}

@Composable
private fun OpenVaultRootApp(
    container: AppContainer,
    activity: FragmentActivity,
) {
    val scope = rememberCoroutineScope()
    val lifecycleOwner = LocalLifecycleOwner.current
    val fold = LocalFoldableLayout.current
    val c = LocalOpenVaultColors.current
    val s = LocalStrings.current

    val settings by container.preferences.settings.collectAsState()
    val usageCounts by container.preferences.itemUsageCounts.collectAsState()
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
    var homeFabExpanded by remember { mutableStateOf(true) }
    var itemsFabExpanded by remember { mutableStateOf(true) }
    var askedNotificationPermThisSession by remember { mutableStateOf(false) }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) {
            container.clipboard.refreshActiveNotification()
        }
    }

    // First-launch 5-slide onboarding state when logged out
    var showingOnboarding by remember { mutableStateOf(!container.preferences.hasSeenOnboarding()) }
    var authInitialRegister by remember { mutableStateOf(false) }

    // Vault-Door overlay remains mounted across LOCKED -> UNLOCKED so the 90° rotating
    // keyhole & sliding top/bottom doors physically part to reveal the live vault underneath!
    var showVaultDoorOverlay by remember { mutableStateOf(lockState == VaultLockState.LOCKED) }
    if (lockState == VaultLockState.LOCKED && !showVaultDoorOverlay) {
        showVaultDoorOverlay = true
    } else if (lockState == VaultLockState.LOGGED_OUT && showVaultDoorOverlay) {
        showVaultDoorOverlay = false
    }

    LaunchedEffect(settings.flagSecureEnabled, settings.safeFlagSecureEnabled, selectedTab, lockState) {
        val enforceSecure = if (lockState == VaultLockState.UNLOCKED && selectedTab == MainTab.VAULT_PLUS) {
            settings.safeFlagSecureEnabled
        } else {
            settings.flagSecureEnabled
        }
        if (enforceSecure) {
            activity.window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
        } else {
            activity.window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
        }
    }

    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(2500L)
            toastMessage = null
        }
    }

    // Smart real-time foreground auto-sync (#16):
    // Checks lightweight GET /api/accounts/revision-date (1 D1 PK read) every 25s while unlocked
    // and only pulls full /api/sync if another device changed the vault!
    LaunchedEffect(lockState) {
        if (lockState == VaultLockState.UNLOCKED) {
            runCatching { container.repository.syncNow(force = false) }
            while (isActive) {
                delay(25_000L)
                runCatching { container.repository.syncNow(force = false) }
            }
        } else {
            overlay = OverlayRoute.None
        }
    }

    // Also run lightweight revision check immediately whenever the user returns to OpenVault
    DisposableEffect(lifecycleOwner, lockState) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME && lockState == VaultLockState.UNLOCKED) {
                scope.launch {
                    runCatching { container.repository.syncNow(force = false) }
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    fun showToast(msg: String) {
        toastMessage = msg
    }

    fun copyToClipboard(label: String, value: String, sensitive: Boolean) {
        container.clipboard.copy(label, value, isSensitive = sensitive)
        val notificationsEnabled = container.preferences.settings.value.clipboardNotificationsEnabled
        val hasNotifPerm = ClipboardNotificationHelper.hasNotificationPermission(activity)
        var requestedPermNow = false
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            notificationsEnabled &&
            !askedNotificationPermThisSession &&
            !hasNotifPerm
        ) {
            askedNotificationPermThisSession = true
            requestedPermNow = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (!(notificationsEnabled && hasNotifPerm) && !requestedPermNow) {
            val clearSec = container.preferences.settings.value.clipboardClearSeconds
            val msg = if (s.isVi) {
                val suffix = if (sensitive && clearSec > 0) " (tự xóa sau ${clearSec}s)" else ""
                "Đã sao chép $label$suffix"
            } else {
                val suffix = if (sensitive && clearSec > 0) " (clears in ${clearSec}s)" else ""
                "Copied $label$suffix"
            }
            showToast(msg)
        }
    }

    fun triggerSync() {
        scope.launch {
            runCatching { container.repository.syncNow(force = true) }
                .onSuccess { showToast(if (s.isVi) "Đã đồng bộ kho thành công!" else "Vault synced!") }
                .onFailure { showToast("${it.message}") }
        }
    }

    fun openItem(item: VaultItem) {
        container.preferences.recordItemUse(item.id)
        selectedItemId = item.id
        overlay = OverlayRoute.Detail(item.id)
    }

    fun openQrScanner(returnToEditor: Boolean) {
        if (!returnToEditor) editingReturnItem = null
        overlay = OverlayRoute.QrScanner(returnToEditor = returnToEditor)
    }

    // Tab-level edge-back: Watchtower or Items root navigates cleanly back to Home instead of exiting app
    BackHandler(enabled = lockState == VaultLockState.UNLOCKED && overlay == OverlayRoute.None && selectedTab != MainTab.HOME) {
        selectedTabName = MainTab.HOME.name
    }

    // Overlay back handling: always returns from Detail/Editor/Settings cleanly
    BackHandler(enabled = overlay != OverlayRoute.None) {
        overlay = when (val current = overlay) {
            is OverlayRoute.QrScanner -> if (current.returnToEditor) OverlayRoute.Editor(editingReturnItem) else OverlayRoute.None
            is OverlayRoute.Editor -> current.initialItem?.let { OverlayRoute.Detail(it.id) } ?: OverlayRoute.None
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
                    homeSections = settings.homeSections,
                    usageCounts = usageCounts,
                    isSyncing = isSyncing,
                    selectedItemId = if (overlay is OverlayRoute.Detail) (overlay as OverlayRoute.Detail).itemId else null,
                    onFabExpandedChange = { homeFabExpanded = it },
                    onOpenProfile = { overlay = OverlayRoute.Profile },
                    onOpenCustomizeHome = { overlay = OverlayRoute.CustomizeHome },
                    onOpenSettings = { overlay = OverlayRoute.Settings },
                    onOpenGenerator = { overlay = OverlayRoute.Generator },
                    onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                    onOpenNewItem = { overlay = OverlayRoute.NewItemPicker },
                    onSync = ::triggerSync,
                    onLock = { container.sessionManager.lock() },
                    onSelectItem = ::openItem,
                    onCopyTotp = { title, code -> copyToClipboard("2FA $title", code, true) },
                )

                MainTab.VAULT_PLUS -> VaultPlusScreen(
                    safeRepository = container.safeRepository,
                    activity = activity,
                    onCopySensitive = { label, value -> copyToClipboard(label, value, true) },
                    onShowMessage = ::showToast,
                )

                MainTab.ITEMS -> ItemsScreen(
                    email = account.email,
                    items = items,
                    folders = folders,
                    isSyncing = isSyncing,
                    isForeground = (overlay == OverlayRoute.None),
                    selectedItemId = if (overlay is OverlayRoute.Detail) (overlay as OverlayRoute.Detail).itemId else null,
                    onFabExpandedChange = { itemsFabExpanded = it },
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
                                .onSuccess { showToast(if (s.isVi) "Đã Tạo Thư Mục \"$name\"" else "Created Folder \"$name\"") }
                                .onFailure { showToast("${it.message}") }
                        }
                    },
                )

                MainTab.WATCHTOWER -> WatchtowerTabScreen(
                    email = account.email,
                    report = watchtower,
                    checkWeak = settings.checkWeakPasswords,
                    checkReused = settings.checkReusedPasswords,
                    checkTwoFactor = settings.checkTwoFactor,
                    isSyncing = isSyncing,
                    selectedItemId = if (overlay is OverlayRoute.Detail) (overlay as OverlayRoute.Detail).itemId else null,
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
                overlay = OverlayRoute.Editor(item, item.type, item.secureNoteType)
            },
            onToggleFavorite = {
                scope.launch {
                    runCatching { container.repository.toggleFavorite(item) }
                        .onFailure { showToast("${it.message}") }
                }
            },
            onSoftDelete = {
                scope.launch {
                    runCatching { container.repository.softDeleteItem(item.id) }
                        .onSuccess {
                            if (selectedItemId == item.id) selectedItemId = null
                            overlay = OverlayRoute.None
                            showToast(if (s.isVi) "Đã Chuyển \"${item.name}\" Vào Thùng Rác." else "Moved \"${item.name}\" To Trash.")
                        }
                        .onFailure { showToast("${it.message}") }
                }
            },
            onRestore = {
                scope.launch {
                    runCatching { container.repository.restoreItem(item.id) }
                        .onSuccess { showToast(if (s.isVi) "Đã Khôi Phục \"${item.name}\"." else "Restored \"${item.name}\".") }
                        .onFailure { showToast("${it.message}") }
                }
            },
            onHardDelete = {
                scope.launch {
                    runCatching { container.repository.hardDeleteItem(item.id) }
                        .onSuccess {
                            if (selectedItemId == item.id) selectedItemId = null
                            overlay = OverlayRoute.None
                            showToast(if (s.isVi) "Đã Xóa Vĩnh Viễn \"${item.name}\"." else "Permanently Deleted \"${item.name}\".")
                        }
                        .onFailure { showToast("${it.message}") }
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
            initialSecureNoteType = editor.initialSecureNoteType,
            email = account.email,
            prefillUsername = settings.prefillUsername,
            folders = folders,
            scannedTotpUri = pendingScannedTotp,
            onConsumeScannedTotp = { pendingScannedTotp = null },
            onRequestQrScan = {
                editingReturnItem = editor.initialItem
                overlay = OverlayRoute.QrScanner(returnToEditor = true)
            },
            onCancel = {
                overlay = editor.initialItem?.let { OverlayRoute.Detail(it.id) } ?: OverlayRoute.None
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
                            showToast(if (s.isVi) "Đã Mã Hóa & Lưu \"${saved.name}\"!" else "Encrypted & Saved \"${saved.name}\"!")
                        }
                        .onFailure { ex ->
                            isSavingItem = false
                            showToast("${ex.message}")
                        }
                }
            },
        )
    }

    @Composable
    fun RenderOverlayContent(currentOverlay: OverlayRoute) {
        when (currentOverlay) {
            OverlayRoute.None -> Unit

            OverlayRoute.NewItemPicker -> NewItemSheet(
                onCreateType = { type, noteType ->
                    editingReturnItem = null
                    overlay = OverlayRoute.Editor(initialItem = null, initialType = type, initialSecureNoteType = noteType)
                },
                onOpenGenerator = { overlay = OverlayRoute.Generator },
                onOpenQrScanner = { openQrScanner(returnToEditor = false) },
                onDismiss = { overlay = OverlayRoute.None },
            )

            OverlayRoute.Profile -> ProfileScreen(
                email = account.email,
                items = items,
                defaultIdentityId = settings.defaultIdentityId,
                defaultCardId = settings.defaultCardId,
                onSelectDefaultIdentity = { container.preferences.setDefaultIdentityId(it) },
                onSelectDefaultCard = { container.preferences.setDefaultCardId(it) },
                onUpdateAccountAvatar = { b64 ->
                    scope.launch { container.repository.saveAccountAvatar(b64) }
                },
                onCreateItemOfType = { cipherType ->
                    editingReturnItem = null
                    overlay = OverlayRoute.Editor(initialItem = null, initialType = cipherType)
                },
                onBack = { overlay = OverlayRoute.None },
                onShowMessage = ::showToast,
            )

            OverlayRoute.CustomizeHome -> CustomizeHomeScreen(
                sections = settings.homeSections,
                onUpdateSections = { container.preferences.setHomeSections(it) },
                onBack = { overlay = OverlayRoute.None },
            )

            OverlayRoute.Settings -> SettingsScreen(
                repository = container.repository,
                activity = activity,
                showCloseButton = true,
                onOpenWelcomeTour = { overlay = OverlayRoute.WelcomeTour },
                onBack = { overlay = OverlayRoute.None },
                onShowMessage = ::showToast,
                onLock = {
                    overlay = OverlayRoute.None
                    container.sessionManager.lock()
                },
            )

            OverlayRoute.Generator -> GeneratorScreen(
                onBack = { overlay = OverlayRoute.None },
                onCopy = { pwd -> copyToClipboard(s.password, pwd, true) },
            )

            OverlayRoute.TotpHub -> TotpScreen(
                items = items,
                onBack = { overlay = OverlayRoute.None },
                onScanQrToAdd = { openQrScanner(returnToEditor = false) },
                onOpenItem = ::openItem,
                onCopyCode = { title, code -> copyToClipboard("2FA $title", code, true) },
            )

            OverlayRoute.WelcomeTour -> OnboardingScreen(
                previewOnly = true,
                onClosePreview = { overlay = OverlayRoute.None },
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

            is OverlayRoute.QrScanner -> Unit
        }
    }

    Box(modifier = Modifier.fillMaxSize()) {
        // ── 1. LOGGED OUT FLOW (5-Slide Onboarding -> Auth Screen) ──────────
        if (lockState == VaultLockState.LOGGED_OUT) {
            if (showingOnboarding) {
                OnboardingScreen(
                    previewOnly = false,
                    onCreateAccount = {
                        container.preferences.setOnboardingSeen(true)
                        authInitialRegister = true
                        showingOnboarding = false
                    },
                    onSignIn = {
                        container.preferences.setOnboardingSeen(true)
                        authInitialRegister = false
                        showingOnboarding = false
                    },
                )
            } else {
                AuthScreen(
                    repository = container.repository,
                    initialRegister = authInitialRegister,
                    onBackToOnboarding = { showingOnboarding = true },
                    onAuthenticated = {
                        container.preferences.setOnboardingSeen(true)
                        showToast(if (s.isVi) "Đăng nhập thành công & đã đồng bộ kho!" else "Signed in & vault synced!")
                    },
                )
            }
        }

        // ── 2. UNLOCKED VAULT LAYER (renders underneath vault door during split reveal!) ──
        if (lockState == VaultLockState.UNLOCKED) {
            val currentOverlay = overlay
            if (currentOverlay is OverlayRoute.QrScanner) {
                QrScannerScreen(
                    onResult = { scanned ->
                        pendingScannedTotp = scanned
                        overlay = if (currentOverlay.returnToEditor) {
                            OverlayRoute.Editor(editingReturnItem)
                        } else {
                            editingReturnItem = null
                            OverlayRoute.Editor(null, CipherType.LOGIN)
                        }
                        showToast(if (s.isVi) "Đã quét mã QR 2FA thành công!" else "2FA QR code scanned!")
                    },
                    onCancel = {
                        overlay = if (currentOverlay.returnToEditor) {
                            OverlayRoute.Editor(editingReturnItem)
                        } else {
                            OverlayRoute.None
                        }
                    },
                )
            } else if (fold.isTwoPane) {
                // ── UNFOLDED GALAXY Z FOLD 3 / TABLET (#18) ─────────────────
                // Spacious full-canvas Home / Items / Watchtower + sleek 72dp Navigation Rail,
                // with Detail / Editor / Settings / Profile opening as a floating elevated modal sheet!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(c.background),
                ) {
                    Row(modifier = Modifier.fillMaxSize()) {
                        OnePassNavigationRail(
                            selectedTab = selectedTab,
                            onSelect = { tab ->
                                selectedTabName = tab.name
                                if (overlay != OverlayRoute.None) overlay = OverlayRoute.None
                            },
                            onNewItem = { overlay = OverlayRoute.NewItemPicker },
                        )

                        ActiveTabContent(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight(),
                        )
                    }

                    // Floating Elevated Overlay Sheet above the spacious canvas
                    var cachedFloatingOverlay by remember { mutableStateOf<OverlayRoute>(OverlayRoute.None) }
                    if (currentOverlay != OverlayRoute.None && currentOverlay !is OverlayRoute.QrScanner) {
                        cachedFloatingOverlay = currentOverlay
                    }
                    val allowScrimDismiss = currentOverlay !is OverlayRoute.Editor
                    AnimatedVisibility(
                        visible = currentOverlay != OverlayRoute.None,
                        enter = fadeIn() + scaleIn(initialScale = 0.95f),
                        exit = fadeOut() + scaleOut(targetScale = 0.95f),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color.Black.copy(alpha = 0.62f))
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) {
                                    if (allowScrimDismiss) overlay = OverlayRoute.None
                                }
                                .statusBarsPadding()
                                .navigationBarsPadding()
                                .padding(horizontal = 24.dp, vertical = 18.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            val sheetShape = RoundedCornerShape(24.dp)
                            Surface(
                                modifier = Modifier
                                    .widthIn(max = 560.dp)
                                    .fillMaxWidth(0.90f)
                                    .fillMaxHeight(0.92f)
                                    .clip(sheetShape)
                                    .clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { /* consume clicks inside floating card */ }
                                    .consumeWindowInsets(WindowInsets.systemBars),
                                shape = sheetShape,
                                color = c.sheetBackground,
                                border = BorderStroke(1.dp, c.cardBorder),
                                shadowElevation = 28.dp,
                                tonalElevation = 0.dp,
                            ) {
                                RenderOverlayContent(
                                    if (currentOverlay != OverlayRoute.None) currentOverlay else cachedFloatingOverlay,
                                )
                            }
                        }
                    }
                }
            } else {
                // ── FOLDED Z FOLD 3 COVER SCREEN (320dp) / STANDARD PHONE ──
                // Keep ActiveTabContent mounted beneath overlays so ItemsScreen scope & scroll state survive Detail/Editor/Settings!
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(c.background),
                ) {
                    ActiveTabContent(modifier = Modifier.fillMaxSize())

                    if (selectedTab != MainTab.WATCHTOWER && selectedTab != MainTab.VAULT_PLUS) {
                        val fabExpanded = when (selectedTab) {
                            MainTab.HOME -> homeFabExpanded
                            MainTab.ITEMS -> itemsFabExpanded
                            MainTab.VAULT_PLUS, MainTab.WATCHTOWER -> true
                        }
                        OnePassExtendedFab(
                            text = s.newItem,
                            expanded = fabExpanded,
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

                    if (currentOverlay != OverlayRoute.None) {
                        Surface(
                            modifier = Modifier
                                .fillMaxSize()
                                .clickable(
                                    interactionSource = remember { MutableInteractionSource() },
                                    indication = null,
                                ) { /* block fallthrough touches under overlay */ },
                            color = c.sheetBackground,
                        ) {
                            RenderOverlayContent(currentOverlay)
                        }
                    }
                }
            }
        }

        // ── 3. VAULT-DOOR UNLOCK OVERLAY (#13) ──────────────────────────────
        if (showVaultDoorOverlay && lockState != VaultLockState.LOGGED_OUT) {
            UnlockScreen(
                repository = container.repository,
                activity = activity,
                autoTriggerBiometric = true,
                allowLogout = true,
                animateDoorReveal = true,
                isUnlockedReveal = lockState == VaultLockState.UNLOCKED,
                onDoorAnimationFinished = { showVaultDoorOverlay = false },
                onUnlocked = { },
            )
        }

        // ── 4. TOP TOAST BANNER ─────────────────────────────────────────────
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
