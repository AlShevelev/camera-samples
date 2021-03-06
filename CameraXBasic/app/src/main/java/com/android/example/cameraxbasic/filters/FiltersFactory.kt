package com.android.example.cameraxbasic.filters

import android.content.Context
import com.android.example.cameraxbasic.filters.filter.BlackAndWhiteCameraFilter
import com.android.example.cameraxbasic.filters.filter.CameraFilter


class FiltersFactory(context: Context) {
    private val filters = mutableMapOf(         // All filters must be created in a renderer thread
        FilterCode.BLACK_AND_WHITE to BlackAndWhiteCameraFilter(context),
    )

    fun getFilter(key: FilterCode): CameraFilter = filters[key] ?: throw Exception("This key is not supported: $key")
}