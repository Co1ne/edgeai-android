package com.edgeaivoice

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.edgeaivoice.audio.AudioCaptureEngine
import com.edgeaivoice.jni.AssetsModelInstaller
import com.edgeaivoice.jni.NativeBridge
import com.edgeaivoice.jni.NativeErrorCode
import com.edgeaivoice.jni.NativeRuntimeLoader
import com.edgeaivoice.pipeline.VoiceEvent
import com.edgeaivoice.pipeline.VoicePipelineCoordinator
import com.edgeaivoice.pipeline.VoiceState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val loadReport = NativeRuntimeLoader.loadRuntimeLibraries()
        var hasAudioPermission = hasRecordAudioPermission()

        val permissionLauncher = registerForActivityResult(
            ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasAudioPermission = granted
        }

        setContent {
            MaterialTheme {
                Surface(modifier = Modifier.fillMaxSize()) {
                    HomeScreen(
                        libsStatus = if (loadReport.success) "Native libs load: OK" else "Native libs load: FAIL",
                        libsDetail = loadReport.message,
                        nativeHello = runCatching { NativeBridge.nativeHello() }
                            .getOrElse { "nativeHello failed: ${it.message}" },
                        installer = { AssetsModelInstaller.installIfNeeded(this) },
                        hasRecordAudioPermission = { hasAudioPermission || hasRecordAudioPermission() },
                        requestRecordAudioPermission = {
                            permissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    )
                }
            }
        }
    }

    private fun hasRecordAudioPermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }
}

@Composable
private fun HomeScreen(
    libsStatus: String,
    libsDetail: String,
    nativeHello: String,
    installer: suspend () -> AssetsModelInstaller.InstallReport,
    hasRecordAudioPermission: () -> Boolean,
    requestRecordAudioPermission: () -> Unit
) {
    val tag = "M1-UI"
    val coordinator = remember { VoicePipelineCoordinator() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val audioEngine = remember(context) { AudioCaptureEngine(context = context) }

    var modelsStatus by remember { mutableStateOf("Models installed: RUNNING") }
    var modelLines by remember { mutableStateOf(listOf("installing...")) }

    var voiceState by remember { mutableStateOf(VoiceState.Idle) }
    var asrText by remember { mutableStateOf("（尚无转写结果）") }
    var isPressingPtt by remember { mutableStateOf(false) }
    var uiHint by remember { mutableStateOf("按住说话开始录音") }

    LaunchedEffect(Unit) {
        val report = installer()
        modelsStatus = if (report.success) "Models installed: OK" else "Models installed: FAIL"
        modelLines = report.modelFiles.map {
            "${it.path} (${it.sizeBytes} bytes) [${it.note}]"
        } + listOf("elapsedMs=${report.elapsedMs}")
    }

    fun dispatch(event: VoiceEvent) {
        val next = coordinator.onEvent(event)
        voiceState = next
        Log.i(tag, "event=$event => state=$next")
    }

    fun onPttPressed() {
        if (!hasRecordAudioPermission()) {
            requestRecordAudioPermission()
            uiHint = "需要录音权限"
            dispatch(VoiceEvent.AsrFailed(NativeErrorCode.PERMISSION_DENIED, "RECORD_AUDIO denied"))
            return
        }

        val ok = audioEngine.startCapture()
        if (!ok) {
            uiHint = "录音启动失败"
            dispatch(VoiceEvent.AsrFailed(NativeErrorCode.AUDIO_IO_FAILED, "start capture failed"))
            return
        }

        isPressingPtt = true
        uiHint = "录音中..."
        dispatch(VoiceEvent.PressToTalk)
    }

    fun onPttReleased() {
        if (!isPressingPtt) return
        isPressingPtt = false
        dispatch(VoiceEvent.ReleaseToStop)
        uiHint = "转写中..."

        scope.launch {
            val pcm = withContext(Dispatchers.IO) { audioEngine.stopCapture() }
            if (pcm.isEmpty()) {
                uiHint = "录音为空"
                dispatch(VoiceEvent.AsrFailed(NativeErrorCode.AUDIO_IO_FAILED, "empty pcm"))
                return@launch
            }

            dispatch(VoiceEvent.AudioReady(pcm.size))

            val asr = withContext(Dispatchers.Default) {
                runCatching { NativeBridge.asrTranscribePcm16(pcm, 16_000) }
                    .getOrElse {
                        com.edgeaivoice.jni.AsrResult(
                            text = "",
                            elapsedMs = 0,
                            errorCode = NativeErrorCode.INFERENCE_FAILED,
                            errorMessage = it.message ?: "asr exception"
                        )
                    }
            }

            if (asr.errorCode == NativeErrorCode.OK) {
                asrText = asr.text
                uiHint = "转写完成"
                dispatch(VoiceEvent.AsrSuccess(asr.text))
            } else {
                uiHint = "转写失败: ${asr.errorMessage ?: asr.errorCode}"
                dispatch(VoiceEvent.AsrFailed(asr.errorCode, asr.errorMessage ?: "asr failed"))
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(text = "EdgeAiVoice Milestone 1", fontWeight = FontWeight.Bold)
        Text(text = libsStatus)
        Text(text = libsDetail)
        Text(text = modelsStatus)
        Text(text = "JNI smoke: $nativeHello")

        Surface(
            modifier = Modifier.fillMaxWidth(),
            tonalElevation = 1.dp,
            shape = RoundedCornerShape(8.dp)
        ) {
            Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text(text = "当前状态: $voiceState", fontWeight = FontWeight.SemiBold)
                Text(text = "ASR文本: $asrText")
                Text(text = "提示: $uiHint")
            }
        }

        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .height(64.dp)
                .border(
                    width = 1.dp,
                    color = if (isPressingPtt) Color(0xFFB91C1C) else Color.Gray,
                    shape = RoundedCornerShape(10.dp)
                )
                .background(
                    color = if (isPressingPtt) Color(0xFFFEE2E2) else Color(0xFFF6F6F6),
                    shape = RoundedCornerShape(10.dp)
                )
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false)
                        onPttPressed()

                        try {
                            waitForUpOrCancellation()
                        } finally {
                            onPttReleased()
                        }
                    }
                },
            shape = RoundedCornerShape(10.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 12.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = if (isPressingPtt) "正在录音，松开结束" else "按住说话（PTT）",
                    fontWeight = FontWeight.Medium,
                    color = if (isPressingPtt) Color(0xFFB91C1C) else Color.Unspecified
                )
            }
        }

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(onClick = {
                dispatch(VoiceEvent.Reset)
                asrText = "（尚无转写结果）"
                uiHint = "已重置"
            }) {
                Text("Reset")
            }
        }

        modelLines.forEach { line -> Text(text = line) }
    }
}
