package com.peartv.launcher.data.launcher

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log
import com.peartv.launcher.domain.model.TvApp
import com.peartv.launcher.domain.repository.AppLauncher

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
    private val context: Context,
) : AppLauncher {

    override fun launch(app: TvApp) {
        val packageManager = context.packageManager
        val intent = packageManager.getLeanbackLaunchIntentForPackage(app.packageName)
            ?: packageManager.getLaunchIntentForPackage(app.packageName)

        if (intent == null) {
            Log.w(TAG, "No launch intent available for ${app.packageName}")
            return
        }

        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to launch ${app.packageName}", it) }
    }

    override fun launchContent(intentUri: String?, fallbackApp: TvApp) {
        val launchedContent = intentUri != null && runCatching {
            val intent = Intent.parseUri(intentUri, Intent.URI_INTENT_SCHEME)
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        }.onFailure {
            Log.w(TAG, "Failed to launch content intent URI '$intentUri', falling back to app launch", it)
        }.isSuccess

        if (!launchedContent) launch(fallbackApp)
    }

    override fun requestUninstall(packageName: String) {
        val intent = Intent(Intent.ACTION_DELETE, Uri.fromParts("package", packageName, null))
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        runCatching { context.startActivity(intent) }
            .onFailure { Log.w(TAG, "Failed to start uninstall flow for $packageName", it) }
    }
}
