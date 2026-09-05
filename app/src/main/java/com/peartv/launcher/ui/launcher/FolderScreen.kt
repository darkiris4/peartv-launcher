package com.peartv.launcher.ui.launcher

import androidx.activity.compose.PredictiveBackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
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
import androidx.compose.ui.focus.FocusDirection
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.positionInWindow
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.util.lerp
import androidx.tv.material3.MaterialTheme
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.LaunchOrigin
import com.peartv.launcher.ui.motion.TvSprings
import com.peartv.launcher.ui.theme.ambientBackground
import kotlin.coroutines.cancellation.CancellationException

/**
 * Grid Reordering & Folders §5 "Open State" — a centered modal that springs
 * open *out of* the folder tile it was launched from ([originBounds], its
 * on-screen window rect) and back into it on close, matching the app-launch
 * icon-zoom motion (`AppLauncherImpl`'s `makeScaleUpAnimation`). Driven by a
 * single [reveal] progress: `1` = fully open and centered, `0` = collapsed
 * onto the tile.
 *
 * [expanded] flips false the moment Back is pressed; the modal stays
 * composed (via `LauncherScreen`'s own `folderRender`) through the collapse
 * animation and reports completion via [onClosed]. [PredictiveBackHandler]
 * ([backEnabled]) previews the collapse as the Back gesture is dragged
 * (button-only remotes get the committed animation), committing via [onBack]
 * or springing back on cancel.
 *
 * [enterRenameMode] decides the initial focus target — the inline title
 * field for a freshly created/merged folder, the first app tile for an
 * ordinary browse open.
 */
@Composable
fun FolderScreen(
    folder: LauncherGridItem.FolderItem,
    originBounds: Rect?,
    expanded: Boolean,
    backEnabled: Boolean,
    onBack: () -> Unit,
    onClosed: () -> Unit,
    enterRenameMode: Boolean,
    onRename: (String) -> Unit,
    onAppClick: (TvApp, LaunchOrigin?) -> Unit,
    onAppFocused: (TvApp) -> Unit,
    modifier: Modifier = Modifier,
    optionsMenuTargetId: String? = null,
    onOpenOptionsMenu: () -> Unit = {},
    onTilePositioned: (LayoutCoordinates) -> Unit = {},
) {
    val focusManager = LocalFocusManager.current
    val titleFocusRequester = remember { FocusRequester() }
    val firstTileFocusRequester = remember { FocusRequester() }
    var titleInput by remember(folder.id) { mutableStateOf(folder.name) }

    val reveal = remember { Animatable(if (expanded) 0f else 1f) }
    LaunchedEffect(expanded) {
        if (expanded) {
            reveal.animateTo(1f, TvSprings.RevealOpen)
        } else {
            reveal.animateTo(0f, TvSprings.RevealClose)
            onClosed()
        }
    }

    PredictiveBackHandler(enabled = backEnabled) { progress ->
        try {
            progress.collect { event ->
                reveal.snapTo((1f - event.progress).coerceIn(0f, 1f))
            }
            onBack()
        } catch (_: CancellationException) {
            reveal.animateTo(1f, TvSprings.RevealOpen)
        }
    }

    // Resting (pre-transform) window rect of the modal, captured on a
    // wrapper that carries no `graphicsLayer` of its own so the spring math
    // below isn't circular.
    var modalRect by remember { mutableStateOf(Rect.Zero) }

    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center,
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .alpha((reveal.value * 2.2f).coerceAtMost(1f))
                .ambientBackground(),
        )

        Box(
            modifier = Modifier.onGloballyPositioned {
                modalRect = Rect(it.positionInWindow(), it.size.toSize())
            },
        ) {
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .width(FolderModalWidth)
                    .graphicsLayer {
                        val p = reveal.value
                        alpha = (p * 2.2f).coerceAtMost(1f)
                        val ob = originBounds
                        if (ob != null && modalRect.width > 0f) {
                            val s = lerp((ob.width / modalRect.width).coerceIn(0.05f, 0.6f), 1f, p)
                            scaleX = s
                            scaleY = s
                            translationX = lerp(ob.center.x - modalRect.center.x, 0f, p)
                            translationY = lerp(ob.center.y - modalRect.center.y, 0f, p)
                        } else {
                            val s = lerp(0.85f, 1f, p)
                            scaleX = s
                            scaleY = s
                        }
                    }
                    .clip(RoundedCornerShape(24.dp))
                    .background(MaterialTheme.colorScheme.surface)
                    .padding(FolderModalPadding),
            ) {
                BasicTextField(
                    value = titleInput,
                    onValueChange = {
                        titleInput = it
                        onRename(it)
                    },
                    singleLine = true,
                    textStyle = TextStyle(
                        color = MaterialTheme.colorScheme.onSurface,
                        fontSize = MaterialTheme.typography.headlineSmall.fontSize,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = Modifier
                        .focusRequester(titleFocusRequester)
                        .onPreviewKeyEvent { event ->
                            if (event.type != KeyEventType.KeyDown || event.key != Key.DirectionDown) return@onPreviewKeyEvent false
                            focusManager.moveFocus(FocusDirection.Down)
                            true
                        },
                )

                Spacer(modifier = Modifier.height(FolderTitleSpacing))

                LazyVerticalGrid(
                    columns = GridCells.Fixed(3),
                    horizontalArrangement = Arrangement.spacedBy(TileSpacing),
                    verticalArrangement = Arrangement.spacedBy(TileSpacing),
                    contentPadding = PaddingValues(top = FolderGridTopPadding, bottom = FolderGridTopPadding),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(folder.apps, key = { _, app -> app.packageName }) { index, app ->
                        val isOptionsMenuTarget = optionsMenuTargetId == app.packageName
                        AppTile(
                            app = app,
                            onClick = { origin -> onAppClick(app, origin) },
                            onFocus = { onAppFocused(app) },
                            onLongPress = onOpenOptionsMenu,
                            isOptionsMenuTarget = isOptionsMenuTarget,
                            onPositioned = if (isOptionsMenuTarget) onTilePositioned else ({}),
                            modifier = Modifier
                                .width(TileWidth)
                                .aspectRatio(TileAspectRatio)
                                .then(if (index == 0) Modifier.focusRequester(firstTileFocusRequester) else Modifier),
                        )
                    }
                }
            }
        }
    }

    LaunchedEffect(folder.id, enterRenameMode) {
        if (enterRenameMode) {
            titleFocusRequester.requestFocus()
        } else {
            firstTileFocusRequester.requestFocus()
        }
    }
}
