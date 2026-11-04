package com.popykeyboard

/**
 * "নতুন লেআউট" ফোনেটিক টাইপিং ইঞ্জিন: ইংরেজি (ল্যাটিন) বর্ণ থেকে বাংলা।
 *
 * ইঞ্জিনটি পুরোপুরি Android-মুক্ত (pure Kotlin), তাই সহজে ইউনিট টেস্ট করা যায়।
 * কিবোর্ড প্রতিটি কী-চাপে বর্তমান শব্দের পুরো ল্যাটিন বাফার [convert] এ পাঠায়,
 * ফলাফলটি composing text হিসেবে দেখানো হয়।
 */
object BanglaPhonetic {

    private const val HASANTA = "্"
    private const val ZWNJ = "\u200C"
    private const val ZWJ = "\u200D"
    private const val NUKTA = "়"

    // ----- ব্যঞ্জন -----
    private val consonants: Map<String, String> = mapOf(
        "k" to "ক", "kh" to "খ", "g" to "গ", "gh" to "ঘ", "nng" to "ঙ",
        "c" to "চ", "ch" to "ছ", "j" to "জ", "jh" to "ঝ", "nnn" to "ঞ",
        "tt" to "ট", "tth" to "ঠ", "dd" to "ড", "ddh" to "ঢ", "nn" to "ণ",
        "t" to "ত", "th" to "থ", "d" to "দ", "dh" to "ধ", "n" to "ন",
        "p" to "প", "f" to "ফ", "ph" to "ফ", "b" to "ব", "bh" to "ভ", "v" to "ভ",
        "m" to "ম", "y" to "য$NUKTA", "z" to "য", "r" to "র", "l" to "ল",
        "s" to "স", "ss" to "শ", "sss" to "ষ", "h" to "হ",
        "x" to "ক${HASANTA}ষ",
        "rr" to "ড$NUKTA", "rrh" to "ঢ$NUKTA",
    )

    // ----- স্বর: টোকেন -> (স্বতন্ত্র রূপ, কার) -----
    private val vowels: Map<String, Pair<String, String>> = mapOf(
        "o" to Pair("অ", ""),
        "a" to Pair("আ", "া"),
        "i" to Pair("ই", "ি"),
        "ii" to Pair("ঈ", "ী"),
        "u" to Pair("উ", "ু"),
        "uu" to Pair("ঊ", "ূ"),
        "rrr" to Pair("ঋ", "ৃ"),
        "e" to Pair("এ", "ে"),
        "oi" to Pair("ঐ", "ৈ"),
        "oo" to Pair("ও", "ো"),
        "ou" to Pair("ঔ", "ৌ"),
    )

    private val digits = "০১২৩৪৫৬৭৮৯"

    /** '0'..'9' -> বাংলা অঙ্ক, অন্যথায় null */
    fun digit(c: Char): String? = if (c in '0'..'9') digits[c - '0'].toString() else null

    private fun isLetter(c: Char) = c in 'a'..'z'

    /** টাইপ চলাকালীন দেখানোর ফল। [markerIndex] >= 0 হলে ওই অবস্থানের অক্ষরটি রঙিন মার্কার ('o' লাল, 'q' সবুজ)। */
    class Preview(val text: String, val markerIndex: Int, val marker: Char)

    /** ল্যাটিন লেখা → বাংলা (চূড়ান্ত, মার্কার ছাড়া)। */
    fun convert(input: String): String = run(input, false).text

    /**
     * টাইপ চলাকালীন composing টেক্সট। শেষ কী যদি যুক্তাক্ষর-বাধা 'o' (ব্যঞ্জনের পর) বা
     * ভাঙার চিহ্ন 'q' হয়, তবে সেটি দৃশ্যমান মার্কার হিসেবে শেষে দেখায়; পরের অক্ষর এলে সরে যায়।
     */
    fun preview(input: String): Preview = run(input, true)

    private fun endsWithPlainRa(out: StringBuilder): Boolean =
        out.isNotEmpty() && out[out.length - 1] == 'র' &&
            (out.length < 2 || out[out.length - 2] != HASANTA[0])

