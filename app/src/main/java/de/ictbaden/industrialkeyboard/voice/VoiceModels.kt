package de.ictbaden.industrialkeyboard.voice

import android.annotation.SuppressLint
import android.content.Context
import android.net.ConnectivityManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.util.Log
import java.io.File
import java.io.IOException
import java.util.concurrent.Executors

/**
 * The installed language packs of the free dictation and the one download or import that may run
 * at a time. One instance per process, shared by the keyboard panel and the settings; listeners
 * are called on the main thread.
 *
 * Packs live in `files/voice-models/<language>/` (downloaded or imported). An MDM can instead put
 * an unpacked model into `Android/data/<package>/files/voice-models/<de|en|vosk-model-…>/`
 * ([managedDir]); such a model is used as it is and wins over a downloaded one.
 */
class VoiceModels private constructor(context: Context) {

    private val app = context.applicationContext
    private val main = Handler(Looper.getMainLooper())
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "voice-models").apply { isDaemon = true } }

    /** Downloaded and imported packs. */
    val modelsDir: File = File(app.filesDir, "voice-models")

    /** Folder an MDM can fill with unpacked models (`Android/data/<package>/files/voice-models`). */
    val managedDir: File? get() = app.getExternalFilesDir(null)?.let { File(it, "voice-models") }

    sealed interface Job {
        data object Idle : Job

        /** [total] = -1 while the size is unknown (import). */
        data class Running(val language: String?, val done: Long, val total: Long) : Job {
            val percent: Int get() = if (total > 0) (done * 100 / total).toInt().coerceIn(0, 100) else -1
        }

        data class Done(val language: String) : Job

        data class Failed(val language: String?, val reason: ModelException.Reason) : Job
    }

    @Volatile
    var job: Job = Job.Idle
        private set

    @Volatile
    private var cancel = false
    private val listeners = mutableListOf<(Job) -> Unit>()

    init {
        worker.execute { ModelInstaller.cleanUp(modelsDir) }
    }

    fun addListener(listener: (Job) -> Unit) {
        listeners += listener
    }

    fun removeListener(listener: (Job) -> Unit) {
        listeners -= listener
    }

    val busy: Boolean get() = job is Job.Running

    /** Where the pack for [language] is, or `null`: the MDM folder first, then the downloaded one. */
    fun installed(language: String): File? = managed(language) ?: File(modelsDir, language).takeIf(ModelInstaller::isModel)

    /** The pack for [language] came from the MDM folder (cannot be deleted in the app). */
    fun isManaged(language: String): Boolean = managed(language) != null

    private fun managed(language: String): File? = runCatching {
        managedDir?.listFiles()?.filter { it.isDirectory && VoiceModelCatalog.languageOfFolder(it.name) == language }
            ?.firstNotNullOfOrNull(ModelInstaller::modelRoot)
    }.getOrNull()

    fun sizeOf(language: String): Long = installed(language)?.let(ModelInstaller::sizeOf) ?: 0L

    /** Removes the downloaded or imported pack of [language] (not one of the MDM). */
    fun delete(language: String): Boolean {
        if (busy) return false
        val dir = File(modelsDir, language)
        val ok = !dir.exists() || dir.deleteRecursively()
        if (job.let { it is Job.Done && it.language == language || it is Job.Failed }) publish(Job.Idle)
        return ok
    }

    /** The device uses mobile data (or another metered network): the panel and the settings say so first. */
    fun metered(): Boolean = runCatching {
        app.getSystemService(ConnectivityManager::class.java)?.isActiveNetworkMetered == true
    }.getOrDefault(false)

    /** Starts the download of [model] (only when the user asked for it); false if another job runs. */
    fun download(model: VoiceModel, baseUrl: String?): Boolean {
        if (busy) return false
        cancel = false
        val url = VoiceModelCatalog.url(model, baseUrl)
        publish(Job.Running(model.language, 0, model.bytes))
        worker.execute {
            var last = -1
            val result = runCatching {
                ModelDownloader().download(url, model, File(app.cacheDir, "voice-models"), modelsDir, { cancel }) { done ->
                    val percent = (done * 100 / model.bytes).toInt()
                    if (percent != last) {
                        last = percent
                        publish(Job.Running(model.language, done, model.bytes))
                    }
                }
            }
            finish(model.language, result)
        }
        return true
    }

    /**
     * Imports a pack from a ZIP the user picked. A known file (same SHA-256 as a download) gets its
     * language from the list; any other Vosk ZIP from its top folder (`vosk-model-…-de-…`).
     */
    fun import(uri: Uri): Boolean {
        if (busy) return false
        cancel = false
        publish(Job.Running(null, 0, -1))
        worker.execute {
            val work = File(app.cacheDir, "voice-models").apply { mkdirs() }
            val copy = File(work, "import.zip.part")
            var language: String? = null
            val result = runCatching {
                try {
                    val input = app.contentResolver.openInputStream(uri) ?: throw IOException("not readable")
                    val sha = try {
                        input.use { stream -> copy.outputStream().use { ModelInstaller.copyHashing(stream, it, ModelInstaller.MAX_UNPACKED_BYTES, { cancel }) } }
                    } catch (e: ModelException) {
                        throw if (e.reason == ModelException.Reason.CHECKSUM) ModelException(ModelException.Reason.TOO_LARGE, "file too large") else e
                    } catch (e: IOException) {
                        throw ModelException(ModelException.Reason.STORAGE, e.message ?: "copy failed", e)
                    }
                    val known = VoiceModelCatalog.bySha256(sha)
                    val top = ModelInstaller.topFolder(copy)
                    language = known?.language ?: top?.let(VoiceModelCatalog::languageOfFolder)
                        ?: throw ModelException(ModelException.Reason.INVALID, "language unknown")
                    publish(Job.Running(language, 0, -1))
                    ModelInstaller.install(copy, modelsDir, language!!, known?.folder ?: top ?: "import", { cancel })
                } finally {
                    copy.delete()
                }
            }
            finish(language, result)
        }
        return true
    }

    fun cancel() {
        cancel = true
    }

    private fun finish(language: String?, result: Result<File>) {
        val error = result.exceptionOrNull()
        if (error == null) {
            Log.i(TAG, "Language pack $language installed")
            publish(Job.Done(language.orEmpty()))
            return
        }
        val reason = (error as? ModelException)?.reason ?: ModelException.Reason.STORAGE
        Log.w(TAG, "Language pack $language: $reason (${error.message})")
        publish(if (reason == ModelException.Reason.CANCELLED) Job.Idle else Job.Failed(language, reason))
    }

    private fun publish(state: Job) {
        job = state
        main.post { listeners.toList().forEach { it(state) } }
    }

    companion object {
        private const val TAG = "VoiceModels"

        // Holds only the application context.
        @SuppressLint("StaticFieldLeak")
        @Volatile
        private var instance: VoiceModels? = null

        fun get(context: Context): VoiceModels = instance ?: synchronized(this) {
            instance ?: VoiceModels(context).also { instance = it }
        }
    }
}
