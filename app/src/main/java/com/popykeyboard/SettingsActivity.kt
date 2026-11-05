package com.popykeyboard

import android.app.Activity
import android.content.Intent
import android.graphics.Typeface
import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.Gravity
import android.view.ViewGroup
import android.view.inputmethod.InputMethodManager
import android.widget.Button
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView

/** চালু করার ধাপ, টেস্ট বক্স ও টাইপিং চিটশিট। */
class SettingsActivity : Activity() {

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun title(text: String) = TextView(this).apply {
        this.text = text
        textSize = 17f
        typeface = Typeface.DEFAULT_BOLD
        setPadding(0, dp(20), 0, dp(6))
    }

    private fun body(text: String) = TextView(this).apply {
        this.text = text
        textSize = 15f
        setLineSpacing(0f, 1.15f)
    }

    /** একটি চালু/বন্ধ সুইচ + মাত্রার স্লাইডার (১–১০০); সুইচ বন্ধ থাকলে স্লাইডার নিষ্ক্রিয় */
    private fun addFeedbackControls(
        column: LinearLayout,
        prefs: android.content.SharedPreferences,
        switchText: String,
        onKey: String,
        valueKey: String,
        defaultValue: Int,
        labelPrefix: String,
        note: String,
        preview: () -> Unit,
    ) {
        val toggle = Switch(this).apply {
            text = switchText
            textSize = 15f
            isChecked = prefs.getBoolean(onKey, true)
        }
        val label = body("")
        val bar = SeekBar(this).apply {
            max = 99 // ১–১০০
            progress = prefs.getInt(valueKey, defaultValue).coerceIn(1, 100) - 1
            isEnabled = toggle.isChecked
        }
        fun showLabel() { label.text = "$labelPrefix: ${bar.progress + 1}%" }
        showLabel()
        label.alpha = if (toggle.isChecked) 1f else 0.45f

        toggle.setOnCheckedChangeListener { _, on ->
            prefs.edit().putBoolean(onKey, on).apply()
            bar.isEnabled = on
            label.alpha = if (on) 1f else 0.45f
            if (on) preview()
        }
        bar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(b: SeekBar?, value: Int, fromUser: Boolean) {
                showLabel()
                prefs.edit().putInt(valueKey, value + 1).apply()
            }
            override fun onStartTrackingTouch(b: SeekBar?) {}
            override fun onStopTrackingTouch(b: SeekBar?) = preview()
        })
        column.addView(toggle)
        column.addView(label)
        column.addView(bar)
        column.addView(body(note))
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(20), dp(20), dp(20), dp(32))
        }

        column.addView(TextView(this).apply {
            text = getString(R.string.app_name)
            textSize = 24f
            typeface = Typeface.DEFAULT_BOLD
        })

        column.addView(title("১. কিবোর্ড চালু করুন"))
        column.addView(Button(this).apply {
            text = "কিবোর্ড সেটিংস খুলুন"
            setOnClickListener { startActivity(Intent(Settings.ACTION_INPUT_METHOD_SETTINGS)) }
        })

        column.addView(title("২. এই কিবোর্ড বেছে নিন"))
        column.addView(Button(this).apply {
            text = "কিবোর্ড পাল্টান"
            setOnClickListener {
                (getSystemService(INPUT_METHOD_SERVICE) as InputMethodManager).showInputMethodPicker()
            }
        })

        column.addView(title("৩. এখানে টাইপ করে দেখুন"))
        column.addView(EditText(this).apply {
            hint = "যেমন: amar sonar bangla"
            inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE
            minLines = 3
            gravity = Gravity.TOP
        }, LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT))

        column.addView(title("কিবোর্ডের উচ্চতা"))
        val prefs = getSharedPreferences("keyboard", MODE_PRIVATE)
        val heightLabel = body("")
        val seek = SeekBar(this).apply {
            max = 16 // ৭০% … ১৫০%, ৫% করে
            progress = ((prefs.getInt("height_pct", 100).coerceIn(70, 150) - 70) / 5)
        }
        fun showHeight() { heightLabel.text = "উচ্চতা: ${70 + seek.progress * 5}%" }
        showHeight()
        seek.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(bar: SeekBar?, value: Int, fromUser: Boolean) {
                showHeight()
                prefs.edit().putInt("height_pct", 70 + value * 5).apply()
            }
            override fun onStartTrackingTouch(bar: SeekBar?) {}
            override fun onStopTrackingTouch(bar: SeekBar?) {}
        })
        column.addView(heightLabel)
        column.addView(seek)
        column.addView(body("পরের বার কিবোর্ড খুললে নতুন উচ্চতা কাজ করবে।"))

        // ---- শব্দ ও ভাইব্রেশন ----
        column.addView(title("কী চাপার শব্দ"))
        addFeedbackControls(
            column, prefs,
            switchText = "কী চাপলে শব্দ হবে",
            onKey = Feedback.KEY_SOUND_ON, valueKey = Feedback.KEY_SOUND_VOL,
            defaultValue = Feedback.DEFAULT_SOUND_VOL, labelPrefix = "শব্দের মাত্রা",
            note = "ফোন সাইলেন্ট বা ভাইব্রেট মোডে থাকলে শব্দ বাজে না। স্লাইডার ছাড়লে নমুনা শোনা যায়।",
            preview = { Feedback.previewSound(this) },
        )

        column.addView(title("ভাইব্রেশন"))
        addFeedbackControls(
            column, prefs,
            switchText = "কী চাপলে ভাইব্রেশন হবে",
            onKey = Feedback.KEY_VIBRATE_ON, valueKey = Feedback.KEY_VIBRATE_LEVEL,
            defaultValue = Feedback.DEFAULT_VIBRATE_LEVEL, labelPrefix = "ভাইব্রেশনের মাত্রা",
            note = "স্লাইডার ছাড়লে নমুনা অনুভব করা যায়। ২ সেকেন্ড চেপে ধরার কমান্ডে কম্পন দ্বিগুণ হয়।",
            preview = { Feedback.previewVibration(this) },
        )

        column.addView(title("বাটনগুলো"))
        column.addView(body(
            "• বাং / EN — বাংলা ও ইংরেজির মধ্যে বদল (চেপে ধরলে কিবোর্ড বদলানোর মেনু)\n" +
                "• 😊 — ইমোজি প্যানেল (সাম্প্রতিক ইমোজি আগে দেখায়)\n" +
                "• শিফট (⇧) বাংলায় শুধু কী-র ইংরেজি হরফ বড়/ছোট দেখায়; টাইপিংয়ে কোনো প্রভাব নেই\n" +
                "• বাংলা মোডে ',' দুইবার = হসন্ত;  qw = ঃ;  ng / qn = ং;  nnn = ঞ;  nng = ঙ\n" +
                "• b = শুধু স্বাধীন ব (যুক্ত হয় না);  w = ব্যঞ্জনের পর ব-ফলা, শুরুতে/স্বরের পর ও\n" +
                "• লাল o = যুক্তাক্ষরে বাধা (ব্যঞ্জনের পর দেখা যায়, পরের অক্ষর এলে সরে যায়)\n" +
                "• সবুজ q = ভাঙার চিহ্ন (একইভাবে মার্কার দেখায়); q সক্রিয় থাকলে যেসব কী-র ফল বদলায় তাদের ইঙ্গিত সবুজ\n" +
                "• q + অক্ষর = স্বাধীন (ব্যঞ্জনের পরেও): qa = আ, qi = ই, qoo = ও, qz = য, qh = হ, qx = ক্স, q. = ?, q, = !\n" +
                "• স্বরবর্ণে য-ফলা: oza = অ্যা (ভাঙতে q: oqzotha = অযথা)\n" +
                "• শব্দ লেখার সময় কী-গুলোর গায়ে নীল ইঙ্গিত দেখায়: এরপর কোন কী চাপলে কী আসবে (কার, ফলা, খ/ঘ/ছ ইত্যাদিও)\n" +
                "• র‍্য: শব্দের শুরুতে rz, অন্য জায়গায় rqz (রেফ-য হলে শুধু rz: কার্য = karz)"
        ))

        column.addView(title("২ সেকেন্ড চেপে ধরলে (এডিট কমান্ড)"))
        column.addView(body(
            "• X — কাট     • C — কপি     • V — পেস্ট\n" +
                "• Z — আনডু (আগের অবস্থায় ফেরা)     • Y — রিডু (আবার করা)\n" +
                "• A — সব নির্বাচন\n" +
                "চেপে ধরলে কী-র নিচে একটা লাইন ভরতে থাকে; ভরে গেলে কমান্ড চলে। তার আগে ছেড়ে দিলে সাধারণ অক্ষরই বসে।"
        ))

        column.addView(title("টাইপিং চিটশিট"))
        column.addView(body(
            "amar → আমার     kotha → কথা     prem → প্রেম\n" +
                "bangla → বাংলা     utqtor → উত্তর     budqdhi → বুদ্ধি\n" +
                "boqi → বই     hotthaqt → হঠাৎ     caqqd → চাঁদ\n" +
                "dhorm → ধর্ম     durga → দুর্গা     bidza → বিদ্যা\n" +
                "jnnnan → জ্ঞান     xoma → ক্ষমা     rrrsssi → ঋষি\n" +
                "ghorri → ঘড়ি     duqwkh → দুঃখ     swopn → স্বপ্ন\n\n" +
                "q = ভাঙার চিহ্ন;  ng / qn = ং,  nnn = ঞ,  nng = ঙ,  qt = ৎ (সব জায়গায়),  qq = ঁ,  qk = ৳,  qw = ঃ"
        ))

        setContentView(ScrollView(this).apply { addView(column) })
    }
}
