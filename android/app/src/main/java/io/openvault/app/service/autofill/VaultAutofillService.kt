package io.openvault.app.service.autofill

import android.app.PendingIntent
import android.content.Intent
import android.os.Build
import android.os.CancellationSignal
import android.service.autofill.AutofillService
import android.service.autofill.Dataset
import android.service.autofill.FillCallback
import android.service.autofill.FillRequest
import android.service.autofill.FillResponse
import android.service.autofill.SaveCallback
import android.service.autofill.SaveInfo
import android.service.autofill.SaveRequest
import android.view.autofill.AutofillId
import io.openvault.app.OpenVaultApplication
import io.openvault.app.core.crypto.CipherType
import io.openvault.app.core.crypto.TotpEngine
import io.openvault.app.core.crypto.VaultItem
import io.openvault.app.core.crypto.VaultLogin
import io.openvault.app.core.crypto.VaultUri
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Android System AutofillService with:
 * - Android 11+ Inline Keyboard Suggestions (Gboard / Samsung Keyboard)
 * - Automatic Biometric / PIN unlock flow via AutofillAuthActivity when locked
 * - Smart Web Domain + Android Package matching + OTP auto-fill
 * - Automatic credential capture via onSaveRequest()
 */
class VaultAutofillService : AutofillService() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    @Suppress("DEPRECATION")
    override fun onFillRequest(
        request: FillRequest,
        cancellationSignal: CancellationSignal,
        callback: FillCallback,
    ) {
        val structure = request.fillContexts.lastOrNull()?.structure
        if (structure == null) {
            callback.onSuccess(null)
            return
        }

        val parsed = AutofillStructureParser.parse(structure)
        if (!parsed.hasAnyTarget) {
            callback.onSuccess(null)
            return
        }

        val app = application as? OpenVaultApplication
        val container = app?.container
        if (container == null || !container.preferences.account.value.isLoggedIn) {
            callback.onSuccess(null)
            return
        }

        val inlineSpecs = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            request.inlineSuggestionsRequest?.inlinePresentationSpecs.orEmpty()
        } else {
            emptyList()
        }

        serviceScope.launch {
            if (cancellationSignal.isCanceled) return@launch

            val responseBuilder = FillResponse.Builder()
            val saveIds = (parsed.usernameIds + parsed.passwordIds).distinct().toTypedArray()
            if (saveIds.isNotEmpty() && parsed.passwordIds.isNotEmpty()) {
                val saveType = SaveInfo.SAVE_DATA_TYPE_USERNAME or SaveInfo.SAVE_DATA_TYPE_PASSWORD
                responseBuilder.setSaveInfo(SaveInfo.Builder(saveType, saveIds).build())
            }

            if (!container.sessionManager.isUnlocked()) {
                val authIntent = AutofillAuthActivity.createIntent(
                    context = this@VaultAutofillService,
                    webDomain = parsed.webDomain,
                    packageName = parsed.packageName,
                    usernameIds = parsed.usernameIds,
                    passwordIds = parsed.passwordIds,
                    otpIds = parsed.otpIds,
                )
                val sender = PendingIntent.getActivity(
                    this@VaultAutofillService,
                    1001,
                    authIntent,
                    PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ).intentSender

                val presentation = AutofillDatasetBuilder.createDropdownPresentation(
                    context = this@VaultAutofillService,
                    title = "Mở khóa OpenVault",
                    subtitle = "Chạm để mở bằng vân tay / PIN",
                )
                val inlinePresentation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlineSpecs.isNotEmpty()) {
                    AutofillDatasetBuilder.createInlinePresentation(
                        context = this@VaultAutofillService,
                        spec = inlineSpecs[0],
                        title = "Mở khóa OpenVault",
                        subtitle = "Vân tay / PIN",
                        requestCode = 1002,
                    )
                } else {
                    null
                }

                val targetIds = parsed.allTargetIds.toTypedArray()
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && inlinePresentation != null) {
                    responseBuilder.setAuthentication(targetIds, sender, presentation, inlinePresentation)
                } else {
                    responseBuilder.setAuthentication(targetIds, sender, presentation)
                }

                callback.onSuccess(responseBuilder.build())
                return@launch
            }

            val matches = container.repository.findAutofillMatches(
                webDomain = parsed.webDomain,
                packageName = parsed.packageName,
            )

            var specIndex = 0
            for ((idx, item) in matches.take(5).withIndex()) {
                val spec = inlineSpecs.getOrNull(specIndex)
                val dataset = AutofillDatasetBuilder.buildCredentialDataset(
                    context = this@VaultAutofillService,
                    item = item,
                    usernameIds = parsed.usernameIds,
                    passwordIds = parsed.passwordIds,
                    otpIds = parsed.otpIds,
                    inlineSpec = spec,
                    requestCode = 2000 + idx,
                )
                if (dataset != null) {
                    responseBuilder.addDataset(dataset)
                    if (spec != null) specIndex++
                }
            }

            // Always add a "Choose from OpenVault" manual picker dataset
            val pickerIntent = AutofillAuthActivity.createIntent(
                context = this@VaultAutofillService,
                webDomain = parsed.webDomain,
                packageName = parsed.packageName,
                usernameIds = parsed.usernameIds,
                passwordIds = parsed.passwordIds,
                otpIds = parsed.otpIds,
            )
            val pickerSender = PendingIntent.getActivity(
                this@VaultAutofillService,
                2999,
                pickerIntent,
                PendingIntent.FLAG_MUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            ).intentSender

            val pickerDropdown = AutofillDatasetBuilder.createDropdownPresentation(
                context = this@VaultAutofillService,
                title = "Chọn từ OpenVault…",
                subtitle = if (matches.isEmpty()) "Tìm trong toàn bộ kho" else "Xem thêm tài khoản khác",
            )
            val pickerBuilder = Dataset.Builder(pickerDropdown)
            val pickerSpec = inlineSpecs.getOrNull(specIndex)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R && pickerSpec != null) {
                AutofillDatasetBuilder.createInlinePresentation(
                    context = this@VaultAutofillService,
                    spec = pickerSpec,
                    title = "OpenVault…",
                    subtitle = "Tìm kiếm",
                    requestCode = 2998,
                )?.let { pickerBuilder.setInlinePresentation(it) }
            }
            pickerBuilder.setAuthentication(pickerSender)
            for (id: AutofillId in parsed.allTargetIds) {
                pickerBuilder.setValue(id, null)
            }
            responseBuilder.addDataset(pickerBuilder.build())

            // Copy TOTP automatically if there's an exact single match with 2FA
            matches.firstOrNull()?.login?.totp?.takeIf { it.isNotBlank() }?.let { totp ->
                if (parsed.otpIds.isEmpty() && matches.size == 1) {
                    TotpEngine.generateFromInput(totp)?.code?.let { code ->
                        container.clipboard.copy("Mã 2FA OpenVault", code, isSensitive = true)
                    }
                }
            }

            callback.onSuccess(responseBuilder.build())
        }
    }

    override fun onSaveRequest(request: SaveRequest, callback: SaveCallback) {
        val structure = request.fillContexts.lastOrNull()?.structure
        val app = application as? OpenVaultApplication
        val container = app?.container

        if (structure == null || container == null || !container.sessionManager.isUnlocked()) {
            callback.onFailure("Vui lòng mở khóa OpenVault để lưu tài khoản mới.")
            return
        }

        val parsed = AutofillStructureParser.parse(structure)
        val username = parsed.capturedUsername.orEmpty()
        val password = parsed.capturedPassword.orEmpty()
        if (password.isBlank()) {
            callback.onSuccess()
            return
        }

        val uriString = when {
            !parsed.webDomain.isNullOrBlank() -> "https://${parsed.webDomain}"
            !parsed.packageName.isNullOrBlank() -> "androidapp://${parsed.packageName}"
            else -> ""
        }
        val title = parsed.webDomain
            ?.removePrefix("www.")
            ?.substringBefore('.')
            ?.replaceFirstChar { it.uppercase() }
            ?: parsed.packageName?.substringAfterLast('.')?.replaceFirstChar { it.uppercase() }
            ?: "Tài khoản mới"

        serviceScope.launch {
            runCatching {
                container.repository.saveItem(
                    VaultItem(
                        type = CipherType.LOGIN,
                        name = title,
                        login = VaultLogin(
                            uris = if (uriString.isNotBlank()) listOf(VaultUri(uriString)) else emptyList(),
                            username = username,
                            password = password,
                        ),
                    ),
                )
            }
                .onSuccess { callback.onSuccess() }
                .onFailure { callback.onFailure(it.message ?: "Không thể lưu vào OpenVault.") }
        }
    }
}
