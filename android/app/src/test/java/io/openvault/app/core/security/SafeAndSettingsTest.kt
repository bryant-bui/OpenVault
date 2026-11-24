package io.openvault.app.core.security

import io.openvault.app.core.network.SafeItemUpdateRequest
import io.openvault.app.core.network.SafePasscodeUpdateRequest
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Locale

class SafeAndSettingsTest {

    private val safeApiJson = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    @Test
    fun safeItemUpdateRequestOmitsNullOptionalFields() {
        val payload = SafeItemUpdateRequest(favorite = true)
        val encoded = safeApiJson.encodeToString(payload)
        assertEquals("""{"favorite":true}""", encoded)
        assertFalse(encoded.contains("category"))
        assertFalse(encoded.contains("encryptedMetadata"))
    }

    @Test
    fun safePasscodeUpdateRequestOmitsNullRecoveryFields() {
        val payload = SafePasscodeUpdateRequest(
            mode = "change",
            kdfType = 1,
            kdfIterations = 3,
            kdfMemory = 64,
            kdfParallelism = 4,
            passcodeSalt = "AAAAAAAAAAAAAAAAAAAAAA==",
            passcodeEncryptedSafeKey = "2.abc|def|ghi",
        )
        val encoded = safeApiJson.encodeToString(payload)
        assertFalse(encoded.contains("recoverySalt"))
        assertFalse(encoded.contains("recoveryKdfIterations"))
        assertFalse(encoded.contains("recoveryEncryptedSafeKey"))
        assertTrue(encoded.contains(""""mode":"change""""))
    }

    @Test
    fun appLanguageSystemFollowsActiveLocale() {
        val viLocale = Locale.forLanguageTag("vi-VN")
        val enLocale = Locale.US

        assertTrue(AppLanguage.SYSTEM.isVietnameseActive(viLocale))
        assertFalse(AppLanguage.SYSTEM.isVietnameseActive(enLocale))
        assertTrue(AppLanguage.VIETNAMESE.isVietnameseActive(enLocale))
        assertFalse(AppLanguage.ENGLISH.isVietnameseActive(viLocale))
    }

    @Test
    fun accountProfileInfoRoundTripsCleanly() {
        val profile = AccountProfileInfo(
            displayName = "Bryant Bui",
            birthDate = "01/01/1998",
            phone = "+84 900 000 000",
            city = "Da Nang",
            address = "Hai Chau, Da Nang",
            bio = "Security enthusiast",
        )
        val encoded = safeApiJson.encodeToString(profile)
        val decoded = safeApiJson.decodeFromString<AccountProfileInfo>(encoded)
        assertEquals(profile, decoded)
    }
}
