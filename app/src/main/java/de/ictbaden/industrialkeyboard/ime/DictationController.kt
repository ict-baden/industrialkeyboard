package de.ictbaden.industrialkeyboard.ime

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.util.Log
import de.ictbaden.industrialkeyboard.config.VoiceInput
import java.util.Locale

/**
 * Dictation inside the keyboard with Android's [SpeechRecognizer] (issue #23). Keeps listening
 * utterance by utterance while [isActive], like Gboard, until [stop] or [Dictation.SILENCE_TIMEOUT_MS]
 * without speech. All calls on the main thread.
 */
class DictationController(private val context: Context, private val listener: Listener) {

    interface Listener {
        fun onDictationState(state: State)
        fun onPartial(text: String)
        fun onFinal(text: String)
        fun onLevel(level: Float)
    }

    enum class State { LISTENING, PROCESSING, PAUSED, ERROR_PERMISSION, ERROR_NETWORK, ERROR_UNAVAILABLE, ERROR_NO_MATCH, ERROR_LANGUAGE_PACK }

    /** `typing.voice: auto`: a missing on-device language pack falls back to the speech service. */
    var fallbackToService = true

    private val main = Handler(Looper.getMainLooper())
    private var recognizer: SpeechRecognizer? = null
    private var engine = Dictation.Engine.NONE
    private var locale: Locale = Locale.getDefault()

    /** The panel is open and the user did not pause: utterances follow each other. */
    var isActive = false
        private set

    private val silenceTimeout = Runnable { pause() }

    /** Which engine this device offers for [mode]; asked when a field starts, not per key. */
    fun engineFor(mode: VoiceInput): Dictation.Engine {
        val onDevice = Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            runCatching { SpeechRecognizer.isOnDeviceRecognitionAvailable(context) }.getOrDefault(false)
        val service = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        return Dictation.engine(mode, onDevice, service)
    }

    fun start(engine: Dictation.Engine, locale: Locale) {
        if (engine == Dictation.Engine.NONE) return
        if (recognizer == null || this.engine != engine) {
            release()
            recognizer = create(engine) ?: run {
                listener.onDictationState(State.ERROR_UNAVAILABLE)
                return
            }
            recognizer?.setRecognitionListener(callbacks)
        }
        this.engine = engine
        this.locale = locale
        isActive = true
        main.removeCallbacks(silenceTimeout)
        main.postDelayed(silenceTimeout, Dictation.SILENCE_TIMEOUT_MS)
        listen()
    }

    /** Stops listening but keeps the panel open (tap on the microphone continues). */
    fun pause() {
        isActive = false
        main.removeCallbacks(silenceTimeout)
        runCatching { recognizer?.stopListening() }
        listener.onDictationState(State.PAUSED)
    }

    /** Stops and frees the recognizer (panel closed, field changed, keyboard hidden). */
    fun stop() {
        isActive = false
        main.removeCallbacks(silenceTimeout)
        release()
    }

    private fun release() {
        runCatching { recognizer?.cancel() }
        runCatching { recognizer?.destroy() }
        recognizer = null
    }

    private fun create(engine: Dictation.Engine): SpeechRecognizer? = runCatching {
        if (engine == Dictation.Engine.ON_DEVICE && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            SpeechRecognizer.createOnDeviceSpeechRecognizer(context)
        } else {
            SpeechRecognizer.createSpeechRecognizer(context)
        }
    }.onFailure { Log.w(TAG, "No speech recognizer", it) }.getOrNull()

    private fun listen() {
        val intent = recognizeIntent()
        runCatching { recognizer?.startListening(intent) }.onFailure {
            Log.w(TAG, "startListening failed", it)
            isActive = false
            listener.onDictationState(State.ERROR_UNAVAILABLE)
        }
    }

