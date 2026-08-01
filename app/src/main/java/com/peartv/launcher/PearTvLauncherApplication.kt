package com.peartv.launcher

import android.app.Application
import android.graphics.Bitmap
import androidx.core.content.ContextCompat
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.memory.MemoryCache
import com.peartv.launcher.data.launcher.AppLauncherImpl
import com.peartv.launcher.data.receiver.PackageChangeReceiver
import com.peartv.launcher.data.repository.AppEnrichmentRepositoryImpl
import com.peartv.launcher.data.repository.ChannelsRepositoryImpl
import com.peartv.launcher.data.repository.LauncherAppRepositoryImpl
import com.peartv.launcher.data.repository.LayoutRepositoryImpl
import com.peartv.launcher.data.repository.SettingsRepositoryImpl
import com.peartv.launcher.data.repository.TmdbRepositoryImpl
import com.peartv.launcher.domain.repository.AppEnrichmentRepository
import com.peartv.launcher.domain.repository.AppLauncher
import com.peartv.launcher.domain.repository.ChannelsRepository
import com.peartv.launcher.domain.repository.LauncherAppRepository
import com.peartv.launcher.domain.repository.LayoutRepository
import com.peartv.launcher.domain.repository.SettingsRepository
import com.peartv.launcher.domain.repository.TmdbRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

/** Debounce window for coalescing a burst of package-change broadcasts (§3.2), e.g. after a reboot. */
private const val PackageChangeDebounceMillis = 300L

/**
 * Manual DI root — the launcher is a single, always-resident process (§2.1),
 * so a lightweight lazily-built object graph here is enough; there's no
 * multi-scope/multi-Activity lifecycle to justify a DI framework yet.
 */
class PearTvLauncherApplication : Application(), ImageLoaderFactory {

    val appEnrichmentRepository: AppEnrichmentRepository by lazy { AppEnrichmentRepositoryImpl(this) }
    val launcherAppRepository: LauncherAppRepository by lazy {
        LauncherAppRepositoryImpl(this, appEnrichmentRepository)
    }
    val appLauncher: AppLauncher by lazy { AppLauncherImpl(this) }
    val settingsRepository: SettingsRepository by lazy { SettingsRepositoryImpl(this) }
    val layoutRepository: LayoutRepository by lazy { LayoutRepositoryImpl(this) }

    // Shared with Coil's own network stack (newImageLoader below) rather than
    // each maintaining a separate connection pool for the same handful of
    // hosts (TMDB's API + image CDN).
    private val sharedHttpClient: OkHttpClient by lazy { OkHttpClient() }
    val tmdbRepository: TmdbRepository by lazy { TmdbRepositoryImpl(sharedHttpClient) }
    val channelsRepository: ChannelsRepository by lazy { ChannelsRepositoryImpl(this) }

    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private var refreshDebounceJob: Job? = null

    // A method reference to scheduleRefresh(debounce: Boolean = true) won't
    // do here — default parameter values don't apply to callable references,
    // so ::scheduleRefresh has type (Boolean) -> Unit, not () -> Unit.
    private val packageChangeReceiver = PackageChangeReceiver(onPackagesChanged = { scheduleRefresh() })

    override fun onCreate() {
        super.onCreate()
        // ContextCompat, not the raw 2-arg registerReceiver: targetSdk 33+
        // requires an explicit RECEIVER_EXPORTED/RECEIVER_NOT_EXPORTED flag
        // on API 33+ devices or registration throws at runtime. PACKAGE_*
        // broadcasts are system-only, so NOT_EXPORTED is correct here — no
        // other app should be able to fake one to us.
        ContextCompat.registerReceiver(
            this,
            packageChangeReceiver,
            PackageChangeReceiver.intentFilter(),
            ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        // Initial query happens off the main thread — PackageManager +
        // banner decode for ~40-150 apps is not free (§0 cold-launch budget).
        scheduleRefresh(debounce = false)
    }

    private fun scheduleRefresh(debounce: Boolean = true) {
        refreshDebounceJob?.cancel()
        refreshDebounceJob = applicationScope.launch {
            if (debounce) delay(PackageChangeDebounceMillis)
            launcherAppRepository.refresh()
        }
    }

    /**
     * PRODUCT_SPEC.md §2.2 — HARDWARE bitmap config + a generous memory
     * cache. Exercised by Tier 1's remote TMDB backdrop art (§3.1.1) — the
     * core PackageManager banner path still decodes directly and never
     * touches Coil, since that art is always local.
     */
    override fun newImageLoader(): ImageLoader =
        ImageLoader.Builder(this)
            .okHttpClient(sharedHttpClient)
            .bitmapConfig(Bitmap.Config.HARDWARE)
            .memoryCache {
                MemoryCache.Builder(this)
                    .maxSizePercent(0.25)
                    .build()
            }
            .build()
}
