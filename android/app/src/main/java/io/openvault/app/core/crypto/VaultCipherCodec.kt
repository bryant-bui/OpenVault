package io.openvault.app.core.crypto

import io.openvault.app.core.network.CipherCardDto
import io.openvault.app.core.network.CipherDto
import io.openvault.app.core.network.CipherFieldDto
import io.openvault.app.core.network.CipherIdentityDto
import io.openvault.app.core.network.CipherLoginDto
import io.openvault.app.core.network.CipherRequest
import io.openvault.app.core.network.CipherSecureNoteDto
import io.openvault.app.core.network.CipherUriDto
import io.openvault.app.core.network.Fido2CredentialDto
import io.openvault.app.core.network.FolderDto
import io.openvault.app.core.network.PasswordHistoryDto

enum class CipherType(val value: Int, val labelVi: String) {
    LOGIN(1, "Đăng nhập"),
    SECURE_NOTE(2, "Ghi chú bảo mật"),
    CARD(3, "Thẻ thanh toán"),
    IDENTITY(4, "Danh tính");

    companion object {
        fun fromValue(v: Int): CipherType = entries.firstOrNull { it.value == v } ?: LOGIN
    }
}

data class VaultFolder(
    val id: String,
    val name: String,
    val revisionDate: String? = null,
)

data class VaultUri(
    val uri: String,
    val match: Int? = null,
)

data class VaultFido2Credential(
    val credentialId: String,
    val keyType: String,
    val keyAlgorithm: String,
    val keyCurve: String,
    val keyValue: String,
    val rpId: String,
    val rpName: String? = null,
    val userHandle: String? = null,
    val userName: String? = null,
    val userDisplayName: String? = null,
    val counter: String,
    val discoverable: String,
    val creationDate: String,
)

data class VaultLogin(
    val uris: List<VaultUri> = emptyList(),
    val username: String = "",
    val password: String = "",
    val passwordRevisionDate: String? = null,
    val totp: String? = null,
    val fido2Credentials: List<VaultFido2Credential> = emptyList(),
)

data class VaultCard(
    val cardholderName: String = "",
    val brand: String = "",
    val number: String = "",
    val expMonth: String = "",
    val expYear: String = "",
    val code: String = "",
)

data class VaultIdentity(
    val title: String = "",
    val firstName: String = "",
    val middleName: String = "",
    val lastName: String = "",
    val address1: String = "",
    val address2: String = "",
    val address3: String = "",
    val city: String = "",
    val state: String = "",
    val postalCode: String = "",
    val country: String = "",
    val company: String = "",
    val email: String = "",
    val phone: String = "",
    val ssn: String = "",
    val username: String = "",
    val passportNumber: String = "",
    val licenseNumber: String = "",
) {
    val fullName: String
        get() = listOf(title, firstName, middleName, lastName)
            .filter { it.isNotBlank() }
            .joinToString(" ")
}

data class VaultField(
    val name: String,
    val value: String,
    val type: Int = 0,
    val linkedId: Int? = null,
)

data class VaultPasswordHistory(
    val password: String,
    val lastUsedDate: String,
)

data class VaultItem(
    val id: String = "",
    val folderId: String? = null,
    val type: CipherType = CipherType.LOGIN,
    val name: String = "",
    val notes: String = "",
    val favorite: Boolean = false,
    val reprompt: Int = 0,
    val login: VaultLogin? = null,
    val card: VaultCard? = null,
    val identity: VaultIdentity? = null,
    val fields: List<VaultField> = emptyList(),
    val passwordHistory: List<VaultPasswordHistory> = emptyList(),
    val creationDate: String? = null,
    val revisionDate: String? = null,
    val deletedDate: String? = null,
) {
    val isDeleted: Boolean get() = !deletedDate.isNullOrBlank()
    val hasTotp: Boolean get() = !login?.totp.isNullOrBlank()
    val hasPasskeys: Boolean get() = !login?.fido2Credentials.isNullOrEmpty()
    val primarySubtitle: String
        get() = when (type) {
            CipherType.LOGIN -> login?.username?.takeIf { it.isNotBlank() }
                ?: login?.uris?.firstOrNull()?.uri?.let { DomainMatcher.extractDisplayDomain(it) }
                ?: "Tài khoản đăng nhập"
            CipherType.CARD -> {
                val digits = card?.number?.filter { it.isDigit() }.orEmpty()
                val last4 = if (digits.length >= 4) "•••• ${digits.takeLast(4)}" else digits
                listOfNotNull(card?.brand?.takeIf { it.isNotBlank() }, last4.takeIf { it.isNotBlank() })
                    .joinToString(" · ")
                    .ifBlank { card?.cardholderName.orEmpty().ifBlank { "Thẻ thanh toán" } }
            }
            CipherType.IDENTITY -> identity?.fullName?.takeIf { it.isNotBlank() }
                ?: identity?.email?.takeIf { it.isNotBlank() }
                ?: "Danh tính"
            CipherType.SECURE_NOTE -> notes.lineSequence().firstOrNull()?.take(48)
                ?.takeIf { it.isNotBlank() } ?: "Ghi chú bảo mật"
        }
}

