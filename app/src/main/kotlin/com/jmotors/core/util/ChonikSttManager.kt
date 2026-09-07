package com.jmotors.core.util

import android.content.Context
import android.content.Intent
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log

/**
 * System STT for the glasses voice loop. Russian free-form, Korean names/models allowed.
 */
class ChonikSttManager(
    context: Context,
) {
    private val appContext = context.applicationContext
    private val audioManager = appContext.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private val mainHandler = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var listening: Boolean = false
    private var routedToHeadset: Boolean = false

    var onResult: (String) -> Unit = {}
    var onListeningChanged: (Boolean) -> Unit = {}
    var onError: (String) -> Unit = {}

    fun initialize() {
        if (recognizer != null) return
        if (!SpeechRecognizer.isRecognitionAvailable(appContext)) {
            Log.e(TAG, "SpeechRecognizer is not available")
            onError("Распознавание речи на устройстве недоступно.")
            return
        }
        runOnMain {
            recognizer = SpeechRecognizer.createSpeechRecognizer(appContext).apply {
                setRecognitionListener(listener)
            }
            Log.i(TAG, "SpeechRecognizer created")
        }
    }

    fun startListening() {
        runOnMain {
            if (recognizer == null) initialize()
            val engine = recognizer ?: return@runOnMain
            if (listening) {
                Log.d(TAG, "already listening, skip restart")
                return@runOnMain
            }
            listening = true
            onListeningChanged(true)
            routeCaptureToHeadset()
            Log.i(TAG, "startListening")
            engine.startListening(recognitionIntent())
        }
    }

    fun stopListening() {
        runOnMain {
            listening = false
            onListeningChanged(false)
            try {
                recognizer?.stopListening()
            } catch (error: Exception) {
                Log.w(TAG, "stopListening: ${error.message}")
            }
            restoreAudioRoute()
        }
    }

    fun destroy() {
        runOnMain {
            listening = false
            onListeningChanged(false)
            recognizer?.setRecognitionListener(null)
            recognizer?.destroy()
            recognizer = null
            restoreAudioRoute()
        }
    }

    private fun recognitionIntent(): Intent =
        Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "ru-RU")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 3)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, appContext.packageName)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS, 2_500L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 2_200L)
            putExtra(
                RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,
                1_800L,
            )
        }

    private val listener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            listening = true
            onListeningChanged(true)
            Log.i(TAG, "onReadyForSpeech")
        }

        override fun onBeginningOfSpeech() {
            Log.i(TAG, "onBeginningOfSpeech")
        }

        override fun onRmsChanged(rmsdB: Float) = Unit

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            listening = false
            onListeningChanged(false)
            Log.i(TAG, "onEndOfSpeech")
        }

        override fun onError(error: Int) {
            listening = false
            onListeningChanged(false)
            Log.w(TAG, "onError code=$error")
            if (error == SpeechRecognizer.ERROR_CLIENT || error == SpeechRecognizer.ERROR_RECOGNIZER_BUSY) {
                recreateRecognizer()
            }
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
            Log.i(TAG, "onResults: $text")
            if (text.isNotEmpty()) {
                onResult(text)
            } else {
                onError("empty")
            }
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
            if (!partial.isNullOrBlank()) {
                Log.d(TAG, "partial: $partial")
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * SpeechRecognizer uses the voice-call path. On XREAL that path stays on the
     * phone earpiece unless we pin the USB headset as the communication device.
     */
    private fun routeCaptureToHeadset() {
        audioManager.mode = AudioManager.MODE_IN_COMMUNICATION
        audioManager.isMicrophoneMute = false
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val devices = audioManager.availableCommunicationDevices
        Log.i(TAG, "comm devices: " + devices.joinToString { "${it.productName}:${it.type}" })
        val headset = devices.firstOrNull { it.type.isHeadsetInput() }
        if (headset == null) {
            Log.w(TAG, "no USB/wired headset in communication devices")
            return
        }
        val ok = audioManager.setCommunicationDevice(headset)
        routedToHeadset = ok
        Log.i(TAG, "setCommunicationDevice ${headset.productName} type=${headset.type} ok=$ok")
    }

    private fun restoreAudioRoute() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && routedToHeadset) {
            audioManager.clearCommunicationDevice()
            routedToHeadset = false
        }
        audioManager.mode = AudioManager.MODE_NORMAL
    }

    private fun Int.isHeadsetInput(): Boolean = this == AudioDeviceInfo.TYPE_USB_HEADSET ||
        this == AudioDeviceInfo.TYPE_USB_DEVICE ||
        this == AudioDeviceInfo.TYPE_WIRED_HEADSET ||
        this == AudioDeviceInfo.TYPE_BLE_HEADSET

    private fun recreateRecognizer() {
        try {
            recognizer?.destroy()
        } catch (_: Exception) {
            // ignore
        }
        recognizer = null
        initialize()
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
        SpeechRecognizer.ERROR_CLIENT,
        SpeechRecognizer.ERROR_RECOGNIZER_BUSY,
        -> "retry"
        SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> "Нет доступа к микрофону."
        SpeechRecognizer.ERROR_NETWORK,
        SpeechRecognizer.ERROR_NETWORK_TIMEOUT,
        -> "Сеть для распознавания речи недоступна."
        else -> "retry"
    }

    private companion object {
        const val TAG = "JMotors"
    }
}
