package com.example.banglakeyboard

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView

/** ইমোজি প্যানেল: উপরে ক্যাটাগরি, মাঝে ইমোজি গ্রিড, নিচে ফিরে যাওয়া / স্পেস / ব্যাকস্পেস। */
@SuppressLint("ViewConstructor")
class EmojiPanel(
    context: Context,
    private val colors: KeyColors,
    private val keyHeightPx: Int,
    private val listener: Listener,
) : LinearLayout(context) {

    interface Listener {
        fun onEmojiPicked(emoji: String)
        fun onEmojiBackspace()
        fun onEmojiSpace()
        fun onEmojiClose()
    }

    private val prefs = context.getSharedPreferences("emoji", Context.MODE_PRIVATE)
    private val density = resources.displayMetrics.density
    private val tabViews = ArrayList<TextView>()
    private val scroll = ScrollView(context)
    private val grid = LinearLayout(context)
    private var selected = 1 // 0 = সাম্প্রতিক, 1.. = ক্যাটাগরি
    private var closeLabel: TextView? = null
    private val columns = 8

    init {
        orientation = VERTICAL
        setBackgroundColor(colors.background)

        // ---- ক্যাটাগরি বার ----
        val tabs = LinearLayout(context).apply { orientation = HORIZONTAL }
        val icons = listOf("🕘") + EmojiData.categories.map { it.icon }
        icons.forEachIndexed { index, icon ->
            val tv = TextView(context).apply {
                text = icon
                textSize = 20f
                gravity = Gravity.CENTER
                setOnClickListener { select(index) }
            }
            tabViews.add(tv)
            tabs.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, 1f))
        }
        addView(tabs, LayoutParams(LayoutParams.MATCH_PARENT, (keyHeightPx * 0.85f).toInt()))

        // ---- গ্রিড ----
        grid.orientation = VERTICAL
        scroll.addView(grid, ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT)
        addView(scroll, LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f))

        // ---- নিচের বার ----
        val bottom = LinearLayout(context).apply { orientation = HORIZONTAL }
        closeLabel = bottomKey("ABC", 1.5f, bottom) { listener.onEmojiClose() }
        bottomKey("বাংলা / English", 4f, bottom) { listener.onEmojiSpace() }
        val backspace = KeyView(
            context,
            Key("⌫", Act.BACKSPACE, weight = 1.5f, func = true),
            colors,
            onPress = { listener.onEmojiBackspace() },
        )
        bottom.addView(backspace, LayoutParams(0, LayoutParams.MATCH_PARENT, 1.5f))
        addView(bottom, LayoutParams(LayoutParams.MATCH_PARENT, keyHeightPx))

        select(1)
    }

    private fun bottomKey(label: String, weight: Float, parent: LinearLayout, onClick: () -> Unit): TextView {
        val tv = TextView(context).apply {
            text = label
            textSize = if (label.length > 3) 13f else 16f
            setTextColor(if (label.length > 3) colors.hint else colors.accent)
            gravity = Gravity.CENTER
            val bgColor = if (label.length > 3) colors.key else colors.funcKey
            val bg = GradientDrawable().apply {
                cornerRadius = 7 * density
                setColor(bgColor)
            }
            background = android.graphics.drawable.InsetDrawable(bg, (2.5f * density).toInt(), (3.5f * density).toInt(), (2.5f * density).toInt(), (3.5f * density).toInt())
            setOnClickListener {
                performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
                onClick()
            }
        }
        parent.addView(tv, LayoutParams(0, LayoutParams.MATCH_PARENT, weight))
        return tv
    }

    /** ভাষা বদলালে নিচের বাম বাটনের লেখা বদলায় */
    fun setLanguage(bangla: Boolean) {
        closeLabel?.text = if (bangla) "কখ" else "ABC"
    }

    /** প্যানেল খোলার সময় সাম্প্রতিক থাকলে সেটা, নইলে স্মাইলি দেখায় */
    fun onShown() {
        select(if (recent().isNotEmpty()) 0 else if (selected == 0) 1 else selected)
        scroll.scrollTo(0, 0)
    }

    private fun recent(): List<String> =
        prefs.getString("recent", "")!!.split(' ').filter { it.isNotEmpty() }

    private fun remember(emoji: String) {
        val list = ArrayList<String>()
        list.add(emoji)
        list.addAll(recent().filter { it != emoji })
        prefs.edit().putString("recent", list.take(40).joinToString(" ")).apply()
    }

    private fun select(index: Int) {
        selected = index
        tabViews.forEachIndexed { i, tv ->
            val bgColor = colors.pressed
            tv.background = if (i == index) {
                GradientDrawable().apply {
                    cornerRadius = 8 * density
                    setColor(bgColor)
                }
            } else null
        }
        val items = if (index == 0) recent() else EmojiData.categories[index - 1].emojis
        fill(items)
        scroll.scrollTo(0, 0)
    }

    private fun fill(items: List<String>) {
        grid.removeAllViews()
        if (items.isEmpty()) {
            grid.addView(TextView(context).apply {
                text = "এখনও কোনো সাম্প্রতিক ইমোজি নেই"
                setTextColor(colors.hint)
                gravity = Gravity.CENTER
                setPadding(0, (24 * density).toInt(), 0, 0)
            })
            return
        }
        val cell = (keyHeightPx * 0.9f).toInt()
        items.chunked(columns).forEach { rowItems ->
            val row = LinearLayout(context).apply { orientation = HORIZONTAL }
            rowItems.forEach { emoji ->
                row.addView(emojiCell(emoji), LayoutParams(0, cell, 1f))
            }
            // শেষ সারি অসম্পূর্ণ হলে ফাঁকা ঘর দিয়ে ভরা, যাতে প্রস্থ ঠিক থাকে
            repeat(columns - rowItems.size) { row.addView(View(context), LayoutParams(0, cell, 1f)) }
            grid.addView(row, LayoutParams(LayoutParams.MATCH_PARENT, cell))
        }
    }

    private fun emojiCell(emoji: String) = TextView(context).apply {
        text = emoji
        textSize = 25f
        gravity = Gravity.CENTER
        setTextColor(Color.BLACK)
        setOnClickListener {
            performHapticFeedback(android.view.HapticFeedbackConstants.KEYBOARD_TAP)
            remember(emoji)
            listener.onEmojiPicked(emoji)
        }
    }
}
