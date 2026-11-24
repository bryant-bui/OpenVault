package io.openvault.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.openvault.app.core.crypto.SymmetricKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class VaultLockState {
    LOGGED_OUT,
    LOCKED,
    UNLOCKED,
}

/**
 * Holds the decrypted 512-bit UserSymmetricKey strictly in volatile RAM.
 * Automatically locks when the app returns from background past the configured auto-lock timeout.
 */
class VaultSessionManager(
    private val preferences: AppPreferences,
) : DefaultLifecycleObserver {

    @Volatile
    private var userKey: SymmetricKey? = null

    @Volatile
    private var backgroundedAtMs: Long = 0L

    private val _lockState = MutableStateFlow(
        if (preferences.account.value.isLoggedIn) VaultLockState.LOCKED else VaultLockState.LOGGED_OUT,
    )
    val lockState: StateFlow<VaultLockState> = _lockState.asStateFlow()

    @Volatile
    var onVaultLocked: (() -> Unit)? = null

    fun registerProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun unlock(key: SymmetricKey) {
        userKey?.wipe()
        userKey = key.copy()
        backgroundedAtMs = 0L
        _lockState.value = VaultLockState.UNLOCKED
    }

    fun getActiveKey(): SymmetricKey? = userKey

    fun isUnlocked(): Boolean = userKey != null && _lockState.value == VaultLockState.UNLOCKED

    fun lock() {
        userKey?.wipe()
        userKey = null
        onVaultLocked?.invoke()
        _lockState.value =
            if (preferences.account.value.isLoggedIn) VaultLockState.LOCKED else VaultLockState.LOGGED_OUT
    }

    fun markLoggedOut() {
        userKey?.wipe()
        userKey = null
        onVaultLocked?.invoke()
        _lockState.value = VaultLockState.LOGGED_OUT
    }

    override fun onStop(owner: LifecycleOwner) {
        if (isUnlocked()) {
            backgroundedAtMs = System.currentTimeMillis()
        }
    }

    fun enforceAutoLockIfExpired(): Boolean {
        val bgAt = backgroundedAtMs
        if (!isUnlocked() || bgAt == 0L) return isUnlocked()
        val timeoutMinutes = preferences.settings.value.autoLockMinutes
        if (timeoutMinutes > 0) {
            val elapsedMs = System.currentTimeMillis() - bgAt
            if (elapsedMs >= timeoutMinutes * 60_000L) {
                backgroundedAtMs = 0L
                lock()
                return false
            }
        }
        return true
    }

    override fun onStart(owner: LifecycleOwner) {
        enforceAutoLockIfExpired()
        backgroundedAtMs = 0L
    }
}
