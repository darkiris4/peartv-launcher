package com.peartv.launcher.data.launcher

import android.app.Activity
import android.app.ActivityOptions
import android.app.Application
import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.View
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppLauncher
import com.peartv.launcher.domain.repository.LaunchOrigin

private const val TAG = "AppLauncher"

/**
 * PRODUCT_SPEC.md §3.3 — launches via the TV-specific leanback intent, not
 * the phone/tablet launch intent.
 *
 * No manual task-stack management here: [android.app.ActivityManager]'s
 * `getAppTasks()`/`AppTask.moveToFront()` only ever returns tasks owned by
 * the *calling* package, so a launcher can't use it to bring an arbitrary
 * third-party app's task forward — that would be a plausible-looking but
 * broken API call. Instead this relies on standard Android task-affinity
 * behavior: starting an activity that's already the root of an existing
 * task resumes that task rather than creating a duplicate, which is exactly
 * what avoids the "task stack pollution" §3.3 calls out, with no extra code
 * required.
 */
class AppLauncherImpl(
    private val context: Application,
) : AppLauncher {

    // Tracks whichever Activity is currently resumed, purely so
    // [buildLaunchOptions] has a live, attached View to build
    // [ActivityOptions] from — this app only ever has one Activity
    // (MainActivity, singleTask), so this never needs to disambiguate
    // between several. Cleared on pause (not just overwritten on the next
    // resume) so a launch racing a teardown falls back to a plain,
    // un-animated startActivity instead of handing ActivityOptions a
    // detached View.
    private var foregroundActivity: Activity? = null

    init {
        context.registerActivityLifecycleCallbacks(object : Application.ActivityLifecycleCallbacks {
            override fun onActivityResumed(activity: Activity) {
                foregroundActivity = activity
            }

            override fun onActivityPaused(activity: Activity) {
                if (foregroundActivity === activity) foregroundActivity = null
            }

            override fun onActivityCreated(activity: Activity, savedInstanceState: Bundle?) {}
            override fun onActivityStarted(activity: Activity) {}
            override fun onActivityStopped(activity: Activity) {}
            override fun onActivitySaveInstanceState(activity: Activity, outState: Bundle) {}
            override fun onActivityDestroyed(activity: Activity) {}
        })
    }

    override fun launch(app: TvApp, origin: LaunchOrigin?) {
        val packageManager = context.packageManager
        val intent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)

        if (intent == null) {
            Log.w(TAG, "No launch intent available for ${app.packageName}")
            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        val options = buildLaunchOptions(origin)
        runCatching { context.startActivity(intent, options?.toBundle()) }
            .onFailure { Log.w(TAG, "Failed to launch ${app.packageName}", it) }
    }

    override fun launchContent(intentUri: String?, fallbackApp: TvApp, origin: LaunchOrigin?) {
        val options = buildLaunchOptions(origin)
        val launchedContent = intentUri != null && runCatching {
            val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent, options?.toBundle())
        }.onFailure {
            Log.w(TAG, "Failed to launch content intent URI '$intentUri', falling back to app launch", it)
        }.isSuccess

        if (!launchedContent) launch(fallbackApp, origin)
    }

    override fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to start uninstall flow for $packageName", it) }
    }

    /**
     * `null` whenever there's nothing useful to animate from — no captured
     * tile bounds ([origin]), or no resumed Activity to source a window
     * token/View from (e.g. a launch racing process teardown) — callers
     * pass that straight through to `startActivity`, which treats a `null`
     * options bundle exactly like today's plain, un-animated call.
     *
     * `makeScaleUpAnimation` is the default; swap to `makeClipRevealAnimation`
     * below (same arguments) to A/B compare — user-directed: try both
     * against a couple of real installed apps and pick whichever reads
     * better, since behavior can vary with the target app's own theme/window
     * flags. Not wired as a runtime toggle since this is a one-time visual
     * call to make once, not an ongoing setting.
     */
    private fun buildLaunchOptions(origin: LaunchOrigin?): ActivityOptions? {
        val activity = foregroundActivity ?: return null
        if (origin == null) return null
        val sourceView = activity.findViewById<View>(android.R.id.content)
        return ActivityOptions.makeScaleUpAnimation(
            sourceView,
            origin.x,
            origin.y,
            origin.width,
            origin.height,
        )
        // return ActivityOptions.makeClipRevealAnimation(
        //     sourceView, origin.x, origin.y, origin.width, origin.height,
        // )
    }
}
