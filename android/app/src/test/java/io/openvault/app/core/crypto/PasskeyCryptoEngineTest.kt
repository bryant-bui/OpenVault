package io.openvault.app.core.crypto

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.security.KeyFactory
import java.security.Signature
import java.security.spec.X509EncodedKeySpec

class PasskeyCryptoEngineTest {

    @Test
    fun createAndSignPasskeyProducesValidWebAuthnDerAssertion() {
        val challengeB64 = PasskeyCryptoEngine.bytesToBase64Url("challenge-create-12345".toByteArray())
        val userHandleB64 = PasskeyCryptoEngine.bytesToBase64Url("user-uid-42".toByteArray())

        val created = PasskeyCryptoEngine.createPasskey(
            rpId = "github.com",
            rpName = "GitHub",
            userHandleB64Url = userHandleB64,
            userName = "alice@openvault.io",
            userDisplayName = "Alice",
            challengeB64Url = challengeB64,
            origin = "https://github.com",
            discoverable = true,
        )

        assertEquals("public-key", created.credential.keyType)
        assertEquals("ECDSA", created.credential.keyAlgorithm)
        assertEquals("P-256", created.credential.keyCurve)
        assertEquals("github.com", created.credential.rpId)
        assertEquals("alice@openvault.io", created.credential.userName)
        assertEquals(16, created.credentialIdBytes.size)
        // AuthenticatorData flags byte at index 32 must be 0x5d (UP|UV|BE|BS|AT)
        assertEquals(0x5d.toByte(), created.authenticatorData[32])
        assertArrayEquals(
            PasskeyCryptoEngine.sha256("github.com".toByteArray()),
            created.authenticatorData.copyOfRange(0, 32),
        )

        // Verify registration JSON shape
        val regJson = Json.parseToJsonElement(created.registrationResponseJson) as JsonObject
        assertEquals(created.credential.credentialId, (regJson["id"] as JsonPrimitive).content)
        val regResp = regJson["response"] as JsonObject
        assertEquals("-7", (regResp["publicKeyAlgorithm"] as JsonPrimitive).content)

        // Sign an assertion and verify DER signature using the exported SPKI public key
        val getChallenge = PasskeyCryptoEngine.bytesToBase64Url("challenge-get-99999".toByteArray())
        val signed = PasskeyCryptoEngine.signAssertion(
            credential = created.credential,
            challengeB64Url = getChallenge,
            origin = "https://github.com",
        )

        assertEquals(37, signed.authenticatorData.size)
        assertEquals(0x1d.toByte(), signed.authenticatorData[32])
        assertEquals(0x30.toByte(), signed.signatureDer[0]) // ASN.1 SEQUENCE tag

        val pubKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(created.publicKeySpki))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pubKey)
        verifier.update(signed.authenticatorData)
        verifier.update(PasskeyCryptoEngine.sha256(signed.clientDataJson.toByteArray(Charsets.UTF_8)))
        assertTrue("ASN.1 DER assertion signature must verify", verifier.verify(signed.signatureDer))
    }

    @Test
    fun signAssertionRespectsPrivilegedBrowserClientDataHashOverride() {
        val created = PasskeyCryptoEngine.createPasskey(
            rpId = "webauthn.io",
            rpName = "WebAuthn.io",
            userHandleB64Url = PasskeyCryptoEngine.bytesToBase64Url("uid-99".toByteArray()),
            userName = "demo",
            userDisplayName = "Demo",
            challengeB64Url = "Y2hhbGxlbmdl",
            origin = "https://webauthn.io",
        )

        // Simulate Chrome passing its own clientDataHash directly to CredentialProviderService
        val browserClientDataJson =
            """{"type":"webauthn.get","challenge":"Y2hhbGxlbmdl","origin":"https://webauthn.io","crossOrigin":false}"""
        val browserClientDataHash = PasskeyCryptoEngine.sha256(browserClientDataJson.toByteArray(Charsets.UTF_8))

        val signed = PasskeyCryptoEngine.signAssertion(
            credential = created.credential,
            challengeB64Url = "Y2hhbGxlbmdl",
            origin = "https://webauthn.io",
            clientDataHashOverride = browserClientDataHash,
        )

        val pubKey = KeyFactory.getInstance("EC")
            .generatePublic(X509EncodedKeySpec(created.publicKeySpki))
        val verifier = Signature.getInstance("SHA256withECDSA")
        verifier.initVerify(pubKey)
        verifier.update(signed.authenticatorData)
        verifier.update(browserClientDataHash)
        assertTrue("Signature over privileged browser clientDataHash must verify", verifier.verify(signed.signatureDer))
    }

    @Test
    fun rpIdValidationMatchesWebAuthnSecurityRules() {
        assertTrue(PasskeyCryptoEngine.isValidRpIdForOrigin("github.com", "https://github.com"))
        assertTrue(PasskeyCryptoEngine.isValidRpIdForOrigin("github.com", "https://gist.github.com"))
        assertTrue(PasskeyCryptoEngine.isValidRpIdForOrigin("vietcombank.com.vn", "https://ebanking.vietcombank.com.vn"))
        assertTrue(PasskeyCryptoEngine.isValidRpIdForOrigin("localhost", "http://localhost:3000"))
        assertTrue(
            PasskeyCryptoEngine.isValidRpIdForOrigin(
                "github.com",
                PasskeyCryptoEngine.computeAndroidApkKeyHashOrigin("dummy-cert".toByteArray()),
            ),
        )

        assertFalse(PasskeyCryptoEngine.isValidRpIdForOrigin("github.com", "http://github.com"))
        assertFalse(PasskeyCryptoEngine.isValidRpIdForOrigin("com", "https://github.com"))
        assertFalse(PasskeyCryptoEngine.isValidRpIdForOrigin("com.vn", "https://ebanking.vietcombank.com.vn"))
        assertFalse(PasskeyCryptoEngine.isValidRpIdForOrigin("github.com", "https://evilgithub.com"))
        assertFalse(PasskeyCryptoEngine.isValidRpIdForOrigin("google.com", "https://github.com"))
    }

    @Test
    fun parsesWebAuthnCreateAndGetRequestJsonCleanly() {
        val createJson = """
            {
              "rp": { "id": "webauthn.io", "name": "WebAuthn.io" },
              "user": { "id": "dXNlcg", "name": "alice", "displayName": "Alice" },
              "challenge": "Y2hhbGxlbmdl",
              "pubKeyCredParams": [{ "type": "public-key", "alg": -7 }],
              "excludeCredentials": [{ "type": "public-key", "id": "Y3JlZDE" }]
            }
        """.trimIndent()

        val parsedCreate = PasskeyCryptoEngine.parseCreateRequestJson(createJson)
        assertNotNull(parsedCreate)
        assertEquals("webauthn.io", parsedCreate!!.rpId)
        assertEquals("WebAuthn.io", parsedCreate.rpName)
        assertEquals("alice", parsedCreate.userName)
        assertTrue(parsedCreate.supportsEs256)
        assertEquals(listOf("Y3JlZDE"), parsedCreate.excludeCredentialIds)

        val getJson = """
            {
              "rpId": "github.com",
              "challenge": "Z2V0LWNoYWxsZW5nZQ",
              "allowCredentials": [{ "type": "public-key", "id": "YWJj" }]
            }
        """.trimIndent()

        val parsedGet = PasskeyCryptoEngine.parseGetRequestJson(getJson)
        assertNotNull(parsedGet)
        assertEquals("github.com", parsedGet!!.rpId)
        assertEquals("Z2V0LWNoYWxsZW5nZQ", parsedGet.challengeB64Url)
        assertEquals(listOf("YWJj"), parsedGet.allowCredentialIds)
    }

    @Test
    fun deterministicCborEncodesCoseP256KeyExactlyLikeCryptoCore() {
        val x = ByteArray(32) { 0x11 }
        val y = ByteArray(32) { 0x22 }
        val cose = PasskeyCryptoEngine.encodeCoseP256PublicKey(x, y)
        // Map(5): 0xa5, key 1 -> 2 (0x01, 0x02), key 3 -> -7 (0x03, 0x26), key -1 -> 1 (0x20, 0x01),
        // key -2 -> bstr(32) (0x21, 0x58, 0x20, x...), key -3 -> bstr(32) (0x22, 0x58, 0x20, y...)
        assertEquals(77, cose.size)
        assertEquals(0xa5.toByte(), cose[0])
        assertEquals(0x01.toByte(), cose[1])
        assertEquals(0x02.toByte(), cose[2])
        assertEquals(0x03.toByte(), cose[3])
        assertEquals(0x26.toByte(), cose[4]) // -7 -> major 1, value 6 -> 0x20 | 6 = 0x26
        assertEquals(0x20.toByte(), cose[5]) // -1 -> major 1, value 0 -> 0x20
        assertEquals(0x01.toByte(), cose[6])
        assertEquals(0x21.toByte(), cose[7]) // -2
        assertEquals(0x58.toByte(), cose[8]) // bstr(len in next byte)
        assertEquals(0x20.toByte(), cose[9]) // 32 bytes
        assertEquals(0x11.toByte(), cose[10])
        assertEquals(0x22.toByte(), cose[42]) // -3
        assertEquals(0x58.toByte(), cose[43])
        assertEquals(0x20.toByte(), cose[44])
        assertEquals(0x22.toByte(), cose[45])
    }
}
