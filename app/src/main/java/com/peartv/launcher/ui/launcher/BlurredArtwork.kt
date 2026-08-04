package com.peartv.launcher.ui.launcher

import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.platform.LocalContext
import coil.imageLoader
import coil.request.ImageRequest
import com.google.android.renderscript.Toolkit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * How much smaller than the decoded source the blur operates on — blurring
 * destroys fine detail regardless, so there's no visible quality loss from
 * running it on a quarter-size copy, and it's proportionally faster.
 */
private const val BlurDownscaleFactor = 4

/**
 * User-directed, lowered from the toolkit's own max (25) — Liquid Glass reads
 * as *glass*, not a fully abstracted frosted pane, partly because shapes
 * stay loosely legible through it. 25 (this project's original choice, before
 * a tint/legibility pass) abstracted away too much of that; 16 is the
 * smallest value that still kept dock tile focus/text comfortably legible
 * against the busiest carousel art tested on-device.
 */
private const val BlurRadius = 16

/** Side length of the tiny scratch bitmap [averageColorAndLuminance] samples — cheap enough to run once per artwork change without a real cost. */
private const val AverageColorSampleSize = 8

/**
 * Blurs [url]'s own decoded artwork directly — replacing an earlier attempt
 * that tried to blur a *captured composited frame* instead
 * (`GraphicsLayer.toImageBitmap()`, `BackdropBlur.kt`, since deleted).
 * Confirmed on-device that capture path returns fully transparent data on
 * this project's own reference hardware — a platform limitation with that
 * (relatively new) Compose API, not something fixable from app code. This
 * sidesteps it entirely: the artwork is already a known, independently-
 * decodable asset (the same one the sharp poster in `ContentCarousel` shows)
 * long before it's ever composited into a frame, so there's nothing to
 * capture — just decode it a second time (via Coil's own cache, so a real
 * network re-fetch is the rare case, not the common one) and blur that.
 *
 * `allowHardware(false)` is load-bearing: without it Coil may hand back a
 * `HARDWARE`-config `Bitmap` (GPU-backed, no CPU pixel access), which
 * [Toolkit.blur] rejects outright (confirmed on-device:
 * `IllegalArgumentException`, "supports only ARGB_8888 and ALPHA_8
 * bitmaps"). Forcing a software decode here avoids that class of bug
 * altogether rather than copying a hardware bitmap after the fact.
 *
 * Blurs once per [url] change (a whole `ContentCarousel` poster hold, ~8s),
 * not per frame — the artwork itself is static for that whole window; only
 * the Ken Burns transform applied on top of it (by the caller, via
 * [com.peartv.launcher.ui.motion.kenBurnsTransform]) animates. The average
 * color/luminance `TopShelfRow` derives its tint from (see [BlurredArtwork]'s
 * own doc) is computed in this same pass for the same reason — one artwork,
 * one blur, one sample, all per ~8s poster rather than per frame.
 */
@Composable
fun rememberBlurredArtwork(url: String?): State<BlurredArtwork?> {
    val context = LocalContext.current
    val artwork = remember { mutableStateOf<BlurredArtwork?>(null) }
    LaunchedEffect(url) {
        artwork.value = null
        if (url == null) return@LaunchedEffect
        val drawable = kotlin.runCatching {
            val request = ImageRequest.Builder(context)
                .data(url)
                .allowHardware(false)
                .build()
            context.imageLoader.execute(request).drawable
        }.getOrNull()
        val sourceBitmap = (drawable as? BitmapDrawable)?.bitmap ?: return@LaunchedEffect
        val result = withContext(Dispatchers.Default) {
            val downscaled = Bitmap.createScaledBitmap(
                sourceBitmap,
                (sourceBitmap.width / BlurDownscaleFactor).coerceAtLeast(1),
                (sourceBitmap.height / BlurDownscaleFactor).coerceAtLeast(1),
                true,
            )
            val blurred = Toolkit.blur(downscaled, BlurRadius)
            val averageColor = averageColor(blurred)
            BlurredArtwork(blurred.asImageBitmap(), averageColor, averageColor.luminance())
        }
        artwork.value = result
    }
    return artwork
}

/** Cheap average-color sample — scales [bitmap] down to [AverageColorSampleSize]² and means the pixels, not a real histogram. */
private fun averageColor(bitmap: Bitmap): Color {
    val tiny = Bitmap.createScaledBitmap(bitmap, AverageColorSampleSize, AverageColorSampleSize, true)
    val pixels = IntArray(AverageColorSampleSize * AverageColorSampleSize)
    tiny.getPixels(pixels, 0, AverageColorSampleSize, 0, 0, AverageColorSampleSize, AverageColorSampleSize)
    var r = 0L
    var g = 0L
    var b = 0L
    for (pixel in pixels) {
        r += (pixel shr 16) and 0xFF
        g += (pixel shr 8) and 0xFF
        b += pixel and 0xFF
    }
    val n = pixels.size
    return Color(r / n / 255f, g / n / 255f, b / n / 255f, 1f)
}

/**
 * One blurred snapshot of a carousel poster, plus what `TopShelfRow` needs to
 * tint it Liquid-Glass-style instead of a flat static tint: [averageColor]
 * (what the dock's own tint leans toward, on top of its usual theme-based
 * color) and [luminance] (drives the tint's *alpha* — bright art needs more
 * cover to hold contrast, dark art can show through more, see
 * `TopShelfRow`'s own tint-alpha doc). Bundled together, not three separate
 * `State`s, so a reader can never see the bitmap from one artwork change
 * paired with the luminance from another — all three come from the exact
 * same [Toolkit.blur] pass.
 */
data class BlurredArtwork(
    val bitmap: ImageBitmap,
    val averageColor: Color,
    val luminance: Float,
)

/**
 * What [ContentCarousel]'s currently-shown poster hands up to `TopShelfRow`
 * so the dock can draw its own crop of the *same* artwork, animated by the
 * *same* [kenBurnsProgress] clock the sharp poster is using — not a separate
 * one (see [com.peartv.launcher.ui.motion.rememberKenBurnsProgress]'s own
 * doc for why that would drift out of sync). Both fields are [State]s, not
 * raw values: `TopShelfRow` reads `.value` directly in its own draw scope,
 * so it always sees this frame's live value without `ContentCarousel` (a
 * sibling, not an ancestor) needing to push a write on every single frame —
 * only when the poster itself changes.
 */
data class DockBackdrop(
    val artwork: State<BlurredArtwork?>,
    val kenBurnsProgress: State<Float>,
)
