package com.jmotors.core.util

import android.content.Context
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import android.util.Log
import java.util.Locale
import kotlin.math.sqrt

/**
 * System TTS for Чоник. Speaks Russian and reports a 0..1 amplitude for the sphere pulse.
 */
class ChonikTtsManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var tts: TextToSpeech? = null
    private var ready: Boolean = false
    private var speaking: Boolean = false
    private var onBufferPosition: ((Float) -> Unit)? = null
    private var onComplete: (() -> Unit)? = null
    private var pendingText: String? = null

    private val breathPulse = object : Runnable {
        override fun run() {
            if (!speaking) return
            val phase = (System.currentTimeMillis() % 420L) / 420.0
            val fake = (0.28f + 0.42f * kotlin.math.abs(kotlin.math.sin(phase * Math.PI * 2)).toFloat())
            onBufferPosition?.invoke(fake)
            mainHandler.postDelayed(this, 80)
        }
    }

    fun initialize(onReady: (Boolean) -> Unit = {}) {
        if (tts != null) {
            onReady(ready)
            return
        }
        tts = TextToSpeech(appContext) { status ->
            ready = status == TextToSpeech.SUCCESS
            if (ready) {
                val russian = Locale("ru", "RU")
                val result = tts?.setLanguage(russian)
                if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                    tts?.language = Locale.getDefault()
                }
                tts?.setSpeechRate(1.03f)
                tts?.setPitch(1.02f)
                tts?.setOnUtteranceProgressListener(progressListener)
                Log.i("JMotors", "TTS ready")
            }
            onReady(ready)
            pendingText?.let { queued ->
                pendingText = null
                speak(queued, onBufferPosition ?: {}, onComplete ?: {})
            }
        }
    }

    /**
     * Speaks [text]. [onBufferPosition] receives a 0..1 level for avatar scale (RMS or breath fallback).
     */
    fun speak(
        text: String,
        onBufferPosition: (Float) -> Unit,
        onComplete: () -> Unit = {},
    ) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) {
            onComplete()
            return
        }
        this.onBufferPosition = onBufferPosition
        this.onComplete = onComplete
        if (!ready) {
            pendingText = trimmed
            initialize()
            return
        }
        speaking = true
        mainHandler.removeCallbacks(breathPulse)
        mainHandler.post(breathPulse)
        val params = Bundle()
        tts?.speak(trimmed, TextToSpeech.QUEUE_FLUSH, params, UTTERANCE_ID)
    }

    fun stop() {
        speaking = false
        mainHandler.removeCallbacks(breathPulse)
        tts?.stop()
        onBufferPosition?.invoke(0f)
    }

    fun shutdown() {
        stop()
        tts?.setOnUtteranceProgressListener(null)
        tts?.shutdown()
        tts = null
        ready = false
        onBufferPosition = null
        onComplete = null
    }

    private val progressListener = object : UtteranceProgressListener() {
        override fun onStart(utteranceId: String?) {
            speaking = true
            mainHandler.post { onBufferPosition?.invoke(0.35f) }
        }

        override fun onDone(utteranceId: String?) {
            Log.i("JMotors", "TTS onDone")
            finishSpeaking()
        }

        @Deprecated("Deprecated in Java")
        override fun onError(utteranceId: String?) {
            finishSpeaking()
        }

        override fun onError(utteranceId: String?, errorCode: Int) {
            finishSpeaking()
        }

        override fun onAudioAvailable(utteranceId: String?, audio: ByteArray?) {
            val amplitude = rmsAmplitude(audio) ?: return
            mainHandler.post { onBufferPosition?.invoke(amplitude) }
        }
    }

    private fun finishSpeaking() {
        speaking = false
        mainHandler.removeCallbacks(breathPulse)
        mainHandler.post {
            onBufferPosition?.invoke(0f)
            val done = onComplete
            onComplete = null
            done?.invoke()
        }
    }

    private fun rmsAmplitude(audio: ByteArray?): Float? {
        if (audio == null || audio.size < 4) return null
        var sumSquares = 0.0
        var samples = 0
        var i = 0
        while (i + 1 < audio.size) {
            val sample = (audio[i].toInt() and 0xFF) or (audio[i + 1].toInt() shl 8)
            val signed = sample.toShort().toInt()
            sumSquares += signed.toDouble() * signed
            samples++
            i += 2
        }
        if (samples == 0) return null
        val rms = sqrt(sumSquares / samples)
        return (rms / 10_000.0).toFloat().coerceIn(0.18f, 1f)
    }

    private companion object {
        const val UTTERANCE_ID = "chonik_utterance"
    }
}
