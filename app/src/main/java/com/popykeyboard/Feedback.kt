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
 *  - শব্দ: কী-র ধরন অনুযায়ী ৪টি আলাদা ক্লিক (সাধারণ, স্পেস, মোছা, এন্টার)।
 *    ফোন সাইলেন্ট/ভাইব্রেট মোডে থাকলে শব্দ বাজে না।
 *  - কম্পন: মাত্রা ১–১০০; অ্যামপ্লিচিউড-নিয়ন্ত্রণ থাকলে জোর বদলায়, না থাকলে সময় বদলায়।
 */
object Feedback {

    const val PREFS = "keyboard"
    const val KEY_SOUND_ON = "sound_on"
    const val KEY_SOUND_VOL = "sound_vol"
    const val KEY_VIBRATE_ON = "vibrate_on"
    const val KEY_VIBRATE_LEVEL = "vibrate_level"

    const val DEFAULT_SOUND_VOL = 60
    const val DEFAULT_VIBRATE_LEVEL = 50

    private enum class Snd { STANDARD, SPACE, DELETE, ENTER }

    private var pool: SoundPool? = null
    private val ids = IntArray(Snd.values().size)

    private fun prefs(ctx: Context) =
        ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    fun soundOn(ctx: Context) = prefs(ctx).getBoolean(KEY_SOUND_ON, true)
    fun soundVolume(ctx: Context) = prefs(ctx).getInt(KEY_SOUND_VOL, DEFAULT_SOUND_VOL).coerceIn(1, 100)
    fun vibrateOn(ctx: Context) = prefs(ctx).getBoolean(KEY_VIBRATE_ON, true)
    fun vibrateLevel(ctx: Context) = prefs(ctx).getInt(KEY_VIBRATE_LEVEL, DEFAULT_VIBRATE_LEVEL).coerceIn(1, 100)

    /** কিবোর্ড তৈরির সময় ডাকলে শব্দ আগে থেকে লোড থাকে, প্রথম চাপেই বাজে */
    fun prepare(ctx: Context) {
        if (pool != null) return
        val attrs = AudioAttributes.Builder()
            .setUsage(AudioAttributes.USAGE_ASSISTANCE_SONIFICATION)
            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
            .build()
        val p = SoundPool.Builder().setMaxStreams(4).setAudioAttributes(attrs).build()
        val app = ctx.applicationContext
        ids[Snd.STANDARD.ordinal] = p.load(app, R.raw.key_standard, 1)
        ids[Snd.SPACE.ordinal] = p.load(app, R.raw.key_space, 1)
        ids[Snd.DELETE.ordinal] = p.load(app, R.raw.key_delete, 1)
        ids[Snd.ENTER.ordinal] = p.load(app, R.raw.key_enter, 1)
        pool = p
    }

    fun release() {
        pool?.release()
        pool = null
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
