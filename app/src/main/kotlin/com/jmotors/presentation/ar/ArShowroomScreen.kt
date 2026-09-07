package com.jmotors.presentation.ar

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.opengl.GLSurfaceView
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Display
import android.view.Surface
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.google.ar.core.ArCoreApk
import com.google.ar.core.Config
import com.google.ar.core.Session
import com.google.ar.core.exceptions.UnavailableException
import com.jmotors.domain.model.ai.ChonikState
import com.jmotors.presentation.chonik.ChonikAvatar
import com.jmotors.presentation.viewmodel.ChonikViewModel
import kotlin.math.roundToInt

private const val HANDOVER_TAG = "JMotors"

/**
 * AR showroom for XREAL Air 2 Pro / phone: camera + horizontal planes + Чоник overlay.
 */
@Composable
fun ArShowroomScreen(
    viewModel: ChonikViewModel = viewModel(),
) {
    val context = LocalContext.current
    val activity = context as Activity
    val lifecycleOwner = LocalLifecycleOwner.current
    val density = LocalDensity.current

    val emotion by viewModel.emotion.collectAsStateWithLifecycle()
    val audioAmplitude by viewModel.audioAmplitude.collectAsStateWithLifecycle()
    val assistantReply by viewModel.assistantReply.collectAsStateWithLifecycle()
    val chonikState by viewModel.state.collectAsStateWithLifecycle()
    val userProfile by viewModel.userProfile.collectAsStateWithLifecycle()
    val isGenerating by viewModel.isGenerating.collectAsStateWithLifecycle()
    val isListening by viewModel.isListening.collectAsStateWithLifecycle()
    val isSpeaking by viewModel.isSpeaking.collectAsStateWithLifecycle()

    var hasCameraPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED)
    }
    var hasAudioPermission by remember {
        mutableStateOf(context.checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED)
    }
    var arCoreReady by remember { mutableStateOf(false) }
    var arError by remember { mutableStateOf<String?>(null) }
    var userRequestedInstall by remember { mutableStateOf(true) }
    var avatarScreenPos by remember { mutableStateOf<Offset?>(null) }
    var isAnchored by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestMultiplePermissions(),
    ) { result ->
        hasCameraPermission = result[Manifest.permission.CAMERA] == true
        hasAudioPermission = result[Manifest.permission.RECORD_AUDIO] == true
        if (hasCameraPermission && hasAudioPermission) {
            arError = null
        }
    }

    LaunchedEffect(Unit) {
        val missing = buildList {
            if (!hasCameraPermission) add(Manifest.permission.CAMERA)
            if (!hasAudioPermission) add(Manifest.permission.RECORD_AUDIO)
        }
        if (missing.isNotEmpty()) {
            permissionLauncher.launch(missing.toTypedArray())
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
                Lifecycle.Event.ON_PAUSE -> viewModel.pauseVoiceLoop()
                Lifecycle.Event.ON_RESUME -> if (audioGranted.value) viewModel.startVoiceLoop()
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            viewModel.pauseVoiceLoop()
        }
    }

    LaunchedEffect(hasCameraPermission, userRequestedInstall) {
        if (!hasCameraPermission) return@LaunchedEffect
        arError = null
        val availability = ArCoreApk.getInstance().checkAvailability(context)
        if (availability.isTransient) {
            arCoreReady = false
            return@LaunchedEffect
        }
        if (!availability.isSupported) {
            arCoreReady = false
            arError = "ARCore на этом устройстве недоступен."
            return@LaunchedEffect
        }
        val installStatus = ArCoreApk.getInstance().requestInstall(activity, userRequestedInstall)
        when (installStatus) {
            ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                userRequestedInstall = false
                arCoreReady = false
            }
            ArCoreApk.InstallStatus.INSTALLED -> arCoreReady = true
        }
    }

    LaunchedEffect(chonikState, userProfile) {
        if (chonikState is ChonikState.HandoverToOffice) {
            Log.d(HANDOVER_TAG, "Handover stub → Suwon office: $userProfile")
        }
    }

    val dialogueText = when {
        !assistantReply.isNullOrBlank() -> assistantReply.orEmpty()
        chonikState is ChonikState.Greeting -> (chonikState as ChonikState.Greeting).openingLine
        isGenerating -> "Секунду, думаю…"
        isListening -> "Слушаю тебя — говори."
        else -> "Наведи камеру на стол или пол — я приземлюсь."
    }

    if (!hasCameraPermission || !hasAudioPermission) {
        PermissionGate(
            message = when {
                !hasCameraPermission && !hasAudioPermission ->
                    "Чонику нужны камера и микрофон: смотрим шоурум и говорим голосом."
                !hasAudioPermission -> "Нужен микрофон: в очках общение только голосом."
                else -> arError ?: "Чонику нужна камера, чтобы парить в AR."
            },
            onRequest = {
                permissionLauncher.launch(
                    arrayOf(Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO),
                )
            },
        )
        return
    }

    if (arError != null && !arCoreReady) {
        PermissionGate(message = arError.orEmpty(), onRequest = null)
        return
    }

    BoxWithConstraints(modifier = Modifier.fillMaxSize().background(Color.Black)) {
        val containerWidthPx = with(density) { maxWidth.toPx() }
        val containerHeightPx = with(density) { maxHeight.toPx() }
        val avatarSize = 180.dp
        val avatarSizePx = with(density) { avatarSize.toPx() }

        if (arCoreReady) {
            ArCameraLayer(
                activity = activity,
                lifecycle = lifecycleOwner.lifecycle,
                onProjected = { offset, anchored ->
                    avatarScreenPos = offset
                    isAnchored = anchored
                },
                onSessionError = { message -> arError = message },
            )
        }

        val overlayAlignment = if (isAnchored && avatarScreenPos != null) {
            Alignment.TopStart
        } else {
            Alignment.Center
        }
        val overlayOffset = if (isAnchored && avatarScreenPos != null) {
            val pos = avatarScreenPos!!
            val x = pos.x.coerceIn(
                avatarSizePx / 2f,
                (containerWidthPx - avatarSizePx / 2f).coerceAtLeast(avatarSizePx / 2f),
            )
            val y = pos.y.coerceIn(
                avatarSizePx / 2f,
                (containerHeightPx - avatarSizePx).coerceAtLeast(avatarSizePx / 2f),
            )
            IntOffset(
                (x - avatarSizePx / 2f).roundToInt(),
                (y - avatarSizePx / 2f).roundToInt(),
            )
        } else {
            IntOffset.Zero
        }

        Column(
            modifier = Modifier
                .align(overlayAlignment)
                .then(if (isAnchored) Modifier.offset { overlayOffset } else Modifier)
                .widthIn(max = 320.dp)
                .padding(12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            ChonikAvatar(
                emotion = emotion,
                audioAmplitude = audioAmplitude,
                size = avatarSize,
            )
            Spacer(modifier = Modifier.height(12.dp))
            Text(
                text = dialogueText,
                color = Color.White,
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.58f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 14.dp, vertical = 10.dp),
            )
        }

        Text(
            text = when {
                isGenerating -> "Думаю…"
                isSpeaking -> "Говорю…"
                isListening -> "Слушаю тебя"
                isAnchored -> "Чоник зафиксирован на плоскости"
                else -> "Ищу стол или пол…"
            },
            color = Color.White.copy(alpha = 0.85f),
            style = MaterialTheme.typography.labelMedium,
            modifier = Modifier
                .align(Alignment.TopCenter)
                .padding(top = 28.dp)
                .background(Color.Black.copy(alpha = 0.45f), RoundedCornerShape(20.dp))
                .padding(horizontal = 12.dp, vertical = 6.dp),
        )
    }
}

