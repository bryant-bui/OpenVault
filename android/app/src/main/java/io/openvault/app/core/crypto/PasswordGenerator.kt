package io.openvault.app.core.crypto

import java.security.SecureRandom
import java.util.Locale
import kotlin.math.log2
import kotlin.math.roundToInt

data class PasswordOptions(
    val length: Int = 20,
    val lowercase: Boolean = true,
    val uppercase: Boolean = true,
    val digits: Boolean = true,
    val symbols: Boolean = true,
    val avoidAmbiguous: Boolean = false,
)

data class PassphraseOptions(
    val wordCount: Int = 4,
    val separator: String = "-",
    val capitalize: Boolean = true,
    val includeNumber: Boolean = true,
)

enum class StrengthLevel(val score: Int, val labelVi: String, val labelEn: String) {
    WEAK(0, "Yếu", "Weak"),
    FAIR(1, "Trung bình", "Fair"),
    GOOD(2, "Khá", "Good"),
    STRONG(3, "Mạnh", "Very Good"),
    EXCELLENT(4, "Rất mạnh", "Fantastic");

    fun label(isVi: Boolean): String = if (isVi) labelVi else labelEn
}

data class PasswordStrength(
    val entropyBits: Int,
    val level: StrengthLevel,
)

object PasswordGenerator {

    private val random = SecureRandom()

    private const val LOWER = "abcdefghijklmnopqrstuvwxyz"
    private const val LOWER_SAFE = "abcdefghijkmnopqrstuvwxyz"
    private const val UPPER = "ABCDEFGHIJKLMNOPQRSTUVWXYZ"
    private const val UPPER_SAFE = "ABCDEFGHJKLMNPQRSTUVWXYZ"
    private const val DIGITS = "0123456789"
    private const val DIGITS_SAFE = "23456789"
    private const val SYMBOLS = "!@#\$%^&*()-_=+[]{};:,.<>?"

    private val PASSPHRASE_WORDS = listOf(
        "anchor", "apricot", "atlas", "aurora", "bamboo", "beacon", "berry", "bison",
        "breeze", "bridge", "cactus", "canyon", "cedar", "cipher", "cloud", "clover",
        "cobalt", "comet", "compass", "coral", "crane", "crystal", "cypress", "delta",
        "dolphin", "eclipse", "ember", "emerald", "falcon", "fern", "fjord", "flame",
        "forest", "frost", "galaxy", "garden", "glacier", "granite", "harbor", "hazel",
        "horizon", "indigo", "island", "ivory", "jasper", "jungle", "kayak", "kestrel",
        "lagoon", "lantern", "laurel", "lemon", "lilac", "lotus", "lunar", "magnet",
        "mango", "maple", "marble", "meadow", "meteor", "mint", "monarch", "moss",
        "nebula", "nectar", "nimbus", "north", "oak", "oasis", "ocean", "olive",
        "onyx", "opal", "orbit", "orchid", "osprey", "otter", "palm", "panda",
        "pebble", "pelican", "pepper", "phoenix", "pine", "planet", "plaza", "plum",
        "polar", "prism", "quartz", "quiver", "radish", "raven", "reef", "ridge",
        "river", "robin", "rocket", "ruby", "saffron", "sage", "sailor", "salmon",
        "sapphire", "saturn", "scarlet", "sequoia", "shadow", "silver", "solstice", "sparrow",
        "spruce", "starlight", "summit", "sunset", "timber", "topaz", "tulip", "tundra",
        "valley", "velvet", "verbena", "violet", "voyage", "walnut", "willow", "zephyr",
    )

    fun generatePassword(options: PasswordOptions = PasswordOptions()): String {
        val pools = mutableListOf<String>()
        if (options.lowercase) pools += if (options.avoidAmbiguous) LOWER_SAFE else LOWER
        if (options.uppercase) pools += if (options.avoidAmbiguous) UPPER_SAFE else UPPER
        if (options.digits) pools += if (options.avoidAmbiguous) DIGITS_SAFE else DIGITS
        if (options.symbols) pools += SYMBOLS

        require(pools.isNotEmpty()) { "Cần bật ít nhất một nhóm ký tự" }
        val length = options.length.coerceIn(pools.size, 128)
        val combined = pools.joinToString("")

        val chars = ArrayList<Char>(length)
        for (pool in pools) {
            chars += pool[random.nextInt(pool.length)]
        }
        while (chars.size < length) {
            chars += combined[random.nextInt(combined.length)]
        }

        // Fisher-Yates shuffle with CSPRNG
        for (i in chars.lastIndex downTo 1) {
            val j = random.nextInt(i + 1)
            val tmp = chars[i]
            chars[i] = chars[j]
            chars[j] = tmp
        }
        return String(chars.toCharArray())
    }

    fun generatePassphrase(options: PassphraseOptions = PassphraseOptions()): String {
        val count = options.wordCount.coerceIn(3, 10)
        val words = MutableList(count) {
            var word = PASSPHRASE_WORDS[random.nextInt(PASSPHRASE_WORDS.size)]
            if (options.capitalize) {
                word = word.replaceFirstChar { it.titlecase(Locale.ROOT) }
            }
            word
        }
        if (options.includeNumber) {
            val idx = random.nextInt(words.size)
            words[idx] = "${words[idx]}${random.nextInt(90) + 10}"
        }
        return words.joinToString(options.separator)
    }

    fun estimateStrength(password: String): PasswordStrength {
        if (password.isEmpty()) return PasswordStrength(0, StrengthLevel.WEAK)
        var charsetSize = 0
        if (password.any { it in 'a'..'z' }) charsetSize += 26
        if (password.any { it in 'A'..'Z' }) charsetSize += 26
        if (password.any { it in '0'..'9' }) charsetSize += 10
        if (password.any { !it.isLetterOrDigit() }) charsetSize += 32

        val entropy = (password.length * log2(charsetSize.coerceAtLeast(1).toDouble())).roundToInt()
        val level = when {
            entropy < 40 -> StrengthLevel.WEAK
            entropy < 60 -> StrengthLevel.FAIR
            entropy < 80 -> StrengthLevel.GOOD
            entropy < 100 -> StrengthLevel.STRONG
            else -> StrengthLevel.EXCELLENT
        }
        return PasswordStrength(entropyBits = entropy, level = level)
    }
}
