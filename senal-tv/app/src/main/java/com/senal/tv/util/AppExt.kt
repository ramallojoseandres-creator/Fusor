package com.senal.tv.util

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import com.senal.tv.AppContainer
import com.senal.tv.SenalApp

@Composable
fun rememberAppContainer(): AppContainer {
    val context = LocalContext.current
    return remember(context) { context.appContainer() }
}

fun Context.appContainer(): AppContainer =
    (applicationContext as SenalApp).container

fun formatDurationMinutes(minutes: Int?): String {
    if (minutes == null || minutes <= 0) return "—"
    val h = minutes / 60
    val m = minutes % 60
    return if (h > 0) "${h}h ${m}m" else "${m} min"
}

fun formatRating(rating: Double?): String {
    if (rating == null || rating <= 0.0) return "—"
    return String.format("%.1f", rating)
}
