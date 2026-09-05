package com.peartv.launcher.ui.motion

import androidx.activity.BackEventCompat
import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.util.lerp
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/**
 * Predictive-back visual retreat for a whole screen subtree.
 *
 * Applied to the `Box` wrapping the Settings screen in `MainActivity`. While a
 * system predictive-back gesture is in progress the content it wraps scales
 * down, fades, and slides a little toward the swiped edge — mirroring the
 * platform's own predictive-back affordance. Letting the gesture complete
 * commits the back (`onBack` fires exactly once); cancelling it springs the
 * content back to rest and `onBack` never fires.
 *
 * Contract notes (verified against `androidx.activity.compose` 1.9.3 source):
 * - The `onBack` lambda MUST collect the whole progress flow or the library
 *   throws (`check(completed)` in `OnBackInstance`). Every path here collects
 *   fully before doing anything else.
 * - On cancel, the library calls `channel.cancel(CancellationException(...))`
 *   AND `job.cancel()` on the coroutine running `onBack`. That means any
 *   suspend call made *after* catching the `CancellationException` (e.g.
 *   `Animatable.animateTo`) would immediately re-throw. The settle-back
 *   animation is therefore launched on a separate [rememberCoroutineScope]
 *   that outlives the cancelled `onBack` coroutine.
 * - Per the library docs/sample, returning normally from `onBack` after a
 *   cancellation is expected — we do NOT re-throw the `CancellationException`
 *   (re-throwing here can crash).
 * - Button/D-pad back delivers the flow near-instantly (complete with zero or
 *   a couple of progress events); the completion path below just runs the
 *   quick commit animation and fires `onBack`, so no separate `BackHandler`
 *   is needed.
 */
fun Modifier.predictiveBackTransform(
    enabled: Boolean,
    onBack: () -> Unit,
): Modifier = composed {
    val reduceMotion = LocalReduceMotion.current
    val currentOnBack by rememberUpdatedState(onBack)

    val scaleAnim = remember { Animatable(RestScale) }
    val alphaAnim = remember { Animatable(RestAlpha) }
    val txAnim = remember { Animatable(0f) }

    // Outlives the (cancelled) onBack coroutine — see the KDoc contract notes.
    val settleScope = rememberCoroutineScope()

    PredictiveBackHandler(enabled = enabled) { progress ->
        if (reduceMotion) {
            // Still must drain the flow; then commit on completion, nothing on
            // cancel. No animation under reduce-motion.
            try {
                progress.collect { }
                currentOnBack()
            } catch (_: CancellationException) {
                // gesture cancelled — nothing to undo
            }
            return@PredictiveBackHandler
        }

        var edgeSign = 1f
        try {
            progress.collect { event ->
                edgeSign = if (event.swipeEdge == BackEventCompat.EDGE_LEFT) 1f else -1f
                val t = decelerate(event.progress.coerceIn(0f, 1f))
                scaleAnim.snapTo(lerp(RestScale, GesturePeakScale, t))
                alphaAnim.snapTo(lerp(RestAlpha, GesturePeakAlpha, t))
                txAnim.snapTo(edgeSign * lerp(0f, MaxTranslationDp, t))
            }

            // Flow completed normally -> commit the back.
            coroutineScope {
                launch { scaleAnim.animateTo(CommitScale, tween(CommitMillis)) }
                launch { alphaAnim.animateTo(CommitAlpha, tween(CommitMillis)) }
            }
            currentOnBack()

            // Reset to rest so a reused instance (popping a Settings sub-page
            // keeps this Box composed) starts clean. If the commit disposed
            // this composable instead (popping the whole screen), the scope is
            // already cancelled and snapTo throws — harmless, the flow already
            // completed.
            try {
                scaleAnim.snapTo(RestScale)
                alphaAnim.snapTo(RestAlpha)
                txAnim.snapTo(0f)
            } catch (_: CancellationException) {
                // composable was disposed by the commit — nothing to reset
            }
        } catch (_: CancellationException) {
            // Gesture cancelled. onBack must NOT fire. Spring back to rest on a
            // scope that isn't the (now-cancelled) onBack coroutine.
            settleScope.launch { scaleAnim.animateTo(RestScale, TvSprings.ScaleFocusLoss) }
            settleScope.launch { alphaAnim.animateTo(RestAlpha, TvSprings.ScaleFocusLoss) }
            settleScope.launch { txAnim.animateTo(0f, TvSprings.ScaleFocusLoss) }
        }
    }

    graphicsLayer {
        scaleX = scaleAnim.value
        scaleY = scaleAnim.value
        alpha = alphaAnim.value
        translationX = txAnim.value.dp.toPx()
        transformOrigin = TransformOrigin(0.5f, 0.5f)
    }
}

/** Rest / gesture-peak / commit targets for [predictiveBackTransform]. */
private const val RestScale = 1f
private const val GesturePeakScale = 0.90f
private const val CommitScale = 0.88f

private const val RestAlpha = 1f
private const val GesturePeakAlpha = 0.55f
private const val CommitAlpha = 0f

/** Max horizontal slide toward the swiped edge, in dp. */
private const val MaxTranslationDp = 32f

/** Quick "rest of the way out" once the gesture commits. */
private const val CommitMillis = 140

/**
 * Ease the linear `BackEventCompat.progress` the way the platform's own
 * predictive-back animation does — roughly a decelerate curve.
 */
private fun decelerate(t: Float): Float = 1f - (1f - t) * (1f - t)
