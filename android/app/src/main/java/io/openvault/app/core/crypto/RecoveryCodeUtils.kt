package io.openvault.app.core.crypto

import kotlin.math.min

/**
 * Shared Recovery Code parser & serializer matching OpenVault Chrome Extension:
 * - Preserves 6-digit codes, 6-digit pairs (`123456 789012`), Google 4+4 codes (`0642 0983`),
 *   hyphenated codes (`abcde-12345`), and >10 codes (10 default slots, auto-expands up to 100).
 * - Automatically migrates legacy 20x4 space-separated serialization (`0642 0983 7890 4684`).
 */
object RecoveryCodeUtils {
    const val RECOVERY_DEFAULT_SLOTS = 10
    const val RECOVERY_MAX_SLOTS = 100
    const val RECOVERY_MAX_LEN = 24

    private val FIELD_NAME_REGEX = Regex("(recovery\\s*code|backup\\s*code|mã\\s*khôi\\s*phục)", RegexOption.IGNORE_CASE)
    private val LINE_PREFIX_REGEX = Regex("^\\s*(?:\\d+[.):]\\s+|[-•*]\\s+)")
    private val SEGMENT_SPLIT_REGEX = Regex("(?:\\t|[,;|]+|\\s{2,}|\\s+(?=\\d+[.):]\\s+))")
    private val NON_ALLOWED_REGEX = Regex("[^A-Za-z0-9\\s-]")
    private val MULTI_SPACE_REGEX = Regex("\\s+")
    private val FOUR_CHAR_LINE_REGEX = Regex("^[A-Za-z0-9]{4}(?:\\s+[A-Za-z0-9]{4}){0,3}$")
    private val FOUR_CHAR_TOKEN_REGEX = Regex("^[A-Za-z0-9]{4}$")

    fun isRecoveryCodesField(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return FIELD_NAME_REGEX.containsMatchIn(name.trim())
    }

    fun normalizeRecoveryCode(raw: String?): String {
        if (raw.isNullOrEmpty()) return ""
        return raw
            .replace(NON_ALLOWED_REGEX, "")
            .replace(MULTI_SPACE_REGEX, " ")
            .trim()
            .take(RECOVERY_MAX_LEN)
    }

    private fun tryParseLegacyFourCharGrid(rawText: String): List<String>? {
        val nonEmptyLines = rawText.lineSequence().map { it.trim() }.filter { it.isNotEmpty() }.toList()
        if (nonEmptyLines.isEmpty()) return null
        if (!nonEmptyLines.all { FOUR_CHAR_LINE_REGEX.matches(it) }) return null

        val lineGroups = nonEmptyLines.map { it.split(MULTI_SPACE_REGEX) }
        if (!lineGroups.any { it.size >= 3 }) return null

        val flat = lineGroups.flatten()
        val paired = ArrayList<String>((flat.size + 1) / 2)
        var i = 0
        while (i < flat.size && paired.size < RECOVERY_MAX_SLOTS) {
            if (i + 1 < flat.size) {
                paired.add("${flat[i]} ${flat[i + 1]}")
            } else {
                paired.add(flat[i])
            }
            i += 2
        }
        return paired
    }

