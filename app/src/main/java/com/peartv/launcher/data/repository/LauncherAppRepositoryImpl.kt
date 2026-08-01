package com.peartv.launcher.data.repository

import android.content.Context
import android.content.Intent
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.content.pm.ResolveInfo
import android.util.Log
import com.peartv.launcher.data.image.toHardwareBitmap
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppEnrichmentRepository
import com.peartv.launcher.domain.repository.LauncherAppRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

private const val TAG = "LauncherAppRepository"

/**
 * PRODUCT_SPEC.md §3.2 — queries CATEGORY_LEANBACK_LAUNCHER (not the phone
 * CATEGORY_LAUNCHER) via PackageManager, and pre-decodes each app's banner
 * to a HARDWARE bitmap immediately so the grid never decodes on the focus
 * path (§2.2).
 *
 * [refresh] is synchronous and does file/binder I/O (PackageManager +
 * drawable decode) — callers on the main thread (PearTvLauncherApplication's
 * boot call, the debounced broadcast-receiver callback) are expected to
 * dispatch it off the main thread themselves.
 */
class LauncherAppRepositoryImpl(
    private val context: Context,
    private val appEnrichmentRepository: AppEnrichmentRepository,
) : LauncherAppRepository {

    private val _apps = MutableStateFlow<List<TvApp>>(emptyList())
    override val apps: StateFlow<List<TvApp>> = _apps.asStateFlow()

    override fun refresh() {
        _apps.value = queryLeanbackApps()
    }

    private fun queryLeanbackApps(): List<TvApp> {
        val packageManager = context.packageManager
        val leanbackIntent = Intent(Intent.ACTION_MAIN)
            .addCategory(Intent.CATEGORY_LEANBACK_LAUNCHER)

        val resolveInfos: List<ResolveInfo> = packageManager
            .queryIntentActivities(leanbackIntent, PackageManager.MATCH_ALL)

        return resolveInfos
            .asSequence()
            .filter { it.activityInfo.packageName != context.packageName }
            .distinctBy { it.activityInfo.packageName }
            .mapNotNull { it.toTvAppOrNull(packageManager) }
            .sortedBy { it.label.lowercase() }
            .toList()
    }

    private fun ResolveInfo.toTvAppOrNull(packageManager: PackageManager): TvApp? {
        val activityInfo = activityInfo ?: return null
        return runCatching {
            val label = loadLabel(packageManager).toString()
            // TV banner preferred (§2.2); fall back to the activity/app icon
            // for apps that never declared one.
            val bannerDrawable = activityInfo.loadBanner(packageManager)
                ?: activityInfo.applicationInfo.loadBanner(packageManager)
                ?: loadIcon(packageManager)
            val enrichment = appEnrichmentRepository.forPackage(activityInfo.packageName)

            TvApp(
                packageName = activityInfo.packageName,
                activityName = activityInfo.name,
                label = enrichment?.displayNameOverride ?: label,
                banner = runCatching { bannerDrawable?.toHardwareBitmap() }
                    .getOrElse {
                        Log.w(TAG, "Failed to decode banner for ${activityInfo.packageName}", it)
                        null
                    },
                accentColorArgb = enrichment?.accentColorArgb,
                pinnedToTopShelf = enrichment?.pinnedToTopShelf ?: false,
                tmdbProviderId = enrichment?.tmdbProviderId,
                category = categoryLabel(activityInfo.applicationInfo.category),
            )
        }.getOrElse {
            Log.w(TAG, "Skipping unresolvable leanback activity: ${activityInfo.packageName}", it)
            null
        }
    }

    /** Grid Reordering & Folders Decision #5 Tier 2 — most real installed apps declare nothing here (confirmed: `CATEGORY_UNDEFINED`), which is exactly why folder auto-naming always falls through to Tier 3. */
    private fun categoryLabel(category: Int): String? = when (category) {
        ApplicationInfo.CATEGORY_GAME -> "Games"
        ApplicationInfo.CATEGORY_AUDIO -> "Music"
        ApplicationInfo.CATEGORY_VIDEO -> "Video"
        ApplicationInfo.CATEGORY_IMAGE -> "Photos"
        ApplicationInfo.CATEGORY_SOCIAL -> "Social"
        ApplicationInfo.CATEGORY_NEWS -> "News"
        ApplicationInfo.CATEGORY_MAPS -> "Maps"
        ApplicationInfo.CATEGORY_PRODUCTIVITY -> "Productivity"
        ApplicationInfo.CATEGORY_ACCESSIBILITY -> "Accessibility"
        else -> null
    }
}
