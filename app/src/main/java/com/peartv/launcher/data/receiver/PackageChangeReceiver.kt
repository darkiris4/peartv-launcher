package com.peartv.launcher.data.receiver

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter

/**
 * PRODUCT_SPEC.md §3.2 — implicit package-change broadcasts require runtime
 * registration on modern Android (manifest-declared receivers no longer get
 * them). Registered from PearTvLauncherApplication, since the launcher *is*
 * the always-resident process.
 *
 * This receiver only signals "something changed" — burst-debouncing (e.g. a
 * batch of updates after a reboot) is the registering caller's job, not
 * this class's, so it stays a dumb trigger.
 */
class PackageChangeReceiver(
    private val onPackagesChanged: () -> Unit,
) : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        onPackagesChanged()
    }

    companion object {
        fun intentFilter(): IntentFilter = IntentFilter().apply {
            addAction(Intent.ACTION_PACKAGE_ADDED)
            addAction(Intent.ACTION_PACKAGE_REMOVED)
            addAction(Intent.ACTION_PACKAGE_REPLACED)
            addDataScheme("package")
        }
    }
}
