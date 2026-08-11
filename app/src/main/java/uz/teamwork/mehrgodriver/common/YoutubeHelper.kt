package uz.teamwork.mehrgodriver.common

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import uz.teamwork.mehrgodriver.common.YoutubeHelper.openExternally

/**
 * Shared helpers for the instruction/how-to YouTube videos.
 *
 * The instruction screens do NOT play video in-app — they show a thumbnail poster and hand
 * off to YouTube via [openExternally]. Two in-app approaches were tried and both failed
 * on-device (2026-07-20):
 *  - PierfrancescoSoffritti IFrame player: YouTube rejects WebView embeds outright, firing
 *    an instant UNKNOWN error on EVERY video — including one with embedding enabled, and on
 *    the newest (12.1.2) library. The library is unmaintained since Dec-2024. Dependency
 *    removed.
 *  - Full-screen WebView on the m.youtube.com watch page (with a desktop-Chrome UA): also
 *    would not play. Removed, so nobody re-wires a path we know is dead.
 */
object YoutubeHelper {

    /** Pull the 11-char video id out of any of the URL shapes the backend returns. */
    fun extractId(url: String): String = when {
        url.contains("youtu.be/") -> url.substringAfterLast("/").substringBefore("?")
        url.contains("v=") -> url.substringAfter("v=").substringBefore("&")
        url.contains("/embed/") -> url.substringAfter("/embed/").substringBefore("?")
        else -> url.substringAfterLast("/")
    }

    /**
     * Open the video in the YouTube app if installed, otherwise in the browser.
     * The `vnd.youtube:` scheme targets the YouTube app directly; if it isn't
     * present (or throws), fall back to the standard watch URL, which the system
     * resolves to a browser or any handler.
     */
    fun openExternally(context: Context, videoId: String) {
        val watchUrl = "https://www.youtube.com/watch?v=$videoId"
        val appIntent = Intent(Intent.ACTION_VIEW, Uri.parse("vnd.youtube:$videoId"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(appIntent)
        } catch (e: ActivityNotFoundException) {
            context.startActivity(
                Intent(Intent.ACTION_VIEW, Uri.parse(watchUrl))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            )
        }
    }

}
