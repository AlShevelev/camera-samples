package com.android.example.cameraxbasic.camera

import android.content.Context
import android.graphics.SurfaceTexture
import android.view.TextureView
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.android.example.cameraxbasic.fragments.PreviewSurfaceProvider
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

class CameraManager {
    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    fun initCamera(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        surface: SurfaceTexture,
        screenTexture: TextureView) {

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            bindCameraUseCases(context, lifecycleOwner, surface, screenTexture)
        }, ContextCompat.getMainExecutor(context))
    }

    fun releaseCamera() {
        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases(
        context: Context,
        lifecycleOwner: LifecycleOwner,
        cameraSurface: SurfaceTexture,
        screenTexture: TextureView) {

        // Get screen metrics used to setup camera for full screen resolution

        val screenAspectRatio = aspectRatio(screenTexture.width, screenTexture.height)

        val rotation = screenTexture.display.rotation

        // CameraProvider
        val cameraProvider = cameraProvider
            ?: throw IllegalStateException("Camera initialization failed.")

        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(CameraSelector.LENS_FACING_BACK).build()

        // Preview
        preview = Preview.Builder()
            // We request aspect ratio but no resolution
            .setTargetAspectRatio(screenAspectRatio)
            // Set initial target rotation
            .setTargetRotation(rotation)
            //          .setTargetResolution(Size(textureView.width, textureView.height))
            .build()

        // Must unbind the use-cases before rebinding them
        cameraProvider.unbindAll()

        try {
            // A variable number of use-cases can be passed here -
            // camera provides access to CameraControl & CameraInfo
            camera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview)

            // Attach the viewfinder's surface provider to preview use case
            //preview?.setSurfaceProvider(viewFinder.surfaceProvider)

            val cameraInfo = CameraInfoCalculator.calculateCameraInfo(context)

            preview?.setSurfaceProvider(PreviewSurfaceProvider(
                cameraSurface,
                cameraInfo.optimalOutputSize,
                cameraExecutor))

        } catch (ex: Exception) {
            ex.printStackTrace()
        }
    }

    /**
     *  [androidx.camera.core.ImageAnalysisConfig] requires enum value of
     *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
     *
     *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
     *  of preview ratio to one of the provided values.
     *
     *  @param width - preview width
     *  @param height - preview height
     *  @return suitable aspect ratio
     */
    private fun aspectRatio(width: Int, height: Int): Int {
        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            return AspectRatio.RATIO_4_3
        }

        return AspectRatio.RATIO_16_9
    }


    companion object {
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }
}