package com.popykeyboard

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.Toast

/**
 * পপি কিবোর্ড (IME) সার্ভিস।
 *
 * বাংলা মোডে প্রতিটি অক্ষর একটি বাফারে জমে; প্রতিবার পুরো বাফার [BanglaPhonetic.convert] দিয়ে
 * বাংলায় বদলে composing text হিসেবে দেখানো হয়। স্পেস/এন্টার/চিহ্নে শব্দটি পাকা হয়।
 */
class BanglaKeyboardService : InputMethodService(), KeyboardRoot.Listener {

    private var root: KeyboardRoot? = null
    private val buffer = StringBuilder()

    private var bangla = true
    private var passwordField = false
    private var shift = ShiftState.OFF
    private var lastShiftTap = 0L

    private val prefs by lazy { getSharedPreferences("keyboard", MODE_PRIVATE) }

    private var appliedHeight = 100

    override fun onCreateInputView(): View {
        appliedHeight = prefs.getInt("height_pct", 100)
        bangla = prefs.getBoolean("bangla", true)
        Feedback.prepare(this) // কী-চাপার শব্দ আগে থেকে লোড
        applyJoinPrefs()
        val view = KeyboardRoot(this, this)
        view.setLanguage(bangla)
        root = view
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onDestroy() {
        Feedback.release()
        super.onDestroy()
    }

    /** সেটিংসের "স্বয়ংক্রিয় যুক্তাক্ষর" পছন্দ ইঞ্জিনে পৌঁছে দেয় */
    private fun applyJoinPrefs() {
        BanglaPhonetic.configure(
            prefs.getBoolean("auto_join", true),
            prefs.getString("join_list", BanglaPhonetic.DEFAULT_JOIN_LIST) ?: BanglaPhonetic.DEFAULT_JOIN_LIST,
        )
    }

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        buffer.clear()
        applyJoinPrefs()
        // সেটিংসে উচ্চতা বদলালে কিবোর্ড নতুন করে বানাই
        if (root != null && prefs.getInt("height_pct", 100) != appliedHeight) {
            setInputView(onCreateInputView())
        }

        val cls = info.inputType and InputType.TYPE_MASK_CLASS
        val variation = info.inputType and InputType.TYPE_MASK_VARIATION
        passwordField = cls == InputType.TYPE_CLASS_TEXT && (
            variation == InputType.TYPE_TEXT_VARIATION_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD ||
                variation == InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD
            )
        val wantsLatin = cls != InputType.TYPE_CLASS_TEXT || passwordField ||
            variation == InputType.TYPE_TEXT_VARIATION_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_WEB_EMAIL_ADDRESS ||
            variation == InputType.TYPE_TEXT_VARIATION_URI

        // ইউজারের পছন্দ মনে রাখা হয়; তবে নম্বর/ইমেইল/পাসওয়ার্ড ফিল্ডে ইংরেজি
        bangla = prefs.getBoolean("bangla", true) && !wantsLatin
        root?.apply {
            resetToLetters()
            setLanguage(bangla)
            setEnterLabel(enterLabelFor(info))
        }
        refreshShift()
        updateHints()
    }

    override fun onFinishInput() {
        super.onFinishInput()
        commitComposing()
    }

    /** ইউজার কার্সর সরালে চলমান composing বাদ দিই */
    override fun onUpdateSelection(
        oldSelStart: Int, oldSelEnd: Int,
        newSelStart: Int, newSelEnd: Int,
        candidatesStart: Int, candidatesEnd: Int,
    ) {
        super.onUpdateSelection(oldSelStart, oldSelEnd, newSelStart, newSelEnd, candidatesStart, candidatesEnd)
        if (buffer.isNotEmpty() && (newSelStart != candidatesEnd || newSelEnd != candidatesEnd)) {
            buffer.clear()
            currentInputConnection?.finishComposingText()
            updateHints()
        }
    }

    // ---------- সাহায্যকারী ----------

