package com.pesaflow.app.ui.motion

import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.airbnb.lottie.compose.LottieAnimation
import com.airbnb.lottie.compose.LottieCompositionSpec
import com.airbnb.lottie.compose.animateLottieCompositionAsState
import com.airbnb.lottie.compose.rememberLottieComposition
import com.pesaflow.app.R


/** One-shot success burst (green check + gold stars). Plays once, then
 *  calls onDone — callers swap dialog content for this, then dismiss. */
@Composable
fun SuccessBurst(
    modifier: Modifier = Modifier,
    onDone: () -> Unit = {}
) {
    val composition by rememberLottieComposition(
        LottieCompositionSpec.RawRes(R.raw.success_check)
    )
    val progress by animateLottieCompositionAsState(
        composition = composition,
        iterations = 1,
        restartOnPlay = false
    )
    LottieAnimation(
        composition = composition,
        progress = { progress },
        modifier = modifier.size(180.dp)
    )
    LaunchedEffect(progress) {
        if (progress >= 1f) onDone()
    }
}
