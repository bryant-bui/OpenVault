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
import kotlinx.coroutines.launch

/**
 * Copies secrets with Android 13+ EXTRA_IS_SENSITIVE flag (hides passwords & 2FA codes
 * from keyboard clipboard overlays) and schedules timed auto-clear.
 */
class SensitiveClipboard(
    context: Context,
    private val preferences: AppPreferences,
) {
    private val clipboard =
        context.applicationContext.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var clearJob: Job? = null

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

        val clearSeconds = preferences.settings.value.clipboardClearSeconds
        clearJob?.cancel()
        if (isSensitive && clearSeconds > 0) {
            clearJob = scope.launch {
                delay(clearSeconds * 1000L)
                val current = clipboard.primaryClip?.getItemAt(0)?.coerceToText(null)?.toString()
                if (current == null || current == text) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                        clipboard.clearPrimaryClip()
                    } else {
                        clipboard.setPrimaryClip(ClipData.newPlainText("", ""))
                    }
                }
            }
        }
    }
}
