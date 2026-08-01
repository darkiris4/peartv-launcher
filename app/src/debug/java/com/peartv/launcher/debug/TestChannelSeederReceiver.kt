package com.peartv.launcher.debug

import android.content.BroadcastReceiver
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.media.tv.TvContract
import android.util.Log

private const val TAG = "TestChannelSeeder"
private const val ActionSeed = "com.peartv.launcher.debug.SEED_TEST_CHANNEL"
private const val ActionClear = "com.peartv.launcher.debug.CLEAR_TEST_CHANNEL"
private const val TestInputId = "com.peartv.launcher.debug/TestInput"

/**
 * Debug-only (see `src/debug/AndroidManifest.xml`) — publishes/clears a
 * synthetic Home Screen Channel + a few `PreviewPrograms` under this app's
 * own package identity, purely to verify §2.4/§3.1.1 Tier 3's query + Content
 * Rows UI end-to-end on the reference Shield, where no real installed app
 * currently publishes channel data to test against.
 *
 * `TvContract` attributes every row's package_name to the *actual calling
 * app's* identity — there's no way to spoof this as if e.g. Hulu published
 * it, by design. Verifying the real pipeline therefore means temporarily
 * pointing `LauncherViewModel.tier3Channel`'s lookup at [TestInputId]'s
 * owning package during that one verification pass, not something this
 * receiver can do on its own.
 *
 * Trigger via:
 *   adb shell am broadcast -a com.peartv.launcher.debug.SEED_TEST_CHANNEL -p com.peartv.launcher.debug
 *   adb shell am broadcast -a com.peartv.launcher.debug.CLEAR_TEST_CHANNEL -p com.peartv.launcher.debug
 */
class TestChannelSeederReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        when (intent.action) {
            ActionSeed -> seed(context)
            ActionClear -> clear(context)
        }
    }

    private fun seed(context: Context) {
        val channelValues = ContentValues().apply {
            put(TvContract.Channels.COLUMN_INPUT_ID, TestInputId)
            put(TvContract.Channels.COLUMN_TYPE, TvContract.Channels.TYPE_PREVIEW)
            put(TvContract.Channels.COLUMN_DISPLAY_NAME, "Test Channel (debug-only)")
        }
        val channelUri = context.contentResolver.insert(TvContract.Channels.CONTENT_URI, channelValues)
        if (channelUri == null) {
            Log.e(TAG, "Channel insert failed")
            return
        }
        val channelId = ContentUris.parseId(channelUri)

        val programs = listOf(
            Triple("Portrait Poster Test", "https://picsum.photos/seed/peartv1/400/600", TvContract.PreviewPrograms.ASPECT_RATIO_2_3),
            Triple("Landscape Poster Test", "https://picsum.photos/seed/peartv2/640/360", TvContract.PreviewPrograms.ASPECT_RATIO_16_9),
            Triple("Square Poster Test", "https://picsum.photos/seed/peartv3/400/400", TvContract.PreviewPrograms.ASPECT_RATIO_1_1),
            Triple("No Poster Art Test", null, TvContract.PreviewPrograms.ASPECT_RATIO_2_3),
        )
        programs.forEachIndexed { index, (title, posterUri, aspectRatio) ->
            val values = ContentValues().apply {
                put(TvContract.PreviewPrograms.COLUMN_CHANNEL_ID, channelId)
                put(TvContract.PreviewPrograms.COLUMN_TYPE, TvContract.PreviewPrograms.TYPE_MOVIE)
                put(TvContract.PreviewPrograms.COLUMN_TITLE, title)
                put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_ASPECT_RATIO, aspectRatio)
                put(TvContract.PreviewPrograms.COLUMN_WEIGHT, programs.size - index)
                if (posterUri != null) {
                    put(TvContract.PreviewPrograms.COLUMN_POSTER_ART_URI, posterUri)
                }
            }
            context.contentResolver.insert(TvContract.PreviewPrograms.CONTENT_URI, values)
        }
        Log.i(TAG, "Seeded test channel $channelId with ${programs.size} programs")
    }

    private fun clear(context: Context) {
        val channelDeleted = context.contentResolver.delete(
            TvContract.Channels.CONTENT_URI,
            "${TvContract.Channels.COLUMN_INPUT_ID} = ?",
            arrayOf(TestInputId),
        )
        Log.i(TAG, "Cleared $channelDeleted test channel(s) (programs cascade-delete with their channel)")
    }
}
