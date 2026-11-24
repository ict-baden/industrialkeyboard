package de.ictbaden.industrialkeyboard.voice

import java.io.File

/**
 * A speech recognizer inside the app that works with a model on the device (issue #24): records
 * the microphone itself and recognises without any network. Today [VoskEngine]; kept small so that
 * another engine (e.g. sherpa-onnx) can take its place. Callbacks come on the main thread.
 */
interface OfflineSpeechEngine {

    interface Callback {
        /** The model is loaded and the microphone records. */
        fun onReady()

        /** Text of the utterance so far (replaces the previous partial). */
        fun onPartial(text: String)

        /** An utterance ended after a pause (or [stop]): its final text, may be empty. */
        fun onResult(text: String)

        /** Loudness 0–1 for the pulsing microphone. */
        fun onLevel(level: Float)

        fun onError(error: Error)
    }

    enum class Error {
        /** The model could not be loaded (damaged, wrong format, too little memory). */
        MODEL,

        /** The microphone could not be opened (permission, used by another app). */
        AUDIO,
    }

    /** Loads [model] (if not loaded yet) and starts listening; utterance follows utterance until [stop]. */
    fun start(model: File, callback: Callback)

    /** Stops recording; the words of the last utterance still come as [Callback.onResult]. */
    fun stop()

    /** Stops and frees everything, the model included. */
    fun release()
}
