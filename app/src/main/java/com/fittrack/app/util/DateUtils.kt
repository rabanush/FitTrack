package com.fittrack.app.util

import java.util.Calendar

/** Returns the start-of-today timestamp in milliseconds (midnight of the current day). */
fun todayMillis(): Long = Calendar.getInstance().apply {
    set(Calendar.HOUR_OF_DAY, 0)
    set(Calendar.MINUTE, 0)
    set(Calendar.SECOND, 0)
    set(Calendar.MILLISECOND, 0)
}.timeInMillis
