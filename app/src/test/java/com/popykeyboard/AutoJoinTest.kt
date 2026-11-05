package com.popykeyboard

import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Test

class AutoJoinTest {
    @After fun reset() = BanglaPhonetic.configure(true, "")

    private fun off(list: String, typed: String, want: String) {
        BanglaPhonetic.configure(false, list)
        assertEquals(typed, want, BanglaPhonetic.convert(typed))
    }

    @Test fun offModeNeedsQ() {
        off("", "kt", "কত"); off("", "kqt", "ক্ত"); off("", "bhinqno", "ভিন্ন")
    }
    @Test fun phalaAndRephStayAutomatic() {
        off("", "kro", "ক্র"); off("", "kzo", "ক্য"); off("", "kwo", "ক্ব"); off("", "rko", "র্ক")
    }
    @Test fun listJoinsAndQBreaks() {
        off("nt st", "nto", "ন্ত"); off("nt st", "nqto", "নত"); off("st", "stro", "স্ত্র")
    }
    @Test fun tAfterVowelStillWorks() {
        off("", "jogoqt", "জগৎ"); off("", "jogng", "জগং")
    }
    @Test fun onModeUnchanged() {
        BanglaPhonetic.configure(true, "zz")
        assertEquals("ক্ত", BanglaPhonetic.convert("kto"))
    }
}
