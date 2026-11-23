package io.openvault.app.core.crypto

import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.io.ByteArrayOutputStream
import java.math.BigInteger
import java.net.URI
import java.security.KeyFactory
import java.security.KeyPairGenerator
import java.security.MessageDigest
import java.security.SecureRandom
import java.security.Signature
import java.security.interfaces.ECPublicKey
import java.security.spec.ECGenParameterSpec
import java.security.spec.PKCS8EncodedKeySpec
import java.time.Instant
import java.util.Base64
import java.util.Locale

/**
 * WebAuthn / FIDO2 Passkey cryptographic engine (ECDSA P-256 / ES256, alg=-7).
 *
 * 100% wire-compatible with `@openvault/crypto-core/src/passkey-crypto.ts` and
 * the Bitwarden `login.fido2Credentials` schema (PKCS#8 DER base64url private key,
 * deterministic CBOR, COSE EC2 P-256 key, ASN.1 DER signatures).
 */
object PasskeyCryptoEngine {

    private val secureRandom = SecureRandom()
    private val OPENVAULT_AAGUID = ByteArray(16) // 16 zero bytes (privacy-preserving software authenticator)

    private val PUBLIC_SUFFIX_ONLY = setOf(
        "com", "org", "net", "io", "dev", "app", "gov", "edu", "vn", "uk", "jp", "au",
        "co.uk", "com.vn", "net.vn", "org.vn", "edu.vn", "gov.vn", "com.au", "co.jp", "com.sg",
    )

    private val IPV4_REGEX = Regex("^\\d{1,3}(\\.\\d{1,3}){3}$")

    // --- Minimal Deterministic CBOR (RFC 8949) ---

    sealed interface CborValue {
        data class IntVal(val value: Long) : CborValue
        data class BytesVal(val value: ByteArray) : CborValue
        data class TextVal(val value: String) : CborValue
        data class MapVal(val entries: List<Pair<CborValue, CborValue>>) : CborValue
    }

