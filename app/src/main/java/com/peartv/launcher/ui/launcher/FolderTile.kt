package com.peartv.launcher.ui.launcher

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.tv.material3.MaterialTheme
import androidx.tv.material3.Text
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.ui.focus.FocusGainMillis
import com.peartv.launcher.ui.focus.FocusLossMillis
import com.peartv.launcher.ui.focus.tvOSFocusable

/**
 * Grid Reordering & Folders §5 "Closed State (Grid View Tile)" — a frosted-
 * glass tile with a mini thumbnail matrix of its member apps' banners.
 *
 * Decision #4's API-30 fallback (flat translucent tint, no real
 * `RenderEffect` blur) is used unconditionally here, not SDK-gated like the
 * top-shelf tray: a folder tile is one of potentially many scrolling grid
 * items, not the tray's single always-on-screen panel, so real per-tile
 * backdrop capture would be considerably more machinery for a small, mostly
 * static element — and it would be invisible on this project's actual API 30
 * reference hardware regardless. A documented scope simplification, not an
 * oversight.
 */
@Composable
fun FolderTile(
    folder: LauncherGridItem.FolderItem,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    onFocus: () -> Unit = {},
    onLongPress: (() -> Unit)? = null,
    isEditMode: Boolean = false,
    isActiveDrag: Boolean = false,
    isDimmed: Boolean = false,
    isOptionsMenuTarget: Boolean = false,
    jigglePhaseSeed: Int = 0,
    onPositioned: (LayoutCoordinates) -> Unit = {},
) {
    var isFocused by remember { mutableStateOf(false) }
    val labelAlpha by animateFloatAsState(
        targetValue = if (isFocused) 1f else 0f,
        animationSpec = tween(if (isFocused) FocusGainMillis else FocusLossMillis),
        label = "folderTileFocusLabelAlpha",
    )

    val jiggleTransition = rememberInfiniteTransition(label = "folderJiggle")
    val jigglePhase by jiggleTransition.animateFloat(
        initialValue = -1f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(JiggleLoopMillis, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse,
            initialStartOffset = StartOffset(jigglePhaseSeed % JiggleLoopMillis),
        ),
        label = "folderJigglePhase",
    )
    val jiggleScale = when {
        isActiveDrag -> JiggleActiveScale
        isDimmed -> JiggleNonActiveScale
        else -> 1f
    }
    val jiggleAlpha = when {
        isActiveDrag -> 1f
        isDimmed -> JiggleDimmedAlpha
        isOptionsMenuTarget -> OptionsMenuTargetDimAlpha
        else -> 1f
    }

    // Grid Reordering & Folders — see `AppTile`'s identical comment: real
    // Android focus needs to be explicitly reclaimed here the instant this
    // folder tile becomes Edit Mode's active tile, since Compose's own
    // default fallback (once the Options popover's own row stops holding
    // focus) doesn't reliably land back on it.
    val tileFocusRequester = remember { FocusRequester() }
    LaunchedEffect(isActiveDrag) {
        if (isActiveDrag) tileFocusRequester.requestFocus()
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier.graphicsLayer {
            rotationZ = if (isEditMode) jigglePhase * JiggleRotationDegrees else 0f
            scaleX = jiggleScale
            scaleY = jiggleScale
            alpha = jiggleAlpha
        },
    ) {
        Box(
            modifier = modifier
                .onGloballyPositioned(onPositioned)
                .focusRequester(tileFocusRequester)
                .tvOSFocusable(
                    focusedScale = if (isActiveDrag) 1f else 1.15f,
                    cornerRadius = TileCornerRadius,
                    glowColor = MaterialTheme.colorScheme.onBackground,
                    onFocusChange = { focused ->
                        isFocused = focused
                        if (focused) onFocus()
                    },
                    onLongPress = onLongPress,
                    onClick = onClick,
                )
                .clip(RoundedCornerShape(TileCornerRadius))
                .background(MaterialTheme.colorScheme.surfaceVariant),
            contentAlignment = Alignment.Center,
        ) {
            val thumbnails = folder.apps.take(FolderTileMaxThumbnails)
            LazyVerticalGrid(
                columns = GridCells.Fixed(2),
                horizontalArrangement = Arrangement.spacedBy(FolderTileMatrixSpacing),
                verticalArrangement = Arrangement.spacedBy(FolderTileMatrixSpacing),
                userScrollEnabled = false,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(FolderTileMatrixSpacing * 2),
            ) {
                items(thumbnails, key = { it.packageName }) { app ->
                    FolderThumbnail(app, Modifier.aspectRatio(1f))
                }
            }
        }

        Box(
            modifier = Modifier
                .padding(top = FocusLabelSpacing)
                .height(FocusLabelHeight),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = folder.name,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onBackground,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.alpha(labelAlpha),
            )
        }
    }
}

@Composable
private fun FolderThumbnail(app: TvApp, modifier: Modifier = Modifier) {
    val banner = app.banner
    Box(modifier = modifier.fillMaxWidth().clip(RoundedCornerShape(4.dp)).background(MaterialTheme.colorScheme.surface)) {
        if (banner != null) {
            Image(
                painter = BitmapPainter(banner.asImageBitmap()),
                contentDescription = app.label,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}