    private fun enterLabelFor(info: EditorInfo): String {
        if (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION != 0) return KeyView.ENTER_ICON
        return when (info.imeOptions and EditorInfo.IME_MASK_ACTION) {
            EditorInfo.IME_ACTION_GO -> "যান"
            EditorInfo.IME_ACTION_SEARCH -> "খুঁজুন"
            EditorInfo.IME_ACTION_SEND -> "পাঠান"
            EditorInfo.IME_ACTION_NEXT -> "পরের"
            EditorInfo.IME_ACTION_DONE -> "শেষ"
            else -> KeyView.ENTER_ICON
        }
    }

    private fun updateComposing() {
        val ic = currentInputConnection ?: return
        val p = BanglaPhonetic.preview(buffer.toString())
        val shown: CharSequence = if (p.markerIndex >= 0) {
            // o = লাল, q = সবুজ মার্কার; পরের অক্ষর এলে নিজে সরে যায়
            SpannableString(p.text).apply {
                val color = if (p.marker == 'o') MARK_RED else MARK_GREEN
                setSpan(
                    ForegroundColorSpan(color), p.markerIndex, p.markerIndex + 1,
                    Spanned.SPAN_EXCLUSIVE_EXCLUSIVE,
                )
            }
        } else p.text
        ic.setComposingText(shown, 1)
        updateHints()
    }

    /** কী-গুলোর গায়ে "এরপর এটা চাপলে কী আসবে" ইঙ্গিত */
    private fun updateHints() {
        val r = root ?: return
        if (!bangla || buffer.isEmpty()) r.setHints(null)
        else r.setHints(BanglaPhonetic.nextHints(buffer.toString()))
    }

    private fun commitComposing() {
        if (buffer.isEmpty()) return
        // মার্কার (o/q) বাদ দিয়ে চূড়ান্ত লেখা পাকা করি
        currentInputConnection?.apply {
            setComposingText(BanglaPhonetic.convert(buffer.toString()), 1)
            finishComposingText()
        }
        buffer.clear()
        updateHints()
    }

    /** ইংরেজি মোডে বাক্যের শুরুতে স্বয়ংক্রিয় বড় হাতের অক্ষর */
    private fun refreshShift() {
        if (bangla) {
            // শিফট বাংলায় শুধু চোখে দেখার (বড়/ছোট হরফ); নিজে থেকে বদলায় না
        } else if (shift != ShiftState.CAPS) {
            val info = currentInputEditorInfo
            val caps = if (info != null && info.inputType != InputType.TYPE_NULL) {
                currentInputConnection?.getCursorCapsMode(info.inputType) ?: 0
            } else 0
            shift = if (caps != 0) ShiftState.ON else ShiftState.OFF
        }
        root?.setShift(shift)
    }

    // ---------- KeyboardRoot.Listener ----------

    override fun onLetter(ch: Char) {
        if (bangla) {
            buffer.append(ch)
            updateComposing()
            if (shift == ShiftState.ON) { shift = ShiftState.OFF; root?.setShift(shift) }
        } else {
            val out = if (shift != ShiftState.OFF) ch.uppercaseChar() else ch
            currentInputConnection?.commitText(out.toString(), 1)
            if (shift == ShiftState.ON) refreshShiftAfterLetter()
        }
    }

    private fun refreshShiftAfterLetter() {
        shift = ShiftState.OFF
        root?.setShift(shift)
    }

    /** ':' ও ',' — বাংলায় বাফারে যায় (:: = ঃ, ,, = হসন্ত) */
    override fun onCompose(text: String) {
        if (bangla) {
            buffer.append(text)
            updateComposing()
        } else {
            onText(text)
        }
    }

    override fun onText(text: String) {
        commitComposing()
        currentInputConnection?.commitText(text, 1)
        refreshShift()
    }

    override fun onSpace() {
        commitComposing()
        currentInputConnection?.commitText(" ", 1)
        refreshShift()
    }

