package com.example.banglakeyboard

/**
 * "নতুন লেআউট" ফোনেটিক টাইপিং ইঞ্জিন: ইংরেজি (ল্যাটিন) বর্ণ থেকে বাংলা।
 *
 * ইঞ্জিনটি পুরোপুরি Android-মুক্ত (pure Kotlin), তাই সহজে ইউনিট টেস্ট করা যায়।
 * কিবোর্ড প্রতিটি কী-চাপে বর্তমান শব্দের পুরো ল্যাটিন বাফার [convert] এ পাঠায়,
 * ফলাফলটি composing text হিসেবে দেখানো হয়।
 */
object BanglaPhonetic {

    private const val HASANTA = "্"
    private const val ZWNJ = "‌"
    private const val NUKTA = "়"

    // ----- ব্যঞ্জন -----
    private val consonants: Map<String, String> = mapOf(
        "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ", "ng" to "ঙ",
        "c" to "চ", "ch" to "ছ", "j" to "জ", "jh" to "ঝ", "qny" to "ঞ",
        "tt" to "ট", "tth" to "ঠ", "dd" to "ড", "ddh" to "ঢ", "nn" to "ণ",
        "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
        "p" to "প", "f" to "ফ", "ph" to "ফ", "b" to "ব", "bh" to "ভ", "v" to "ভ",
        "m" to "ম", "y" to "য$NUKTA", "z" to "য", "r" to "র", "l" to "ল",
        "s" to "স", "ss" to "শ", "sss" to "ষ", "h" to "হ",
        "x" to "ক${HASANTA}ষ",
        "rr" to "ড$NUKTA", "rrh" to "ঢ$NUKTA",
        "w" to "ব",
    )

    // ----- স্বর: টোকেন -> (স্বতন্ত্র রূপ, কার) -----
    private val vowels: Map<String, Pair<String, String>> = mapOf(
        "o" to Pair("অ", ""),
        "a" to Pair("আ", "া"),
        "i" to Pair("ই", "ি"),
        "ii" to Pair("ঈ", "ী"),
        "u" to Pair("উ", "ু"),
        "uu" to Pair("ঊ", "ূ"),
        "rri" to Pair("ঋ", "ৃ"),
        "e" to Pair("এ", "ে"),
        "oi" to Pair("ঐ", "ৈ"),
        "oo" to Pair("ও", "ো"),
        "ou" to Pair("ঔ", "ৌ"),
    )

    private val digits = "০১২৩৪৫৬৭৮৯"

    /** '0'..'9' -> বাংলা অঙ্ক, অন্যথায় null */
    fun digit(c: Char): String? = if (c in '0'..'9') digits[c - '0'].toString() else null

    private fun isLetter(c: Char) = c in 'a'..'z'

    /** ল্যাটিন লেখা → বাংলা। */
    fun convert(input: String): String {
        // শুধু A–Z ছোট হাতের করা হয় (দৈর্ঘ্য অপরিবর্তিত থাকে)
        val src = buildString(input.length) {
            for (ch in input) append(if (ch in 'A'..'Z') (ch + 32) else ch)
        }
        val out = StringBuilder()
        // consAfter = সর্বশেষ আউটপুট একটি ব্যঞ্জন (কার/যুক্তাক্ষর নেওয়ার জন্য প্রস্তুত)
        var consAfter = false
        var wordStart = true
        var i = 0

        while (i < src.length) {
            val c = src[i]

            // ঃ  ( :: )
            if (c == ':' && src.startsWith("::", i)) {
                out.append("ঃ"); consAfter = false; wordStart = false; i += 2; continue
            }
            // হসন্ত + ZWNJ  ( ,, )
            if (c == ',' && src.startsWith(",,", i)) {
                out.append(HASANTA).append(ZWNJ); consAfter = false; wordStart = false; i += 2; continue
            }

            if (c == 'q' && !src.startsWith("qny", i)) {
                // qr = জোড়া ছাড়া র (ধর্ম = dhqrm)
                if (src.startsWith("qr", i)) {
                    out.append("র"); consAfter = true; wordStart = false; i += 2; continue
                }
                if (consAfter) {
                    // ব্যঞ্জনের পরে q = ভাঙার চিহ্ন, কিছু আসে না, যুক্তাক্ষর তবুও হয়
                    i += 1; continue
                }
                // স্বরের পরে / শুরুতে: বিশেষ চিহ্ন
                when {
                    src.startsWith("qng", i) -> { out.append("ং"); wordStart = false; i += 3; continue }
                    src.startsWith("qt", i) -> { out.append("ৎ"); wordStart = false; i += 2; continue }
                    src.startsWith("qq", i) -> { out.append("ঁ"); wordStart = false; i += 2; continue }
                    src.startsWith("qk", i) -> { out.append("৳"); wordStart = false; i += 2; continue }
                }
                // স্বর ভাঙতে q (boqi = বই)
                if (!wordStart && i + 1 < src.length && isLetter(src[i + 1])) {
                    i += 1; continue
                }
                // শুধু q: q-ই থাকে
                out.append('q'); consAfter = false; wordStart = false; i += 1; continue
            }

            // সবচেয়ে বড় টোকেন আগে (৩ → ২ → ১)
            var matched = false
            var len = minOf(3, src.length - i)
            while (len >= 1) {
                val tok = src.substring(i, i + len)
                val cons = consonants[tok]
                if (cons != null) {
                    if (consAfter) out.append(HASANTA)
                    out.append(cons)
                    consAfter = true; wordStart = false
                    i += len; matched = true; break
                }
                val v = vowels[tok]
                if (v != null) {
                    out.append(if (consAfter) v.second else v.first)
                    consAfter = false; wordStart = false
                    i += len; matched = true; break
                }
                len--
            }
            if (matched) continue

            // অঙ্ক, দাঁড়ি, অন্য সব অক্ষর
            val d = digit(c)
            when {
                d != null -> out.append(d)
                c == '.' -> out.append('।')
                else -> out.append(input[i])
            }
            consAfter = false; wordStart = true; i += 1
        }
        return out.toString()
    }
}
