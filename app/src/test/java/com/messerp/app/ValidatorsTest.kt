package com.messerp.app

import com.messerp.app.data.Validators
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ValidatorsTest {
    @Test fun normalizesPlainAndPrefixedNumbers() {
        assertEquals("01712345678", Validators.normalizePhone("017-1234 5678"))
        assertEquals("01712345678", Validators.normalizePhone("+8801712345678"))
    }

    @Test fun normalizesBengaliDigits() {
        assertEquals("01712345678", Validators.normalizePhone("০১৭১২৩৪৫৬৭৮"))
    }

    @Test fun validatesPhone() {
        assertTrue(Validators.isValidPhone("01712345678"))
        assertFalse(Validators.isValidPhone("01212345678"))
        assertFalse(Validators.isValidPhone("1712345678"))
        assertFalse(Validators.isValidPhone("0171234567"))
    }

    @Test fun validatesPasswordAndName() {
        assertTrue(Validators.isValidPassword("12345678"))
        assertFalse(Validators.isValidPassword("1234567"))
        assertTrue(Validators.isValidName("রাহিম"))
        assertFalse(Validators.isValidName(" a "))
    }

    @Test fun normalizesInviteCode() {
        assertEquals("ABC123", Validators.normalizeInvite("  abc123 "))
    }
}
