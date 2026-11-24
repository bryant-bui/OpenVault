package io.openvault.app.core.security

import android.content.ClipData
import android.content.ClipDescription
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import android.os.PersistableBundle
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Copies secrets with Android 13+ EXTRA_IS_SENSITIVE flag (hides passwords & 2FA codes
 * from keyboard clipboard overlays), broadcasts live countdown warning notifications,
 * and schedules timed auto-clear.
 */
class SensitiveClipboard(
    context: Context,
    private val preferences: AppPreferences,
) {
    private val appContext = context.applicationContext
    private val clipboard =
        appContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

    private var activeLabel: String? = null
    private var activeSecretText: String? = null
    private var activeTotalSeconds: Int = 0
    private var activeExpiresAtMs: Long = 0L
    private var activeIsSensitive: Boolean = false

    fun copy(label: String, text: String, isSensitive: Boolean = true) {
        val clip = ClipData.newPlainText(label, text)
        if (isSensitive) {
            val extras = PersistableBundle().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    putBoolean(ClipDescription.EXTRA_IS_SENSITIVE, true)
                } else {
                    putBoolean("android.content.extra.IS_SENSITIVE", true)
                }
            }
            clip.description.extras = extras
        }
        clipboard.setPrimaryClip(clip)

        val settings = preferences.settings.value
        val clearSeconds = settings.clipboardClearSeconds
        val isVi = settings.language.isVietnameseActive()
        clearJob?.cancel()

        activeLabel = label
        activeSecretText = text
        activeTotalSeconds = clearSeconds
        activeIsSensitive = isSensitive

        if (isSensitive && clearSeconds > 0) {
            val expiresAtMs = System.currentTimeMillis() + clearSeconds * 1_000L
            activeExpiresAtMs = expiresAtMs
            if (settings.clipboardNotificationsEnabled) {
                ClipboardNotificationHelper.showCountdownNotification(
                    context = appContext,
                    label = label,
                    expiresAtMs = expiresAtMs,
                    totalSeconds = clearSeconds,
                    isVi = isVi,
                )
            }
            clearJob = scope.launch {
                delay(clearSeconds * 1_000L)
                if (!isActive) return@launch

                val cleared = clearClipboardIfMatches(text)
                resetActiveState()
                val currentSettings = preferences.settings.value
                if (cleared && currentSettings.clipboardNotificationsEnabled) {
                    ClipboardNotificationHelper.showClearedConfirmation(
                        context = appContext,
                        label = label,
                        isVi = currentSettings.language.isVietnameseActive(),
                    )
                } else {
                    ClipboardNotificationHelper.cancelNotification(appContext)
                }
            }
        } else {
            activeExpiresAtMs = 0L
            if (settings.clipboardNotificationsEnabled) {
                ClipboardNotificationHelper.showStaticCopiedNotification(
                    context = appContext,
                    label = label,
                    isSensitive = isSensitive,
                    isVi = isVi,
                )
            } else {
                ClipboardNotificationHelper.cancelNotification(appContext)
            }
        }
    }

    fun clearNow(force: Boolean = true) {
        clearJob?.cancel()
        clearJob = null
        val label = activeLabel
        val expectedText = activeSecretText
        if (force) {
            wipePrimaryClip()
        } else if (expectedText != null) {
            clearClipboardIfMatches(expectedText)
        }
        resetActiveState()

        val currentSettings = preferences.settings.value
        if (currentSettings.clipboardNotificationsEnabled) {
            ClipboardNotificationHelper.showClearedConfirmation(
                context = appContext,
                label = label,
                isVi = currentSettings.language.isVietnameseActive(),
            )
        } else {
            ClipboardNotificationHelper.cancelNotification(appContext)
        }
    }

    fun refreshActiveNotification() {
        val label = activeLabel ?: return
        val expiresAt = activeExpiresAtMs
        val total = activeTotalSeconds
        val settings = preferences.settings.value
        if (!settings.clipboardNotificationsEnabled) return
        val isVi = settings.language.isVietnameseActive()
        if (activeIsSensitive && total > 0) {
            if (expiresAt > System.currentTimeMillis()) {
                ClipboardNotificationHelper.showCountdownNotification(
                    context = appContext,
                    label = label,
                    expiresAtMs = expiresAt,
                    totalSeconds = total,
                    isVi = isVi,
                )
            }
        } else {
            ClipboardNotificationHelper.showStaticCopiedNotification(
                context = appContext,
                label = label,
                isSensitive = activeIsSensitive,
                isVi = isVi,
            )
        }
    }

    private fun clearClipboardIfMatches(expectedText: String): Boolean {
        val current = runCatching {
            clipboard.primaryClip?.getItemAt(0)?.coerceToText(appContext)?.toString()
        }.getOrNull()
        if (current == null || current == expectedText) {
            wipePrimaryClip()
            return true
        }
        return false
    }

    private fun wipePrimaryClip() {
        runCatching {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                clipboard.clearPrimaryClip()
            } else {
                clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
            }
        }
    }

    private fun resetActiveState() {
        activeLabel = null
        activeSecretText = null
        activeTotalSeconds = 0
        activeExpiresAtMs = 0L
        activeIsSensitive = false
    }
}
