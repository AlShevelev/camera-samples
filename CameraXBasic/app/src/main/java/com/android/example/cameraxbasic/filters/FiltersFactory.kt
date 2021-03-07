package com.android.example.cameraxbasic.filters

import android.content.Context
import com.android.example.cameraxbasic.R
import com.android.example.cameraxbasic.filters.filter.CameraFilter


class FiltersFactory(context: Context) {
    private val filters = mutableMapOf(         // All filters must be created in a renderer thread
        FilterCode.GRAY to CameraFilter(context, R.raw.gray),
        FilterCode.NEGATIVE to CameraFilter(context, R.raw.negative)
    )

    fun getFilter(key: FilterCode): CameraFilter = filters[key] ?: throw Exception("This key is not supported: $key")
}