package com.example.audio

import android.media.AudioAttributes
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioTrack
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.exp
import kotlin.math.sin

object AudioSynthesizer {
    private val scope = CoroutineScope(Dispatchers.Default)

    // Standard frequencies for matching pairs (C4 to C5 scale)
    val pairFrequencies = listOf(
        261.63f, // C4
        293.66f, // D4
        329.63f, // E4
        349.23f, // F4
        392.00f, // G4
        440.00f, // A4
        493.88f, // B4
        523.25f  // C5
    )

    fun playTone(frequency: Float, durationMs: Long = 250, decayRate: Double = 8.0) {
        scope.launch {
            try {
                val sampleRate = 44100
                val numSamples = (sampleRate * (durationMs / 1000.0)).toInt()
                val buffer = ShortArray(numSamples)

                for (i in 0 until numSamples) {
                    val t = i.toDouble() / sampleRate
                    // Exponential decay for plucking sound
                    val decay = exp(-t * decayRate)
                    val sample = sin(2.0 * Math.PI * frequency * t) * decay
                    buffer[i] = (sample * 32767).toInt().coerceIn(-32768, 32767).toShort()
                }

                val audioTrack = AudioTrack.Builder()
                    .setAudioAttributes(
                        AudioAttributes.Builder()
                            .setUsage(AudioAttributes.USAGE_GAME)
                            .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION)
                            .build()
                    )
                    .setAudioFormat(
                        AudioFormat.Builder()
                            .setEncoding(AudioFormat.ENCODING_PCM_16BIT)
                            .setSampleRate(sampleRate)
                            .setChannelMask(AudioFormat.CHANNEL_OUT_MONO)
                            .build()
                    )
                    .setBufferSizeInBytes(buffer.size * 2)
                    .setTransferMode(AudioTrack.MODE_STATIC)
                    .build()

                audioTrack.write(buffer, 0, buffer.size)
                audioTrack.play()
                delay(durationMs + 50)
                audioTrack.stop()
                audioTrack.release()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    fun playMatchSuccess() {
        scope.launch {
            playTone(392.00f, 100, 15.0) // G4
            delay(80)
            playTone(523.25f, 150, 10.0) // C5
        }
    }

    fun playMatchFailure() {
        scope.launch {
            playTone(180.00f, 200, 5.0) // Buzzy low note
        }
    }

    fun playLevelComplete() {
        scope.launch {
            val notes = listOf(261.63f, 329.63f, 392.00f, 523.25f)
            for (note in notes) {
                playTone(note, 150, 8.0)
                delay(120)
            }
        }
    }

    fun playSequenceReveal(index: Int) {
        // Play an rising synth scale based on reveal order
        val notes = listOf(261.63f, 293.66f, 329.63f, 349.23f, 392.00f, 440.00f, 493.88f, 523.25f)
        val note = notes.getOrElse(index % notes.size) { 440.00f }
        playTone(note, 200, 12.0)
    }
}
