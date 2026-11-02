package com.example.banglakeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.Path
import android.graphics.RectF
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.TypedValue
import android.view.HapticFeedbackConstants
import android.view.MotionEvent
import android.view.View

/** কিবোর্ডের রং (লাইট/ডার্ক — res/values ও values-night থেকে) */
class KeyColors(context: Context) {
    val background = context.getColor(R.color.kb_background)
    val key = context.getColor(R.color.kb_key)
    val funcKey = context.getColor(R.color.kb_func_key)
    val pressed = context.getColor(R.color.kb_key_pressed)
    val text = context.getColor(R.color.kb_text)
    val hint = context.getColor(R.color.kb_hint)
    val accent = context.getColor(R.color.kb_accent)
    val red = context.getColor(R.color.kb_blocker_o)
    val green = context.getColor(R.color.kb_blocker_q)
}

/** একটি কী। নিজে আঁকে, তাই হালকা ও দ্রুত। */
@SuppressLint("ViewConstructor")
class KeyView(
    context: Context,
    private val key: Key,
    private val colors: KeyColors,
    private val onPress: (Key) -> Unit,
    private val onLongPress: ((Key) -> Unit)? = null,
) : View(context) {

    private val density = resources.displayMetrics.density
    private val bgRect = RectF()
    private val path = Path()
    private var down = false
    private var longFired = false
    private var downAt = 0L
    private var dynMode = false
    private var dynHint: String? = null
    private var dynGreen = false
    private val uiHandler = Handler(Looper.getMainLooper())

    private val bgPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val textPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.CENTER }
    private val hintPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.RIGHT }
    private val holdPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply { textAlign = Paint.Align.LEFT }
    private val barPaint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val iconPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }

    /** বাংলা মোডে শব্দ চলাকালীন: এই কী চাপলে যা আসবে তার ইঙ্গিত (null হলে কিছু দেখায় না) */
    fun setDynamic(on: Boolean, hint: String?, green: Boolean = false) {
        if (dynMode == on && dynHint == hint && dynGreen == green) return
        dynMode = on
        dynHint = hint
        dynGreen = green
        invalidate()
    }

    private fun sp(v: Float) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_SP, v, resources.displayMetrics)

    private val repeatRunnable = object : Runnable {
        override fun run() {
            if (!down) return
            onPress(key)
            uiHandler.postDelayed(this, 55)
        }
    }
    private val longRunnable = Runnable {
        if (down) {
            longFired = true
            performHapticFeedback(HapticFeedbackConstants.LONG_PRESS)
            onLongPress?.invoke(key)
        }
    }

    private fun isIconKey() = key.act == Act.BACKSPACE || key.act == Act.SHIFT ||
        (key.act == Act.ENTER && key.label == ENTER_ICON)

    override fun onDraw(canvas: Canvas) {
        if (key.act == Act.SPACER) return
        val gap = 2.5f * density
        bgRect.set(gap, gap * 1.4f, width - gap, height - gap * 1.4f)

        bgPaint.color = when {
            down -> colors.pressed
            key.act == Act.ENTER -> colors.accent
            key.active -> colors.pressed
            key.func -> colors.funcKey
            else -> colors.key
        }
        canvas.drawRoundRect(bgRect, 7 * density, 7 * density, bgPaint)

        val textColor = when {
            key.act == Act.ENTER -> 0xFFFFFFFF.toInt()
            key.tone == Tone.RED -> colors.red
            key.tone == Tone.GREEN -> colors.green
            key.accent -> colors.accent
            else -> colors.text
        }

        if (isIconKey()) {
            drawIcon(canvas, textColor)
            return
        }

        val dyn = if (dynMode) dynHint else null
        val size = when {
            dyn != null && key.act == Act.LETTER -> 19f
            key.act == Act.SPACE -> 13f
            key.act == Act.LETTER || key.act == Act.TEXT && key.label.length == 1 -> 22f
            key.act == Act.EMOJI -> 21f
            key.label.length > 2 -> 15f
            else -> 17f
        }
        textPaint.textSize = sp(size)
        textPaint.color = if (key.act == Act.SPACE) colors.hint else textColor
        val lower = if (dyn != null && key.act == Act.LETTER) 4 * density else 0f
        val baseline = height / 2f - (textPaint.descent() + textPaint.ascent()) / 2f + lower
        canvas.drawText(key.label, width / 2f, baseline, textPaint)

        if (dynMode) {
            // শব্দ চলাকালীন স্থির ইঙ্গিত লুকিয়ে, পরের কী-তে যা আসবে তা বড় করে দেখাই
            dyn?.let {
                var s = when {
                    it.length <= 2 -> 13f
                    it.length == 3 -> 11.5f
                    else -> 10f
                }
                hintPaint.typeface = Typeface.DEFAULT_BOLD
                hintPaint.color = if (dynGreen) colors.green else colors.accent
                hintPaint.textSize = sp(s)
                val maxW = bgRect.width() - 5 * density
                while (hintPaint.measureText(it) > maxW && s > 7f) {
                    s -= 0.5f
                    hintPaint.textSize = sp(s)
                }
                canvas.drawText(it, bgRect.right - 3 * density, bgRect.top + sp(s + 2.5f), hintPaint)
                hintPaint.typeface = Typeface.DEFAULT
            }
        } else {
            key.hint?.let {
                hintPaint.textSize = sp(10f)
                hintPaint.color = colors.hint
                canvas.drawText(it, bgRect.right - 4 * density, bgRect.top + sp(11.5f), hintPaint)
            }
        }

        // ২ সেকেন্ড চেপে ধরার কমান্ডের ছোট নাম (নিচের বাম কোণে)
        key.holdLabel?.let {
            holdPaint.textSize = sp(8.5f)
            holdPaint.color = colors.hint
            canvas.drawText(it, bgRect.left + 4 * density, bgRect.bottom - 4 * density, holdPaint)
        }
        // ধরে রাখার সময় নিচে একটা লাইন ভরতে থাকে; ভরলে কমান্ড চলে
        if (key.holdCmd != null && down && !longFired) {
            val progress = ((SystemClock.uptimeMillis() - downAt) / HOLD_MS.toFloat()).coerceIn(0f, 1f)
            barPaint.color = colors.accent
            val h = 3 * density
            canvas.drawRoundRect(
                bgRect.left, bgRect.bottom - h, bgRect.left + bgRect.width() * progress, bgRect.bottom,
                h / 2, h / 2, barPaint,
            )
            postInvalidateOnAnimation()
        }
    }

    /** ⌫ ⇧ ⏎ কে নিজে আঁকা হয় (ফন্টের ওপর নির্ভর করে না) */
    private fun drawIcon(canvas: Canvas, color: Int) {
        val unit = minOf(width, height) * 0.46f / 24f
        iconPaint.color = color
        iconPaint.strokeWidth = 2f * unit
        canvas.save()
        canvas.translate(width / 2f - 12f * unit, height / 2f - 12f * unit)
        path.reset()
        when (key.act) {
            Act.BACKSPACE -> {
                path.moveTo(2f * unit, 12f * unit)
                path.lineTo(8f * unit, 5f * unit)
                path.lineTo(21f * unit, 5f * unit)
                path.lineTo(21f * unit, 19f * unit)
                path.lineTo(8f * unit, 19f * unit)
                path.close()
                path.moveTo(11.5f * unit, 9f * unit); path.lineTo(17f * unit, 15f * unit)
                path.moveTo(17f * unit, 9f * unit); path.lineTo(11.5f * unit, 15f * unit)
                iconPaint.style = Paint.Style.STROKE
            }
            Act.SHIFT -> {
                path.moveTo(12f * unit, 3f * unit)
                path.lineTo(21f * unit, 13f * unit)
                path.lineTo(16f * unit, 13f * unit)
                path.lineTo(16f * unit, 19f * unit)
                path.lineTo(8f * unit, 19f * unit)
                path.lineTo(8f * unit, 13f * unit)
                path.lineTo(3f * unit, 13f * unit)
                path.close()
                if (key.label == "⇪") {
                    path.moveTo(8f * unit, 22f * unit); path.lineTo(16f * unit, 22f * unit)
                }
                iconPaint.style = if (key.active) Paint.Style.FILL_AND_STROKE else Paint.Style.STROKE
            }
            else -> { // ENTER
                path.moveTo(20f * unit, 5f * unit)
                path.lineTo(20f * unit, 13f * unit)
                path.lineTo(5f * unit, 13f * unit)
                path.moveTo(10.5f * unit, 7.5f * unit)
                path.lineTo(5f * unit, 13f * unit)
                path.lineTo(10.5f * unit, 18.5f * unit)
                iconPaint.style = Paint.Style.STROKE
            }
        }
        canvas.drawPath(path, iconPaint)
        canvas.restore()
    }

    private fun isInside(e: MotionEvent): Boolean =
        e.x >= 0 && e.x <= width && e.y >= -8 * density && e.y <= height + 8 * density

    private fun release() {
        down = false
        uiHandler.removeCallbacks(repeatRunnable)
        uiHandler.removeCallbacks(longRunnable)
        invalidate()
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e: MotionEvent): Boolean {
        if (key.act == Act.SPACER) return false
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                down = true
                longFired = false
                downAt = SystemClock.uptimeMillis()
                invalidate()
                performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP)
                if (key.act == Act.BACKSPACE) {
                    onPress(key)
                    uiHandler.postDelayed(repeatRunnable, 400)
                } else if (onLongPress != null) {
                    uiHandler.postDelayed(longRunnable, if (key.holdCmd != null) HOLD_MS else 450L)
                }
            }
            MotionEvent.ACTION_MOVE -> if (down && !isInside(e)) release()
            MotionEvent.ACTION_UP -> {
                val fire = down && key.act != Act.BACKSPACE && !longFired
                release()
                if (fire) onPress(key)
            }
            MotionEvent.ACTION_CANCEL -> release()
        }
        return true
    }

    override fun onDetachedFromWindow() {
        super.onDetachedFromWindow()
        release()
    }

    companion object {
        const val ENTER_ICON = "⏎"
        /** কাট/কপি/পেস্ট ইত্যাদির জন্য চেপে ধরার সময় */
        const val HOLD_MS = 2000L
    }
}
