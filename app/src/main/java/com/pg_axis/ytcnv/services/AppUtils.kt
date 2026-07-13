package com.pg_axis.ytcnv.services

import androidx.annotation.Keep

@Keep
enum class Theme(private val displayName: String) {
    CYAN("theme_cyan"),
    GRAYSCALE("theme_gray"),
    EMBER("theme_ember"),
    AETHER("theme_aether"),
    PHOSPHOR("theme_phosphor"),
    CHALK("theme_chalk"),
    SUNSHINE("theme_sunshine"),
    BORDO("theme_bordo"),
    VOID("theme_void");

    override fun toString(): String = displayName
}