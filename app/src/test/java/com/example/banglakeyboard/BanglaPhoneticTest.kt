package com.example.banglakeyboard

import java.text.Normalizer
import org.junit.Assert.assertEquals
import org.junit.Test

class BanglaPhoneticTest {
    private fun nfc(s: String) = Normalizer.normalize(s, Normalizer.Form.NFC)

    @Test
    fun guideExamples() {
        val stream = javaClass.classLoader!!.getResourceAsStream("typing_cases.tsv")!!
        val failures = ArrayList<String>()
        var n = 0
        stream.bufferedReader(Charsets.UTF_8).forEachLine { line ->
            if (line.isBlank()) return@forEachLine
            val (typed, expected) = line.split('\t')
            n++
            val got = BanglaPhonetic.convert(typed)
            if (nfc(got) != nfc(expected)) failures.add("$typed -> $got (expected $expected)")
        }
        assertEquals("$n cases, failures:\n" + failures.joinToString("\n"), 0, failures.size)
    }
}
