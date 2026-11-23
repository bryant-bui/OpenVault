package io.openvault.app.service.autofill

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.WindowManager
import android.view.autofill.AutofillId
import android.view.autofill.AutofillManager
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.IntentCompat
import androidx.fragment.app.FragmentActivity
import io.openvault.app.OpenVaultApplication
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.DomainMatcher
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.security.VaultLockState
import io.openvault.app.ui.components.OnePassCard
import io.openvault.app.ui.components.OnePassItemRow
import io.openvault.app.ui.components.OnePassSearchBar
import io.openvault.app.ui.screens.UnlockScreen
import io.openvault.app.ui.theme.LocalOpenVaultColors
import io.openvault.app.ui.theme.OpenVaultTheme

class AutofillAuthActivity : FragmentActivity() {

    companion object {
        private const val EXTRA_WEB_DOMAIN = "ov_web_domain"
        private const val EXTRA_PACKAGE_NAME = "ov_package_name"
        private const val EXTRA_USERNAME_IDS = "ov_username_ids"
        private const val EXTRA_PASSWORD_IDS = "ov_password_ids"
        private const val EXTRA_OTP_IDS = "ov_otp_ids"

        fun createIntent(
            context: Context,
            webDomain: String?,
            packageName: String?,
            usernameIds: List<AutofillId>,
            passwordIds: List<AutofillId>,
            otpIds: List<AutofillId>,
        ): Intent = Intent(context, AutofillAuthActivity::class.java).apply {
            putExtra(EXTRA_WEB_DOMAIN, webDomain)
            putExtra(EXTRA_PACKAGE_NAME, packageName)
            putParcelableArrayListExtra(EXTRA_USERNAME_IDS, ArrayList(usernameIds))
            putParcelableArrayListExtra(EXTRA_PASSWORD_IDS, ArrayList(passwordIds))
            putParcelableArrayListExtra(EXTRA_OTP_IDS, ArrayList(otpIds))
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        val container = (application as OpenVaultApplication).container
        if (container.preferences.settings.value.flagSecureEnabled) {
            window.setFlags(
                WindowManager.LayoutParams.FLAG_SECURE,
                WindowManager.LayoutParams.FLAG_SECURE,
            )
        }

        val webDomain = intent.getStringExtra(EXTRA_WEB_DOMAIN)
        val targetPackage = intent.getStringExtra(EXTRA_PACKAGE_NAME)
        val usernameIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_USERNAME_IDS,
            AutofillId::class.java,
        ).orEmpty()
        val passwordIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_PASSWORD_IDS,
            AutofillId::class.java,
        ).orEmpty()
        val otpIds = IntentCompat.getParcelableArrayListExtra(
            intent,
            EXTRA_OTP_IDS,
            AutofillId::class.java,
        ).orEmpty()

        fun completeWithItem(item: VaultItem) {
            val dataset = AutofillDatasetBuilder.buildCredentialDataset(
                context = this,
                item = item,
                usernameIds = usernameIds,
                passwordIds = passwordIds,
                otpIds = otpIds,
            )
            if (dataset == null) {
                setResult(Activity.RESULT_CANCELED)
                finish()
                return
            }
            item.login?.totp?.takeIf { it.isNotBlank() && otpIds.isEmpty() }?.let { totp ->
                TotpEngine.generateFromInput(totp)?.code?.let { code ->
                    container.clipboard.copy("Mã 2FA OpenVault", code, isSensitive = true)
                }
            }
            val reply = Intent().apply {
                putExtra(AutofillManager.EXTRA_AUTHENTICATION_RESULT, dataset)
            }
            setResult(Activity.RESULT_OK, reply)
            finish()
        }

        setContent {
            val settings by container.preferences.settings.collectAsState()
            val lockState by container.sessionManager.lockState.collectAsState()
            val allItems by container.repository.items.collectAsState()

            OpenVaultTheme(themeMode = settings.themeMode) {
                if (lockState != VaultLockState.UNLOCKED) {
                    UnlockScreen(
                        repository = container.repository,
                        activity = this@AutofillAuthActivity,
                        autoTriggerBiometric = true,
                        allowLogout = false,
                        onUnlocked = { },
                    )
                } else {
                    AutofillPickerContent(
                        allItems = allItems,
                        webDomain = webDomain,
                        targetPackage = targetPackage,
                        onSelect = ::completeWithItem,
                        onCancel = {
                            setResult(Activity.RESULT_CANCELED)
                            finish()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun AutofillPickerContent(
    allItems: List<VaultItem>,
    webDomain: String?,
    targetPackage: String?,
    onSelect: (VaultItem) -> Unit,
    onCancel: () -> Unit,
) {
    val c = LocalOpenVaultColors.current
    var query by remember { mutableStateOf("") }

    val sortedLogins = remember(allItems, webDomain, targetPackage, query) {
        val q = query.trim().lowercase()
        allItems
            .filter { !it.isDeleted && it.type == CipherType.LOGIN && it.login != null }
            .filter { item ->
                if (q.isEmpty()) true
                else item.name.lowercase().contains(q) ||
                    item.login?.username?.lowercase()?.contains(q) == true ||
                    item.login?.uris?.any { it.uri.lowercase().contains(q) } == true
            }
            .sortedByDescending { item ->
                val matchScore = item.login?.uris.orEmpty().maxOfOrNull { u ->
                    DomainMatcher.scoreMatch(u.uri, webDomain, targetPackage)
                } ?: 0
                matchScore * 10 + if (item.favorite) 5 else 0
            }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(c.background)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Chọn tài khoản để điền",
                    color = c.textPrimary,
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold,
                )
                Text(
                    text = webDomain ?: targetPackage ?: "OpenVault Autofill",
                    color = c.accent,
                    fontSize = 13.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onCancel) {
                Icon(Icons.Rounded.Close, contentDescription = "Đóng", tint = c.textSecondary)
            }
        }

        OnePassSearchBar(
            query = query,
            onQueryChange = { query = it },
            placeholder = "Tìm tài khoản trong kho",
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            item {
                OnePassCard(modifier = Modifier.fillMaxWidth(), padding = PaddingValues(0.dp)) {
                    Column {
                        sortedLogins.forEachIndexed { idx, item ->
                            if (idx > 0) {
                                HorizontalDivider(
                                    color = c.divider,
                                    thickness = 0.7.dp,
                                    modifier = Modifier.padding(start = 62.dp),
                                )
                            }
                            OnePassItemRow(item = item, onClick = { onSelect(item) })
                        }
                    }
                }
            }
        }
    }
}
