package de.ictbaden.industrialkeyboard.voice

import android.annotation.SuppressLint
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.os.Handler
import android.os.Looper
import android.util.Log
import org.json.JSONObject
import org.vosk.LibVosk
import org.vosk.LogLevel
import org.vosk.Model
import org.vosk.Recognizer
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.log10
import kotlin.math.sqrt

/**
 * [OfflineSpeechEngine] with Vosk (Kaldi, Apache-2.0): 16 kHz mono from the microphone, streamed into
 * the recognizer on its own thread. Audio stays in memory only for the block being recognised; nothing
 * is written or sent. The model stays loaded until [release] (loading takes a few seconds).
 */
class VoskEngine : OfflineSpeechEngine {

    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "dictation-vosk").apply { isDaemon = true } }

    // Only touched on the worker thread.
    private var model: Model? = null
    private var modelPath: String? = null

    @Volatile
    private var session = 0

    @Volatile
    private var recording = false

    override fun start(model: File, callback: OfflineSpeechEngine.Callback) {
        val id = ++session
        recording = true
        worker.execute { listen(id, model, callback) }
    }

    override fun stop() {
        recording = false
    }

    override fun release() {
        recording = false
        session++
        worker.execute {
            runCatching { model?.close() }
            model = null
            modelPath = null
        }
    }

    private fun post(id: Int, action: () -> Unit) {
        main.post { if (id == session) action() }
    }

    @SuppressLint("MissingPermission") // Checked before dictation starts (MicrophonePermissionActivity); a SecurityException is caught.
    private fun listen(id: Int, dir: File, callback: OfflineSpeechEngine.Callback) {
        if (id != session) return
        // Paused before the recording began: an empty result ends the pause (see DictationController.pause).
        if (!recording) return post(id) { callback.onResult("") }
        val loaded = loadModel(dir) ?: return post(id) { callback.onError(OfflineSpeechEngine.Error.MODEL) }
        if (!recording) return post(id) { callback.onResult("") }
        val minBuffer = AudioRecord.getMinBufferSize(SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        val record = runCatching {
            AudioRecord(MediaRecorder.AudioSource.VOICE_RECOGNITION, SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, maxOf(minBuffer, BLOCK * 2 * 4))
                .takeIf { it.state == AudioRecord.STATE_INITIALIZED }
        }.getOrNull() ?: return post(id) { callback.onError(OfflineSpeechEngine.Error.AUDIO) }
        val recognizer = runCatching { Recognizer(loaded, SAMPLE_RATE.toFloat()) }.getOrElse {
            Log.w(TAG, "Recognizer failed", it)
            record.release()
            return post(id) { callback.onError(OfflineSpeechEngine.Error.MODEL) }
        }
        try {
            record.startRecording()
            if (record.recordingState != AudioRecord.RECORDSTATE_RECORDING) return post(id) { callback.onError(OfflineSpeechEngine.Error.AUDIO) }
            post(id) { callback.onReady() }
            val buffer = ShortArray(BLOCK)
            var partial = ""
            while (recording && id == session) {
                val read = record.read(buffer, 0, buffer.size)
                if (read < 0) return post(id) { callback.onError(OfflineSpeechEngine.Error.AUDIO) }
                if (read == 0) continue
                val level = level(buffer, read)
                post(id) { callback.onLevel(level) }
                if (recognizer.acceptWaveForm(buffer, read)) {
                    val text = text(recognizer.result, "text")
                    partial = ""
                    post(id) { callback.onResult(text) }
                } else {
                    val now = text(recognizer.partialResult, "partial")
                    if (now != partial) {
                        partial = now
                        post(id) { callback.onPartial(now) }
                    }
                }
            }
            // Paused or stopped: what was said last still counts (not after release()).
            val last = text(recognizer.finalResult, "text")
            post(id) { callback.onResult(last) }
        } catch (e: SecurityException) {
            post(id) { callback.onError(OfflineSpeechEngine.Error.AUDIO) }
        } catch (e: IllegalStateException) {
            post(id) { callback.onError(OfflineSpeechEngine.Error.AUDIO) }
        } finally {
            runCatching { record.stop() }
            record.release()
            recognizer.close()
        }
    }

    private fun loadModel(dir: File): Model? {
        if (model != null && modelPath == dir.path) return model
        runCatching { model?.close() }
        model = null
        return runCatching {
            LibVosk.setLogLevel(LogLevel.WARNINGS)
            Model(dir.path).also {
                model = it
                modelPath = dir.path
            }
        }.onFailure { Log.w(TAG, "Model ${dir.name} could not be loaded", it) }.getOrNull()
    }

    private fun text(json: String?, key: String): String =
        runCatching { JSONObject(json ?: "{}").optString(key) }.getOrDefault("").trim()

    private fun level(buffer: ShortArray, count: Int): Float {
        var sum = 0.0
        for (i in 0 until count) sum += buffer[i].toDouble() * buffer[i]
        val rms = sqrt(sum / count)
        if (rms < 1) return 0f
        val db = 20 * log10(rms / Short.MAX_VALUE)
        return ((db + 50) / 40).toFloat().coerceIn(0f, 1f)
    }

    private companion object {
        const val TAG = "Dictation"
        const val SAMPLE_RATE = 16_000

        /** 0.1 s per block: partial results follow the voice closely. */
        const val BLOCK = 1_600
    }
}
