package com.jmotors.core.util

import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer

/**
 * System STT for the glasses voice loop. Russian free-form, Korean names/models allowed.
 */
class ChonikSttManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening: Boolean = false

    var onResult: (String) -> Unit = {}
    var onListeningChanged: (Boolean) -> Unit = {}
    var onError: (String) -> Unit = {}

    fun initialize() {
        if (recognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            onError("Распознавание речи на устройстве недоступно.")
            return
        }
        runOnMain {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(listener)
            }
        }
    }

    fun startListening() {
        runOnMain {
            if (recognizer == null) initialize()
            val engine = recognizer ?: return@runOnMain
            if (listening) {
                engine.cancel()
            }
            listening = true
            onListeningChanged(true)
            engine.startListening(recognitionIntent())
        }
    }

    fun stopListening() {
        runOnMain {
            listening = false
            onListeningChanged(false)
            recognizer?.stopListening()
            recognizer?.cancel()
        }
    }

    fun destroy() {
        runOnMain {
            listening = false
            onListeningChanged(false)
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
            recognizer = null
        }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_PROMPT, "Говори с Чоником")
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_400,
            )
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_000,
            )
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            onListeningChanged(true)
        }

        override fun onBeginningOfSpeech() = Unit

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            onListeningChanged(false)
        }

        override fun onError(error: Int) {
            listening = false
            onListeningChanged(false)
            onError(error.toSttMessage())
        }

        override fun onResults(results: Bundle?) {
            listening = false
            onListeningChanged(false)
            val text = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (text.isNotEmpty()) {
                onResult(text)
            } else {
                onError("empty")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) = Unit

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun runOnMain(block: () -> Unit) {
        if (Looper.myLooper() == Looper.getMainLooper()) {
            block()
        } else {
            mainHandler.post(block)
        }
    }

    private fun Int.toSttMessage(): String = when (this) {
        SpeechRecognizer.ERROR_NO_MATCH,
        SpeechRecognizer.ERROR_SPEECH_TIMEOUT,
        SpeechRecognizer.ERROR_AUDIO,
        -> "retry"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "Сеть для распознавания речи недоступна."
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> "retry"
        else -> "retry"
    }
}
