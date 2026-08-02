package com.peartv.launcher.ui.launcher

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * PRODUCT_SPEC.md §3.1.1 Decisions Log: tiles standardize on 16:9, matching
 * the native 320x180 leanback banner (§2.2) — not the `peartv` web
 * reference's ~1.54:1 ratio. Corner radius / gap proportions carry over.
 *
 * One shared width for BOTH the top shelf and the grid — confirmed against
 * the actual `peartv` reference: `.tile-row-glass` uses the exact same
 * `grid-auto-columns: 200px` as `.tile-remaining`'s grid. The tray is only
 * visually distinct via its glass-panel container (see TopShelfRow), never
 * via a bigger tile — an earlier pass here made top-shelf tiles larger
 * (288x162dp vs 224x126dp), which was wrong.
 *
 * `AppGrid` uses `GridCells.FixedSize(TileWidth)`, not `Fixed(N)`/`Adaptive`
 * (both of which stretch cells to fill available space — confirmed
 * on-device that `Fixed(5)` silently overrode any width a tile requested).
 * `FixedSize` is Compose Foundation's actual equivalent of the reference's
 * `repeat(auto-fill, 200px)`: fixed cell size, computed column count, no
 * stretch — which is also what makes tray/grid tiles guaranteed identical,
 * since both now reference this same literal constant.
 *
 * 115dp (not the original 150dp) so that a full 6-tile top shelf fits
 * without horizontal scroll: 6*115 + 5*TileSpacing + 2*ScreenSafeAreaHorizontal
 * = 926dp, comfortably inside this device's measured ~960dp logical width
 * (confirmed via on-device accessibility-bounds measurement — 1920px
 * bounds ÷ 2.0 density). At 150dp, six tiles needed ~1060dp — wider than the
 * screen, forcing a scroll the tray should never need. This also nudges
 * AppGrid from 5 to 6 columns at this width, still within §3.1's "5–6
 * columns" target.
 */
val TileWidth = 115.dp
const val TileAspectRatio = 16f / 9f
val TileHeight: Dp = TileWidth / TileAspectRatio
val TileCornerRadius = 12.dp
val TileSpacing = 24.dp

/**
 * PRODUCT_SPEC.md §1.4 per-tile focus label — reserved *permanently*
 * (occupied whether or not the tile is currently focused, only the label's
 * own alpha changes) rather than only while visible, per that section's own
 * resolution of its "unresolved detail": reserving the space avoids every
 * neighboring row/tile reflowing on each focus change, which a
 * conditionally-present label would cause.
 */
val FocusLabelSpacing = 4.dp
val FocusLabelHeight = 20.dp

/** Tile + its reserved label space — the actual per-item height grid rows need to account for (§1.4). Tray tiles don't reserve this — see `TopShelfTrayHeight`. */
val TopShelfItemHeight: Dp = TileHeight + FocusLabelSpacing + FocusLabelHeight

/** TV overscan-safe inset for the grid and hero text — reference used 48px on a desktop viewport (§3.1.1). */
val ScreenSafeAreaHorizontal = 48.dp
val ScreenSafeAreaVertical = 27.dp

/**
 * Top-shelf glass tray — PRODUCT_SPEC.md §3.1.1's `.tile-row-glass`
 * reference (`padding: 18px 28px`, `border-radius: 28px`, translated to
 * TV-appropriate dp values, not a literal px copy).
 *
 * Column-aligned with the grid below (tvOS photo reference,
 * `design/IMG_1858.jpeg` vs `IMG_1859.jpeg`): the tray's horizontal content
 * padding is [ScreenSafeAreaHorizontal] — the same inset the grid's
 * `contentPadding` uses — not a separate, narrower `TrayPaddingHorizontal`,
 * so column 0/1/2... line up vertically between the tray and every grid row
 * beneath it. An earlier version of this tray shrank to a "snug," content-
 * sized width when there were only a few pinned apps; that's gone — a
 * user-directed reversal (this file's tray/grid alignment is more important
 * than a tightly-hugging panel).
 *
 * [TrayOuterMargin] is a separate, later refinement (user-directed: "a
 * little bit narrower") — applied as an *outer* margin around the whole
 * panel (shadow/clip/background/border all sit inside it), not the tray's
 * inner content padding above, so the panel itself reads as a floating card
 * with breathing room from the screen edge rather than a full-bleed bar.
 */
val TrayPaddingVertical = 16.dp
val TrayCornerRadius = 24.dp
val TrayOuterMargin = 32.dp