    private fun recognizeIntent() = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, locale.toLanguageTag())
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE, true)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, Dictation.PAUSE_MS)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, Dictation.PAUSE_MS)
            putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, context.packageName)
        }

    private val callbacks = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) = listener.onDictationState(State.LISTENING)

        override fun onBeginningOfSpeech() {
            // Speech heard: the silence timeout starts again.
            main.removeCallbacks(silenceTimeout)
            main.postDelayed(silenceTimeout, Dictation.SILENCE_TIMEOUT_MS)
        }

        override fun onRmsChanged(rmsdB: Float) = listener.onLevel(((rmsdB + 2f) / 12f).coerceIn(0f, 1f))

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() = listener.onDictationState(State.PROCESSING)

        override fun onPartialResults(partialResults: Bundle?) {
            best(partialResults)?.let(listener::onPartial)
        }

        override fun onResults(results: Bundle?) {
            best(results)?.let(listener::onFinal)
            // Continuous dictation: the next utterance while the panel stays open.
            if (isActive) listen()
        }

        override fun onError(error: Int) {
            Log.i(TAG, "Recognition error $error")
            if ((error == ERROR_LANGUAGE_UNAVAILABLE || error == ERROR_LANGUAGE_NOT_SUPPORTED) && engine == Dictation.Engine.ON_DEVICE) {
                languagePackMissing()
                return
            }
            when (error) {
                // Nothing said in this round: keep listening while active.
                SpeechRecognizer.ERROR_NO_MATCH, SpeechRecognizer.ERROR_SPEECH_TIMEOUT ->
                    if (isActive) listen() else listener.onDictationState(State.PAUSED)
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> fail(State.ERROR_PERMISSION)
                SpeechRecognizer.ERROR_NETWORK, SpeechRecognizer.ERROR_NETWORK_TIMEOUT, SpeechRecognizer.ERROR_SERVER -> fail(State.ERROR_NETWORK)
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> main.postDelayed({ if (isActive) listen() }, BUSY_RETRY_MS)
                SpeechRecognizer.ERROR_CLIENT -> if (!isActive) listener.onDictationState(State.PAUSED)
                else -> fail(if (error == ERROR_LANGUAGE_UNAVAILABLE || error == ERROR_LANGUAGE_NOT_SUPPORTED) State.ERROR_UNAVAILABLE else State.ERROR_NO_MATCH)
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    /**
     * The on-device recogniser has no language pack for [locale] (seen on a Pixel 9 Pro for German):
     * ask Android to download it, and with `auto` continue right away with the device's speech
     * service – like Gboard, which does not make the user wait.
     */
    private fun languagePackMissing() {
        val service = runCatching { SpeechRecognizer.isRecognitionAvailable(context) }.getOrDefault(false)
        // Only for "on the device only": the download opens a system dialog over the app, which would
        // interrupt dictation that can go on with the speech service anyway (seen on a Pixel 9 Pro).
        if (!(fallbackToService && service) && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            runCatching { recognizer?.triggerModelDownload(recognizeIntent()) }.onFailure { Log.w(TAG, "Language pack download not possible", it) }
        }
        if (fallbackToService && service) {
            Log.i(TAG, "No on-device language pack for ${locale.toLanguageTag()}: using the speech service")
            release()
            recognizer = create(Dictation.Engine.SERVICE)?.also { it.setRecognitionListener(callbacks) }
            engine = Dictation.Engine.SERVICE
            if (recognizer != null && isActive) {
                listen()
                return
            }
        }
        fail(State.ERROR_LANGUAGE_PACK)
    }

    private fun fail(state: State) {
        isActive = false
        main.removeCallbacks(silenceTimeout)
        listener.onDictationState(state)
    }

    private fun best(bundle: Bundle?): String? =
        bundle?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)?.firstOrNull()?.takeIf { it.isNotBlank() }

    private companion object {
        const val TAG = "Dictation"
        const val BUSY_RETRY_MS = 300L

        // SpeechRecognizer.ERROR_LANGUAGE_NOT_SUPPORTED / _UNAVAILABLE (API 31), as numbers for older SDK stubs.
        const val ERROR_LANGUAGE_NOT_SUPPORTED = 12
        const val ERROR_LANGUAGE_UNAVAILABLE = 13
    }
}
