package com.azizov.habitsense.domain.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DateStringUtilsTest {
    @Test
    fun datesBetweenInclusive_returnsOrderedDates() {
        val dates = DateStringUtils.datesBetweenInclusive("2026-07-01", "2026-07-03")
        assertEquals(listOf("2026-07-01", "2026-07-02", "2026-07-03"), dates)
    }

    @Test
    fun minusDays_and_plusDays_areConsistent() {
        val original = "2026-07-10"
        val shifted = DateStringUtils.minusDays(original, 3)
        assertEquals("2026-07-07", shifted)
        assertEquals(original, DateStringUtils.plusDays(shifted, 3))
    }

    @Test
    fun isValid_rejectsInvalidFormat() {
        assertTrue(DateStringUtils.isValid("2026-07-13"))
        assertFalse(DateStringUtils.isValid("13-07-2026"))
    }
}
