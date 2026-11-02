package com.example.banglakeyboard

/** কী চাপলে কী হবে। */
enum class Act {
    LETTER,      // বাংলা মোডে composing বাফারে যায়, ইংরেজিতে সরাসরি লেখা হয়
    COMPOSE,     // ',' — বাংলা মোডে বাফারে যায় (,, = হসন্ত), ইংরেজিতে সরাসরি
    TEXT,        // যেমন আছে তেমন লেখা (আগে composing শেষ করে)
    SPACE,
    BACKSPACE,
    ENTER,
    SHIFT,
    LANG,        // বাংলা <-> ইংরেজি
    EMOJI,       // ইমোজি প্যানেল
    TO_SYMBOLS,
    TO_LETTERS,
    SYMBOL_PAGE,
    SPACER
}

enum class Tone { NONE, RED, GREEN }

enum class ShiftState { OFF, ON, CAPS }

enum class Layer { LETTERS, SYMBOLS_1, SYMBOLS_2 }

data class Key(
    val label: String,
    val act: Act,
    val text: String = label,
    val hint: String? = null,
    val weight: Float = 1f,
    val func: Boolean = false,
    val accent: Boolean = false,
    val active: Boolean = false,
    val tone: Tone = Tone.NONE,
    /** ২ সেকেন্ড চেপে ধরলে যে এডিট কমান্ড চলবে (x c v z y a), আর কী-র কোণে তার ছোট নাম */
    val holdCmd: Char? = null,
    val holdLabel: String? = null,
)

object KeyboardLayouts {

    /** বাংলা মোডে প্রতিটি ল্যাটিন কী-তে ছোট করে দেখানো বাংলা ইঙ্গিত */
    private val hints = mapOf(
        'a' to "আ", 'b' to "ব", 'c' to "চ", 'd' to "দ", 'e' to "এ", 'f' to "ফ",
        'g' to "গ", 'h' to "হ", 'i' to "ই", 'j' to "জ", 'k' to "ক", 'l' to "ল",
        'm' to "ম", 'n' to "ন", 'o' to "অ", 'p' to "প", 'q' to "ং", 'r' to "র",
        's' to "স", 't' to "ত", 'u' to "উ", 'v' to "ভ", 'w' to "ও", 'x' to "ক্ষ",
        'y' to "য়", 'z' to "য",
    )

    private const val BN_DIGITS = "০১২৩৪৫৬৭৮৯"

    /** ২ সেকেন্ড চেপে ধরার কমান্ড: কী → কোণের নাম (কাজ BanglaKeyboardService.onEditCommand-এ) */
    val holdCommands = mapOf(
        'x' to "কাট",      // Cut
        'c' to "কপি",      // Copy
        'v' to "পেস্ট",    // Paste
        'z' to "আনডু",     // Undo
        'y' to "রিডু",     // Redo
        'a' to "সব",       // Select All
    )

    private fun spacer(w: Float) = Key("", Act.SPACER, weight = w)

    private fun letters(chars: String, bangla: Boolean, shift: ShiftState): List<Key> =
        chars.map { c ->
            // শিফট শুধু কী-র লেখা বড়/ছোট দেখায়; বাংলা টাইপিংয়ে প্রভাব নেই
            val shown = if (shift != ShiftState.OFF) c.uppercaseChar() else c
            Key(
                label = shown.toString(),
                act = Act.LETTER,
                text = shown.toString(),
                hint = if (bangla) hints[c] else null,
                holdCmd = if (c in holdCommands) c else null,
                holdLabel = holdCommands[c],
                // বাংলায়: o = যুক্তাক্ষরে বাধা (লাল), q = ভাঙার চিহ্ন (সবুজ)
                tone = if (!bangla) Tone.NONE else when (c) {
                    'o' -> Tone.RED
                    'q' -> Tone.GREEN
                    else -> Tone.NONE
                },
            )
        }

    private fun numberRow(bangla: Boolean, layer: Layer): List<Key> =
        "1234567890".map { d ->
            if (bangla && layer == Layer.LETTERS) {
                val bn = BN_DIGITS[d - '0'].toString()
                Key(bn, Act.TEXT, text = bn)
            } else {
                Key(d.toString(), Act.TEXT)
            }
        }

    private fun literalKeys(chars: String) = chars.map { Key(it.toString(), Act.TEXT) }

    fun rows(
        layer: Layer,
        bangla: Boolean,
        shift: ShiftState,
        enterLabel: String,
    ): List<List<Key>> {
        val result = ArrayList<List<Key>>()
        result.add(numberRow(bangla, layer))

        val enter = Key(enterLabel, Act.ENTER, weight = 1.5f, func = true, accent = true)
        val space = Key(if (bangla) "বাংলা" else "English", Act.SPACE, text = " ", weight = 3f)
        val lang = Key(if (bangla) "বাং" else "EN", Act.LANG, weight = 1.2f, func = true, accent = true)
        val emoji = Key("😊", Act.EMOJI, weight = 1.2f, func = true)
        val backspace = Key("⌫", Act.BACKSPACE, weight = 1.5f, func = true)

        when (layer) {
            Layer.LETTERS -> {
                result.add(letters("qwertyuiop", bangla, shift))
                val row2 = ArrayList<Key>()
                row2.add(spacer(0.5f))
                row2.addAll(letters("asdfghjkl", bangla, shift))
                row2.add(spacer(0.5f))
                result.add(row2)

                val row3 = ArrayList<Key>()
                row3.add(
                    Key(
                        label = if (shift == ShiftState.CAPS) "⇪" else "⇧",
                        act = Act.SHIFT, weight = 1.5f, func = true,
                        active = shift != ShiftState.OFF,
                    )
                )
                row3.addAll(letters("zxcvbnm", bangla, shift))
                row3.add(backspace)
                result.add(row3)

                result.add(
                    listOf(
                        Key("?123", Act.TO_SYMBOLS, weight = 1.3f, func = true),
                        lang,
                        emoji,
                        // বাংলায় ',' কী দুইবার = হসন্ত (,,)
                        Key(",", if (bangla) Act.COMPOSE else Act.TEXT, hint = if (bangla) "্" else null, weight = 0.9f),
                        space,
                        Key(if (bangla) "।" else ".", Act.TEXT, weight = 0.9f),
                        enter,
                    )
                )
            }

            Layer.SYMBOLS_1, Layer.SYMBOLS_2 -> {
                val first = layer == Layer.SYMBOLS_1
                result.add(literalKeys(if (first) "@#\$৳%&-+()" else "€£¥₹°©®™§¶"))
                result.add(literalKeys(if (first) "*\"':;!?/_=" else "[]{}«»`´¿¡"))
                val row3 = ArrayList<Key>()
                row3.add(Key(if (first) "১/২" else "২/২", Act.SYMBOL_PAGE, weight = 1.5f, func = true))
                row3.addAll(literalKeys(if (first) "\\|~^<>•" else "\\|~^•…¬"))
                row3.add(backspace)
                result.add(row3)
                result.add(
                    listOf(
                        Key(if (bangla) "কখ" else "ABC", Act.TO_LETTERS, weight = 1.3f, func = true),
                        lang,
                        emoji,
                        Key(",", Act.TEXT, weight = 0.9f),
                        space,
                        Key(".", Act.TEXT, weight = 0.9f),
                        enter,
                    )
                )
            }
        }
        return result
    }
}