@Composable
private fun ArCameraLayer(
    activity: Activity,
    lifecycle: Lifecycle,
    onProjected: (Offset?, Boolean) -> Unit,
    onSessionError: (String) -> Unit,
) {
    val projectedCallback = rememberUpdatedState(onProjected)
    val errorCallback = rememberUpdatedState(onSessionError)
    val mainHandler = remember { Handler(Looper.getMainLooper()) }
    val renderer = remember {
        ArCameraRenderer(
            displayRotation = { currentDisplayRotation(activity) },
            onAnchorProjected = { offset, anchored ->
                mainHandler.post { projectedCallback.value(offset, anchored) }
            },
        )
    }
    val runtime = remember { ArRuntime(renderer) }

    DisposableEffect(lifecycle) {
        val observer = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_RESUME -> runtime.resume(errorCallback.value)
                Lifecycle.Event.ON_PAUSE -> runtime.pause()
                else -> Unit
            }
        }
        lifecycle.addObserver(observer)
        onDispose {
            lifecycle.removeObserver(observer)
            runtime.close()
        }
    }

    AndroidView(
        modifier = Modifier.fillMaxSize(),
        factory = { context ->
            runtime.open(context, errorCallback.value)
        },
        onRelease = { runtime.pause() },
    )
}

/**
 * Owns [Session] + [GLSurfaceView] so Compose recomposition does not tear down tracking.
 */
