package com.splitandmerge.mkvslice.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush

fun Modifier.shimmer(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "ShimmerTransition")
    val translateAnim by infiniteTransition.animateFloat(
        initialValue = -200f,
        targetValue = 1200f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "ShimmerTranslation"
    )

    val baseColor = MaterialTheme.colorScheme.surfaceVariant
    val shimmerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)

    val brush = Brush.linearGradient(
        colors = listOf(baseColor, shimmerColor, baseColor),
        start = Offset(translateAnim, 0f),
        end = Offset(translateAnim + 200f, 0f)
    )

    this.background(brush)
}