    override fun onBackspace() {
        val ic = currentInputConnection ?: return
        if (buffer.isNotEmpty()) {
            buffer.deleteCharAt(buffer.length - 1)
            if (buffer.isEmpty()) { ic.commitText("", 1); updateHints() } else updateComposing()
        } else {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_DEL)
        }
        refreshShift()
    }

    override fun onEnter() {
        commitComposing()
        val info = currentInputEditorInfo
        val action = (info?.imeOptions ?: 0) and EditorInfo.IME_MASK_ACTION
        val noAction = info == null ||
            (info.imeOptions and EditorInfo.IME_FLAG_NO_ENTER_ACTION) != 0 ||
            action == EditorInfo.IME_ACTION_NONE ||
            action == EditorInfo.IME_ACTION_UNSPECIFIED
        if (noAction) {
            sendDownUpKeyEvents(KeyEvent.KEYCODE_ENTER)
        } else {
            currentInputConnection?.performEditorAction(action)
        }
        refreshShift()
    }

    override fun onShift() {
        val now = SystemClock.uptimeMillis()
        shift = when {
            shift == ShiftState.CAPS -> ShiftState.OFF
            shift == ShiftState.ON && now - lastShiftTap < 350 -> ShiftState.CAPS
            shift == ShiftState.ON -> ShiftState.OFF
            else -> ShiftState.ON
        }
        lastShiftTap = now
        root?.setShift(shift)
    }

    override fun onToggleLanguage() {
        if (passwordField) return // পাসওয়ার্ড ফিল্ডে সবসময় ইংরেজি
        commitComposing()
        bangla = !bangla
        prefs.edit().putBoolean("bangla", bangla).apply()
        shift = ShiftState.OFF
        root?.setShift(shift)
        root?.setLanguage(bangla)
        refreshShift()
        updateHints()
    }

    override fun onLanguagePicker() {
        commitComposing()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onEmojiPicked(emoji: String) {
        commitComposing()
        currentInputConnection?.commitText(emoji, 1)
    }

    /**
     * ২ সেকেন্ড চেপে ধরার কমান্ড। চলমান বাংলা শব্দ আগে পাকা করা হয়, যাতে কপি/কাট ঠিক লেখার ওপর চলে।
     * কাট/কপি/পেস্ট/সব-নির্বাচন সরাসরি টেক্সট ফিল্ডে; আনডু/রিডু Ctrl+Z / Ctrl+Shift+Z দিয়ে।
     */
    override fun onEditCommand(cmd: Char) {
        commitComposing()
        val ic = currentInputConnection ?: return
        val name = KeyboardLayouts.holdCommands[cmd] ?: return
        when (cmd) {
            'x' -> ic.performContextMenuAction(android.R.id.cut)
            'c' -> ic.performContextMenuAction(android.R.id.copy)
            'v' -> ic.performContextMenuAction(android.R.id.paste)
            'a' -> ic.performContextMenuAction(android.R.id.selectAll)
            'z' -> sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = false)
            'y' -> sendCtrlKey(KeyEvent.KEYCODE_Z, withShift = true) // Ctrl+Shift+Z = রিডু
            else -> return
        }
        Toast.makeText(this, name, Toast.LENGTH_SHORT).show()
    }

    private fun sendCtrlKey(keyCode: Int, withShift: Boolean) {
        val ic = currentInputConnection ?: return
        var meta = KeyEvent.META_CTRL_ON or KeyEvent.META_CTRL_LEFT_ON
        if (withShift) meta = meta or KeyEvent.META_SHIFT_ON or KeyEvent.META_SHIFT_LEFT_ON
        val now = SystemClock.uptimeMillis()
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_DOWN, keyCode, 0, meta))
        ic.sendKeyEvent(KeyEvent(now, now, KeyEvent.ACTION_UP, keyCode, 0, meta))
    }

    private companion object {
        const val MARK_RED = 0xFFE53935.toInt()
        const val MARK_GREEN = 0xFF1E9E45.toInt()
    }
}
