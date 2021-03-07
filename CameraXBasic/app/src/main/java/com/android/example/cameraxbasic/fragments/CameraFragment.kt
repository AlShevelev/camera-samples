package com.android.example.cameraxbasic.fragments

import android.annotation.SuppressLint
import android.graphics.SurfaceTexture
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.TextureView
import android.view.View
import android.view.ViewGroup
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.fragment.app.Fragment
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.camera.CameraManager
import com.android.example.cameraxbasic.filters.FilterCode
import com.android.example.cameraxbasic.filters.FiltersFactory
import com.android.example.cameraxbasic.filters.filter.CameraFilter
import com.android.example.cameraxbasic.filters.filter_settings.EmptyFilterSettings
import com.android.example.cameraxbasic.gl.GLRenderer

/**
 * Main fragment for this app. Implements all camera operations including:
 * - Viewfinder
 * - Photo taking
 * - Image analysis
 */
class CameraFragment : Fragment(), TextureView.SurfaceTextureListener {

    private lateinit var container: ConstraintLayout

    private lateinit var textureView: TextureView

    private lateinit var renderer: GLRenderer

    private lateinit var cameraManager: CameraManager

    override fun onDestroyView() {
        super.onDestroyView()

        cameraManager.releaseCamera()
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

        textureView =  container.findViewById(R.id.previewSurface)
        textureView.surfaceTextureListener = this
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("textureSize", "onSurfaceTextureAvailable: $width; $height")
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        Log.d("textureSize", "onSurfaceTextureSizeChanged: $width; $height")

        renderer = GLRenderer(-width, -height)
        renderer.initGL(surface)

        val filtersFactory = FiltersFactory(requireContext())
        val selectedFilterSettings = EmptyFilterSettings(FilterCode.GRAY)
        val selectedFilter = filtersFactory.getFilter(selectedFilterSettings.code)
        selectedFilter.onAttach(selectedFilterSettings)

        renderer.setFilter(selectedFilter)

        // Set up the camera and its use cases

        renderer.cameraSurfaceTexture.setOnFrameAvailableListener {
            Log.d("textureSize", "onFrameAvailable")

            it.updateTexImage()

            renderer.renderFrame()
        }

        cameraManager = CameraManager()
        cameraManager.initCamera(requireContext(), this, renderer.cameraSurfaceTexture, textureView)
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        renderer.releaseGL()

        CameraFilter.release()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        Log.d("textureSize", "onSurfaceTextureUpdated")
    }
}
