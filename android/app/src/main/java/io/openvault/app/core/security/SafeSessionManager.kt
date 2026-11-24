package io.openvault.app.core.security

import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner
import io.openvault.app.core.crypto.SafeCrypto
import io.openvault.app.core.crypto.SymmetricKey
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

enum class SafeLockState {
    NOT_INITIALIZED,
    LOCKED,
    UNLOCKED,
}

/**
 * Holds the decrypted 512-bit Vault+ SafeKey strictly in volatile RAM.
 * Enforces independent short auto-lock timeout and wipes SafeKey when locked or backgrounded.
 */
class SafeSessionManager : DefaultLifecycleObserver {

    @Volatile
    private var safeKey: SymmetricKey? = null

    @Volatile
    private var initialized: Boolean = false

    @Volatile
    private var autoLockMinutes: Int = SafeCrypto.SAFE_DEFAULT_AUTO_LOCK_MINUTES

    @Volatile
    private var lastActivityAtMs: Long = 0L

    @Volatile
    private var backgroundedAtMs: Long = 0L

    @Volatile
    private var lockOnExit: Boolean = false

    @Volatile
    private var suppressNextStopUntilMs: Long = 0L

    private val _lockState = MutableStateFlow(SafeLockState.NOT_INITIALIZED)
    val lockState: StateFlow<SafeLockState> = _lockState.asStateFlow()

    fun setLockOnExit(enabled: Boolean) {
        lockOnExit = enabled
    }

    fun suppressNextBackgroundLock(windowMs: Long = 90_000L) {
        suppressNextStopUntilMs = System.currentTimeMillis() + windowMs
    }

    private fun consumeStopSuppression(): Boolean {
        val until = suppressNextStopUntilMs
        suppressNextStopUntilMs = 0L
        return until > System.currentTimeMillis()
    }

    fun registerProcessLifecycle() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
    }

    fun setConfigured(isInitialized: Boolean, autoLockMins: Int) {
        initialized = isInitialized
        autoLockMinutes = autoLockMins.coerceIn(1, 15)
        if (!isInitialized) {
            wipeKey()
            _lockState.value = SafeLockState.NOT_INITIALIZED
        } else if (safeKey == null) {
            _lockState.value = SafeLockState.LOCKED
        }
    }

    fun setAutoLockMinutes(minutes: Int) {
        autoLockMinutes = minutes.coerceIn(1, 15)
    }

    fun getAutoLockMinutes(): Int = autoLockMinutes

    fun unlock(key: SymmetricKey, autoLockMins: Int = autoLockMinutes) {
        wipeKey()
        initialized = true
        autoLockMinutes = autoLockMins.coerceIn(1, 15)
        safeKey = key.copy()
        val now = System.currentTimeMillis()
        lastActivityAtMs = now
        backgroundedAtMs = 0L
        _lockState.value = SafeLockState.UNLOCKED
    }

    fun touchActivity() {
        if (isUnlocked()) {
            lastActivityAtMs = System.currentTimeMillis()
        }
    }

    fun getActiveKey(): SymmetricKey? {
        enforceAutoLockIfExpired()
        return safeKey
    }

    fun isUnlocked(): Boolean = safeKey != null && _lockState.value == SafeLockState.UNLOCKED

    fun remainingAutoLockSeconds(): Long {
        if (!isUnlocked()) return 0L
        val elapsedMs = (System.currentTimeMillis() - lastActivityAtMs).coerceAtLeast(0L)
        val totalMs = autoLockMinutes * 60_000L
        return ((totalMs - elapsedMs) / 1000L).coerceAtLeast(0L)
    }

    fun lock() {
        wipeKey()
        backgroundedAtMs = 0L
        lastActivityAtMs = 0L
        _lockState.value = if (initialized) SafeLockState.LOCKED else SafeLockState.NOT_INITIALIZED
    }

    fun markLoggedOut() {
        initialized = false
        wipeKey()
        backgroundedAtMs = 0L
        lastActivityAtMs = 0L
        _lockState.value = SafeLockState.NOT_INITIALIZED
    }

    fun enforceAutoLockIfExpired(): Boolean {
        if (safeKey == null) return false
        val now = System.currentTimeMillis()
        val timeoutMs = autoLockMinutes * 60_000L
        val bgAt = backgroundedAtMs
        val idleAt = lastActivityAtMs
        if ((bgAt > 0L && now - bgAt >= timeoutMs) || (idleAt > 0L && now - idleAt >= timeoutMs)) {
            lock()
            return false
        }
        return true
    }

    override fun onStop(owner: LifecycleOwner) {
        if (safeKey == null) return
        if (consumeStopSuppression()) return
        if (lockOnExit) {
            lock()
        } else {
            backgroundedAtMs = System.currentTimeMillis()
        }
    }

    override fun onStart(owner: LifecycleOwner) {
        enforceAutoLockIfExpired()
        backgroundedAtMs = 0L
    }

    override fun onResume(owner: LifecycleOwner) {
        suppressNextStopUntilMs = 0L
    }

    private fun wipeKey() {
        safeKey?.wipe()
        safeKey = null
    }
}
