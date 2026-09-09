package com.jmotors.presentation.viewmodel

import android.app.Application
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.jmotors.core.util.ChonikSttManager
import com.jmotors.core.util.ChonikTtsManager
import com.jmotors.data.lead.SuwonLeadExporter
import com.jmotors.data.repository.AiRepositoryImpl
import com.jmotors.domain.model.ai.ChonikEmotion
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.ClientProfile
import com.jmotors.domain.model.ai.LeadFactExtractor
import com.jmotors.domain.model.ai.PaymentMethod
import com.jmotors.domain.model.ai.ShowcaseCar
import com.jmotors.domain.model.ai.SphereVisualState
import com.jmotors.domain.model.ai.UserProfile
import com.jmotors.domain.model.ai.VehicleCategory
import com.jmotors.domain.model.ai.VisaType
import com.jmotors.domain.model.ai.resolveSphereVisualState
import com.jmotors.domain.repository.AiRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * Dialogue, Gemini tag parsing, ClientProfile → Suwon JSON, and the glasses voice loop.
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
    private var leadExported: Boolean = false

    private val _userProfile = MutableStateFlow(UserProfile())
    val userProfile: StateFlow<UserProfile> = _userProfile.asStateFlow()

    /** Alias for the Suwon snapshot (name, model, cash/credit, visa). */
    val clientProfile: ClientProfile
        get() = ClientProfile.from(_userProfile.value)

    private val _state = MutableStateFlow<ChonikState>(
        ChonikState.Greeting(openingLine = GREETING_POOL.random()),
    )
    val state: StateFlow<ChonikState> = _state.asStateFlow()

    private val _assistantReply = MutableStateFlow<String?>(null)
    val assistantReply: StateFlow<String?> = _assistantReply.asStateFlow()

    private val _emotion = MutableStateFlow(ChonikEmotion.CALM)
    val emotion: StateFlow<ChonikEmotion> = _emotion.asStateFlow()

    private val _forcedSphereState = MutableStateFlow<SphereVisualState?>(null)

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

    private val _carType = MutableStateFlow(ShowcaseCar.AVANTE.key)
    val carType: StateFlow<String> = _carType.asStateFlow()

    /** Bumps on every [SET_CAR] so the showroom can replay materialize even for the same model. */
    private val _carRevealNonce = MutableStateFlow(0)
    val carRevealNonce: StateFlow<Int> = _carRevealNonce.asStateFlow()

    val sphereVisualState: StateFlow<SphereVisualState> = combine(
        _isGenerating,
        _errorMessage,
        _emotion,
        _state,
        _forcedSphereState,
    ) { generating, error, emotion, dialogueState, forced ->
        resolveSphereVisualState(
            isGenerating = generating,
            errorMessage = error,
            emotion = emotion,
            dialogueState = dialogueState,
            forcedState = forced,
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.Eagerly,
        initialValue = SphereVisualState.IDLE,
    )

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
            aiRepository.recordOpeningLine(greeting)
            speakThenListen(greeting)
        } else {
            listenForUser()
        }
    }

    fun pauseVoiceLoop() {
        voiceLoopEnabled = false
        mainHandler.removeCallbacks(listenRetry)
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
        syncCarType(_userProfile.value)
        _state.value = ChonikState.ModelDiscussion()
        generateReply("Меня зовут $trimmed.")
    }

    fun selectCategory(category: VehicleCategory) {
        if (category == VehicleCategory.UNKNOWN) return
        _userProfile.update { it.copy(vehicleCategory = category) }
        syncCarType(_userProfile.value)
        _state.value = ChonikState.ModelDiscussion()
        generateReply("Интересует категория: $category")
    }

    fun submitChosenModel(modelName: String) {
        val trimmed = modelName.trim()
        if (trimmed.isEmpty()) return
        val showcase = ShowcaseCar.fromUtterance(trimmed) ?: ShowcaseCar.fromTag(trimmed)
        _userProfile.update {
            it.copy(
                chosenModel = showcase?.displayName ?: trimmed,
                carKey = showcase?.key ?: it.carKey,
                vehicleCategory = VehicleCategory.CARS,
            )
        }
        syncCarType(_userProfile.value)
        _state.value = ChonikState.FinancialQualification
        generateReply("Хочу обсудить модель ${showcase?.displayName ?: trimmed}.")
    }

    fun finishModelDiscussion() {
        _state.value = ChonikState.FinancialQualification
        generateReply("Давай перейдём к оплате: наличные или кредит.")
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
        _state.value = ChonikState.HandoverToOffice
        generateReply(
            "Бюджет $budget KRW, оплата $paymentMethod, виза $visaType, " +
                "официальная работа=$isOfficiallyEmployed. Закрой заявку в Сувон.",
        )
    }

    fun finishMarketCalculation() {
        _state.value = ChonikState.HandoverToOffice
        generateReply("Передай заявку в офис Сувона и предложи бесплатный трансфер.")
    }

    fun logHandoverStub() {
        Log.d("JMotors", "Handover snapshot → Suwon: ${clientProfile}")
    }

    fun sendUserMessage(text: String) {
        val trimmed = text.trim()
        if (trimmed.isEmpty()) return
        val (profile, state) = LeadFactExtractor.absorb(trimmed, _userProfile.value, _state.value)
        _userProfile.value = profile
        _state.value = state
        syncCarType(profile)
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
        mainHandler.removeCallbacks(listenRetry)
        sttManager.stopListening()
        ttsManager.stop()
        _isListening.value = false
        _isSpeaking.value = false
        _isGenerating.value = true
        _errorMessage.value = null
        Log.i("JMotors", "Gemini start: $userMessage")
        viewModelScope.launch {
            _emotion.value = ChonikEmotion.CALM
            _audioAmplitude.value = 0f
            runCatching {
                aiRepository.generateReply(
                    userMessage = userMessage,
                    profile = _userProfile.value,
                    state = _state.value,
                )
            }.onSuccess { rawReply ->
                val parsed = parseAssistantTags(rawReply)
                applyParsedTags(parsed)
                _assistantReply.value = parsed.visibleText
                _errorMessage.value = null
                _isGenerating.value = false
                Log.i("JMotors", "Gemini ok, speak ${parsed.visibleText.length} chars")
                speakThenListen(parsed.visibleText)
            }.onFailure { error ->
                Log.e("JMotors", "Gemini failed: ${error.message}", error)
                val spoken = "Сейчас не достучался до Gemini. Повтори, пожалуйста."
                _errorMessage.value = error.message ?: spoken
                _emotion.value = ChonikEmotion.WARNING
                _isGenerating.value = false
                speakThenListen(spoken)
            }
        }
    }

    /**
     * Applies [SET_CAR] / [SET_STATE] then strips every hidden tag so TTS never reads them.
     */
    private fun parseAssistantTags(rawReply: String): ParsedReply {
        val emotion = EMOTION_TAG_REGEX.findAll(rawReply).lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
            ?.let { name -> ChonikEmotion.entries.find { it.name == name } }
            ?: ChonikEmotion.CALM
        val car = SET_CAR_REGEX.findAll(rawReply).lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.let { ShowcaseCar.fromTag(it) }
        val forcedState = SET_STATE_REGEX.findAll(rawReply).lastOrNull()
            ?.groupValues
            ?.getOrNull(1)
            ?.uppercase()
            ?.let { name -> SphereVisualState.entries.find { it.name == name } }
        val visibleText = rawReply
            .replace(EMOTION_TAG_REGEX, "")
            .replace(SET_CAR_REGEX, "")
            .replace(SET_STATE_REGEX, "")
            .replace(HIDDEN_TAG_REGEX, "")
            .replace(Regex("[ \\t]+"), " ")
            .replace(Regex("\\n{3,}"), "\n\n")
            .trim()
        return ParsedReply(
            emotion = emotion,
            visibleText = visibleText,
            showcaseCar = car,
            forcedState = forcedState,
        )
    }

    private fun applyParsedTags(parsed: ParsedReply) {
        _emotion.value = parsed.emotion
        parsed.showcaseCar?.let { applyShowcaseCar(it) }
        parsed.forcedState?.let { applyForcedSphereState(it) }
        if (_state.value is ChonikState.HandoverToOffice ||
            parsed.forcedState == SphereVisualState.SUCCESS
        ) {
            exportLeadIfNeeded()
        }
    }

    private fun applyShowcaseCar(car: ShowcaseCar) {
        _userProfile.update {
            it.copy(
                chosenModel = car.displayName,
                carKey = car.key,
                vehicleCategory = VehicleCategory.CARS,
            )
        }
        _carType.value = car.key
        _carRevealNonce.update { it + 1 }
        when (_state.value) {
            is ChonikState.Greeting,
            ChonikState.CategorySelection,
            is ChonikState.ModelDiscussion,
            -> _state.value = ChonikState.FinancialQualification
            else -> Unit
        }
        Log.i("JMotors", "SET_CAR → ${car.key} (${car.displayName})")
    }

    private fun applyForcedSphereState(state: SphereVisualState) {
        _forcedSphereState.value = state
        if (state == SphereVisualState.SUCCESS) {
            _emotion.value = ChonikEmotion.JOY
            _state.value = ChonikState.HandoverToOffice
        }
        Log.i("JMotors", "SET_STATE → $state")
    }

    private fun exportLeadIfNeeded() {
        if (leadExported) return
        leadExported = true
        val snapshot = ClientProfile.from(_userProfile.value)
        runCatching {
            SuwonLeadExporter.export(getApplication(), snapshot)
        }.onFailure { error ->
            leadExported = false
            Log.e("JMotors", "Suwon JSON export failed: ${error.message}", error)
        }
        logHandoverStub()
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

    private fun syncCarType(profile: UserProfile) {
        val car = ShowcaseCar.fromTag(profile.carKey)
            ?: ShowcaseCar.fromUtterance(profile.chosenModel.orEmpty())
            ?: ShowcaseCar.fromTag(profile.chosenModel)
        _carType.value = car?.key ?: ShowcaseCar.AVANTE.key
    }

    override fun onCleared() {
        mainHandler.removeCallbacks(listenRetry)
        pauseVoiceLoop()
        aiRepository.clearSession()
        ttsManager.shutdown()
        sttManager.destroy()
        super.onCleared()
    }

    private data class ParsedReply(
        val emotion: ChonikEmotion,
        val visibleText: String,
        val showcaseCar: ShowcaseCar?,
        val forcedState: SphereVisualState?,
    )

    private companion object {
        val EMOTION_TAG_REGEX: Regex =
            Regex("""\[EMOTION:\s*(CALM|JOY|WARNING|SARCASM|DELIGHT)\s*\]""", RegexOption.IGNORE_CASE)

        val SET_CAR_REGEX: Regex =
            Regex("""\[SET_CAR:\s*(avante|sonata|santa_fe|santa-fe|santafe|elantra)\s*\]""", RegexOption.IGNORE_CASE)

        val SET_STATE_REGEX: Regex =
            Regex("""\[SET_STATE:\s*(IDLE|THINKING|SUCCESS|ERROR)\s*\]""", RegexOption.IGNORE_CASE)

        /** Safety net so leftover machine tags never reach Google TTS. */
        val HIDDEN_TAG_REGEX: Regex =
            Regex("""\[(?:EMOTION|SET_CAR|SET_STATE)\s*:[^\]]*\]""", RegexOption.IGNORE_CASE)

        val GREETING_POOL: List<String> = listOf(
            "Привет! Я Чоник из J Motors. Как тебя зовут?",
            "О, живой человек. Я Чоник, автоконсультант. Как к тебе обращаться?",
            "Чоник на связи. Сначала имя — потом машину подберём. Как тебя зовут?",
        )
    }
}
