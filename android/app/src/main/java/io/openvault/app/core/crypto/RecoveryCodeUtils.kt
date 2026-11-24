package io.openvault.app.core.crypto

import kotlin.math.min

/**
 * Shared 20-slot x 4-char Recovery Code parser & serializer matching OpenVault Chrome Extension.
 */
object RecoveryCodeUtils {
    const val RECOVERY_SLOT_COUNT = 20
    const val RECOVERY_MAX_LEN = 4

    private val FIELD_NAME_REGEX = Regex("(recovery\\s*code|backup\\s*code|mã\\s*khôi\\s*phục)", RegexOption.IGNORE_CASE)
    private val LINE_PREFIX_REGEX = Regex("^\\s*(?:\\d+[.):]\\s+|[-•*]\\s+)")
    private val TOKEN_SPLIT_REGEX = Regex("[\\s,;|\\-_/\\\\]+")
    private val NON_ALNUM_REGEX = Regex("[^A-Za-z0-9]")

    fun isRecoveryCodesField(name: String?): Boolean {
        if (name.isNullOrBlank()) return false
        return FIELD_NAME_REGEX.containsMatchIn(name.trim())
    }

    /**
     * Parses pasted clipboard text (numbered lists, newlines, spaces, dashes, 8-digit Google codes, etc.)
     * into up to 20 clean <=4-character chunks while preserving leading zeros.
     */
    fun parseRecoveryCodesPaste(rawText: String?): List<String> {
        if (rawText.isNullOrEmpty()) return emptyList()
        val chunks = ArrayList<String>(RECOVERY_SLOT_COUNT)

        for (rawLine in rawText.lineSequence()) {
            val withoutPrefix = rawLine.replaceFirst(LINE_PREFIX_REGEX, "")
            for (rawToken in withoutPrefix.split(TOKEN_SPLIT_REGEX)) {
                val clean = rawToken.replace(NON_ALNUM_REGEX, "")
                if (clean.isEmpty()) continue
                if (clean.length <= RECOVERY_MAX_LEN) {
                    chunks.add(clean)
                } else {
                    var i = 0
                    while (i < clean.length && chunks.size < RECOVERY_SLOT_COUNT) {
                        chunks.add(clean.substring(i, min(i + RECOVERY_MAX_LEN, clean.length)))
                        i += RECOVERY_MAX_LEN
                    }
                }
                if (chunks.size >= RECOVERY_SLOT_COUNT) break
            }
            if (chunks.size >= RECOVERY_SLOT_COUNT) break
        }
        return chunks.take(RECOVERY_SLOT_COUNT)
    }

    fun parseRecoveryCodesToSlots(value: String?): List<String> {
        val slots = MutableList(RECOVERY_SLOT_COUNT) { "" }
        if (value.isNullOrBlank()) return slots
        val tokens = parseRecoveryCodesPaste(value)
        for (i in 0 until min(tokens.size, RECOVERY_SLOT_COUNT)) {
            slots[i] = tokens[i]
        }
        return slots
    }

    fun applyRecoveryCodesPaste(
        currentSlots: List<String>,
        targetIndex: Int,
        rawText: String,
        forceFromStart: Boolean = false,
    ): Pair<List<String>, Int> {
        val next = MutableList(RECOVERY_SLOT_COUNT) { idx ->
            currentSlots.getOrElse(idx) { "" }.take(RECOVERY_MAX_LEN)
        }
        val chunks = parseRecoveryCodesPaste(rawText)
        if (chunks.isEmpty()) return next to targetIndex

        val boundedTarget = targetIndex.coerceIn(0, RECOVERY_SLOT_COUNT - 1)
        val earlierEmpty = (0 until boundedTarget).all { next[it].isEmpty() }
        val startIdx = if (forceFromStart || (chunks.size > 1 && (boundedTarget == 0 || earlierEmpty))) {
            0
        } else {
            boundedTarget
        }

        var cursor = startIdx
        for (chunk in chunks) {
            if (cursor >= RECOVERY_SLOT_COUNT) break
            next[cursor] = chunk.take(RECOVERY_MAX_LEN)
            cursor++
        }

        val lastFilled = maxOf(startIdx, cursor - 1)
        val nextFocusIndex = if (cursor < RECOVERY_SLOT_COUNT && next[lastFilled].length >= RECOVERY_MAX_LEN) {
            cursor
        } else {
            min(lastFilled, RECOVERY_SLOT_COUNT - 1)
        }
        return next to nextFocusIndex
    }

    fun serializeRecoveryCodesSlots(slots: List<String>): String {
        val normalized = List(RECOVERY_SLOT_COUNT) { idx ->
            slots.getOrElse(idx) { "" }.replace(NON_ALNUM_REGEX, "").take(RECOVERY_MAX_LEN)
        }
        val lastNonEmpty = normalized.indexOfLast { it.isNotEmpty() }
        if (lastNonEmpty < 0) return ""

        val rows = mutableListOf<String>()
        var rowStart = 0
        while (rowStart <= lastNonEmpty) {
            val end = min(lastNonEmpty + 1, rowStart + 4)
            rows.add(normalized.subList(rowStart, end).filter { it.isNotEmpty() }.joinToString(" "))
            rowStart += 4
        }
        return rows.joinToString("\n")
    }

    fun countFilledRecoveryCodes(slots: List<String>): Int =
        slots.count { it.isNotBlank() }
}