data class WatchtowerReport(
    val score: Int = 100,
    val totalLogins: Int = 0,
    val weakItems: List<VaultItem> = emptyList(),
    val reusedItems: List<VaultItem> = emptyList(),
    val missingTotpItems: List<VaultItem> = emptyList(),
)

object WatchtowerAnalyzer {
    fun analyze(items: List<VaultItem>): WatchtowerReport {
        val logins = items.filter { !it.isDeleted && it.type == CipherType.LOGIN }
        if (logins.isEmpty()) return WatchtowerReport(score = 100, totalLogins = 0)

        val weak = mutableListOf<VaultItem>()
        val missingTotp = mutableListOf<VaultItem>()
        val passwordCounts = mutableMapOf<String, MutableList<VaultItem>>()

        for (item in logins) {
            val pwd = item.login?.password.orEmpty()
            if (pwd.isNotEmpty()) {
                val strength = PasswordGenerator.estimateStrength(pwd)
                if (strength.level.score <= 1) weak += item
                passwordCounts.getOrPut(pwd) { mutableListOf() } += item
            }
            if (!item.hasTotp) missingTotp += item
        }

        val reused = passwordCounts.values
            .filter { it.size > 1 }
            .flatten()
            .distinctBy { it.id }

        val weakRatio = weak.size.toDouble() / logins.size
        val reusedRatio = reused.size.toDouble() / logins.size
        val totpCoverage = (logins.size - missingTotp.size).toDouble() / logins.size

        val rawScore = (100.0 - weakRatio * 45.0 - reusedRatio * 35.0 - (1.0 - totpCoverage) * 20.0)
            .toInt()
            .coerceIn(0, 100)

        return WatchtowerReport(
            score = rawScore,
            totalLogins = logins.size,
            weakItems = weak,
            reusedItems = reused,
            missingTotpItems = missingTotp,
        )
    }
}

object VaultCipherCodec {

    private fun encOpt(value: String?, key: SymmetricKey): String? =
        value?.takeIf { it.isNotEmpty() }?.let { BitwardenCryptoEngine.encryptString(it, key) }

    private fun decOpt(value: String?, key: SymmetricKey): String =
        value?.takeIf { it.isNotEmpty() }?.let {
            runCatching { BitwardenCryptoEngine.decryptString(it, key) }.getOrDefault("")
        } ?: ""

    private fun decOptNullable(value: String?, key: SymmetricKey): String? =
        value?.takeIf { it.isNotEmpty() }?.let {
            runCatching { BitwardenCryptoEngine.decryptString(it, key) }.getOrNull()
        }

    fun encryptFolderName(name: String, key: SymmetricKey): String =
        BitwardenCryptoEngine.encryptString(name, key)

    fun decryptFolder(dto: FolderDto, key: SymmetricKey): VaultFolder = VaultFolder(
        id = dto.id,
        name = runCatching { BitwardenCryptoEngine.decryptString(dto.name, key) }
            .getOrDefault("Thư mục"),
        revisionDate = dto.revisionDate,
    )

