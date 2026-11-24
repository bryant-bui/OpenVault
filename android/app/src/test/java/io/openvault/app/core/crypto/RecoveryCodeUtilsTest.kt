package io.openvault.app.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class RecoveryCodeUtilsTest {

    @Test
    fun detectsEnglishAndVietnameseFieldNames() {
        assertTrue(RecoveryCodeUtils.isRecoveryCodesField("Recovery Codes"))
        assertTrue(RecoveryCodeUtils.isRecoveryCodesField("recovery code"))
        assertTrue(RecoveryCodeUtils.isRecoveryCodesField("Backup Codes"))
        assertTrue(RecoveryCodeUtils.isRecoveryCodesField("Mã Khôi Phục"))
        assertFalse(RecoveryCodeUtils.isRecoveryCodesField("Username"))
        assertFalse(RecoveryCodeUtils.isRecoveryCodesField(null))
        assertFalse(RecoveryCodeUtils.isRecoveryCodesField(""))
    }

    @Test
    fun preserves6DigitCodesPairsGoogle4Plus4AndGitHubHyphenated() {
        val raw = """
            1. 0421 8899
            2) 123456
            3: abcde-12345
            • 123456 789012
            - 000789
        """.trimIndent()
        val parsed = RecoveryCodeUtils.parseRecoveryCodesPaste(raw)
        assertEquals(
            listOf("0421 8899", "123456", "abcde-12345", "123456 789012", "000789"),
            parsed,
        )
    }

    @Test
    fun migratesLegacyFourByFourGridIntoEightDigitPairs() {
        val legacy = """
            0642 0983 7890 4684
            9027 0949 6644 9527
            3650 1793 1091 1009
            7446 0168 5156 5150
            3191 9860 3847 8943
        """.trimIndent()
        val parsed = RecoveryCodeUtils.parseRecoveryCodesPaste(legacy)
        assertEquals(10, parsed.size)
        assertEquals("0642 0983", parsed.first())
        assertEquals("3847 8943", parsed.last())
    }

    @Test
    fun supportsMoreThan10CodesAndAutoExpandsSlots() {
        val raw = (1..16).joinToString("\n") { "%06d".format(it) }
        val initial = MutableList(RecoveryCodeUtils.RECOVERY_DEFAULT_SLOTS) { "" }
        val (slots, nextFocus) = RecoveryCodeUtils.applyRecoveryCodesPaste(
            currentSlots = initial,
            targetIndex = 2,
            rawText = raw,
        )
        assertEquals(16, slots.size)
        assertEquals("000001", slots[0])
        assertEquals("000016", slots[15])
        assertEquals(15, nextFocus)
        assertEquals(16, RecoveryCodeUtils.countFilledRecoveryCodes(slots))
    }

    @Test
    fun applyPasteStartsFromTargetSlotWhenEarlierSlotsPopulated() {
        val initial = MutableList(RecoveryCodeUtils.RECOVERY_DEFAULT_SLOTS) { "" }.also {
            it[0] = "111111"
        }
        val (slots, nextFocus) = RecoveryCodeUtils.applyRecoveryCodesPaste(
            currentSlots = initial,
            targetIndex = 4,
            rawText = "123456\n654321",
        )
        assertEquals("111111", slots[0])
        assertEquals("", slots[1])
        assertEquals("123456", slots[4])
        assertEquals("654321", slots[5])
        assertEquals(6, nextFocus)
    }

    @Test
    fun serializeAndParseSlotsRoundTripPreservesFullCodesAndExpandedSize() {
        val original = MutableList(14) { "" }
        original[0] = "000111"
        original[1] = "0642 0983"
        original[11] = "999888"

        val serialized = RecoveryCodeUtils.serializeRecoveryCodesSlots(original)
        assertEquals("000111\n0642 0983\n999888", serialized)

        val reparsed = RecoveryCodeUtils.parseRecoveryCodesToSlots(serialized)
        assertEquals(10, reparsed.size)
        assertEquals("000111", reparsed[0])
        assertEquals("0642 0983", reparsed[1])
        assertEquals("999888", reparsed[2])
        assertEquals("", reparsed[3])
    }
}