/**
 * Tray/pill panel fill opacity over their blurred backdrop (`BackdropBlur.kt`)
 * — shared by `TopShelfRow` and `StatusBar` so both panels read as one
 * consistent "glass" material rather than two independently-tuned looks.
 * Reopened at user request (Decisions Log, "Dock/pill backdrop blur") after
 * the Decisions Log's own prior "§3.1.1 'liquid glass' tray/pill styling —
 * removed" entry had settled on fully opaque for both.
 *
 * Raised from an initial `0.72f` — user-reported the pill as reading "nearly
 * totally transparent" at that value. A multi-pass blur (stronger softening
 * underneath, so the same alpha would read more frosted) was tried first and
 * reverted — it crashed the renderer outright on-device (Decisions Log:
 * "Multi-pass backdrop blur — reverted, renderer crash"). Raising the panel's
 * own opacity instead is the safe lever: still genuinely translucent (not
 * the fully-opaque `1f` the "liquid glass removed" entry originally settled
 * on), just weighted further toward the tint than the blur underneath it.
 */
const val TranslucentPanelAlpha = 0.9f

/**
 * The tray's full rendered height — tile height + the tray's own vertical
 * padding, deliberately *not* [TopShelfItemHeight] (tray tiles don't carry
 * the per-tile focus label — user-directed: the tray's own "you're focused
 * here" read already comes from the scale/glow motion, a label felt
 * redundant in that context specifically, unlike the grid).
 */
val TopShelfTrayHeight: Dp = TileHeight + TrayPaddingVertical * 2

/**
 * Hero/tray/grid collapsing-header behavior — tvOS photo reference
 * (`design/IMG_1858.jpeg` vs `IMG_1859.jpeg`): focused-in-tray shows a large
 * hero with only a sliver of the grid's first row peeking below the fold;
 * focused-in-grid collapses the hero down to just the tray's own height (the
 * backdrop artwork fades out entirely as it collapses), sitting flush at the
 * very top so every grid row becomes visible. See `LauncherScreen`'s
 * `heroExpansion` animation — these are its two endpoints, not a fixed
 * split.
 *
 * [HeroGridPeekHeight] must clear `AppGrid`'s own top `contentPadding`
 * ([ScreenSafeAreaVertical]) before any tile pixel is visible at all —
 * confirmed on-device: an earlier, smaller value (24dp, less than that
 * padding alone) left zero pixels of row 1 visible, not a sliver. This adds
 * a real ~20dp peek on top of clearing that padding.
 */
val HeroGridPeekHeight = ScreenSafeAreaVertical + 20.dp
const val AppGridWeight = 1f

/**
 * Grid Reordering & Folders §2 — long-press-to-enter-Edit-Mode threshold and
 * the jiggle oscillation's own timing/magnitude, taken directly from the
 * user's functional spec ("Holding down... for 1,000 ms," "±1.2° over a 120
 * ms sine loop").
 */
const val EditModeLongPressMillis = 1000L
const val JiggleRotationDegrees = 1.2f
const val JiggleLoopMillis = 120
const val JiggleNonActiveScale = 0.95f
const val JiggleActiveScale = 1.15f
const val JiggleDimmedAlpha = 0.7f

/** Options popover — dim-only treatment (no jiggle) for whichever tile the popover is currently open on, matching the design reference (`design/editHomeScreen.png`): the target tile darkens; Edit Mode/jiggle hasn't started yet at this point. */
const val OptionsMenuTargetDimAlpha = 0.5f

/**
 * §5 closed folder tile — frosted-glass background (API 30 fallback: flat
 * tint, matching the tray's own SDK-gated blur pattern) plus a mini
 * thumbnail matrix. User-directed against the real tvOS photo reference
 * (`design/IMG_1858.jpeg`): always a fixed [FolderTileGridColumns]×3 grid —
 * apps fill left to right, top to bottom, and a folder with fewer than
 * [FolderTileMaxThumbnails] (9 = 3×3) apps just leaves the remaining grid
 * slots empty rather than the grid shrinking to fit however many there are.
 * Each mini thumbnail keeps [TileAspectRatio] (16:9), the same as every
 * other tile in this app, rather than being cropped to a square.
 */
val FolderTileMatrixSpacing = 4.dp
const val FolderTileMaxThumbnails = 9
const val FolderTileGridColumns = 3
val FolderTitleSpacing = 8.dp

/** §5 open folder — sub-grid modal sizing; same tile metrics as the root grid, just presented inside a centered, dimmed-backdrop panel. */
val FolderModalWidth = 720.dp
val FolderModalPadding = 32.dp

/** Vertical breathing room inside the folder modal's own sub-grid — without it, a focused first-row tile's scale-up (AppTile's default 1.15x) clipped against the grid's own bounds (confirmed on-device). */
val FolderGridTopPadding = 12.dp
