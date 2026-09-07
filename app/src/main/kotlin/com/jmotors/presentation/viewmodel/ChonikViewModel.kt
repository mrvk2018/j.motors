package com.jmotors.presentation.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmotors.core.util.ChonikSttManager
import com.jmotors.core.util.ChonikTtsManager
import com.jmotors.data.repository.AiRepositoryImpl
import com.jmotors.domain.model.ai.ChonikEmotion
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.PaymentMethod
import com.jmotors.domain.model.ai.UserProfile
import com.jmotors.domain.model.ai.VehicleCategory
import com.jmotors.domain.model.ai.VisaType
import com.jmotors.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dialogue, Gemini, emotion, and the glasses voice loop (STT → think → TTS → listen).
 * SBS 3D layout lives in [com.jmotors.presentation.ar.ArShowroomScreen].
 */
class ChonikViewModel @JvmOverloads constructor(
    application: Application,
    private val aiRepository: AiRepository = AiRepositoryImpl(),
) : AndroidViewModel(application) {

    private val mainHandler = Handler(Looper.getMainLooper())
    private val ttsManager = ChonikTtsManager(application)
    private val sttManager = ChonikSttManager(application)

    private var voiceLoopEnabled: Boolean = false
    private var greetingSpoken: Boolean = false

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    private val _state = MutableStateFlow<ChonikState>(
        ChonikState.Greeting(openingLine = GREETING_POOL.random()),
    )
    val state: StateFlow<ChonikState> = _state.asStateFlow()

    private val _assistantReply = MutableStateFlow<String?>(null)
    val assistantReply: StateFlow<String?> = _assistantReply.asStateFlow()

    private val _emotion = MutableStateFlow(ChonikEmotion.CALM)
    val emotion: StateFlow<ChonikEmotion> = _emotion.asStateFlow()

    private val _audioAmplitude = MutableStateFlow(0f)
    val audioAmplitude: StateFlow<Float> = _audioAmplitude.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val _isSpeaking = MutableStateFlow(false)
    val isSpeaking: StateFlow<Boolean> = _isSpeaking.asStateFlow()

    private val _errorMessage = MutableStateFlow<String?>(null)
    val errorMessage: StateFlow<String?> = _errorMessage.asStateFlow()

    init {
        sttManager.onResult = { text -> onUserSpeech(text) }
        sttManager.onListeningChanged = { listening -> _isListening.value = listening }
        sttManager.onError = { message -> onSttError(message) }
        ttsManager.initialize()
        sttManager.initialize()
    }

    /** Call from the showroom after RECORD_AUDIO is granted. */
    fun startVoiceLoop() {
        if (voiceLoopEnabled) return
        voiceLoopEnabled = true
        val greeting = (_state.value as? ChonikState.Greeting)?.openingLine
        if (!greetingSpoken && greeting != null && _assistantReply.value.isNullOrBlank()) {
            greetingSpoken = true
            _assistantReply.value = greeting
            speakThenListen(greeting)
        } else {
            listenForUser()
        }
    }

    fun pauseVoiceLoop() {
        voiceLoopEnabled = false
        ttsManager.stop()
        sttManager.stopListening()
        _isSpeaking.value = false
        _isListening.value = false
        _audioAmplitude.value = 0f
    }

    fun submitName(name: String) {
        val trimmed = name.trim()
        if (trimmed.isEmpty()) return
        _userProfile.update { it.copy(name = trimmed) }
        _state.value = ChonikState.CategorySelection
        generateReply("Меня зовут $trimmed.")
    }

    fun selectCategory(category: VehicleCategory) {
        if (category == VehicleCategory.UNKNOWN) return
        _userProfile.update { it.copy(vehicleCategory = category) }
        _state.value = ChonikState.ModelDiscussion()
        generateReply("Интересует категория: $category")
    }

    fun submitChosenModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        _userProfile.update { it.copy(chosenModel = trimmed) }
        _state.value = ChonikState.ModelDiscussion(modelName = trimmed)
        generateReply("Хочу обсудить модель $trimmed на корейском рынке.")
    }

    fun finishModelDiscussion() {
        _state.value = ChonikState.FinancialQualification
        generateReply("Давай перейдём к бюджету, визе и работе.")
    }

    fun submitFinancials(
        budget: Long,
        paymentMethod: PaymentMethod,
        visaType: VisaType,
        isOfficiallyEmployed: Boolean,
    ) {
        _userProfile.update {
            it.copy(
                budget = budget,
                paymentMethod = paymentMethod,
                visaType = visaType,
                isOfficiallyEmployed = isOfficiallyEmployed,
            )
        }
        _state.value = ChonikState.MarketCalculation
        generateReply(
            "Бюджет $budget KRW, оплата $paymentMethod, виза $visaType, " +
                "официальная работа=$isOfficiallyEmployed. Посчитай налог 7%, страховку и техосмотр.",
        )
    }

    fun finishMarketCalculation() {
        _state.value = ChonikState.HandoverToOffice
        logHandoverStub()
        generateReply("Передай заявку в офис Сувона и предложи бесплатную дорогу до шоурума.")
    }

    fun logHandoverStub() {
        Log.d("JMotors", "Handover stub → Suwon office: ${_userProfile.value}")
    }

    fun sendUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        generateReply(trimmed)
    }

    fun updateAudioAmplitude(amplitude: Float) {
        _audioAmplitude.value = amplitude.coerceIn(0f, 1f)
    }

    private fun onUserSpeech(text: String) {
        Log.i("JMotors", "STT heard: $text")
        if (!voiceLoopEnabled || _isSpeaking.value || _isGenerating.value) return
        sendUserMessage(text)
    }

    private fun onSttError(message: String) {
        Log.w("JMotors", "STT error: $message")
        if (message != "retry" && message.isNotBlank() && message != "empty") {
            _errorMessage.value = message
        }
        if (voiceLoopEnabled && (message == "retry" || message == "empty")) {
            scheduleListenRetry()
        }
    }

    private fun generateReply(userMessage: String) {
        sttManager.stopListening()
        ttsManager.stop()
        _isListening.value = false
        _isSpeaking.value = false
        viewModelScope.launch {
            _isGenerating.value = true
            _errorMessage.value = null
            _emotion.value = ChonikEmotion.CALM
            _audioAmplitude.value = 0f
            runCatching {
                aiRepository.generateReply(
                    userMessage = userMessage,
                    profile = _userProfile.value,
                    state = _state.value,
                )
            }.onSuccess { rawReply ->
                val parsed = parseEmotionTag(rawReply)
                _emotion.value = parsed.emotion
                _assistantReply.value = parsed.visibleText
                _isGenerating.value = false
                speakThenListen(parsed.visibleText)
            }.onFailure { error ->
                _errorMessage.value = error.message ?: "Не удалось получить ответ Чоника"
                _emotion.value = ChonikEmotion.CALM
                _isGenerating.value = false
                listenForUser()
            }
            _isGenerating.value = false
        }
    }

    private fun speakThenListen(text: String) {
        if (text.isBlank()) {
            listenForUser()
            return
        }
        sttManager.stopListening()
        _isListening.value = false
        _isSpeaking.value = true
        ttsManager.speak(
            text = text,
            onBufferPosition = { amplitude -> updateAudioAmplitude(amplitude) },
            onComplete = {
                _isSpeaking.value = false
                _audioAmplitude.value = 0f
                // Let TTS release the mic / audio focus before STT starts.
                mainHandler.postDelayed({ listenForUser() }, 800)
            },
        )
    }

    private fun listenForUser() {
        if (!voiceLoopEnabled || _isSpeaking.value || _isGenerating.value) {
            Log.d("JMotors", "STT skip listen enabled=$voiceLoopEnabled speaking=${_isSpeaking.value} generating=${_isGenerating.value}")
            return
        }
        Log.i("JMotors", "STT listenForUser")
        sttManager.startListening()
    }

    private fun scheduleListenRetry() {
        mainHandler.removeCallbacks(listenRetry)
        mainHandler.postDelayed(listenRetry, 1_200)
    }

    private val listenRetry = Runnable { listenForUser() }

    private fun parseEmotionTag(rawReply: String): ParsedReply {
        val matches = EMOTION_TAG_REGEX.findAll(rawReply).toList()
        val emotion = matches.lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
            ?.let { name -> ChonikEmotion.entries.find { it.name == name } }
            ?: ChonikEmotion.CALM
        val visibleText = EMOTION_TAG_REGEX.replace(rawReply, "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return ParsedReply(emotion = emotion, visibleText = visibleText)
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(listenRetry)
        pauseVoiceLoop()
        ttsManager.shutdown()
        sttManager.destroy()
        super.onCleared()
    }

    private data class ParsedReply(
        val emotion: ChonikEmotion,
        val visibleText: String,
    )

    private companion object {
        val EMOTION_TAG_REGEX: Regex =
            Regex("""\[EMOTION:\s*(CALM|JOY|WARNING|SARCASM|DELIGHT)\s*\]""", RegexOption.IGNORE_CASE)

        val GREETING_POOL: List<String> = listOf(
            "Привет! Я Чоник из J Motors — давай честно разберёмся, какая машина в Корее тебе реально подойдёт, а не та, что красиво блестит на фото. Как тебя зовут?",
            "О, живой человек, а не очередной «просто смотрю». Я Чоник. Если ищем авто или байк без сказок про идеальный вариант — ты по адресу. Как к тебе обращаться?",
            "Добрый день! Чоник на связи. Я не робот-продажник: сначала спрошу, кто ты и что тебе нужно, и только потом посчитаем корейские налоги. Как тебя зовут?",
        )
    }
}
