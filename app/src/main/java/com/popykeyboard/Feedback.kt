package com.popykeyboard

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioManager
import android.media.SoundPool
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator

/**
 * কী চাপার শব্দ ও কম্পন। সেটিংসের পছন্দ ("keyboard" SharedPreferences) প্রতিবার পড়া হয়,
 * তাই সেটিংসে বদলালে সঙ্গে সঙ্গে কাজ করে।
 *
 *  - শব্দ: বাছাই-করা থিমের ৪টি আলাদা আওয়াজ (সাধারণ, স্পেস, মোছা, এন্টার)।
 *    ফোন সাইলেন্ট/ভাইব্রেট মোডে থাকলে শব্দ বাজে না।
 *  - কম্পন: মাত্রা ১–১০০; অ্যামপ্লিচিউড-নিয়ন্ত্রণ থাকলে জোর বদলায়, না থাকলে সময় বদলায়।
 */
object Feedback {

    const val PREFS = "keyboard"
    const val KEY_SOUND_ON = "sound_on"
    const val KEY_SOUND_VOL = "sound_vol"
    const val KEY_VIBRATE_ON = "vibrate_on"
    const val KEY_VIBRATE_LEVEL = "vibrate_level"

    const val KEY_SOUND_THEME = "sound_theme"
    const val DEFAULT_THEME = "classic"

    /** একটি সাউন্ড থিম: id, বাংলা নাম, [সাধারণ, স্পেস, মোছা, এন্টার] এর raw রিসোর্স */
    class Theme(val id: String, val label: String, val res: IntArray)

    val THEMES: List<Theme> = listOf(
        Theme("classic", "ক্লিক (সাধারণ)", intArrayOf(R.raw.key_classic_standard, R.raw.key_classic_space, R.raw.key_classic_delete, R.raw.key_classic_enter)),
        Theme("typewriter", "টাইপরাইটার", intArrayOf(R.raw.key_typewriter_standard, R.raw.key_typewriter_space, R.raw.key_typewriter_delete, R.raw.key_typewriter_enter)),
        Theme("mechanical", "মেকানিক্যাল কিবোর্ড", intArrayOf(R.raw.key_mechanical_standard, R.raw.key_mechanical_space, R.raw.key_mechanical_delete, R.raw.key_mechanical_enter)),
        Theme("bubble", "বাবল পপ", intArrayOf(R.raw.key_bubble_standard, R.raw.key_bubble_space, R.raw.key_bubble_delete, R.raw.key_bubble_enter)),
        Theme("drop", "পানির ফোঁটা", intArrayOf(R.raw.key_drop_standard, R.raw.key_drop_space, R.raw.key_drop_delete, R.raw.key_drop_enter)),
        Theme("wood", "কাঠের টোকা", intArrayOf(R.raw.key_wood_standard, R.raw.key_wood_space, R.raw.key_wood_delete, R.raw.key_wood_enter)),
        Theme("retro", "রেট্রো বিপ", intArrayOf(R.raw.key_retro_standard, R.raw.key_retro_space, R.raw.key_retro_delete, R.raw.key_retro_enter)),
    )

    const val DEFAULT_SOUND_VOL = 60
    const val DEFAULT_VIBRATE_LEVEL = 50

    private enum class Snd { STANDARD, SPACE, DELETE, ENTER }

    private var pool: SoundPool? = null
    private val ids = IntArray(Snd.values().size)
    private var loadedTheme: String? = null

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun soundTheme(ctx: Context): String {
        val id = prefs(ctx).getString(KEY_SOUND_THEME, DEFAULT_THEME) ?: DEFAULT_THEME
        return if (THEMES.any { it.id == id }) id else DEFAULT_THEME
    }
    fun soundOn(ctx: Context) = prefs(ctx).getBoolean(KEY_SOUND_ON, true)
    fun soundVolume(ctx: Context) = prefs(ctx).getInt(KEY_SOUND_VOL, DEFAULT_SOUND_VOL).coerceIn(1, 100)
    fun vibrateOn(ctx: Context) = prefs(ctx).getBoolean(KEY_VIBRATE_ON, true)
    fun vibrateLevel(ctx: Context) = prefs(ctx).getInt(KEY_VIBRATE_LEVEL, DEFAULT_VIBRATE_LEVEL).coerceIn(1, 100)

    /** কিবোর্ড তৈরির সময় ডাকলে শব্দ আগে থেকে লোড থাকে, প্রথম চাপেই বাজে */
    fun prepare(ctx: Context) {
        val theme = soundTheme(ctx)
        if (pool != null && loadedTheme == theme) return
        pool?.release()
        pool = null
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        val app = ctx.applicationContext
        val res = THEMES.first { it.id == theme }.res
        for (snd in Snd.values()) ids[snd.ordinal] = p.load(app, res[snd.ordinal], 1)
        loadedTheme = theme
        pool = p
    }

    fun release() {
        pool?.release()
        pool = null
        loadedTheme = null
    }

    /** একটি কী চাপা হলে: কম্পন + শব্দ */
    fun press(ctx: Context, act: Act) {
        vibrate(ctx, 1f)
        play(
            ctx,
            when (act) {
                Act.SPACE -> Snd.SPACE
                Act.BACKSPACE -> Snd.DELETE
                Act.ENTER -> Snd.ENTER
                else -> Snd.STANDARD
            },
        )
    }

    /** ২ সেকেন্ড ধরে রাখার কমান্ড/চেপে ধরা পূর্ণ হলে: জোরালো কম্পন */
    fun longPress(ctx: Context) = vibrate(ctx, 2f)

    /** সেটিংস স্ক্রিনে স্লাইডার ছাড়লে নমুনা */
    fun previewSound(ctx: Context) = play(ctx, Snd.STANDARD)
    /** থিম বদলের পর: নতুন শব্দ লোড হতে একটু সময় লাগে, তাই কিছু পরে বাজাই */
    fun previewTheme(ctx: Context) {
        prepare(ctx)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({ play(ctx, Snd.STANDARD) }, 350)
    }
    fun previewVibration(ctx: Context) = vibrate(ctx, 1f)

    private fun play(ctx: Context, snd: Snd) {
        if (!soundOn(ctx)) return
        val am = ctx.applicationContext.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
        if (am != null && am.ringerMode != AudioManager.RINGER_MODE_NORMAL) return
        prepare(ctx)
        // মাত্রাকে বর্গ করা হয়, যাতে স্লাইডারের নিচের দিকেও কানে স্পষ্ট তফাত বোঝা যায়
        val v = soundVolume(ctx) / 100f
        val gain = v * v
        pool?.play(ids[snd.ordinal], gain, gain, 1, 0, 1f)
    }

    @Suppress("DEPRECATION")
    private fun vibrate(ctx: Context, scale: Float) {
        if (!vibrateOn(ctx)) return
        val level = vibrateLevel(ctx)
        val vib = ctx.applicationContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator ?: return
        if (!vib.hasVibrator()) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (vib.hasAmplitudeControl()) {
                val amp = (level * 255 / 100).coerceIn(1, 255)
                val ms = ((12 + level * 0.12f) * scale).toLong()
                vib.vibrate(VibrationEffect.createOneShot(ms, amp))
            } else {
                val ms = ((6 + level * 0.4f) * scale).toLong()
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            }
        } else {
            vib.vibrate(((6 + level * 0.4f) * scale).toLong())
        }
    }
}
