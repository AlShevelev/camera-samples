package com.android.example.cameraxbasic.camera

import android.content.Context
import android.graphics.ImageFormat
import android.graphics.Point
import android.graphics.Rect
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.util.Range
import android.util.Rational
import android.util.Size
import android.view.WindowManager
import kotlin.math.abs

object CameraInfoCalculator {
    fun calculateCameraInfo(context: Context): CameraInfo {
        val cameraService = context.getSystemService(Context.CAMERA_SERVICE) as CameraManager

        val cameraIds = cameraService.cameraIdList

        cameraIds.forEach { cameraId ->
            val cameraCharacteristics = cameraService.getCameraCharacteristics(cameraId)
            if (cameraCharacteristics[CameraCharacteristics.LENS_FACING] == CameraCharacteristics.LENS_FACING_BACK) {
                val configurationMap = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)

                val outputSize = configurationMap!!.getOutputSizes(ImageFormat.JPEG).toList()

                // We need 1 (Full) or 3 (Level 3) to work
                val camera2Support =cameraCharacteristics[CameraCharacteristics.INFO_SUPPORTED_HARDWARE_LEVEL]

                return CameraInfo(
                    id = cameraId,
                    isMeteringAreaAFSupported = (cameraCharacteristics[CameraCharacteristics.CONTROL_MAX_REGIONS_AF] as Int) >= 1,
                    sensorArraySize = cameraCharacteristics[CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE] as Rect,
                    maxZoom = cameraCharacteristics[CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM] as Float,
                    exposureRange = cameraCharacteristics[CameraCharacteristics.CONTROL_AE_COMPENSATION_RANGE] as Range<Int>,
                    exposureStep = cameraCharacteristics[CameraCharacteristics.CONTROL_AE_COMPENSATION_STEP] as Rational,
                    outputSize = outputSize,
                    optimalOutputSize = calculateOptimalOutputSize(context, outputSize),
                    canUseCamera = camera2Support == 3 || camera2Support == 1
                )
            }
        }

        throw UnsupportedOperationException("Can't find back camera")
    }

    private fun calculateOptimalOutputSize(context: Context, sourceOutputSize: List<Size>): Size {
        val candidates = mutableListOf<Pair<Int, Size>>()

        val realScreenSize = calculateRealScreenSize(context)

        sourceOutputSize.forEach { outputSize ->
            val relativeScreenHeight = ((outputSize.height / realScreenSize.width.toFloat()) * realScreenSize.height).toInt()
            candidates.add(Pair(abs(relativeScreenHeight - outputSize.width), outputSize))
        }

        val resultCandidates = candidates
            .sortedWith(compareBy({ it.first }, { it.second.width }))
            .map { it.second }

        resultCandidates.forEach {
            if(it.width > realScreenSize.height) {
                return it
            }
        }
        return resultCandidates.last()
    }

    private fun calculateRealScreenSize(context: Context): Size {
        val windowsService = context.applicationContext.getSystemService(Context.WINDOW_SERVICE) as WindowManager
        val display = windowsService.defaultDisplay

        val realSize = Point()
        display.getRealSize(realSize)
        return Size(realSize.x, realSize.y)
    }
}