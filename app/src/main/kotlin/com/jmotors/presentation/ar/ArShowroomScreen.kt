package com.jmotors.presentation.ar

import android.Manifest
import android.content.pm.PackageManager
import android.util.Log
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.domain.model.ai.SphereVisualState
import com.jmotors.presentation.ar.car.FloatingCarShowcase
import com.jmotors.presentation.ar.car.CarModelLoader
import com.jmotors.presentation.chonik.ChonikAvatar
import com.jmotors.presentation.chonik.glowColor
import com.jmotors.presentation.chonik.highlightColor
import com.jmotors.presentation.viewmodel.ChonikViewModel
import java.nio.ByteBuffer

private const val HANDOVER_TAG = "JMotors"

/** Absolute black — XREAL Air 2 Pro treats this as optical passthrough. */
private val PassthroughBlack = Color(0xFF000000)

/** Left / right halves of the XREAL SBS framebuffer. */
private enum class StereoEye {
    LEFT,
    RIGHT,
}

/**
 * Native SBS 3D showroom: pitch-black passthrough, glowing AI sphere, floating car.
 */
@Composable
fun ArShowroomScreen(
    viewModel: ChonikViewModel = viewModel(),
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current

    val visualState by viewModel.sphereVisualState.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val assistantReply by viewModel.assistantReply.collectAsStateWithLifecycle()
    val chonikState by viewModel.state.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()
    val errorMessage by viewModel.errorMessage.collectAsStateWithLifecycle()
    val carType by viewModel.carType.collectAsStateWithLifecycle()
    val carRevealNonce by viewModel.carRevealNonce.collectAsStateWithLifecycle()

    var carBytes by remember { mutableStateOf<ByteBuffer?>(null) }
    var carLoadFailed by remember { mutableStateOf(false) }

    LaunchedEffect(carType) {
        carLoadFailed = false
        runCatching { CarModelLoader.fetchCarModel(context, carType) }
            .onSuccess { buffer ->
                carBytes = buffer
                carLoadFailed = false
                Log.i(HANDOVER_TAG, "Showroom car ready: ${CarModelLoader.loadCarModel(carType).carType}")
            }
            .onFailure { error ->
                Log.e(HANDOVER_TAG, "Showroom car load failed: ${error.message}", error)
                carBytes = null
                carLoadFailed = true
            }
    }

    var hasAudioPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasAudioPermission = granted
    }

    LaunchedEffect(Unit) {
        if (!hasAudioPermission) {
            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    LaunchedEffect(hasAudioPermission) {
        if (hasAudioPermission) {
            viewModel.startVoiceLoop()
        }
    }

    val audioGranted = rememberUpdatedState(hasAudioPermission)
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> viewModel.pauseVoiceLoop()
                Lifecycle.Event.ON_START -> if (audioGranted.value) viewModel.startVoiceLoop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseVoiceLoop()
        }
    }

    LaunchedEffect(chonikState, userProfile) {
        if (chonikState is ChonikState.HandoverToOffice) {
            Log.d(HANDOVER_TAG, "Handover stub → Suwon office: $userProfile")
        }
    }

    val dialogueText = when {
        isGenerating -> "Секунду, думаю…"
        !errorMessage.isNullOrBlank() -> errorMessage.orEmpty()
        !assistantReply.isNullOrBlank() -> assistantReply.orEmpty()
        chonikState is ChonikState.Greeting -> (chonikState as ChonikState.Greeting).openingLine
        isListening -> "Слушаю тебя — говори."
        else -> "Я рядом. Говори — я слушаю."
    }

    val statusText = when {
        isGenerating -> "Думаю…"
        isSpeaking -> "Говорю…"
        isListening -> "Слушаю тебя"
        else -> "Чоник в эфире"
    }

    if (!hasAudioPermission) {
        PermissionGate(
            message = "Нужен микрофон: в очках общение только голосом.",
            onRequest = { permissionLauncher.launch(Manifest.permission.RECORD_AUDIO) },
        )
        return
    }

    Row(modifier = Modifier.fillMaxSize().background(PassthroughBlack)) {
        StereoEyePane(
            eye = StereoEye.LEFT,
            visualState = visualState,
            audioAmplitude = audioAmplitude,
            dialogueText = dialogueText,
            statusText = statusText,
            carBytes = carBytes,
            carLoadFailed = carLoadFailed,
            carRevealNonce = carRevealNonce,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
        StereoEyePane(
            eye = StereoEye.RIGHT,
            visualState = visualState,
            audioAmplitude = audioAmplitude,
            dialogueText = dialogueText,
            statusText = statusText,
            carBytes = carBytes,
            carLoadFailed = carLoadFailed,
            carRevealNonce = carRevealNonce,
            modifier = Modifier.weight(1f).fillMaxHeight(),
        )
    }
}

