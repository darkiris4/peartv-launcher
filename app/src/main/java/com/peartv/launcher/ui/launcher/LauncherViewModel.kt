package com.peartv.launcher.ui.launcher

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.peartv.launcher.domain.model.AppChannel
import com.peartv.launcher.domain.model.ChannelProgram
import com.peartv.launcher.domain.model.GridNode
import com.peartv.launcher.domain.model.TmdbBackdrop
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.model.renumbered
import com.peartv.launcher.domain.model.stableId
import com.peartv.launcher.domain.model.withDock
import com.peartv.launcher.domain.model.withPosition
import com.peartv.launcher.domain.repository.ChannelsRepository
import com.peartv.launcher.domain.repository.LayoutRepository
import com.peartv.launcher.domain.repository.SettingsRepository
import com.peartv.launcher.domain.repository.TmdbRepository
import com.peartv.launcher.domain.usecase.GetInstalledAppsUseCase
import com.peartv.launcher.domain.usecase.LaunchAppUseCase
import com.peartv.launcher.domain.usecase.LaunchContentUseCase
import com.peartv.launcher.domain.usecase.RequestUninstallUseCase
import com.peartv.launcher.ui.focus.DpadDirection
import java.util.UUID
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** Grid Reordering & Folders — the dock+grid rearrange state machine. `isActive`/`activeId` describe the one tile currently "picked up"; since D-pad direction presses are entirely consumed to move it (`LauncherScreen`'s root key handling), the active tile and Compose's own focused tile are the same thing for the whole edit session — there's no way to focus-navigate to a different tile without exiting first. Only ever entered via [LauncherViewModel.startEditHomeScreen] (the Options popover's "Edit Home Screen" row) — a long-press alone no longer enters it directly. */
data class EditModeState(
    val isActive: Boolean = false,
    val activeId: String? = null,
)

/** The small anchored "liquid glass" popover a long-press opens on whatever tile currently has focus — Edit Home Screen / Move to… / Delete App. */
data class OptionsMenuState(val targetId: String)

/**
 * Owns the launcher screen's UI state — the installed-app list (via
 * [GetInstalledAppsUseCase]) and, per this task's requirement, which app is
 * currently focused, so [HeroBanner] can react to it independently of
 * whichever row (top shelf or grid) actually holds focus right now.
 *
 * [focusedApp] is driven entirely by [onAppFocused], which every [AppTile]
 * calls the instant it gains focus (see `Modifier.tvOSFocusable`'s
 * `onFocusChange`) — there's deliberately no "nothing focused" state to
 * handle, since D-pad navigation always keeps exactly one item focused once
 * the initial cold-launch focus request (§1.3) lands.
 *
 * [heroBackdrop] is Tier 1 (§3.1.1/§2.4's three-tier model): non-null only
 * when the focused app is curated ([TvApp.tmdbProviderId] set, §3.2.1) *and*
 * a TMDB API key is configured (§4's settings screen); `null` otherwise,
 * which [HeroBanner] reads as "fall back to Tier 2" (its own local banner
 * art). Carries the trending title alongside the backdrop URL (§3.1.2's
 * restored hero title — the *content's* title, not the app's name; that
 * stays §1.4's per-tile label). `flatMapLatest` cancels any in-flight TMDB
 * fetch the instant focus moves again — necessary since rapid D-pad
 * traversal changes [focusedApp] far faster than a network round-trip
 * resolves, and a stale response landing after focus has already moved on
 * would otherwise flash the wrong app's backdrop.
 *
 * Grid Reordering & Folders — [layoutRepository] is reconciled against every
 * fresh [apps] emission (install/uninstall, and the very first cold launch)
 * so [dockItems]/[gridItems] are always resolved from the persisted,
 * user-editable [GridNode] layout rather than the old fixed alphabetical
 * sort. A long-press always opens the Options popover ([optionsMenu]) first;
 * Edit Mode ([editMode]) and the open folder modal ([openFolder]) are both
 * reached *from* that popover, not directly from the long-press itself
 * (user-directed revision — the original double-long-press design felt
 * clunky in practice). All three are owned here rather than as local
 * Composable state, since every action any of them expose
 * (move/rename/eject/delete) needs to read-modify-write the same persisted
 * layout.
 */
