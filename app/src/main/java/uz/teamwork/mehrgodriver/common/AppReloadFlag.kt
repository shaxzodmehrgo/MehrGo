package uz.teamwork.mehrgodriver.common

import uz.teamwork.mehrgodriver.common.AppReloadFlag.transitionSnapshot


/**
 * Set true right before a theme or locale change recreates the activity, consumed once by
 * the map's loading mask so the branded "reloading" screen shows ONLY on those reloads —
 * never on cold start, where FirstActivity's splash already covers the launch (a second
 * splash there would look like two splash screens).
 */
object AppReloadFlag {
    var brandedReload = false

    /**
     * The frozen previous-frame bitmap captured right before a theme / locale recreate, laid over
     * the rebuilt activity and faded out so the recreate never flashes white or flickers the bars.
     * Set by [MainActivity.recreateWithCrossfade], consumed once by the rebuilt activity.
     */
    @Volatile
    var transitionSnapshot: android.graphics.Bitmap? = null

    /** When [transitionSnapshot] was captured (elapsedRealtime). A recreate lands within a few
     *  hundred ms; if a snapshot is older it belongs to a change that never recreated — consuming
     *  it would flash a STALE frame over an unrelated recreate (e.g. rotation), so it's dropped. */
    @Volatile
    var transitionSnapshotAtMs: Long = 0L
}
