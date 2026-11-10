package de.ictbaden.industrialkeyboard.scanner

import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import zxingcpp.BarcodeReader
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

/**
 * Reads barcodes with the camera (CameraX + zxing-cpp) into a [PreviewView] inside the keyboard.
 * The input method is no activity, so this class is the lifecycle the camera is bound to:
 * RESUMED while open, CREATED while closed. All calls on the main thread.
 */
class CameraScanner(
    private val context: Context,
    private val onResult: (text: String, format: String) -> Unit,
    private val onError: (Throwable) -> Unit,
) : LifecycleOwner {

    private val registry = LifecycleRegistry(this).apply { currentState = Lifecycle.State.CREATED }
    override val lifecycle: Lifecycle get() = registry

    private val main = Handler(Looper.getMainLooper())
    private var executor: ExecutorService? = null
    private var provider: ProcessCameraProvider? = null
    private var camera: Camera? = null
    private val reader by lazy { BarcodeReader(BarcodeReader.Options(tryRotate = true, tryInvert = true)) }

    /** Set once a code was read, so the frames still in the queue do not type it twice. */
    @Volatile
    private var delivered = false

    val isOpen: Boolean get() = registry.currentState.isAtLeast(Lifecycle.State.STARTED)

    val hasTorch: Boolean get() = camera?.cameraInfo?.hasFlashUnit() == true

    /** Called once the camera runs, e.g. to show the torch button. */
    var onStarted: (() -> Unit)? = null

    fun open(preview: PreviewView) {
        if (isOpen) return
        delivered = false
        registry.currentState = Lifecycle.State.RESUMED
        val analyzer = executor ?: Executors.newSingleThreadExecutor().also { executor = it }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!isOpen) return@addListener
            val cameraProvider = runCatching { future.get() }.getOrElse { fail(it); return@addListener }
            provider = cameraProvider
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
            analysis.setAnalyzer(analyzer) { image ->
                image.use {
                    if (delivered) return@use
                    val result = runCatching { reader.read(it) }
                        .onFailure { e -> Log.w(TAG, "Decoding failed", e) }
                        .getOrNull()
                        ?.firstOrNull { r -> !r.text.isNullOrEmpty() && r.error == null }
                        ?: return@use
                    delivered = true
                    main.post { if (isOpen) onResult(result.text.orEmpty(), result.format.name) }
                }
            }
            val selector = if (runCatching { cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA) }.getOrDefault(false)) {
                CameraSelector.DEFAULT_BACK_CAMERA
            } else {
                CameraSelector.DEFAULT_FRONT_CAMERA
            }
            runCatching {
                cameraProvider.unbindAll()
                camera = cameraProvider.bindToLifecycle(this, selector, previewUseCase, analysis)
                onStarted?.invoke()
            }.onFailure(::fail)
        }, ContextCompat.getMainExecutor(context))
    }

    fun setTorch(on: Boolean) {
        camera?.takeIf { hasTorch }?.cameraControl?.enableTorch(on)
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

    private companion object {
        const val TAG = "CameraScanner"
    }
}
