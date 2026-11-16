package com.example.game

import android.content.Context
import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioTrack
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.os.VibratorManager
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin
import kotlin.random.Random

class SoundSynthEngine(context: Context) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @Volatile
    var soundEnabled: Boolean = true

    private val vibrator: Vibrator? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            val manager = appContext.getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as? VibratorManager
            manager?.defaultVibrator
        } else {
            @Suppress("DEPRECATION")
            appContext.getSystemService(Context.VIBRATOR_SERVICE) as? Vibrator
        }
    } catch (_: Exception) {
        null
    }

    private val gunShotPcm by lazy { synthesizeShot(durationMs = 95, baseFreq = 180.0, noiseMix = 0.72) }
    private val sniperShotPcm by lazy { synthesizeShot(durationMs = 175, baseFreq = 120.0, noiseMix = 0.85) }
    private val hitMarkerPcm by lazy { synthesizeTone(durationMs = 55, startFreq = 1100.0, endFreq = 850.0, volume = 0.45) }
    private val headshotPcm by lazy { synthesizeTone(durationMs = 115, startFreq = 1480.0, endFreq = 2150.0, volume = 0.65) }
    private val reloadPcm by lazy { synthesizeTone(durationMs = 90, startFreq = 520.0, endFreq = 760.0, volume = 0.35) }
    private val glooPlacePcm by lazy { synthesizeTone(durationMs = 130, startFreq = 240.0, endFreq = 140.0, volume = 0.55) }
    private val roundWinPcm by lazy { synthesizeTone(durationMs = 260, startFreq = 523.0, endFreq = 784.0, volume = 0.5) }

    fun playFire(category: WeaponCategory) {
        vibrate(if (category == WeaponCategory.SNIPER || category == WeaponCategory.SHOTGUN) 35L else 16L)
        if (!soundEnabled) return
        val pcm = if (category == WeaponCategory.SNIPER || category == WeaponCategory.SHOTGUN) sniperShotPcm else gunShotPcm
        playPcm(pcm)
    }

    fun playHitMarker(isHeadshot: Boolean) {
        if (isHeadshot) vibrate(28L)
        if (!soundEnabled) return
        playPcm(if (isHeadshot) headshotPcm else hitMarkerPcm)
    }

    fun playReload() {
        if (!soundEnabled) return
        playPcm(reloadPcm)
    }

    fun playGlooPlace() {
        vibrate(24L)
        if (!soundEnabled) return
        playPcm(glooPlacePcm)
    }

    fun playRoundWin() {
        if (!soundEnabled) return
        playPcm(roundWinPcm)
    }

    private fun vibrate(ms: Long) {
        val vib = vibrator ?: return
        try {
            if (!vib.hasVibrator()) return
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                vib.vibrate(VibrationEffect.createOneShot(ms, VibrationEffect.DEFAULT_AMPLITUDE))
            } else {
                @Suppress("DEPRECATION")
                vib.vibrate(ms)
            }
        } catch (_: Exception) {
        }
    }

    private fun playPcm(samples: ShortArray) {
        scope.launch {
            try {
                val minBuf = AudioTrack.getMinBufferSize(
                    SAMPLE_RATE,
                    AudioFormat.CHANNEL_OUT_MONO,
                    AudioFormat.ENCODING_PCM_16BIT
                )
                val bufferBytes = maxOf(minBuf, samples.size * 2)
                val track = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setSampleRate(SAMPLE_RATE)
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(bufferBytes)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                track.write(samples, 0, samples.size)
                track.play()
                kotlinx.coroutines.delay((samples.size * 1000L / SAMPLE_RATE) + 40L)
                track.stop()
                track.release()
            } catch (_: Exception) {
            }
        }
    }

    private fun synthesizeShot(durationMs: Int, baseFreq: Double, noiseMix: Double): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        for (i in 0 until count) {
            val t = i.toDouble() / SAMPLE_RATE
            val env = exp(-t * 28.0)
            val freq = baseFreq * exp(-t * 14.0) + 45.0
            val osc = sin(2.0 * Math.PI * freq * t)
            val noise = Random.nextDouble(-1.0, 1.0)
            val sample = ((1.0 - noiseMix) * osc + noiseMix * noise) * env * 0.65
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private fun synthesizeTone(
        durationMs: Int,
        startFreq: Double,
        endFreq: Double,
        volume: Double,
    ): ShortArray {
        val count = (SAMPLE_RATE * durationMs) / 1000
        val out = ShortArray(count)
        var phase = 0.0
        for (i in 0 until count) {
            val progress = i.toDouble() / count
            val freq = startFreq + (endFreq - startFreq) * progress
            val env = exp(-progress * 3.8)
            phase += 2.0 * Math.PI * freq / SAMPLE_RATE
            val sample = sin(phase) * env * volume
            out[i] = (sample.coerceIn(-1.0, 1.0) * Short.MAX_VALUE).toInt().toShort()
        }
        return out
    }

    private companion object {
        const val SAMPLE_RATE = 22050
    }
}