    fun encryptCipher(item: VaultItem, key: SymmetricKey): CipherRequest {
        val encLogin = item.login?.let { login ->
            CipherLoginDto(
                uris = login.uris.filter { it.uri.isNotBlank() }.map { u ->
                    CipherUriDto(
                        uri = encOpt(u.uri.trim(), key),
                        match = u.match,
                    )
                }.ifEmpty { null },
                username = encOpt(login.username, key),
                password = encOpt(login.password, key),
                passwordRevisionDate = login.passwordRevisionDate,
                totp = encOpt(login.totp, key),
                fido2Credentials = login.fido2Credentials.map { cred ->
                    Fido2CredentialDto(
                        credentialId = BitwardenCryptoEngine.encryptString(cred.credentialId, key),
                        keyType = BitwardenCryptoEngine.encryptString(cred.keyType, key),
                        keyAlgorithm = BitwardenCryptoEngine.encryptString(cred.keyAlgorithm, key),
                        keyCurve = BitwardenCryptoEngine.encryptString(cred.keyCurve, key),
                        keyValue = BitwardenCryptoEngine.encryptString(cred.keyValue, key),
                        rpId = BitwardenCryptoEngine.encryptString(cred.rpId, key),
                        rpName = encOpt(cred.rpName, key),
                        userHandle = encOpt(cred.userHandle, key),
                        userName = encOpt(cred.userName, key),
                        userDisplayName = encOpt(cred.userDisplayName, key),
                        counter = BitwardenCryptoEngine.encryptString(cred.counter, key),
                        discoverable = BitwardenCryptoEngine.encryptString(cred.discoverable, key),
                        creationDate = cred.creationDate,
                    )
                }.ifEmpty { null },
            )
        }

        val encCard = item.card?.let { card ->
            CipherCardDto(
                cardholderName = encOpt(card.cardholderName, key),
                brand = encOpt(card.brand, key),
                number = encOpt(card.number, key),
                expMonth = encOpt(card.expMonth, key),
                expYear = encOpt(card.expYear, key),
                code = encOpt(card.code, key),
            )
        }

        val encIdentity = item.identity?.let { id ->
            CipherIdentityDto(
                title = encOpt(id.title, key),
                firstName = encOpt(id.firstName, key),
                middleName = encOpt(id.middleName, key),
                lastName = encOpt(id.lastName, key),
                address1 = encOpt(id.address1, key),
                address2 = encOpt(id.address2, key),
                address3 = encOpt(id.address3, key),
                city = encOpt(id.city, key),
                state = encOpt(id.state, key),
                postalCode = encOpt(id.postalCode, key),
                country = encOpt(id.country, key),
                company = encOpt(id.company, key),
                email = encOpt(id.email, key),
                phone = encOpt(id.phone, key),
                ssn = encOpt(id.ssn, key),
                username = encOpt(id.username, key),
                passportNumber = encOpt(id.passportNumber, key),
                licenseNumber = encOpt(id.licenseNumber, key),
            )
        }

        val encFields = item.fields.map { f ->
            CipherFieldDto(
                name = encOpt(f.name, key),
                value = encOpt(f.value, key),
                type = f.type,
                linkedId = f.linkedId,
            )
        }.ifEmpty { null }

        val encHistory = item.passwordHistory.map { h ->
            PasswordHistoryDto(
                password = BitwardenCryptoEngine.encryptString(h.password, key),
                lastUsedDate = h.lastUsedDate,
            )
        }.ifEmpty { null }

        return CipherRequest(
            type = item.type.value,
            folderId = item.folderId,
            name = BitwardenCryptoEngine.encryptString(item.name.ifBlank { "Không có tiêu đề" }, key),
            notes = encOpt(item.notes, key),
            favorite = item.favorite,
            reprompt = item.reprompt,
            login = if (item.type == CipherType.LOGIN) encLogin else null,
            secureNote = if (item.type == CipherType.SECURE_NOTE) CipherSecureNoteDto(0) else null,
            card = if (item.type == CipherType.CARD) encCard else null,
            identity = if (item.type == CipherType.IDENTITY) encIdentity else null,
            fields = encFields,
            passwordHistory = encHistory,
        )
    }

