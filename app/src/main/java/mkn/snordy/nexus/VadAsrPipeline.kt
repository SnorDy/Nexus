package mkn.snordy.nexus

import android.content.Context
import android.os.SystemClock
import android.util.Log
import com.k2fsa.sherpa.onnx.FeatureConfig
import com.k2fsa.sherpa.onnx.OfflineModelConfig
import com.k2fsa.sherpa.onnx.OfflineRecognizer
import com.k2fsa.sherpa.onnx.OfflineRecognizerConfig
import com.k2fsa.sherpa.onnx.OfflineTransducerModelConfig
import com.k2fsa.sherpa.onnx.SileroVadModelConfig
import com.k2fsa.sherpa.onnx.Vad
import com.k2fsa.sherpa.onnx.VadModelConfig
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch

interface NexusListener {
    fun onWakeOnly()                    // слово сказано, команды в этой же фразе нет
    fun onCommand(text: String)         // готовая команда
}

class VadAsrPipeline(
    context: Context,
    scope: CoroutineScope,
    private val listener: NexusListener,
) : AutoCloseable {

    companion object {
        const val FRAME = 512                 // 32 мс при 16 кГц
        private const val SR = 16000
        private const val COMMAND_WINDOW_MS = 5000L
    }

    private val vad = Vad(
        assetManager = context.assets,
        config = VadModelConfig(
            sileroVadModelConfig = SileroVadModelConfig(
                model = "silero_vad.onnx",
                threshold = 0.5f,
                minSilenceDuration = 0.4f,   // пауза, после которой фраза закрыта
                minSpeechDuration = 0.3f,
                windowSize = 512,
                maxSpeechDuration = 6.0f,    // длинный монолог режется принудительно
            ),
            sampleRate = SR, numThreads = 1, provider = "cpu",
        )
    )

    private val asr = OfflineRecognizer(
        assetManager = context.assets,
        config = OfflineRecognizerConfig(
            featConfig = FeatureConfig(sampleRate = SR, featureDim = 80),
            modelConfig = OfflineModelConfig(
                transducer = OfflineTransducerModelConfig(
                    encoder = "asr/encoder.int8.onnx",
                    decoder = "asr/decoder.onnx",
                    joiner = "asr/joiner.int8.onnx",
                ),
                tokens = "asr/tokens.txt",
                numThreads = 2,
                provider = "cpu",
                modelType = "transducer",
            ),
        )
    )

    private val segments = Channel<FloatArray>(capacity = 4, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private var commandWindowUntil = 0L

    private val worker = scope.launch(Dispatchers.Default) {
        for (seg in segments) handle(seg)
    }

    fun feed(pcm: ShortArray) {
        val f = FloatArray(pcm.size) { pcm[it] / 32768f }
        vad.acceptWaveform(f)
        while (!vad.empty()) {
            val seg = vad.front()
            vad.pop()
            segments.trySend(seg.samples)
        }
    }

    private fun handle(samples: FloatArray) {
        if (samples.size < SR * 0.4) return              // слишком коротко
        val text = transcribe(samples).trim()
        if (text.isEmpty()) return
        Log.d("Nexus", "ASR: $text")

        val now = SystemClock.elapsedRealtime()
        if (now < commandWindowUntil) {                  // ждали команду после "Нексус"
            commandWindowUntil = 0
            listener.onCommand(text)
            return
        }
        val rest = WakeWordMatcher.match(text) ?: return // слова нет, фраза просто выбрасывается
        if (rest.isBlank()) {
            commandWindowUntil = now + COMMAND_WINDOW_MS
            listener.onWakeOnly()
        } else {
            listener.onCommand(rest)
        }
    }

    private fun transcribe(samples: FloatArray): String {
        val s = asr.createStream()
        s.acceptWaveform(samples, SR)
        asr.decode(s)
        val text = asr.getResult(s).text
        s.release()
        return text
    }

    override fun close() {
        worker.cancel(); segments.close()
        vad.release(); asr.release()
    }
}