package io.openvault.app.core.crypto

import java.net.URI
import java.net.URLDecoder
import java.nio.charset.StandardCharsets
import java.util.Locale
import javax.crypto.Mac
import javax.crypto.spec.SecretKeySpec
import kotlin.math.pow

enum class TotpAlgorithm(val hmacName: String) {
    SHA1("HmacSHA1"),
    SHA256("HmacSHA256"),
    SHA512("HmacSHA512"),
}

data class TotpConfig(
    val secretBase32: String,
    val issuer: String? = null,
    val label: String? = null,
    val algorithm: TotpAlgorithm = TotpAlgorithm.SHA1,
    val digits: Int = 6,
    val period: Int = 30,
)

data class TotpCode(
    val code: String,
    val formattedCode: String,
    val remainingSeconds: Int,
    val period: Int,
) {
    val progress: Float get() = remainingSeconds.toFloat() / period.toFloat().coerceAtLeast(1f)
}

/**
 * RFC 6238 / RFC 4226 TOTP generator supporting raw Base32 secrets and otpauth://totp/... URIs.
 */
object TotpEngine {

    private const val BASE32_ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567"

    fun base32Decode(input: String): ByteArray {
        val cleaned = input.uppercase(Locale.ROOT)
            .replace(Regex("[\\s-=]"), "")
        if (cleaned.isEmpty()) {
            throw IllegalArgumentException("Empty Base32 TOTP secret")
        }

        val out = ByteArray((cleaned.length * 5) / 8)
        var buffer = 0
        var bitsLeft = 0
        var outIdx = 0

        for (ch in cleaned) {
            val value = BASE32_ALPHABET.indexOf(ch)
            if (value < 0) {
                throw IllegalArgumentException("Invalid Base32 character: $ch")
            }
            buffer = (buffer shl 5) or value
            bitsLeft += 5
            if (bitsLeft >= 8) {
                bitsLeft -= 8
                out[outIdx++] = ((buffer ushr bitsLeft) and 0xFF).toByte()
            }
        }
        return if (outIdx == out.size) out else out.copyOf(outIdx)
    }

    fun parseTotpInput(rawInput: String): TotpConfig {
        val trimmed = rawInput.trim()
        if (trimmed.isEmpty()) {
            throw IllegalArgumentException("Empty TOTP secret")
        }

        if (trimmed.startsWith("otpauth://", ignoreCase = true)) {
            val uri = URI(trimmed)
            if (!uri.host.equals("totp", ignoreCase = true)) {
                throw IllegalArgumentException("Only otpauth://totp URIs are supported")
            }

            val rawPath = uri.rawPath?.removePrefix("/") ?: ""
            val decodedLabel = URLDecoder.decode(rawPath, StandardCharsets.UTF_8.name())
            var labelIssuer: String? = null
            var labelAccount = decodedLabel
            val colonIdx = decodedLabel.indexOf(':')
            if (colonIdx > 0) {
                labelIssuer = decodedLabel.substring(0, colonIdx).trim()
                labelAccount = decodedLabel.substring(colonIdx + 1).trim()
            }

            val params = parseQuery(uri.rawQuery)
            val secret = params["secret"]?.trim()
                ?: throw IllegalArgumentException("Missing secret parameter in otpauth URI")

            val algorithm = when (params["algorithm"]?.uppercase(Locale.ROOT)) {
                "SHA256", "SHA-256" -> TotpAlgorithm.SHA256
                "SHA512", "SHA-512" -> TotpAlgorithm.SHA512
                else -> TotpAlgorithm.SHA1
            }
            val digits = params["digits"]?.toIntOrNull()?.takeIf { it in 6..8 } ?: 6
            val period = params["period"]?.toIntOrNull()?.takeIf { it in 5..120 } ?: 30
            val issuer = params["issuer"]?.takeIf { it.isNotBlank() } ?: labelIssuer

            return TotpConfig(
                secretBase32 = secret,
                issuer = issuer,
                label = labelAccount.takeIf { it.isNotBlank() },
                algorithm = algorithm,
                digits = digits,
                period = period,
            )
        }

        // Plain Base32 secret (validate decodable)
        base32Decode(trimmed)
        return TotpConfig(secretBase32 = trimmed)
    }

    fun generateCode(
        config: TotpConfig,
        unixSeconds: Long = System.currentTimeMillis() / 1000L,
    ): TotpCode {
        val keyBytes = base32Decode(config.secretBase32)
        val counter = Math.floorDiv(unixSeconds, config.period.toLong())
        val counterBytes = ByteArray(8)
        var value = counter
        for (i in 7 downTo 0) {
            counterBytes[i] = (value and 0xFFL).toByte()
            value = value ushr 8
        }

        val mac = Mac.getInstance(config.algorithm.hmacName)
        mac.init(SecretKeySpec(keyBytes, config.algorithm.hmacName))
        val digest = mac.doFinal(counterBytes)

        val offset = digest[digest.size - 1].toInt() and 0x0F
        val binary =
            ((digest[offset].toInt() and 0x7F) shl 24) or
                ((digest[offset + 1].toInt() and 0xFF) shl 16) or
                ((digest[offset + 2].toInt() and 0xFF) shl 8) or
                (digest[offset + 3].toInt() and 0xFF)

        val modulo = 10.0.pow(config.digits).toInt()
        val code = (binary % modulo).toString().padStart(config.digits, '0')
        val formatted = when (code.length) {
            6 -> "${code.substring(0, 3)} ${code.substring(3)}"
            8 -> "${code.substring(0, 4)} ${code.substring(4)}"
            else -> code
        }

        val elapsed = Math.floorMod(unixSeconds, config.period.toLong()).toInt()
        val remaining = config.period - elapsed

        return TotpCode(
            code = code,
            formattedCode = formatted,
            remainingSeconds = remaining,
            period = config.period,
        )
    }

    fun generateFromInput(
        rawInput: String,
        unixSeconds: Long = System.currentTimeMillis() / 1000L,
    ): TotpCode? = runCatching {
        generateCode(parseTotpInput(rawInput), unixSeconds)
    }.getOrNull()

    private fun parseQuery(rawQuery: String?): Map<String, String> {
        if (rawQuery.isNullOrBlank()) return emptyMap()
        val result = mutableMapOf<String, String>()
        for (pair in rawQuery.split('&')) {
            val eq = pair.indexOf('=')
            if (eq > 0) {
                val key = URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8.name())
                    .lowercase(Locale.ROOT)
                val value = URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8.name())
                result[key] = value
            }
        }
        return result
    }
}
