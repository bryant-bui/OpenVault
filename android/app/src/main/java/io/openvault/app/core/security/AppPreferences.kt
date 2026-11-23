package io.openvault.app.core.security

import android.content.Context
import android.content.SharedPreferences
import io.openvault.app.core.crypto.KdfConfig
import io.openvault.app.core.crypto.PinCrypto
import io.openvault.app.core.crypto.PinProtectedRecord
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.util.UUID

enum class AppThemeMode(val labelEn: String, val labelVi: String) {
    SYSTEM("System default", "Hệ thống"),
    DARK("Dark (OLED)", "Tối (OLED)"),
    LIGHT("Light", "Sáng"),
}

enum class AppLanguage(val labelNative: String) {
    ENGLISH("English"),
    VIETNAMESE("Tiếng Việt"),
}

enum class HomeSectionType {
    PINNED_TOTP,
    PROFILE,
    FAVORITES,
    RECENT,
    FREQUENT,
}

@Serializable
data class HomeSectionPref(
    val id: String,
    val visible: Boolean = true,
) {
    fun toTypeOrNull(): HomeSectionType? = runCatching { HomeSectionType.valueOf(id) }.getOrNull()
}

data class SecuritySettings(
    val autoLockMinutes: Int = 5,
    val clipboardClearSeconds: Int = 30,
    val flagSecureEnabled: Boolean = true,
    val themeMode: AppThemeMode = AppThemeMode.DARK,
    val language: AppLanguage = AppLanguage.ENGLISH,
    val biometricEnabled: Boolean = false,
    val pinEnabled: Boolean = false,
    val pinLength: Int = 6,
    val pinMaxAttempts: Int = PinCrypto.DEFAULT_PIN_MAX_ATTEMPTS,
    val pinFailedAttempts: Int = 0,
    val showWebsiteIcons: Boolean = true,
    val prefillUsername: Boolean = true,
    val checkWeakPasswords: Boolean = true,
    val checkReusedPasswords: Boolean = true,
    val checkTwoFactor: Boolean = true,
    val defaultIdentityId: String? = null,
    val defaultCardId: String? = null,
    val accountAvatarBase64: String? = null,
    val homeSections: List<HomeSectionPref> = DEFAULT_HOME_SECTIONS,
) {
    companion object {
        val DEFAULT_HOME_SECTIONS = listOf(
            HomeSectionPref(HomeSectionType.PINNED_TOTP.name, true),
            HomeSectionPref(HomeSectionType.PROFILE.name, true),
            HomeSectionPref(HomeSectionType.FAVORITES.name, true),
            HomeSectionPref(HomeSectionType.RECENT.name, true),
            HomeSectionPref(HomeSectionType.FREQUENT.name, true),
        )
    }
}

data class AccountMeta(
    val serverUrl: String = AppPreferences.DEFAULT_SERVER_URL,
    val email: String = "",
    val kdfConfig: KdfConfig = KdfConfig(),
    val protectedSymmetricKey: String = "",
    val lastSyncMs: Long = 0L,
    val lastRevisionMs: Long = 0L,
) {
    val isLoggedIn: Boolean get() = email.isNotBlank() && protectedSymmetricKey.isNotBlank()
}

class AppPreferences(context: Context) {

