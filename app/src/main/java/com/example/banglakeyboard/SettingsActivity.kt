package com.example.banglakeyboard

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

        column.addView(title("বাটনগুলো"))
        column.addView(body(
            "• বাং / EN — বাংলা ও ইংরেজির মধ্যে বদল (চেপে ধরলে কিবোর্ড বদলানোর মেনু)\n" +
                "• 😊 — ইমোজি প্যানেল (সাম্প্রতিক ইমোজি আগে দেখায়)\n" +
                "• বাংলা মোডে শিফট লাগে না; সব ছোট হাতের অক্ষর\n" +
                "• বাংলা মোডে ',' দুইবার = হসন্ত, ':' দুইবার = ঃ"
        ))

        column.addView(title("টাইপিং চিটশিট"))
        column.addView(body(
            "amar → আমার     kotha → কথা     prem → প্রেম\n" +
                "baqngla → বাংলা     utqtor → উত্তর     budqdhi → বুদ্ধি\n" +
                "boqi → বই     hotthaqt → হঠাৎ     caqqd → চাঁদ\n" +
                "dhorm → ধর্ম     durga → দুর্গা     bidza → বিদ্যা\n" +
                "jqnyan → জ্ঞান     xoma → ক্ষমা     rrisssi → ঋষি\n" +
                "ghorrqi → ঘড়ি     du::kh → দুঃখ     swopn → স্বপ্ন\n\n" +
                "q = ভাঙার চিহ্ন;  স্বরের পরে qt = ৎ,  qng = ং,  qq = ঁ,  qk = ৳"
        ))

        setContentView(ScrollView(this).apply { addView(column) })
    }
}
