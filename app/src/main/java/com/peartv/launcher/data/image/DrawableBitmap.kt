package com.peartv.launcher.data.image

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.Drawable

/**
 * Rasterizes a [Drawable] into an immutable, GPU-resident [Bitmap.Config.HARDWARE]
 * bitmap (PRODUCT_SPEC.md §2.2).
 *
 * Hardware bitmaps can't be drawn into directly — a [Canvas] needs a mutable
 * software surface — so this draws into a throwaway ARGB_8888 bitmap first,
 * then copies that into an immutable HARDWARE bitmap. The intermediate copy
 * only happens once, at app-list build time (§3.2), never on the focus path.
 */
fun Drawable.toHardwareBitmap(): Bitmap {
    val width = intrinsicWidth.coerceAtLeast(1)
    val height = intrinsicHeight.coerceAtLeast(1)
    val software = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
    val canvas = Canvas(software)
    val originalBounds = bounds
    setBounds(0, 0, canvas.width, canvas.height)
    draw(canvas)
    bounds = originalBounds
    return software.copy(Bitmap.Config.HARDWARE, /* isMutable = */ false)
}