@Composable
private fun StereoEyePane(
    eye: StereoEye,
    visualState: SphereVisualState,
    audioAmplitude: Float,
    dialogueText: String,
    statusText: String,
    carBytes: ByteBuffer?,
    carLoadFailed: Boolean,
    carRevealNonce: Int,
    modifier: Modifier = Modifier,
) {
    val sphereParallax = stereoOffset(eye, far = false, amount = SPHERE_PARALLAX)
    val carParallax = stereoOffset(eye, far = false, amount = CAR_PARALLAX)
    val plateParallax = stereoOffset(eye, far = false, amount = DIALOGUE_PARALLAX)
    val statusParallax = stereoOffset(eye, far = false, amount = STATUS_PARALLAX)
    val highlightShift = if (eye == StereoEye.LEFT) -1f else 1f

    Box(
        modifier = modifier
            .fillMaxSize()
            .clipToBounds()
            .background(PassthroughBlack),
    ) {
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .offset(x = sphereParallax)
                .widthIn(max = 360.dp)
                .padding(horizontal = 10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChonikAvatar(
                visualState = visualState,
                audioAmplitude = audioAmplitude,
                highlightShift = highlightShift,
                size = 128.dp,
            )
            Spacer(modifier = Modifier.height(8.dp))
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(168.dp)
                    .offset(x = carParallax - sphereParallax),
                contentAlignment = Alignment.Center,
            ) {
                FloatingCarShowcase(
                    modelBytes = carBytes,
                    loadFailed = carLoadFailed,
                    isLeftEye = eye == StereoEye.LEFT,
                    revealNonce = carRevealNonce,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            Spacer(modifier = Modifier.height(8.dp))
            HolographicPlate(
                text = dialogueText,
                visualState = visualState,
                modifier = Modifier.offset(x = plateParallax - sphereParallax),
            )
        }

        HolographicPlate(
            text = statusText,
            visualState = visualState,
            compact = true,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 14.dp)
                .offset(x = statusParallax),
        )
    }
}

@Composable
private fun HolographicPlate(
    text: String,
    visualState: SphereVisualState,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val neon by animateColorAsState(
        targetValue = visualState.glowColor(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "holoBorder",
    )
    val inner by animateColorAsState(
        targetValue = visualState.highlightColor(),
        animationSpec = tween(durationMillis = 700, easing = FastOutSlowInEasing),
        label = "holoInner",
    )
    val shape = RoundedCornerShape(if (compact) 20.dp else 16.dp)
    Text(
        text = text,
        color = lerp(Color.White, inner, 0.22f).copy(alpha = 0.96f),
        style = if (compact) MaterialTheme.typography.labelMedium else MaterialTheme.typography.bodyMedium,
        textAlign = TextAlign.Center,
        modifier = modifier
            .then(if (compact) Modifier else Modifier.fillMaxWidth())
            .background(Color(0xCC050B14), shape)
            .border(width = 3.dp, color = neon.copy(alpha = 0.28f), shape = shape)
            .border(width = 1.3.dp, color = neon.copy(alpha = 0.95f), shape = shape)
            .padding(
                horizontal = if (compact) 12.dp else 14.dp,
                vertical = if (compact) 6.dp else 10.dp,
            ),
    )
}

@Composable
private fun PermissionGate(
    message: String,
    onRequest: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(PassthroughBlack)
            .padding(24.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(text = message, color = Color.White, style = MaterialTheme.typography.bodyLarge)
            if (onRequest != null) {
                Spacer(modifier = Modifier.height(16.dp))
                Button(onClick = onRequest) {
                    Text("Разрешить доступ")
                }
            }
        }
    }
}

/**
 * @param far true = uncrossed (recedes); false = crossed (pops toward the user).
 * Sphere uses a modest crossed offset so the orb sits at a comfortable optical Z.
 */
private fun stereoOffset(eye: StereoEye, far: Boolean, amount: Dp): Dp {
    val leftward = if (eye == StereoEye.LEFT) -1 else 1
    val direction = if (far) leftward else -leftward
    return amount * direction
}

private val SPHERE_PARALLAX = 12.dp
private val CAR_PARALLAX = 14.dp
private val DIALOGUE_PARALLAX = 16.dp
private val STATUS_PARALLAX = 11.dp
