package de.ictbaden.industrialkeyboard.scanner

import android.content.Context
import android.graphics.Point
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import de.ictbaden.industrialkeyboard.config.CameraSettings
import de.ictbaden.industrialkeyboard.config.Gs1Mode
import de.ictbaden.industrialkeyboard.config.Symbology
import zxingcpp.BarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reads barcodes with the camera (CameraX + zxing-cpp) into a [PreviewView]. The input method is
 * no activity, so this class is the lifecycle the camera is bound to: RESUMED while open, CREATED
 * while closed. All calls on the main thread.
 */
class CameraScanner(
    private val context: Context,
    private val onResult: (Hit) -> Unit,
    private val onError: (Throwable) -> Unit,
) : LifecycleOwner {

    /** A code that passed every filter. */
    data class Hit(val text: String, val symbology: Symbology?, val format: String)

    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry

    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null

    /** Applied on the next [open]. */
    var settings = CameraSettings()

    /** GS1 codes human readable or raw (`scanner.data.gs1`); applied on the next [open]. */
    var gs1 = Gs1Mode.HRI

    /** Whether the keyboard would type this code (`scanner.data` rules); others are skipped. */
    @Volatile
    var accept: (String, Symbology?) -> Boolean = { _, _ -> true }

    /** Preview and aiming frame on the screen, updated by the view after each layout. */
    @Volatile
    var screen: AimGeometry.Screen? = null

    /** Called once the camera runs: torch available, torch on. */
    var onStarted: ((hasTorch: Boolean, torchOn: Boolean) -> Unit)? = null

    @Volatile
    private var active: CameraSettings = settings

    @Volatile
    private var reader: BarcodeReader? = null

    /** Set once a single scan was delivered, so frames still in the queue do not type it twice. */
    @Volatile
    private var delivered = false

    // Analyzer thread only.
    private var candidate: String? = null
    private var candidateCount = 0
    private var lastText: String? = null
    private var lastAt = 0L

    val isOpen: Boolean get() = registry.currentState.isAtLeast(Lifecycle.State.STARTED)

    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    var zoom = 1f
        private set

    fun open(preview: PreviewView) {
        if (isOpen) return
        active = settings
        delivered = false
        candidate = null
        candidateCount = 0
        lastText = null
        registry.currentState = Lifecycle.State.RESUMED
        val analyzer = executor ?: Executors.newSingleThreadExecutor().also { executor = it }
        val options = BarcodeReader.Options(
            formats = Symbologies.toZxing(active.symbologies),
            tryRotate = true,
            tryInvert = active.inverted,
            textMode = if (gs1 == Gs1Mode.RAW) BarcodeReader.TextMode.PLAIN else BarcodeReader.TextMode.HRI,
        )
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!isOpen) return@addListener
            val cameraProvider = runCatching { future.get() }.getOrElse { fail(it); return@addListener }
            provider = cameraProvider
            reader = reader?.also { it.options = options } ?: BarcodeReader(options)
            val previewUseCase = Preview.Builder().build().also { it.surfaceProvider = preview.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                // 640×480 (the default) is too coarse for long 1D barcodes at arm's length.
                .setResolutionSelector(
                    ResolutionSelector.Builder()
                        .setResolutionStrategy(ResolutionStrategy(Size(1280, 720), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                        .build(),
                )
                .build()
            analysis.setAnalyzer(analyzer) { image -> image.use(::analyze) }
            val selector = if (runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            runCatching {
                cameraProvider.unbindAll()
                val cam = cameraProvider.bindToLifecycle(this, selector, previewUseCase, analysis)
                camera = cam
                setZoom(active.zoom)
                val torch = active.torch && hasTorch
                if (torch) cam.cameraControl.enableTorch(true)
                onStarted?.invoke(hasTorch, torch)
            }.onFailure(::fail)
        }, ContextCompat.getMainExecutor(context))
    }

    private fun analyze(image: ImageProxy) {
        if (delivered) return
        val reader = reader ?: return
        val results = runCatching { reader.read(image) }
            .onFailure { Log.w(TAG, "Decoding failed", it) }
            .getOrNull()
            ?.filter { !it.text.isNullOrEmpty() && it.error == null }
            .orEmpty()
        val settings = active
        val usable = results.mapNotNull { r ->
            val text = r.text.orEmpty()
            val type = Symbologies.fromZxing(r.format.name)
            when {
                settings.minLength > 0 && text.length < settings.minLength -> null
                settings.maxLength > 0 && text.length > settings.maxLength -> null
                !accept(text, type) -> null
                else -> AimGeometry.Found(Hit(text, type, r.format.name), r.position.let { listOf(it.topLeft, it.topRight, it.bottomRight, it.bottomLeft) }.map(::point))
            }
        }
        val turned = image.imageInfo.rotationDegrees % 180 != 0
        val width = (if (turned) image.cropRect.height() else image.cropRect.width()).toFloat()
        val height = (if (turned) image.cropRect.width() else image.cropRect.height()).toFloat()
        val hit = AimGeometry.pick(usable, width, height, screen, settings.aim)
        if (hit == null) {
            candidate = null
            candidateCount = 0
            return
        }
        // Several frames in a row with the same text against misreads of damaged codes.
        candidateCount = if (hit.text == candidate) candidateCount + 1 else 1
        candidate = hit.text
        if (candidateCount < settings.confirmReads) return
        val now = SystemClock.elapsedRealtime()
        if (settings.continuous && hit.text == lastText && now - lastAt < settings.duplicateDelayMs) {
            lastAt = now
            return
        }
        lastText = hit.text
        lastAt = now
        candidate = null
        candidateCount = 0
        if (!settings.continuous) delivered = true
        main.post { if (isOpen) onResult(hit) }
    }

    fun setTorch(on: Boolean) {
        camera?.takeIf { hasTorch }?.cameraControl?.enableTorch(on)
    }

    /** 1× → 2× → 3× → 1×, limited by the camera; returns the new zoom. */
    fun cycleZoom(): Float {
        val next = when {
            zoom < 1.5f -> 2f
            zoom < 2.5f -> 3f
            else -> 1f
        }
        setZoom(next)
        return zoom
    }

    private fun setZoom(ratio: Float) {
        val cam = camera ?: return
        val state = cam.cameraInfo.zoomState.value
        zoom = ratio.coerceIn(state?.minZoomRatio ?: 1f, state?.maxZoomRatio ?: 1f).coerceAtLeast(1f)
        cam.cameraControl.setZoomRatio(zoom)
    }

    fun close() {
        if (!isOpen) return
        runCatching { provider?.unbindAll() }
        camera = null
        registry.currentState = Lifecycle.State.CREATED
    }

    fun destroy() {
        close()
        registry.currentState = Lifecycle.State.DESTROYED
        executor?.shutdown()
        executor = null
    }

    private fun fail(error: Throwable) {
        Log.e(TAG, "Camera failed", error)
        close()
        onError(error)
    }

    private fun point(p: Point) = AimGeometry.Point(p.x.toFloat(), p.y.toFloat())

    private companion object {
        const val TAG = "CameraScanner"
    }
}