    companion object {
        const val DEFAULT_SERVER_URL = "https://your-openvault-api.workers.dev"

        private const val PREF_NAME = "openvault_prefs_v1"
        private const val KEY_SERVER_URL = "server_url"
        private const val KEY_EMAIL = "user_email"
        private const val KEY_KDF_TYPE = "kdf_type"
        private const val KEY_KDF_ITER = "kdf_iter"
        private const val KEY_KDF_MEM = "kdf_mem"
        private const val KEY_KDF_PAR = "kdf_par"
        private const val KEY_PROTECTED_KEY = "protected_sym_key"
        private const val KEY_ACCESS_TOKEN = "sealed_access_token"
        private const val KEY_REFRESH_TOKEN = "sealed_refresh_token"
        private const val KEY_DEVICE_ID = "device_id"
        private const val KEY_LAST_SYNC = "last_sync_ms"
        private const val KEY_LAST_REV = "last_rev_ms"
        private const val KEY_PIN_RECORD = "pin_record_json"
        private const val KEY_PIN_MAX_ATTEMPTS = "pin_max_attempts"
        private const val KEY_PIN_FAILED = "pin_failed_attempts"
        private const val KEY_BIO_BLOB = "bio_blob_json"
        private const val KEY_AUTO_LOCK_MIN = "auto_lock_minutes"
        private const val KEY_CLIPBOARD_SEC = "clipboard_clear_seconds"
        private const val KEY_FLAG_SECURE = "flag_secure_enabled"
        private const val KEY_THEME_MODE = "theme_mode"
        private const val KEY_LANGUAGE = "app_language"
        private const val KEY_SHOW_WEBSITE_ICONS = "show_website_icons"
        private const val KEY_PREFILL_USERNAME = "prefill_username"
        private const val KEY_WT_WEAK = "wt_check_weak"
        private const val KEY_WT_REUSED = "wt_check_reused"
        private const val KEY_WT_2FA = "wt_check_2fa"
        private const val KEY_DEFAULT_IDENTITY_ID = "default_identity_id"
        private const val KEY_DEFAULT_CARD_ID = "default_card_id"
        private const val KEY_ACCOUNT_AVATAR_B64 = "account_avatar_b64"
        private const val KEY_HOME_SECTIONS = "home_sections_json"
        private const val KEY_ITEM_USAGE_COUNTS = "item_usage_counts_json"
        private const val KEY_ONBOARDING_SEEN = "onboarding_seen"
    }

