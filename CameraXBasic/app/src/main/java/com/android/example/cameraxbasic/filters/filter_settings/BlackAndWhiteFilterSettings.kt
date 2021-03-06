package com.android.example.cameraxbasic.filters.filter_settings

import com.android.example.cameraxbasic.filters.FilterCode

data class BlackAndWhiteFilterSettings(
    override val code: FilterCode = FilterCode.BLACK_AND_WHITE,
    val isInverted: Boolean
): FilterSettings