    private fun run(input: String, preview: Boolean): Preview {
        // শুধু A–Z ছোট হাতের করা হয় (দৈর্ঘ্য অপরিবর্তিত থাকে)
        val src = buildString(input.length) {
            for (ch in input) append(if (ch in 'A'..'Z') (ch + 32) else ch)
        }
        val out = StringBuilder()
        // consAfter = সর্বশেষ আউটপুট একটি ব্যঞ্জন (কার/যুক্তাক্ষর নেওয়ার জন্য প্রস্তুত)
        var consAfter = false
        var wordStart = true
        // শব্দের শুরুর 'র' — এর পর z এলে রেফ নয়, র‍্য (য-ফলা) হয়
        var raInitial = false
        // স্বরের পরে q দিয়ে ভাঙা হয়েছে — পরের z এ য-ফলা হবে না (অযথা = oqzotha)
        var qBreak = false
        // শেষ টোকেনটি মার্কার-যোগ্য হলে: 'o' / 'q' (আউটপুটে অদৃশ্য), 'Q' (আউটপুটে আক্ষরিক q আছে)
        var pending = ' '
        var i = 0

        while (i < src.length) {
            val c = src[i]
            val wasRaInitial = raInitial
            raInitial = false
            val wasQBreak = qBreak
            qBreak = false
            pending = ' '

            // হসন্ত + ZWNJ  ( ,, )
            if (c == ',' && src.startsWith(",,", i)) {
                out.append(HASANTA).append(ZWNJ); consAfter = false; wordStart = false; i += 2; continue
            }

            // ঞ = nnn, ঙ = nng (ব্যঞ্জন-তালিকায়)। q এখন সবসময় বিশেষ/ভাঙার চিহ্ন
            // (ন্ন = nqn, ভিন্ন = bhinqno, ণ্ন = nnqn)
            if (c == 'q') {
                // qw = ঃ (বিসর্গ)
                if (src.startsWith("qw", i)) {
                    out.append("ঃ"); consAfter = false; wordStart = false; i += 2; continue
                }
                // qr = জোড়া ছাড়া র (ধর্ম = dhqrm)
                if (src.startsWith("qr", i)) {
                    out.append("র"); consAfter = true; wordStart = false; i += 2; continue
                }
                if (consAfter) {
                    // rqz = র‍্য (র-এর সাথে য-ফলা, রেফ নয়)
                    if (src.startsWith("qz", i) && endsWithPlainRa(out)) {
                        out.append(ZWJ).append(HASANTA).append("য")
                        consAfter = true; wordStart = false; i += 2; continue
                    }
                    // ব্যঞ্জনের পরে q = ভাঙার চিহ্ন, কিছু আসে না, যুক্তাক্ষর তবুও হয়
                    i += 1
                    if (i == src.length) pending = 'q'
                    continue
                }
                // স্বরের পরে / শুরুতে: বিশেষ চিহ্ন
                when {
                    src.startsWith("qt", i) -> { out.append("ৎ"); wordStart = false; i += 2; continue }
                    src.startsWith("qq", i) -> { out.append("ঁ"); wordStart = false; i += 2; continue }
                    src.startsWith("qk", i) -> { out.append("৳"); wordStart = false; i += 2; continue }
                }
                // স্বর ভাঙতে q (boqi = বই)
                if (!wordStart && i + 1 < src.length && isLetter(src[i + 1])) {
                    qBreak = true
                    i += 1; continue
                }
                // শুধু q: q-ই থাকে
                out.append('q'); consAfter = false; wordStart = false; i += 1
                if (i == src.length) pending = 'Q'
                continue
            }

            // সবচেয়ে বড় টোকেন আগে (৩ → ২ → ১)
            var matched = false
            var len = minOf(3, src.length - i)
            while (len >= 1) {
                val tok = src.substring(i, i + len)

                // w: ব্যঞ্জনের পরে ব-ফলা, অন্যথায় (শুরুতে/স্বরের পরে) ও
                if (tok == "w") {
                    if (consAfter) {
                        out.append(HASANTA).append("ব"); consAfter = true
                    } else {
                        out.append("ও"); consAfter = false
                    }
                    wordStart = false
                    i += 1; matched = true; break
                }

                // ng = ং (অনুস্বার)
                if (tok == "ng") {
                    out.append("ং"); consAfter = false; wordStart = false
                    i += 2; matched = true; break
                }

                val cons = consonants[tok]
                if (cons != null) {
                    val startsWord = wordStart && !consAfter
                    if (tok == "z" && !consAfter && !wasQBreak && out.isNotEmpty() &&
                        out[out.length - 1] in '\u0985'..'\u0994'
                    ) {
                        // স্বরবর্ণের সাথে য-ফলা: অ্যা = oza  (ভাঙতে q: অযথা = oqzotha)
                        out.append(HASANTA).append(cons)
                    } else if (tok == "z" && consAfter && wasRaInitial) {
                        // র‍্য : র + ZWJ + হসন্ত + য
                        out.append(ZWJ).append(HASANTA).append(cons)
                    } else {
                        // b সবসময় স্বাধীন ব; যুক্ত হয় না (ব-ফলা = w)
                        if (consAfter && tok != "b") out.append(HASANTA)
                        out.append(cons)
                    }
                    raInitial = tok == "r" && startsWord
                    consAfter = true; wordStart = false
                    i += len; matched = true; break
                }
                val v = vowels[tok]
                if (v != null) {
                    out.append(if (consAfter) v.second else v.first)
                    // ব্যঞ্জনের পরে একা 'o' অদৃশ্য — শেষ কী হলে মার্কার দেখাই
                    if (tok == "o" && consAfter && i + len == src.length) pending = 'o'
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

        var markerIndex = -1
        var marker = ' '
        if (preview) {
            when (pending) {
                'o', 'q' -> { markerIndex = out.length; marker = pending; out.append(pending) }
                'Q' -> { markerIndex = out.length - 1; marker = 'q' }
            }
        }
        return Preview(out.toString(), markerIndex, marker)
    }

    // ---------- পরের কী-এর ইঙ্গিত ----------

    private fun isCons(c: Char) = c in '\u0995'..'\u09B9' || c == '\u09DC' || c == '\u09DD' || c == '\u09DF'
    private fun isSign(c: Char) = c in '\u09BE'..'\u09CC' || c == '\u0981' || c == '\u0982' || c == '\u0983'

    /** লেখার শেষ "অক্ষর-একক": যুক্তাক্ষর + কার/চিহ্ন (যেমন ক্ষ্মা, কা, র‍্য)। */
    fun tailUnit(s: String): String {
        if (s.isEmpty()) return ""
        var i = s.length
        while (i > 0 && isSign(s[i - 1])) i--
        var j = i
        while (true) {
            var k = j
            if (k > 0 && s[k - 1] == NUKTA[0]) k--
            if (k > 0 && isCons(s[k - 1])) k-- else break
            j = k
            if (j > 0 && s[j - 1] == HASANTA[0]) {
                var m = j - 1
                if (m > 0 && s[m - 1] == '\u200D') m--
                if (m > 0 && (isCons(s[m - 1]) || s[m - 1] == NUKTA[0] || s[m - 1] in '\u0985'..'\u0994')) { j = m; continue }
            }
            break
        }
        val unit = when {
            j < i -> s.substring(j)
            i < s.length -> s.substring(i)
            else -> s.substring(s.length - 1)
        }
        return if (unit.length > 7) unit.substring(unit.length - 7) else unit
    }

    /** কী-র গায়ের ইঙ্গিত; [green] = চলমান 'q'-এর কারণে যে ফল বদলাচ্ছে */
    class Hint(val text: String, val green: Boolean)

    private fun hintFor(buffer: String, c: Char): String? {
        val base = convert(buffer)
        val next = convert(buffer + c)
        if (next == base) return null
        val h = tailUnit(next)
        return if (h.isEmpty() || h == c.toString()) null else h
    }

    /**
     * বাফারের পর প্রতিটি অক্ষর-কী চাপলে কী আসবে — কী-র গায়ে দেখানোর জন্য।
     * যে কী কিছু বদলায় না (যেমন ব্যঞ্জনের পরে o/q) তার জন্য এন্ট্রি থাকে না।
     * 'q' সক্রিয় থাকলে (শেষ কী q) যেসব কী-র ফল q-এর কারণে বদলায় সেগুলো green।
     */
    fun nextHints(buffer: String): Map<Char, Hint> {
        val b = buffer.lowercase()
        val p = preview(b)
        val qActive = p.markerIndex >= 0 && p.marker == 'q' && b.endsWith("q")
        val without = if (qActive) b.dropLast(1) else null
        val res = HashMap<Char, Hint>()
        for (c in 'a'..'z') {
            val h = hintFor(b, c)
            if (h == null) continue
            val green = without != null && hintFor(without, c) != h
            res[c] = Hint(h, green)
        }
        return res
    }
}