class LauncherViewModel(
    getInstalledApps: GetInstalledAppsUseCase,
    private val launchApp: LaunchAppUseCase,
    private val launchContent: LaunchContentUseCase,
    private val requestUninstall: RequestUninstallUseCase,
    private val settingsRepository: SettingsRepository,
    private val tmdbRepository: TmdbRepository,
    private val channelsRepository: ChannelsRepository,
    private val layoutRepository: LayoutRepository,
) : ViewModel() {

    val apps: StateFlow<List<TvApp>> = getInstalledApps()

    private val _focusedApp = MutableStateFlow<TvApp?>(null)
    val focusedApp: StateFlow<TvApp?> = _focusedApp.asStateFlow()

    /** Grid Reordering & Folders §2 — whatever tile currently has real D-pad focus, App or Folder alike (only [focusedApp] above needs the resolved [TvApp] specifically, for the hero). Long-press-to-enter-Edit-Mode (`LauncherScreen`'s root key handling) needs this regardless of which kind of tile it is. */
    private val _focusedItemId = MutableStateFlow<String?>(null)
    val focusedItemId: StateFlow<String?> = _focusedItemId.asStateFlow()

    val heroBackdrop: StateFlow<TmdbBackdrop?> =
        combine(focusedApp, settingsRepository.tmdbApiKey) { app, apiKey -> app to apiKey }
            .flatMapLatest { (app, apiKey) ->
                val providerId = app?.tmdbProviderId
                if (providerId == null || apiKey.isNullOrBlank()) {
                    flowOf(null)
                } else {
                    flowOf(tmdbRepository.fetchTrendingBackdrop(providerId, apiKey))
                }
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    /**
     * Tier 3 (§2.4/§3.1.1) — real Home Screen Channels data for the focused
     * app, independent of [heroBackdropUrl]'s Tier 1/2 logic. Highest display
     * priority whenever non-empty: [LauncherScreen] shows Content Rows instead
     * of [HeroBanner] for as long as this stays non-empty, per spec. Kept as
     * its own flow rather than folded into [heroBackdropUrl] so Tier 1's
     * already-verified logic stays untouched — the UI layer alone decides
     * which one wins.
     *
     * A `List`, not a single nullable [AppChannel]: a package can publish
     * more than one distinct channel (e.g. Plex's own "Continue Watching"
     * alongside a separate "Recently Released" channel —
     * `design/IMG_1859.jpeg`'s real tvOS reference shows exactly this side
     * by side), and [ChannelsRepository.fetchChannels] now surfaces all of
     * them rather than silently keeping only whichever one it found first.
     */
    val tier3Channels: StateFlow<List<AppChannel>> = focusedApp
        .flatMapLatest { app ->
            val packageName = app?.packageName
            if (packageName == null) {
                flowOf(emptyList())
            } else {
                flowOf(channelsRepository.fetchChannels(packageName))
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private val appsByPackage: StateFlow<Map<String, TvApp>> = apps
        .map { list -> list.associateBy { it.packageName } }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyMap())

    /**
     * First-launch Channels permission prompt (Decisions Log) — seeded
     * `true` (hidden), not `false`, so a *returning* user who already
     * dismissed it never sees a one-frame flash of the prompt while
     * DataStore's real (async) value is still loading; a genuinely
     * first-time user just sees it appear slightly after the rest of the
     * UI rather than flicker. `ContentCarousel`/`ChannelsRepository` degrade
     * silently without `READ_TV_LISTINGS` (§2.4) — nothing else in the app
     * would otherwise tell a user this capability exists at all.
     */
    val hasDismissedChannelsPrompt: StateFlow<Boolean> = settingsRepository.hasDismissedChannelsPrompt
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), true)

    fun dismissChannelsPrompt() {
        viewModelScope.launch { settingsRepository.setChannelsPromptDismissed() }
    }

    /** Grid Reordering & Folders Decisions Log "Seed precedence"/"New app placement" — reconciled every time the installed-app set changes, not just once at cold launch. */
    init {
        apps.onEach { list -> if (list.isNotEmpty()) layoutRepository.reconcile(list) }
            .launchIn(viewModelScope)
    }

    val layout: StateFlow<List<GridNode>> = layoutRepository.layout
        .map { it.orEmpty() }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private data class ResolvedLayout(val dock: List<LauncherGridItem>, val grid: List<LauncherGridItem>)

    private val resolvedLayout: StateFlow<ResolvedLayout> =
        combine(layout, appsByPackage) { nodes, byPackage ->
            val dock = nodes.filter { it.isDock }.sortedBy { it.position }.mapNotNull { it.toGridItem(byPackage) }
            val grid = nodes.filterNot { it.isDock }.sortedBy { it.position }.mapNotNull { it.toGridItem(byPackage) }
            ResolvedLayout(dock, grid)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), ResolvedLayout(emptyList(), emptyList()))

    val dockItems: StateFlow<List<LauncherGridItem>> = resolvedLayout
        .map { it.dock }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val gridItems: StateFlow<List<LauncherGridItem>> = resolvedLayout
        .map { it.grid }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    private fun GridNode.toGridItem(byPackage: Map<String, TvApp>): LauncherGridItem? = when (this) {
        is GridNode.App -> byPackage[packageName]?.let { LauncherGridItem.AppItem(it) }
        is GridNode.Folder -> {
            val folderApps = appPackages.mapNotNull { byPackage[it] }
            if (folderApps.isEmpty()) null else LauncherGridItem.FolderItem(id, name, folderApps)
        }
    }

    private val _editMode = MutableStateFlow(EditModeState())
    val editMode: StateFlow<EditModeState> = _editMode.asStateFlow()

    private val _openFolderId = MutableStateFlow<String?>(null)
    val openFolder: StateFlow<LauncherGridItem.FolderItem?> =
        combine(_openFolderId, gridItems) { id, items ->
            if (id == null) null else items.filterIsInstance<LauncherGridItem.FolderItem>().find { it.id == id }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), null)

    private val _optionsMenu = MutableStateFlow<OptionsMenuState?>(null)
    val optionsMenu: StateFlow<OptionsMenuState?> = _optionsMenu.asStateFlow()

    fun onAppFocused(app: TvApp) {
        _focusedApp.value = app
        _focusedItemId.value = app.packageName
    }

    /** Folder tiles have no [TvApp] to give [onAppFocused] — Edit Mode's long-press entry point still needs to know a folder tile is what's focused. */
    fun onFolderFocused(folderId: String) {
        _focusedItemId.value = folderId
    }

    fun onAppClick(app: TvApp) {
        launchApp(app)
    }

    /** Tier 3 (§3.1.2) — launches a Content Rows program's own deep link, falling back to the app itself if it has none. */
    fun onProgramClick(program: ChannelProgram) {
        val app = _focusedApp.value ?: return
        launchContent(program.intentUri, app)
    }

    // --- Options popover: opened by a single long-press on whatever's focused ---

    fun openOptionsMenu() {
        val id = _focusedItemId.value ?: return
        _optionsMenu.value = OptionsMenuState(id)
    }

    fun closeOptionsMenu() {
        _optionsMenu.value = null
    }

    /** "Edit Home Screen" — the popover's own action that starts Edit Mode; a long-press alone no longer does this directly (user-directed revision). Only the tile the popover was opened on becomes active/draggable — not a whole-grid free-navigate jiggle (a scope call, not a technical limitation: real tvOS lets you roam between jiggling tiles before grabbing one, which needs its own focus-navigation design this pass doesn't build). */
    fun startEditHomeScreen() {
        val id = _optionsMenu.value?.targetId ?: return
        closeOptionsMenu()
        _editMode.value = EditModeState(isActive = true, activeId = id)
    }

    fun exitEditMode() {
        _editMode.value = EditModeState()
    }

    fun moveActive(direction: DpadDirection, columnCount: Int) {
        val activeId = _editMode.value.activeId ?: return
        viewModelScope.launch {
            val before = layout.value
            val after = GridLayoutEngine.move(before, activeId, direction, columnCount)
            if (after == before) return@launch
            layoutRepository.setLayout(after)
        }
    }

    // --- "Move to…" (the popover's own submenu) + "Delete App" ---

    /** "+ New Folder" inside "Move to…" — always a single-app folder at creation (the target itself); a second app only ever joins later via another "Move to \"<folder>\"" selection, not a drag/hover gesture (dropped in favor of this explicit path — user-directed). */
    fun optionsNewFolder() {
        val targetId = _optionsMenu.value?.targetId ?: return
        viewModelScope.launch {
            val current = layout.value
            val node = current.find { it.stableId() == targetId } as? GridNode.App ?: return@launch
            val name = appsByPackage.value[node.packageName]?.category ?: DefaultFolderName
            val folderId = UUID.randomUUID().toString()
            val folder = GridNode.Folder(folderId, name, node.position, isDock = false, appPackages = listOf(node.packageName))
            val updated = (current.filterNot { it.stableId() == targetId } + folder).renumbered()
            layoutRepository.setLayout(updated)
            closeOptionsMenu()
            openFolder(folderId)
        }
    }

    fun optionsMoveToFolder(folderId: String) {
        val targetId = _optionsMenu.value?.targetId ?: return
        viewModelScope.launch {
            val current = layout.value
            val node = current.find { it.stableId() == targetId } as? GridNode.App ?: return@launch
            val updated = current.mapNotNull { n ->
                when {
                    n.stableId() == targetId -> null
                    n is GridNode.Folder && n.id == folderId -> n.copy(appPackages = n.appPackages + node.packageName)
                    else -> n
                }
            }.renumbered()
            layoutRepository.setLayout(updated)
            closeOptionsMenu()
        }
    }

    /** "Home Screen" inside "Move to…" — ejects a folder member back to the root grid (§6). Only ever shown/called when the target is actually inside a folder (`OptionsMenu`'s own `isInsideFolder` gate). "Move to Top Row" was dropped entirely (user-directed: dock reordering already works by dragging in Edit Mode; the menu entry added a second, redundant path). */
    fun optionsEjectFromFolder() {
        val targetId = _optionsMenu.value?.targetId ?: return
        viewModelScope.launch {
            val current = layout.value
            val parentFolder = current.filterIsInstance<GridNode.Folder>().find { targetId in it.appPackages } ?: return@launch
            layoutRepository.setLayout(ejectFromFolderInternal(current, parentFolder, targetId))
            closeOptionsMenu()
        }
    }

    fun optionsDeleteApp() {
        val targetId = _optionsMenu.value?.targetId ?: return
        closeOptionsMenu()
        requestUninstall(targetId)
    }

    // --- §5/§6: folder modal, renaming, ejection ---

    fun openFolder(folderId: String) {
        _openFolderId.value = folderId
    }

    fun closeFolder() {
        _openFolderId.value = null
    }

    fun renameFolder(folderId: String, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = layout.value.map { if (it is GridNode.Folder && it.id == folderId) it.copy(name = newName) else it }
            layoutRepository.setLayout(updated)
        }
    }

    /** §6 — ejects one app back to the root grid; auto-dissolves the folder (per §6) if that leaves it with exactly one app. */
    fun ejectFromFolder(folderId: String, packageName: String) {
        viewModelScope.launch {
            val current = layout.value
            val folder = current.filterIsInstance<GridNode.Folder>().find { it.id == folderId } ?: return@launch
            layoutRepository.setLayout(ejectFromFolderInternal(current, folder, packageName))
            if (folder.appPackages.size <= 2) closeFolder()
        }
    }

    private fun ejectFromFolderInternal(nodes: List<GridNode>, folder: GridNode.Folder, packageName: String): List<GridNode> {
        val remaining = folder.appPackages.filterNot { it == packageName }
        val gridCount = nodes.count { !it.isDock }
        val ejected = GridNode.App(packageName, position = gridCount, isDock = false)
        val withoutFolder = nodes.filterNot { it.stableId() == folder.id }
        val replacement: GridNode? = when {
            remaining.isEmpty() -> null
            remaining.size == 1 -> GridNode.App(remaining[0], folder.position, folder.isDock)
            else -> folder.copy(appPackages = remaining)
        }
        return (withoutFolder + listOfNotNull(replacement) + ejected).renumbered()
    }

    private companion object {
        const val DefaultFolderName = "Folder"
    }
}

/** Manual factory — no DI framework in this scaffold (§2.1/PearTvLauncherApplication is the object graph root). */
class LauncherViewModelFactory(
    private val getInstalledApps: GetInstalledAppsUseCase,
    private val launchApp: LaunchAppUseCase,
    private val launchContent: LaunchContentUseCase,
    private val requestUninstall: RequestUninstallUseCase,
    private val settingsRepository: SettingsRepository,
    private val tmdbRepository: TmdbRepository,
    private val channelsRepository: ChannelsRepository,
    private val layoutRepository: LayoutRepository,
) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        @Suppress("UNCHECKED_CAST")
        return LauncherViewModel(
            getInstalledApps,
            launchApp,
            launchContent,
            requestUninstall,
            settingsRepository,
            tmdbRepository,
            channelsRepository,
            layoutRepository,
        ) as T
    }
}
