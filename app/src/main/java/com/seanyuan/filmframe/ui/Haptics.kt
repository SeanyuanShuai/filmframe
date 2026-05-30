package com.seanyuan.filmframe.ui

import android.content.Context
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * Tiered haptics. Four intensities mapped to product hierarchy so the phone
 * speaks at the right volume for the moment:
 *
 *   tick     incidental selection — tab swap, knob nudge, swatch pick, slider step
 *   light    a committed toggle / setting change
 *   medium   a primary action firing — 导入, 导出
 *   success  terminal confirmation — export finished (a double pulse)
 *
 * On API 29+ each tier uses a distinct predefined primitive (TICK / CLICK /
 * HEAVY_CLICK) so the motor's own tuning carries the difference; older devices
 * fall back to amplitude-scaled one-shots.
 */
class Haptics(private val vibrator: Vibrator?) {

    fun tick() = oneShot(VibrationEffect.EFFECT_TICK, ms = 10, amplitude = 55)
    fun light() = oneShot(VibrationEffect.EFFECT_CLICK, ms = 16, amplitude = 100)
    fun medium() = oneShot(VibrationEffect.EFFECT_HEAVY_CLICK, ms = 24, amplitude = 170)

    fun success() {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val timings = longArrayOf(0, 20, 70, 36)
        val effect = if (v.hasAmplitudeControl()) {
            VibrationEffect.createWaveform(timings, intArrayOf(0, 150, 0, 235), -1)
        } else {
            VibrationEffect.createWaveform(timings, -1)
        }
        v.vibrate(effect)
    }

    private fun oneShot(predefined: Int, ms: Long, amplitude: Int) {
        val v = vibrator?.takeIf { it.hasVibrator() } ?: return
        val effect = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            VibrationEffect.createPredefined(predefined)
        } else {
            VibrationEffect.createOneShot(ms, amplitude)
        }
        v.vibrate(effect)
    }
}

@Composable
fun rememberHaptics(): Haptics {
    val context = LocalContext.current
    return remember {
        val vib = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            (context.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager)?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            context.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
        Haptics(vib)
    }
}
