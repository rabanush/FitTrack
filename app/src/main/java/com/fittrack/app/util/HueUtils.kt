package com.fittrack.app.util

fun normalizeHueDegrees(value: Float): Float {
    val mod = value % 360f
    return if (mod < 0f) mod + 360f else mod
}
