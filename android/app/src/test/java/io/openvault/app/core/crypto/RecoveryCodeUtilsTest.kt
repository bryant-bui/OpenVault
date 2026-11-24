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
    fun parsesNumberedListsPreservesLeadingZerosAndSplits8DigitGoogleCodes() {
        val raw = """
            1. 0123-4567
            2) 0042 9981
            3: ABCD EFGH
            • a1b2 c3d4
            - 11112222
        """.trimIndent()
        val parsed = RecoveryCodeUtils.parseRecoveryCodesPaste(raw)
        assertEquals(
            listOf("0123", "4567", "0042", "9981", "ABCD", "EFGH", "a1b2", "c3d4", "1111", "2222"),
            parsed,
        )
    }

    @Test
    fun truncatesToMaximum20Slots() {
        val raw = (1..25).joinToString(" ") { "%04d".format(it) }
        val parsed = RecoveryCodeUtils.parseRecoveryCodesPaste(raw)
        assertEquals(20, parsed.size)
        assertEquals("0001", parsed.first())
        assertEquals("0020", parsed.last())
    }

    @Test
    fun applyPasteFillsFromStartWhenEarlierSlotsAreEmpty() {
        val initial = MutableList(RecoveryCodeUtils.RECOVERY_SLOT_COUNT) { "" }
        val (slots, nextFocus) = RecoveryCodeUtils.applyRecoveryCodesPaste(
            currentSlots = initial,
            targetIndex = 3,
            rawText = "0011-2233-4455-6677",
        )
        assertEquals("0011", slots[0])
        assertEquals("2233", slots[1])
        assertEquals("4455", slots[2])
        assertEquals("6677", slots[3])
        assertEquals(4, nextFocus)
        assertEquals(4, RecoveryCodeUtils.countFilledRecoveryCodes(slots))
    }

    @Test
    fun applyPasteStartsFromTargetSlotWhenEarlierSlotsPopulated() {
        val initial = MutableList(RecoveryCodeUtils.RECOVERY_SLOT_COUNT) { "" }.also {
            it[0] = "1111"
        }
        val (slots, nextFocus) = RecoveryCodeUtils.applyRecoveryCodesPaste(
            currentSlots = initial,
            targetIndex = 4,
            rawText = "ABCD EFGH",
        )
        assertEquals("1111", slots[0])
        assertEquals("", slots[1])
        assertEquals("ABCD", slots[4])
        assertEquals("EFGH", slots[5])
        assertEquals(6, nextFocus)
    }

    @Test
    fun serializeAndParseSlotsRoundTripPreservesOrderAndEmptySlots() {
        val original = MutableList(RecoveryCodeUtils.RECOVERY_SLOT_COUNT) { "" }
        original[0] = "0001"
        original[1] = "AB12"
        original[2] = "CD34"
        original[3] = "EF56"
        original[4] = "9876"

        val serialized = RecoveryCodeUtils.serializeRecoveryCodesSlots(original)
        assertEquals("0001 AB12 CD34 EF56\n9876", serialized)

        val reparsed = RecoveryCodeUtils.parseRecoveryCodesToSlots(serialized)
        assertEquals(20, reparsed.size)
        assertEquals("0001", reparsed[0])
        assertEquals("AB12", reparsed[1])
        assertEquals("CD34", reparsed[2])
        assertEquals("EF56", reparsed[3])
        assertEquals("9876", reparsed[4])
        assertEquals("", reparsed[5])
    }
}