    fun decryptCipher(dto: CipherDto, key: SymmetricKey): VaultItem {
        val type = CipherType.fromValue(dto.type)
        val decLogin = dto.login?.let { login ->
            VaultLogin(
                uris = login.uris.orEmpty().mapNotNull { u ->
                    val uri = decOptNullable(u.uri, key) ?: return@mapNotNull null
                    VaultUri(uri = uri, match = u.match)
                },
                username = decOpt(login.username, key),
                password = decOpt(login.password, key),
                passwordRevisionDate = login.passwordRevisionDate,
                totp = decOptNullable(login.totp, key),
                fido2Credentials = login.fido2Credentials.orEmpty().mapNotNull { c ->
                    runCatching {
                        VaultFido2Credential(
                            credentialId = BitwardenCryptoEngine.decryptString(c.credentialId, key),
                            keyType = BitwardenCryptoEngine.decryptString(c.keyType, key),
                            keyAlgorithm = BitwardenCryptoEngine.decryptString(c.keyAlgorithm, key),
                            keyCurve = BitwardenCryptoEngine.decryptString(c.keyCurve, key),
                            keyValue = BitwardenCryptoEngine.decryptString(c.keyValue, key),
                            rpId = BitwardenCryptoEngine.decryptString(c.rpId, key),
                            rpName = decOptNullable(c.rpName, key),
                            userHandle = decOptNullable(c.userHandle, key),
                            userName = decOptNullable(c.userName, key),
                            userDisplayName = decOptNullable(c.userDisplayName, key),
                            counter = BitwardenCryptoEngine.decryptString(c.counter, key),
                            discoverable = BitwardenCryptoEngine.decryptString(c.discoverable, key),
                            creationDate = c.creationDate,
                        )
                    }.getOrNull()
                },
            )
        }

        val decCard = dto.card?.let { c ->
            VaultCard(
                cardholderName = decOpt(c.cardholderName, key),
                brand = decOpt(c.brand, key),
                number = decOpt(c.number, key),
                expMonth = decOpt(c.expMonth, key),
                expYear = decOpt(c.expYear, key),
                code = decOpt(c.code, key),
            )
        }

        val decIdentity = dto.identity?.let { id ->
            VaultIdentity(
                title = decOpt(id.title, key),
                firstName = decOpt(id.firstName, key),
                middleName = decOpt(id.middleName, key),
                lastName = decOpt(id.lastName, key),
                address1 = decOpt(id.address1, key),
                address2 = decOpt(id.address2, key),
                address3 = decOpt(id.address3, key),
                city = decOpt(id.city, key),
                state = decOpt(id.state, key),
                postalCode = decOpt(id.postalCode, key),
                country = decOpt(id.country, key),
                company = decOpt(id.company, key),
                email = decOpt(id.email, key),
                phone = decOpt(id.phone, key),
                ssn = decOpt(id.ssn, key),
                username = decOpt(id.username, key),
                passportNumber = decOpt(id.passportNumber, key),
                licenseNumber = decOpt(id.licenseNumber, key),
            )
        }

        val decFields = dto.fields.orEmpty().map { f ->
            VaultField(
                name = decOpt(f.name, key),
                value = decOpt(f.value, key),
                type = f.type,
                linkedId = f.linkedId,
            )
        }

        val decHistory = dto.passwordHistory.orEmpty().mapNotNull { h ->
            runCatching {
                VaultPasswordHistory(
                    password = BitwardenCryptoEngine.decryptString(h.password, key),
                    lastUsedDate = h.lastUsedDate,
                )
            }.getOrNull()
        }

        return VaultItem(
            id = dto.id,
            folderId = dto.folderId,
            type = type,
            name = runCatching { BitwardenCryptoEngine.decryptString(dto.name, key) }
                .getOrDefault("Không thể giải mã"),
            notes = decOpt(dto.notes, key),
            favorite = dto.favorite,
            reprompt = dto.reprompt,
            login = decLogin,
            card = decCard,
            identity = decIdentity,
            fields = decFields,
            passwordHistory = decHistory,
            creationDate = dto.creationDate,
            revisionDate = dto.revisionDate,
            deletedDate = dto.deletedDate,
        )
    }
}
