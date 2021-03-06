package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.opengl.EGL14.EGL_CONTEXT_CLIENT_VERSION
import android.opengl.EGL14.EGL_OPENGL_ES2_BIT
import android.opengl.GLES11Ext
import android.opengl.GLES31
import android.os.Bundle
import android.util.Log
import android.view.*
import androidx.camera.core.AspectRatio
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.filters.FiltersFactory
import com.android.example.cameraxbasic.filters.filter.CameraFilter
import com.android.example.cameraxbasic.filters.filter_settings.BlackAndWhiteFilterSettings
import com.android.example.cameraxbasic.filters.filter_settings.FilterSettings
import com.android.example.cameraxbasic.fragments.camera.CameraInfoCalculator
import com.android.example.cameraxbasic.fragments.utils.TextureUtils
import java.util.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import javax.microedition.khronos.egl.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var container: ConstraintLayout

    private lateinit var textureView: TextureView

    private var preview: Preview? = null
    private var camera: Camera? = null
    private var cameraProvider: ProcessCameraProvider? = null

    private lateinit var surfaceTexture: SurfaceTexture

    private var glWidth = 0
    private var glHeight = 0
    private lateinit var egl10: EGL10
    private lateinit var eglDisplay: EGLDisplay
    private var eglSurface: EGLSurface? = null
    private lateinit var eglContext: EGLContext

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private lateinit var cameraSurfaceTexture: SurfaceTexture
    private var cameraTextureId: Int = 0

    private lateinit var selectedFilter: CameraFilter
    private lateinit var selectedFilterSettings: FilterSettings

    private lateinit var filtersFactory: FiltersFactory

    override fun onDestroyView() {
        super.onDestroyView()

        // Shut down our background executor
        cameraExecutor.shutdown()
    }

    override fun onCreateView(
            inflater: LayoutInflater,
            container: ViewGroup?,
            savedInstanceState: Bundle?): View? =
            inflater.inflate(R.layout.fragment_camera, container, false)

    @SuppressLint("MissingPermission")
    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        container = view as ConstraintLayout

        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        textureView =  container.findViewById(R.id.previewSurface)
        textureView.surfaceTextureListener = this
    }

    /** Initialize CameraX, and prepare to bind the camera use cases  */
    private fun setUpCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext())
        cameraProviderFuture.addListener({

            // CameraProvider
            cameraProvider = cameraProviderFuture.get()

            // Build and bind the camera use cases
            bindCameraUseCases()
        }, ContextCompat.getMainExecutor(requireContext()))
    }

    /** Declare and bind preview, capture and analysis use cases */
    private fun bindCameraUseCases() {

        // Get screen metrics used to setup camera for full screen resolution

        val screenAspectRatio = aspectRatio(textureView.width, textureView.height)
        Log.d(TAG, "Preview aspect ratio: $screenAspectRatio")

        val rotation = textureView.display.rotation

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
            camera = cameraProvider.bindToLifecycle(this, cameraSelector, preview)

            // Attach the viewfinder's surface provider to preview use case
            //preview?.setSurfaceProvider(viewFinder.surfaceProvider)

            val cameraInfo = CameraInfoCalculator.calculateCameraInfo(requireContext())

            preview?.setSurfaceProvider(PreviewSurfaceProvider(cameraSurfaceTexture, cameraInfo.optimalOutputSize, cameraExecutor))
        } catch (exc: Exception) {
            Log.e(TAG, "Use case binding failed", exc)
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
        Log.d("textureSize", "aspectRatio: $width; $height")

        val previewRatio = max(width, height).toDouble() / min(width, height)
        if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
            Log.d("textureSize", "aspectRatio: RATIO_4_3")
            return AspectRatio.RATIO_4_3
        }
        Log.d("textureSize", "aspectRatio: RATIO_16_9")

        return AspectRatio.RATIO_16_9
    }

    companion object {
        private const val TAG = "CameraXBasic"
        private const val RATIO_4_3_VALUE = 4.0 / 3.0
        private const val RATIO_16_9_VALUE = 16.0 / 9.0
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("textureSize", "onSurfaceTextureAvailable: $width; $height")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("textureSize", "onSurfaceTextureSizeChanged: $width; $height")

        surfaceTexture = surface
        glWidth = -width
        glHeight = -height

        initGL(surfaceTexture)

        filtersFactory = FiltersFactory(requireContext())
        selectedFilterSettings = BlackAndWhiteFilterSettings(isInverted = false)
        selectedFilter = filtersFactory.getFilter(selectedFilterSettings.code)
        selectedFilter.onAttach(selectedFilterSettings)

        // Set up the camera and its use cases
        cameraTextureId = TextureUtils.createTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES)
        cameraSurfaceTexture = SurfaceTexture(cameraTextureId)
        cameraSurfaceTexture.setOnFrameAvailableListener {
            Log.d("textureSize", "onFrameAvailable")

            it.updateTexImage()

            if (glWidth < 0 && glHeight < 0) {
                glWidth = -glWidth
                glHeight = -glHeight
                GLES31.glViewport(0, 0, glWidth, glHeight)
            }

            GLES31.glClear(GLES31.GL_COLOR_BUFFER_BIT)

            // Draw camera preview
            selectedFilter.draw(cameraTextureId, glWidth, glHeight)

            // Flush
            GLES31.glFlush()
            egl10.eglSwapBuffers(eglDisplay, eglSurface)
        }

        setUpCamera()
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        // Stop rendering
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        Log.d("textureSize", "onSurfaceTextureUpdated")
    }

    private fun initGL(texture: SurfaceTexture) {
        egl10 = EGLContext.getEGL() as EGL10

        eglDisplay = egl10.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
        if (eglDisplay == EGL10.EGL_NO_DISPLAY) {
            throw Exception("eglGetDisplay failed: ${android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError())}")
        }

        val version = IntArray(2)
        if (!egl10.eglInitialize(eglDisplay, version)) {
            throw Exception("eglInitialize failed: ${android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError())}")
        }

        val configsCount = IntArray(1)
        val configs = arrayOfNulls<EGLConfig>(1)

        val configSpec = intArrayOf(
            EGL10.EGL_RENDERABLE_TYPE,
            EGL_OPENGL_ES2_BIT,
            EGL10.EGL_RED_SIZE, 8,
            EGL10.EGL_GREEN_SIZE, 8,
            EGL10.EGL_BLUE_SIZE, 8,
            EGL10.EGL_ALPHA_SIZE, 8,
            EGL10.EGL_DEPTH_SIZE, 0,
            EGL10.EGL_STENCIL_SIZE, 0,
            EGL10.EGL_NONE
        )

        var eglConfig: EGLConfig? = null
        if (!egl10.eglChooseConfig(eglDisplay, configSpec, configs, 1, configsCount)) {
            throw IllegalArgumentException("eglChooseConfig failed: ${android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError())}")
        } else if (configsCount[0] > 0) {
            eglConfig = configs[0]
        }
        if (eglConfig == null) {
            throw Exception("eglConfig not initialized")
        }

        val attrs = intArrayOf(EGL_CONTEXT_CLIENT_VERSION, 2, EGL10.EGL_NONE)
        eglContext = egl10.eglCreateContext(eglDisplay, eglConfig, EGL10.EGL_NO_CONTEXT, attrs)
        eglSurface = egl10.eglCreateWindowSurface(eglDisplay, eglConfig, texture, null)

        if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
            val error = egl10.eglGetError()
            throw Exception("eglCreateWindowSurface failed: ${android.opengl.GLUtils.getEGLErrorString(error)}")
        }

        if (!egl10.eglMakeCurrent(eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw RuntimeException("eglMakeCurrent failed: ${android.opengl.GLUtils.getEGLErrorString(egl10.eglGetError())}")
        }
    }
}