    private val prefs: SharedPreferences =
        context.applicationContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)

    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    private val _settings = MutableStateFlow(readSettings())
    val settings: StateFlow<SecuritySettings> = _settings.asStateFlow()

    private val _account = MutableStateFlow(readAccountMeta())
    val account: StateFlow<AccountMeta> = _account.asStateFlow()

    private val _itemUsageCounts = MutableStateFlow(readUsageCounts())
    val itemUsageCounts: StateFlow<Map<String, Int>> = _itemUsageCounts.asStateFlow()

    fun getOrCreateDeviceId(): String {
        val existing = prefs.getString(KEY_DEVICE_ID, null)
        if (!existing.isNullOrBlank()) return existing
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_DEVICE_ID, created).apply()
        return created
    }

    fun getServerUrl(): String =
        prefs.getString(KEY_SERVER_URL, DEFAULT_SERVER_URL)?.trim()?.removeSuffix("/")
            ?.takeIf { it.isNotBlank() } ?: DEFAULT_SERVER_URL

    fun setServerUrl(url: String) {
        val clean = url.trim().removeSuffix("/").ifBlank { DEFAULT_SERVER_URL }
        prefs.edit().putString(KEY_SERVER_URL, clean).apply()
        _account.value = readAccountMeta()
    }

    fun saveAccountSession(
        serverUrl: String,
        email: String,
        kdfConfig: KdfConfig,
        protectedSymmetricKey: String,
        accessToken: String,
        refreshToken: String?,
    ) {
        val cleanUrl = serverUrl.trim().removeSuffix("/").ifBlank { DEFAULT_SERVER_URL }
        val sealedAccess = runCatching { BiometricKeyManager.sealStringForDisk(accessToken) }
            .getOrDefault(accessToken)
        val sealedRefresh = refreshToken?.let {
            runCatching { BiometricKeyManager.sealStringForDisk(it) }.getOrDefault(it)
        }
        prefs.edit()
            .putString(KEY_SERVER_URL, cleanUrl)
            .putString(KEY_EMAIL, email.trim().lowercase())
            .putInt(KEY_KDF_TYPE, kdfConfig.kdfType)
            .putInt(KEY_KDF_ITER, kdfConfig.iterations)
            .putInt(KEY_KDF_MEM, kdfConfig.memoryMiB ?: 64)
            .putInt(KEY_KDF_PAR, kdfConfig.parallelism ?: 4)
            .putString(KEY_PROTECTED_KEY, protectedSymmetricKey)
            .putString(KEY_ACCESS_TOKEN, sealedAccess)
            .apply {
                if (sealedRefresh != null) putString(KEY_REFRESH_TOKEN, sealedRefresh)
            }
            .apply()
        _account.value = readAccountMeta()
    }

    fun updateTokens(accessToken: String, refreshToken: String?) {
        val sealedAccess = runCatching { BiometricKeyManager.sealStringForDisk(accessToken) }
            .getOrDefault(accessToken)
        val editor = prefs.edit().putString(KEY_ACCESS_TOKEN, sealedAccess)
        if (!refreshToken.isNullOrBlank()) {
            val sealedRefresh = runCatching { BiometricKeyManager.sealStringForDisk(refreshToken) }
                .getOrDefault(refreshToken)
            editor.putString(KEY_REFRESH_TOKEN, sealedRefresh)
        }
        editor.apply()
    }

    fun getAccessToken(): String? =
        BiometricKeyManager.unsealStringFromDisk(prefs.getString(KEY_ACCESS_TOKEN, null))

    fun getRefreshToken(): String? =
        BiometricKeyManager.unsealStringFromDisk(prefs.getString(KEY_REFRESH_TOKEN, null))

    fun updateSyncTimestamps(lastSyncMs: Long, lastRevisionMs: Long? = null) {
        val ed = prefs.edit().putLong(KEY_LAST_SYNC, lastSyncMs)
        if (lastRevisionMs != null) ed.putLong(KEY_LAST_REV, lastRevisionMs)
        ed.apply()
        _account.value = readAccountMeta()
    }

    fun savePinRecord(record: PinProtectedRecord) {
        prefs.edit()
            .putString(KEY_PIN_RECORD, json.encodeToString(record))
            .putInt(KEY_PIN_FAILED, 0)
            .apply()
        _settings.value = readSettings()
    }

    fun getPinRecord(): PinProtectedRecord? {
        val raw = prefs.getString(KEY_PIN_RECORD, null) ?: return null
        return runCatching { json.decodeFromString<PinProtectedRecord>(raw) }.getOrNull()
    }

    fun clearPinRecord() {
        prefs.edit()
            .remove(KEY_PIN_RECORD)
            .putInt(KEY_PIN_FAILED, 0)
            .apply()
        _settings.value = readSettings()
    }

    fun setPinFailedAttempts(count: Int) {
        prefs.edit().putInt(KEY_PIN_FAILED, count.coerceAtLeast(0)).apply()
        _settings.value = readSettings()
    }

    fun setPinMaxAttempts(maxAttempts: Int) {
        prefs.edit().putInt(KEY_PIN_MAX_ATTEMPTS, maxAttempts.coerceIn(3, 10)).apply()
        _settings.value = readSettings()
    }

    fun saveBiometricBlob(blob: BiometricWrappedBlob) {
        prefs.edit().putString(KEY_BIO_BLOB, json.encodeToString(blob)).apply()
        _settings.value = readSettings()
    }

    fun getBiometricBlob(): BiometricWrappedBlob? {
        val raw = prefs.getString(KEY_BIO_BLOB, null) ?: return null
        return runCatching { json.decodeFromString<BiometricWrappedBlob>(raw) }.getOrNull()
    }

    fun clearBiometricBlob() {
        prefs.edit().remove(KEY_BIO_BLOB).apply()
        BiometricKeyManager.deleteBiometricKey()
        _settings.value = readSettings()
    }

    fun setAutoLockMinutes(minutes: Int) {
        prefs.edit().putInt(KEY_AUTO_LOCK_MIN, minutes).apply()
        _settings.value = readSettings()
    }

    fun setClipboardClearSeconds(seconds: Int) {
        prefs.edit().putInt(KEY_CLIPBOARD_SEC, seconds).apply()
        _settings.value = readSettings()
    }

    fun setFlagSecureEnabled(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_FLAG_SECURE, enabled).apply()
        _settings.value = readSettings()
    }

    fun setThemeMode(mode: AppThemeMode) {
        prefs.edit().putString(KEY_THEME_MODE, mode.name).apply()
        _settings.value = readSettings()
    }

    fun setLanguage(language: AppLanguage) {
        prefs.edit().putString(KEY_LANGUAGE, language.name).apply()
        _settings.value = readSettings()
    }

    fun setShowWebsiteIcons(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_SHOW_WEBSITE_ICONS, enabled).apply()
        _settings.value = readSettings()
    }

    fun setPrefillUsername(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_PREFILL_USERNAME, enabled).apply()
        _settings.value = readSettings()
    }

    fun setWatchtowerChecks(weak: Boolean? = null, reused: Boolean? = null, twoFactor: Boolean? = null) {
        prefs.edit().apply {
            if (weak != null) putBoolean(KEY_WT_WEAK, weak)
            if (reused != null) putBoolean(KEY_WT_REUSED, reused)
            if (twoFactor != null) putBoolean(KEY_WT_2FA, twoFactor)
        }.apply()
        _settings.value = readSettings()
    }

    fun setDefaultIdentityId(id: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_DEFAULT_IDENTITY_ID) else putString(KEY_DEFAULT_IDENTITY_ID, id)
        }.apply()
        _settings.value = readSettings()
    }

    fun setDefaultCardId(id: String?) {
        prefs.edit().apply {
            if (id.isNullOrBlank()) remove(KEY_DEFAULT_CARD_ID) else putString(KEY_DEFAULT_CARD_ID, id)
        }.apply()
        _settings.value = readSettings()
    }

    fun setAccountAvatarBase64(b64: String?) {
        prefs.edit().apply {
            if (b64.isNullOrBlank()) remove(KEY_ACCOUNT_AVATAR_B64) else putString(KEY_ACCOUNT_AVATAR_B64, b64)
        }.apply()
        _settings.value = readSettings()
    }

    fun setHomeSections(sections: List<HomeSectionPref>) {
        prefs.edit().putString(KEY_HOME_SECTIONS, json.encodeToString(sections)).apply()
        _settings.value = readSettings()
    }

    fun recordItemUse(itemId: String) {
        if (itemId.isBlank()) return
        val next = _itemUsageCounts.value.toMutableMap()
        next[itemId] = (next[itemId] ?: 0) + 1
        // Keep top 120 tracked items so prefs stay tiny
        val pruned = if (next.size > 120) {
            next.entries.sortedByDescending { it.value }.take(100).associate { it.key to it.value }
        } else {
            next
        }
        prefs.edit().putString(KEY_ITEM_USAGE_COUNTS, json.encodeToString(pruned)).apply()
        _itemUsageCounts.value = pruned
    }

    fun hasSeenOnboarding(): Boolean = prefs.getBoolean(KEY_ONBOARDING_SEEN, false)

    fun setOnboardingSeen(seen: Boolean) {
        prefs.edit().putBoolean(KEY_ONBOARDING_SEEN, seen).apply()
    }

    fun clearSessionForLogout() {
        val savedServer = getServerUrl()
        val savedTheme = _settings.value.themeMode
        val savedLang = _settings.value.language
        BiometricKeyManager.deleteBiometricKey()
        prefs.edit()
            .remove(KEY_EMAIL)
            .remove(KEY_PROTECTED_KEY)
            .remove(KEY_ACCESS_TOKEN)
            .remove(KEY_REFRESH_TOKEN)
            .remove(KEY_LAST_SYNC)
            .remove(KEY_LAST_REV)
            .remove(KEY_PIN_RECORD)
            .remove(KEY_PIN_FAILED)
            .remove(KEY_BIO_BLOB)
            .remove(KEY_DEFAULT_IDENTITY_ID)
            .remove(KEY_DEFAULT_CARD_ID)
            .remove(KEY_ACCOUNT_AVATAR_B64)
            .remove(KEY_ITEM_USAGE_COUNTS)
            .putString(KEY_SERVER_URL, savedServer)
            .putString(KEY_THEME_MODE, savedTheme.name)
            .putString(KEY_LANGUAGE, savedLang.name)
            .apply()
        _account.value = readAccountMeta()
        _settings.value = readSettings()
        _itemUsageCounts.value = emptyMap()
    }

    private fun readAccountMeta(): AccountMeta = AccountMeta(
        serverUrl = getServerUrl(),
        email = prefs.getString(KEY_EMAIL, "").orEmpty(),
        kdfConfig = KdfConfig(
            kdfType = prefs.getInt(KEY_KDF_TYPE, KdfConfig.KDF_ARGON2ID),
            iterations = prefs.getInt(KEY_KDF_ITER, KdfConfig.DEFAULT_ARGON2_ITERATIONS),
            memoryMiB = prefs.getInt(KEY_KDF_MEM, KdfConfig.DEFAULT_ARGON2_MEMORY_MIB),
            parallelism = prefs.getInt(KEY_KDF_PAR, KdfConfig.DEFAULT_ARGON2_PARALLELISM),
        ),
        protectedSymmetricKey = prefs.getString(KEY_PROTECTED_KEY, "").orEmpty(),
        lastSyncMs = prefs.getLong(KEY_LAST_SYNC, 0L),
        lastRevisionMs = prefs.getLong(KEY_LAST_REV, 0L),
    )

    private fun readHomeSections(): List<HomeSectionPref> {
        val raw = prefs.getString(KEY_HOME_SECTIONS, null)
        val parsed = raw?.let { runCatching { json.decodeFromString<List<HomeSectionPref>>(it) }.getOrNull() }
        if (parsed.isNullOrEmpty()) return SecuritySettings.DEFAULT_HOME_SECTIONS
        // Ensure any newly introduced section types are appended cleanly
        val knownIds = parsed.map { it.id }.toSet()
        val missing = SecuritySettings.DEFAULT_HOME_SECTIONS.filter { it.id !in knownIds }
        return (parsed.filter { it.toTypeOrNull() != null } + missing).ifEmpty {
            SecuritySettings.DEFAULT_HOME_SECTIONS
        }
    }

    private fun readUsageCounts(): Map<String, Int> {
        val raw = prefs.getString(KEY_ITEM_USAGE_COUNTS, null) ?: return emptyMap()
        return runCatching { json.decodeFromString<Map<String, Int>>(raw) }.getOrDefault(emptyMap())
    }

    private fun readSettings(): SecuritySettings {
        val pinRec = getPinRecord()
        val bioBlob = getBiometricBlob()
        val themeName = prefs.getString(KEY_THEME_MODE, AppThemeMode.DARK.name)
        val theme = runCatching { AppThemeMode.valueOf(themeName ?: AppThemeMode.DARK.name) }
            .getOrDefault(AppThemeMode.DARK)
        val langName = prefs.getString(KEY_LANGUAGE, AppLanguage.ENGLISH.name)
        val language = runCatching { AppLanguage.valueOf(langName ?: AppLanguage.ENGLISH.name) }
            .getOrDefault(AppLanguage.ENGLISH)

        return SecuritySettings(
            autoLockMinutes = prefs.getInt(KEY_AUTO_LOCK_MIN, 5),
            clipboardClearSeconds = prefs.getInt(KEY_CLIPBOARD_SEC, 30),
            flagSecureEnabled = prefs.getBoolean(KEY_FLAG_SECURE, true),
            themeMode = theme,
            language = language,
            biometricEnabled = bioBlob != null,
            pinEnabled = pinRec != null,
            pinLength = pinRec?.pinLength ?: 6,
            pinMaxAttempts = prefs.getInt(KEY_PIN_MAX_ATTEMPTS, PinCrypto.DEFAULT_PIN_MAX_ATTEMPTS),
            pinFailedAttempts = prefs.getInt(KEY_PIN_FAILED, 0),
            showWebsiteIcons = prefs.getBoolean(KEY_SHOW_WEBSITE_ICONS, true),
            prefillUsername = prefs.getBoolean(KEY_PREFILL_USERNAME, true),
            checkWeakPasswords = prefs.getBoolean(KEY_WT_WEAK, true),
            checkReusedPasswords = prefs.getBoolean(KEY_WT_REUSED, true),
            checkTwoFactor = prefs.getBoolean(KEY_WT_2FA, true),
            defaultIdentityId = prefs.getString(KEY_DEFAULT_IDENTITY_ID, null),
            defaultCardId = prefs.getString(KEY_DEFAULT_CARD_ID, null),
            accountAvatarBase64 = prefs.getString(KEY_ACCOUNT_AVATAR_B64, null),
            homeSections = readHomeSections(),
        )
    }
}
