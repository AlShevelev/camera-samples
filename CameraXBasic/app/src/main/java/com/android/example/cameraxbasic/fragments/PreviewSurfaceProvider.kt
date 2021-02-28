package com.android.example.cameraxbasic.fragments

import android.view.Surface
import androidx.camera.core.Preview
import androidx.camera.core.SurfaceRequest
import java.util.concurrent.Executor

class PreviewSurfaceProvider (
    private val surface: Surface,
    private val executor: Executor
): Preview.SurfaceProvider {
    override fun onSurfaceRequested(request: SurfaceRequest) {
        request.provideSurface(surface, executor, {
            // do noting
        })
    }
}