    fun parseRecoveryCodesPaste(rawText: String?): List<String> {
        if (rawText.isNullOrEmpty()) return emptyList()
        tryParseLegacyFourCharGrid(rawText)?.let { return it }

        val codes = ArrayList<String>(RECOVERY_DEFAULT_SLOTS)
        for (rawLine in rawText.lineSequence()) {
            for (segment in rawLine.split(SEGMENT_SPLIT_REGEX)) {
                val stripped = segment
                    .replaceFirst(LINE_PREFIX_REGEX, "")
                    .replace(NON_ALLOWED_REGEX, " ")
                    .replace(MULTI_SPACE_REGEX, " ")
                    .trim()
                if (stripped.isEmpty()) continue

                val parts = stripped.split(" ").filter { it.isNotEmpty() }
                when {
                    parts.size == 1 -> {
                        val clean = normalizeRecoveryCode(parts[0])
                        if (clean.isNotEmpty()) codes.add(clean)
                    }
                    parts.size == 2 -> {
                        val p1 = parts[0]
                        val p2 = parts[1]
                        val canPair = !p1.contains('-') && !p2.contains('-') &&
                            p1.length in 2..6 && p2.length in 2..6 &&
                            p1.length + 1 + p2.length <= RECOVERY_MAX_LEN
                        if (canPair) {
                            codes.add("$p1 $p2")
                        } else {
                            val c1 = normalizeRecoveryCode(p1)
                            val c2 = normalizeRecoveryCode(p2)
                            if (c1.isNotEmpty()) codes.add(c1)
                            if (c2.isNotEmpty() && codes.size < RECOVERY_MAX_SLOTS) codes.add(c2)
                        }
                    }
                    else -> {
                        val allFourChar = parts.all { FOUR_CHAR_TOKEN_REGEX.matches(it) }
                        val allLongCodes = parts.all { it.length >= 5 }
                        if (allFourChar && parts.size % 2 == 0) {
                            var i = 0
                            while (i < parts.size && codes.size < RECOVERY_MAX_SLOTS) {
                                codes.add("${parts[i]} ${parts[i + 1]}")
                                i += 2
                            }
                        } else if (allLongCodes) {
                            for (part in parts) {
                                val clean = normalizeRecoveryCode(part)
                                if (clean.isNotEmpty()) codes.add(clean)
                                if (codes.size >= RECOVERY_MAX_SLOTS) break
                            }
                        } else if (stripped.length <= RECOVERY_MAX_LEN) {
                            codes.add(stripped)
                        } else {
                            for (part in parts) {
                                val clean = normalizeRecoveryCode(part)
                                if (clean.isNotEmpty()) codes.add(clean)
                                if (codes.size >= RECOVERY_MAX_SLOTS) break
                            }
                        }
                    }
                }
                if (codes.size >= RECOVERY_MAX_SLOTS) break
            }
            if (codes.size >= RECOVERY_MAX_SLOTS) break
        }
        return codes.take(RECOVERY_MAX_SLOTS)
    }

    fun parseRecoveryCodesToSlots(
        value: String?,
        minSlots: Int = RECOVERY_DEFAULT_SLOTS,
    ): List<String> {
        val parsed = if (value.isNullOrBlank()) emptyList() else parseRecoveryCodesPaste(value)
        val rawLen = maxOf(minSlots, parsed.size)
        val slotCount = min(RECOVERY_MAX_SLOTS, if (rawLen % 2 == 0) rawLen else rawLen + 1)
        return MutableList(slotCount) { idx ->
            if (idx < parsed.size) parsed[idx] else ""
        }
    }

    fun applyRecoveryCodesPaste(
        currentSlots: List<String>,
        targetIndex: Int,
        rawText: String,
        forceFromStart: Boolean = false,
    ): Pair<List<String>, Int> {
        val baseCount = maxOf(RECOVERY_DEFAULT_SLOTS, currentSlots.size)
        val existing = List(baseCount) { idx -> normalizeRecoveryCode(currentSlots.getOrElse(idx) { "" }) }
        val chunks = parseRecoveryCodesPaste(rawText)
        if (chunks.isEmpty()) return existing to targetIndex.coerceIn(0, existing.size - 1)

        val boundedTarget = targetIndex.coerceIn(0, existing.size - 1)
        val earlierEmpty = (0 until boundedTarget).all { existing[it].isEmpty() }
        val startIdx = if (forceFromStart || (chunks.size > 1 && (boundedTarget == 0 || earlierEmpty))) {
            0
        } else {
            boundedTarget
        }

        val neededLen = min(RECOVERY_MAX_SLOTS, maxOf(baseCount, startIdx + chunks.size))
        val finalLen = if (neededLen % 2 == 0) neededLen else min(RECOVERY_MAX_SLOTS, neededLen + 1)
        val next = MutableList(finalLen) { idx -> existing.getOrElse(idx) { "" } }

        var cursor = startIdx
        for (chunk in chunks) {
            if (cursor >= RECOVERY_MAX_SLOTS) break
            next[cursor] = chunk
            cursor++
        }

        val lastFilled = maxOf(startIdx, cursor - 1)
        val nextFocusIndex = if (cursor < next.size) cursor else min(lastFilled, next.size - 1)
        return next to nextFocusIndex
    }

    fun serializeRecoveryCodesSlots(slots: List<String>): String {
        return slots
            .map { normalizeRecoveryCode(it) }
            .filter { it.isNotEmpty() }
            .joinToString("\n")
    }

    fun countFilledRecoveryCodes(slots: List<String>): Int =
        slots.count { it.isNotBlank() }
}
