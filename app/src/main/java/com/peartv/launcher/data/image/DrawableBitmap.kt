package com.peartv.launcher.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable
import androidx.palette.graphics.Palette

/** Shared by [toHardwareBitmap] and [dominantColorArgb] — draws this [Drawable] into a fresh, mutable ARGB_8888 bitmap at the given size, restoring this drawable's own [Drawable.getBounds] afterward rather than leaving it mutated. */
private fun Drawable.rasterize(width: Int, height: Int): Bitmap {
    val software = Bitmap.createBitmap(width.coerceAtLeast(1), height.coerceAtLeast(1), Bitmap.Config.ARGB_8888)
    val canvas = Canvas(software)
    val originalBounds = bounds
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    bounds = originalBounds
    return software
}

/**
 * Rasterizes a [Drawable] into an immutable, GPU-resident [Bitmap.Config.HARDWARE]
 * bitmap (PRODUCT_SPEC.md §2.2).
 *
 * Hardware bitmaps can't be drawn into directly — a [Canvas] needs a mutable
 * software surface — so this draws into a throwaway ARGB_8888 bitmap first
 * ([rasterize]), then copies that into an immutable HARDWARE bitmap. The
 * intermediate copy only happens once, at app-list build time (§3.2), never
 * on the focus path.
 */
fun Drawable.toHardwareBitmap(): Bitmap =
    rasterize(intrinsicWidth, intrinsicHeight).copy(Bitmap.Config.HARDWARE, /* isMutable = */ false)

/**
 * Samples this [Drawable]'s dominant color via Android's `Palette` library —
 * `HeroBanner.kt`'s Tier 2 hero fallback (§3.1.2 Template 4) uses this on
 * [TvApp][com.peartv.launcher.domain.model.TvApp.icon] for a flat solid-color
 * backdrop behind the centered icon, computed once at app-list build time
 * (§2.2), same as [toHardwareBitmap].
 *
 * A live blur of the app's banner was tried first for this same backdrop and
 * abandoned (PRODUCT_SPEC.md's Decisions Log — nesting a `GraphicsLayer`
 * recording inside `HeroBanner`'s own existing one silently produced an
 * empty layer on-device). `Palette` needs to read raw pixels, which a
 * `Bitmap.Config.HARDWARE` bitmap can't do — [sampleSize] rasterizes a fresh, small *software*
 * ARGB_8888 bitmap independently via [rasterize] rather than reusing
 * [toHardwareBitmap]'s output; small on purpose, since `Palette`'s own
 * guidance recommends downsizing before sampling for speed, and this is
 * only ever used for a single dominant swatch, not a full palette.
 *
 * Returns `null` if `Palette` couldn't extract a dominant swatch (e.g. a
 * fully transparent or degenerate icon) — callers fall back to a neutral
 * theme color in that case, same as [TvApp.accentColorArgb][com.peartv.launcher.domain.model.TvApp.accentColorArgb]
 * already does when absent.
 */
fun Drawable.dominantColorArgb(sampleSize: Int = 24): Int? =
    Palette.from(rasterize(sampleSize, sampleSize)).generate().dominantSwatch?.rgb
