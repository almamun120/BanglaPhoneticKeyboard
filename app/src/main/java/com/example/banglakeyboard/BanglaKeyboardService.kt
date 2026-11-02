package com.example.banglakeyboard

import android.inputmethodservice.InputMethodService
import android.os.SystemClock
import android.text.InputType
import android.view.KeyEvent
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager

/**
 * বাংলা ফোনেটিক কিবোর্ড (IME) সার্ভিস।
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

    override fun onCreateInputView(): View {
        bangla = prefs.getBoolean("bangla", true)
        val view = KeyboardRoot(this, this)
        view.setLanguage(bangla)
        root = view
        return view
    }

    override fun onEvaluateFullscreenMode(): Boolean = false

    override fun onStartInputView(info: EditorInfo, restarting: Boolean) {
        super.onStartInputView(info, restarting)
        buffer.clear()

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
        ic.setComposingText(BanglaPhonetic.convert(buffer.toString()), 1)
    }

    private fun commitComposing() {
        if (buffer.isEmpty()) return
        currentInputConnection?.finishComposingText()
        buffer.clear()
    }

    /** ইংরেজি মোডে বাক্যের শুরুতে স্বয়ংক্রিয় বড় হাতের অক্ষর */
    private fun refreshShift() {
        if (bangla) {
            shift = ShiftState.OFF
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
            if (buffer.isEmpty()) ic.commitText("", 1) else updateComposing()
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
        root?.setLanguage(bangla)
        refreshShift()
    }

    override fun onLanguagePicker() {
        commitComposing()
        (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
    }

    override fun onEmojiPicked(emoji: String) {
        commitComposing()
        currentInputConnection?.commitText(emoji, 1)
    }
}
