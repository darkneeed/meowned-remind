package com.meowned.remind.core

import org.junit.Assert.*
import org.junit.Test

class SmsParserTest {

    @Test
    fun `mapSlotIndexToSlotNumber converts zero-based index to one-based slot`() {
        assertEquals(1, SmsParser.mapSlotIndexToSlotNumber(0))
        assertEquals(2, SmsParser.mapSlotIndexToSlotNumber(1))
        assertEquals(3, SmsParser.mapSlotIndexToSlotNumber(2))
    }

    @Test
    fun `mapSlotIndexToSlotNumber defaults to slot 1 for negative indices`() {
        assertEquals(1, SmsParser.mapSlotIndexToSlotNumber(-1))
        assertEquals(1, SmsParser.mapSlotIndexToSlotNumber(-10))
    }

    @Test
    fun `determineSimSlot uses slotExtra when provided`() {
        assertEquals(1, SmsParser.determineSimSlot(null, -1, 0))
        assertEquals(2, SmsParser.determineSimSlot(null, -1, 1))
    }

    @Test
    fun `determineSimSlot defaults safely to slot 1 without context or valid subId`() {
        assertEquals(1, SmsParser.determineSimSlot(null, -1, null))
        assertEquals(1, SmsParser.determineSimSlot(null, 0, null))
        assertEquals(1, SmsParser.determineSimSlot(null, 1, null))
    }
}
