package uz.teamwork.mehrgodriver.common

import android.os.SystemClock
import android.view.View

/** Default window during which a repeated tap on the same view is swallowed. */
const val DEFAULT_DEBOUNCE_MS = 700L

/**
 * Double-tap guard for buttons. The [action] fires at most once per [intervalMs] —
 * first-click-wins, the immediate second tap is ignored. Use this in place of
 * [View.setOnClickListener] for any control whose action is NOT idempotent: it opens a
 * dialog / bottom-sheet, navigates, or hits the network. Without it a fast double-tap
 * fires the handler twice — e.g. stacking two finish dialogs where the second becomes an
 * unclosable window, or double-submitting an accept/finish request.
 *
 * The throttle is per-listener (each view tracks its own last-click time), so tapping two
 * different buttons in quick succession is unaffected.
 */
fun View.setDebouncedClickListener(
    intervalMs: Long = DEFAULT_DEBOUNCE_MS,
    action: (View) -> Unit,
) {
    setOnClickListener(object : View.OnClickListener {
        private var lastClickTime = 0L

        override fun onClick(v: View) {
            val now = SystemClock.elapsedRealtime()
            if (now - lastClickTime < intervalMs) return
            lastClickTime = now
            action(v)
        }
    })
}