    private fun writeCborHead(out: ByteArrayOutputStream, majorType: Int, value: Long) {
        val mt = (majorType and 0x07) shl 5
        when {
            value < 24L -> out.write(mt or value.toInt())
            value <= 0xFFL -> {
                out.write(mt or 24)
                out.write(value.toInt() and 0xFF)
            }
            value <= 0xFFFFL -> {
                out.write(mt or 25)
                out.write(((value ushr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
            else -> {
                out.write(mt or 26)
                out.write(((value ushr 24) and 0xFF).toInt())
                out.write(((value ushr 16) and 0xFF).toInt())
                out.write(((value ushr 8) and 0xFF).toInt())
                out.write((value and 0xFF).toInt())
            }
        }
    }

    fun encodeCbor(value: CborValue): ByteArray {
        val out = ByteArrayOutputStream()
        writeCbor(out, value)
        return out.toByteArray()
    }

    private fun writeCbor(out: ByteArrayOutputStream, value: CborValue) {
        when (value) {
            is CborValue.IntVal -> {
                if (value.value >= 0L) {
                    writeCborHead(out, 0, value.value)
                } else {
                    writeCborHead(out, 1, -1L - value.value)
                }
            }
            is CborValue.BytesVal -> {
                writeCborHead(out, 2, value.value.size.toLong())
                out.write(value.value)
            }
            is CborValue.TextVal -> {
                val utf8 = value.value.toByteArray(Charsets.UTF_8)
                writeCborHead(out, 3, utf8.size.toLong())
                out.write(utf8)
            }
            is CborValue.MapVal -> {
                writeCborHead(out, 5, value.entries.size.toLong())
                for ((k, v) in value.entries) {
                    writeCbor(out, k)
                    writeCbor(out, v)
                }
            }
        }
    }

    // --- Base64Url & Digest Utilities ---

    fun bytesToBase64Url(bytes: ByteArray): String =
        Base64.getUrlEncoder().withoutPadding().encodeToString(bytes)

    fun base64UrlToBytes(input: String): ByteArray {
        val normalized = input.trim().replace('+', '-').replace('/', '_').trimEnd('=')
        return Base64.getUrlDecoder().decode(normalized)
    }

    fun sha256(data: ByteArray): ByteArray =
        MessageDigest.getInstance("SHA-256").digest(data)

    fun computeAndroidApkKeyHashOrigin(signingCertDer: ByteArray): String =
        "android:apk-key-hash:${bytesToBase64Url(sha256(signingCertDer))}"

    /**
     * Enforces WebAuthn Level 2/3 RP ID rules for web origins (and accepts valid domain RP IDs
     * when the caller is a native Android app verified via `android:apk-key-hash:...`).
     */
    fun isValidRpIdForOrigin(rpId: String, origin: String): Boolean {
        val cleanRp = rpId.trim().lowercase(Locale.ROOT).trimEnd('.')
        if (cleanRp.isEmpty() || cleanRp.contains('/') || cleanRp.contains(':')) return false
        if (cleanRp in PUBLIC_SUFFIX_ONLY) return false

        if (origin.startsWith("android:apk-key-hash:", ignoreCase = true)) {
            return cleanRp.contains('.') && !cleanRp.startsWith('.') && !cleanRp.endsWith('.')
        }

        val uri = runCatching { URI(origin) }.getOrNull() ?: return false
        val scheme = uri.scheme?.lowercase(Locale.ROOT) ?: return false
        val host = uri.host?.lowercase(Locale.ROOT)?.trimEnd('.') ?: return false
        val isLocal = host == "localhost" || host == "127.0.0.1"
        if (scheme != "https" && !isLocal) return false

        if (cleanRp == host) return true
        if (isLocal || IPV4_REGEX.matches(host)) return false

        val baseDomain = DomainMatcher.getBaseDomain(host)
        if (cleanRp.length < baseDomain.length) return false
        return host.endsWith(".$cleanRp")
    }

    // --- COSE P-256 Public Key ---

    private fun toFixedUnsigned32(value: BigInteger): ByteArray {
        val raw = value.toByteArray()
        return when {
            raw.size == 32 -> raw
            raw.size == 33 && raw[0] == 0.toByte() -> raw.copyOfRange(1, 33)
            raw.size < 32 -> ByteArray(32).also { dest ->
                System.arraycopy(raw, 0, dest, 32 - raw.size, raw.size)
            }
            else -> error("Unexpected P-256 coordinate length: ${raw.size}")
        }
    }

    fun encodeCoseP256PublicKey(x: ByteArray, y: ByteArray): ByteArray {
        require(x.size == 32 && y.size == 32) { "P-256 coordinates must be 32 bytes each" }
        return encodeCbor(
            CborValue.MapVal(
                listOf(
                    CborValue.IntVal(1) to CborValue.IntVal(2),   // kty: EC2
                    CborValue.IntVal(3) to CborValue.IntVal(-7),  // alg: ES256
                    CborValue.IntVal(-1) to CborValue.IntVal(1),  // crv: P-256
                    CborValue.IntVal(-2) to CborValue.BytesVal(x),
                    CborValue.IntVal(-3) to CborValue.BytesVal(y),
                ),
            ),
        )
    }

    // --- Passkey Registration (Create) ---

    data class CreatedPasskey(
        val credential: VaultFido2Credential,
        val credentialIdBytes: ByteArray,
        val authenticatorData: ByteArray,
        val attestationObject: ByteArray,
        val publicKeySpki: ByteArray,
        val clientDataJson: String,
        val registrationResponseJson: String,
    )

    fun createPasskey(
        rpId: String,
        rpName: String?,
        userHandleB64Url: String,
        userName: String,
        userDisplayName: String?,
        challengeB64Url: String,
        origin: String,
        discoverable: Boolean = true,
        packageName: String? = null,
    ): CreatedPasskey {
        val cleanRpId = rpId.trim().lowercase(Locale.ROOT).trimEnd('.')
        require(isValidRpIdForOrigin(cleanRpId, origin)) {
            "RP ID '$cleanRpId' is not valid for origin '$origin'"
        }

        val kpg = KeyPairGenerator.getInstance("EC")
        kpg.initialize(ECGenParameterSpec("secp256r1"), secureRandom)
        val keyPair = kpg.generateKeyPair()

        val pkcs8Bytes = keyPair.private.encoded
        val spkiBytes = keyPair.public.encoded
        val ecPub = keyPair.public as ECPublicKey
        val x = toFixedUnsigned32(ecPub.w.affineX)
        val y = toFixedUnsigned32(ecPub.w.affineY)
        val cosePublicKey = encodeCoseP256PublicKey(x, y)

        val credentialIdBytes = ByteArray(16).also { secureRandom.nextBytes(it) }
        val rpIdHash = sha256(cleanRpId.toByteArray(Charsets.UTF_8))

        // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) | AT (0x40) = 0x5d
        val authDataOut = ByteArrayOutputStream().apply {
            write(rpIdHash)
            write(0x5d)
            write(byteArrayOf(0, 0, 0, 0)) // signCount = 0
            write(OPENVAULT_AAGUID)
            write((credentialIdBytes.size ushr 8) and 0xFF)
            write(credentialIdBytes.size and 0xFF)
            write(credentialIdBytes)
            write(cosePublicKey)
        }
        val authenticatorData = authDataOut.toByteArray()

        val attestationObject = encodeCbor(
            CborValue.MapVal(
                listOf(
                    CborValue.TextVal("fmt") to CborValue.TextVal("none"),
                    CborValue.TextVal("attStmt") to CborValue.MapVal(emptyList()),
                    CborValue.TextVal("authData") to CborValue.BytesVal(authenticatorData),
                ),
            ),
        )

        val clientDataObj = buildJsonObject {
            put("type", "webauthn.create")
            put("challenge", challengeB64Url)
            put("origin", origin)
            put("crossOrigin", false)
            if (!packageName.isNullOrBlank() && origin.startsWith("android:apk-key-hash:")) {
                put("androidPackageName", packageName)
            }
        }
        val clientDataJson = clientDataObj.toString()

        val credIdB64 = bytesToBase64Url(credentialIdBytes)
        val clientDataB64 = bytesToBase64Url(clientDataJson.toByteArray(Charsets.UTF_8))
        val attestationB64 = bytesToBase64Url(attestationObject)
        val authDataB64 = bytesToBase64Url(authenticatorData)
        val spkiB64 = bytesToBase64Url(spkiBytes)

        val responseJson = buildJsonObject {
            put("id", credIdB64)
            put("rawId", credIdB64)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put(
                "response",
                buildJsonObject {
                    put("clientDataJSON", clientDataB64)
                    put("attestationObject", attestationB64)
                    put("authenticatorData", authDataB64)
                    put("publicKey", spkiB64)
                    put("publicKeyAlgorithm", -7)
                    put(
                        "transports",
                        buildJsonArray {
                            add(JsonPrimitive("internal"))
                            add(JsonPrimitive("hybrid"))
                        },
                    )
                },
            )
            put(
                "clientExtensionResults",
                buildJsonObject {
                    put(
                        "credProps",
                        buildJsonObject {
                            put("rk", discoverable)
                        },
                    )
                },
            )
        }.toString()

        val credential = VaultFido2Credential(
            credentialId = credIdB64,
            keyType = "public-key",
            keyAlgorithm = "ECDSA",
            keyCurve = "P-256",
            keyValue = bytesToBase64Url(pkcs8Bytes),
            rpId = cleanRpId,
            rpName = rpName?.takeIf { it.isNotBlank() } ?: cleanRpId,
            userHandle = userHandleB64Url,
            userName = userName,
            userDisplayName = userDisplayName?.takeIf { it.isNotBlank() } ?: userName,
            counter = "0",
            discoverable = discoverable.toString(),
            creationDate = Instant.now().toString(),
        )

        return CreatedPasskey(
            credential = credential,
            credentialIdBytes = credentialIdBytes,
            authenticatorData = authenticatorData,
            attestationObject = attestationObject,
            publicKeySpki = spkiBytes,
            clientDataJson = clientDataJson,
            registrationResponseJson = responseJson,
        )
    }

    // --- Passkey Authentication (Get Assertion) ---

    data class SignedAssertion(
        val authenticatorData: ByteArray,
        val signatureDer: ByteArray,
        val clientDataJson: String,
        val nextCounter: Long,
        val authenticationResponseJson: String,
    )

    fun signAssertion(
        credential: VaultFido2Credential,
        challengeB64Url: String,
        origin: String,
        clientDataHashOverride: ByteArray? = null,
        packageName: String? = null,
    ): SignedAssertion {
        val cleanRpId = credential.rpId.trim().lowercase(Locale.ROOT).trimEnd('.')
        require(isValidRpIdForOrigin(cleanRpId, origin)) {
            "RP ID '$cleanRpId' is not valid for origin '$origin'"
        }
        if (clientDataHashOverride != null) {
            require(clientDataHashOverride.size == 32) {
                "clientDataHash must be 32 bytes (SHA-256), got ${clientDataHashOverride.size}"
            }
        }

        val pkcs8Bytes = base64UrlToBytes(credential.keyValue)
        val privateKey = KeyFactory.getInstance("EC").generatePrivate(PKCS8EncodedKeySpec(pkcs8Bytes))

        val rpIdHash = sha256(cleanRpId.toByteArray(Charsets.UTF_8))
        val currentCounter = credential.counter.toLongOrNull() ?: 0L
        val nextCounter = if (currentCounter > 0L) currentCounter + 1L else 0L

        // Flags: UP (0x01) | UV (0x04) | BE (0x08) | BS (0x10) = 0x1d
        val authenticatorData = ByteArrayOutputStream().apply {
            write(rpIdHash)
            write(0x1d)
            write(((nextCounter ushr 24) and 0xFF).toInt())
            write(((nextCounter ushr 16) and 0xFF).toInt())
            write(((nextCounter ushr 8) and 0xFF).toInt())
            write((nextCounter and 0xFF).toInt())
        }.toByteArray()

        val clientDataObj = buildJsonObject {
            put("type", "webauthn.get")
            put("challenge", challengeB64Url)
            put("origin", origin)
            put("crossOrigin", false)
            if (!packageName.isNullOrBlank() && origin.startsWith("android:apk-key-hash:")) {
                put("androidPackageName", packageName)
            }
        }
        val clientDataJson = clientDataObj.toString()
        val clientDataBytes = clientDataJson.toByteArray(Charsets.UTF_8)
        val effectiveClientDataHash = clientDataHashOverride ?: sha256(clientDataBytes)

        // JCA SHA256withECDSA natively outputs ASN.1 DER required by WebAuthn/FIDO2
        val signer = Signature.getInstance("SHA256withECDSA")
        signer.initSign(privateKey, secureRandom)
        signer.update(authenticatorData)
        signer.update(effectiveClientDataHash)
        val signatureDer = signer.sign()

        val authResponseJson = buildJsonObject {
            put("id", credential.credentialId)
            put("rawId", credential.credentialId)
            put("type", "public-key")
            put("authenticatorAttachment", "platform")
            put(
                "response",
                buildJsonObject {
                    put("clientDataJSON", bytesToBase64Url(clientDataBytes))
                    put("authenticatorData", bytesToBase64Url(authenticatorData))
                    put("signature", bytesToBase64Url(signatureDer))
                    credential.userHandle?.takeIf { it.isNotBlank() }?.let {
                        put("userHandle", it)
                    }
                },
            )
            put("clientExtensionResults", buildJsonObject {})
        }.toString()

        return SignedAssertion(
            authenticatorData = authenticatorData,
            signatureDer = signatureDer,
            clientDataJson = clientDataJson,
            nextCounter = nextCounter,
            authenticationResponseJson = authResponseJson,
        )
    }

    // --- WebAuthn Request JSON Helpers ---

    data class ParsedCreateRequest(
        val rpId: String,
        val rpName: String,
        val challengeB64Url: String,
        val userIdB64Url: String,
        val userName: String,
        val userDisplayName: String,
        val excludeCredentialIds: List<String>,
        val supportsEs256: Boolean,
        val discoverable: Boolean,
    )

    data class ParsedGetRequest(
        val rpId: String,
        val challengeB64Url: String,
        val allowCredentialIds: List<String>,
    )

    fun parseCreateRequestJson(requestJson: String): ParsedCreateRequest? = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(requestJson) as? JsonObject
            ?: return null
        val rp = root["rp"] as? JsonObject ?: return null
        val user = root["user"] as? JsonObject ?: return null
        val rpId = (rp["id"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (rpId.isEmpty()) return null

        val rpName = (rp["name"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: rpId
        val challenge = (root["challenge"] as? JsonPrimitive)?.content.orEmpty()
        val userId = (user["id"] as? JsonPrimitive)?.content.orEmpty()
        val userName = (user["name"] as? JsonPrimitive)?.content.orEmpty()
        val userDisplayName = (user["displayName"] as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() } ?: userName

        val params = (root["pubKeyCredParams"] as? JsonArray).orEmpty()
        val supportsEs256 = params.isEmpty() || params.any { el ->
            ((el as? JsonObject)?.get("alg") as? JsonPrimitive)?.content?.toIntOrNull() == -7
        }

        val excludeIds = extractDescriptorIds(root["excludeCredentials"])
        val selection = root["authenticatorSelection"] as? JsonObject
        val residentKey = (selection?.get("residentKey") as? JsonPrimitive)?.content
        val discoverable = residentKey != "discouraged"

        ParsedCreateRequest(
            rpId = rpId.lowercase(Locale.ROOT),
            rpName = rpName,
            challengeB64Url = challenge,
            userIdB64Url = userId,
            userName = userName,
            userDisplayName = userDisplayName,
            excludeCredentialIds = excludeIds,
            supportsEs256 = supportsEs256,
            discoverable = discoverable,
        )
    }.getOrNull()

    fun parseGetRequestJson(requestJson: String): ParsedGetRequest? = runCatching {
        val root = kotlinx.serialization.json.Json.parseToJsonElement(requestJson) as? JsonObject
            ?: return null
        val rpId = (root["rpId"] as? JsonPrimitive)?.content?.trim().orEmpty()
        if (rpId.isEmpty()) return null
        val challenge = (root["challenge"] as? JsonPrimitive)?.content.orEmpty()
        val allowIds = extractDescriptorIds(root["allowCredentials"])

        ParsedGetRequest(
            rpId = rpId.lowercase(Locale.ROOT),
            challengeB64Url = challenge,
            allowCredentialIds = allowIds,
        )
    }.getOrNull()

    private fun extractDescriptorIds(element: JsonElement?): List<String> {
        val arr = element as? JsonArray ?: return emptyList()
        return arr.mapNotNull { item ->
            ((item as? JsonObject)?.get("id") as? JsonPrimitive)?.content?.takeIf { it.isNotBlank() }
        }
    }
}