private class ArRuntime(
    private val renderer: ArCameraRenderer,
) {
    private var session: Session? = null
    private var surfaceView: GLSurfaceView? = null
    private var sessionResumed: Boolean = false

    fun open(context: android.content.Context, onError: (String) -> Unit): GLSurfaceView {
        surfaceView?.let { return it }
        val createdSession = try {
            Session(context).also { arSession ->
                val config = Config(arSession).apply {
                    planeFindingMode = Config.PlaneFindingMode.HORIZONTAL
                    updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
                    lightEstimationMode = Config.LightEstimationMode.AMBIENT_INTENSITY
                    depthMode = Config.DepthMode.DISABLED
                }
                arSession.configure(config)
            }
        } catch (error: UnavailableException) {
            onError(error.message ?: "Не удалось создать AR-сессию.")
            return GLSurfaceView(context)
        }
        session = createdSession
        renderer.session = createdSession
        val view = GLSurfaceView(context).apply {
            preserveEGLContextOnPause = true
            setEGLContextClientVersion(2)
            setEGLConfigChooser(8, 8, 8, 8, 16, 0)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
        }
        surfaceView = view
        resume(onError)
        return view
    }

    fun resume(onError: (String) -> Unit = {}) {
        try {
            if (!sessionResumed) {
                session?.resume()
                sessionResumed = true
            }
            renderer.sessionReady = session != null
            surfaceView?.onResume()
        } catch (error: Exception) {
            renderer.sessionReady = false
            sessionResumed = false
            onError(error.message ?: "Камера AR недоступна.")
        }
    }

    fun pause() {
        renderer.sessionReady = false
        surfaceView?.onPause()
        if (sessionResumed) {
            session?.pause()
            sessionResumed = false
        }
    }

    fun close() {
        renderer.releaseAnchor()
        pause()
        session?.close()
        session = null
        renderer.session = null
        surfaceView = null
    }
}

@Composable
private fun PermissionGate(
    message: String,
    onRequest: (() -> Unit)?,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF050510))
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

private fun currentDisplayRotation(activity: Activity): Int {
    val display: Display? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display
    } else {
        @Suppress("DEPRECATION")
        activity.windowManager.defaultDisplay
    }
    val rotation = display?.rotation ?: Surface.ROTATION_0
    return when (rotation) {
        Surface.ROTATION_90 -> 90
        Surface.ROTATION_180 -> 180
        Surface.ROTATION_270 -> 270
        else -> 0
    }
}
