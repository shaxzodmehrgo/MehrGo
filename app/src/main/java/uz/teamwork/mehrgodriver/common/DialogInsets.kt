package uz.teamwork.mehrgodriver.common

import android.app.Dialog
import android.os.Build
import android.view.View
import androidx.core.view.ViewCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.core.view.updatePadding

/**
 * Edge-to-edge (Android 15 / SDK 35) safe-area padding for dialog content.
 *
 * Android 15 enforces edge-to-edge on every window. Plain [Dialog]s are centered floating
 * windows the framework keeps inset from the bars, but a Material [com.google.android.material.bottomsheet.BottomSheetDialog]
 * draws its content behind the navigation bar, so a sheet's bottom buttons end
 * up under the nav bar. This pads the dialog's content view by the system-bar (and IME)
 * insets so nothing is hidden.
 *
 * [fitSystemBars] wires the padding on show() — so it works no matter when setContentView
 * is called — and returns the dialog for call-site chaining:
 *     BottomSheetDialog(ctx).fitSystemBars()
 */
fun <T : Dialog> T.fitSystemBars(): T = apply {
    setOnShowListener {
        applyContentInsets()
        hideSystemBars()
    }
}

/**
 * Nav-bar treatment for dialog/sheet windows, split by Android version:
 *
 *  - **Android 14 and lower**: force FIT mode (opaque system bars) and paint the nav bar
 *    the sheet colour (white in light mode, dark in night). Forcing edge-to-edge
 *    (decorFitsSystemWindows=false) made the nav bar TRANSPARENT, so the dim scrim behind
 *    the dialog showed through it grey no matter how we padded the sheet.
 *  - **Android 15+**: edge-to-edge is enforced — the bars are always transparent and the
 *    setters above are deprecated no-ops (Google Play flags any call to them). The nav-bar
 *    strip is instead painted by the sheet's own opaque background, which
 *    [applyContentInsets] extends under the transparent bar via bottom inset padding.
 *
 * Both paths flip the nav-bar icon appearance to stay visible on the light/dark strip.
 * Call once the window/decor exists (e.g. on show()).
 */
fun Dialog.hideSystemBars() {
    val w = window ?: return
    val night = (w.context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
        WindowCompat.setDecorFitsSystemWindows(w, true)
        @Suppress("DEPRECATION")
        w.navigationBarColor = androidx.core.content.ContextCompat.getColor(
            w.context, uz.teamwork.mehrgodriver.R.color.white_black
        )
    }
    WindowInsetsControllerCompat(w, w.decorView).isAppearanceLightNavigationBars = !night
}

/**
 * Applies safe-area inset padding so dialog content isn't hidden behind the system bars.
 * Call once the content view exists (e.g. inside an OnShowListener).
 *
 *  - BottomSheetDialog: the sheet (`design_bottom_sheet`) is padded at the bottom by the
 *    navigation-bar / IME inset, so the sheet background still extends edge-to-edge while
 *    its content (e.g. the bottom action buttons) clears the nav bar.
 *  - Plain Dialog: the content view is padded on all sides by the system-bar insets.
 */
fun Dialog.applyContentInsets() {
    val w = window ?: return
    val sheet = w.findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
    if (sheet != null) {
        // design_bottom_sheet itself carries the sheet's rounded white background (BottomSheetModalStyle
        // → background_dialog_bsh: solid white_black, top corners only / SQUARE bottom). Pad ITS bottom
        // by the nav/IME inset: the white background then extends DOWN under the (visible, transparent)
        // nav bar — painting that strip solid white — while the content inside is pushed up to clear the
        // bar/keyboard. (Padding the inner child instead does NOT extend the sheet's own background, so
        // the nav strip stayed dark — that was the regression.)
        val base = sheet.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(sheet) { v, insets ->
            val bottom = insets.getInsets(
                WindowInsetsCompat.Type.systemBars() or WindowInsetsCompat.Type.ime()
            ).bottom
            v.updatePadding(bottom = base + bottom)
            insets
        }
        ViewCompat.requestApplyInsets(sheet)
        return
    }
    val content = w.findViewById<View>(android.R.id.content) ?: return
    val l = content.paddingLeft
    val t = content.paddingTop
    val r = content.paddingRight
    val b = content.paddingBottom
    ViewCompat.setOnApplyWindowInsetsListener(content) { v, insets ->
        val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
        val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
        v.updatePadding(
            left = l + bars.left,
            top = t + bars.top,
            right = r + bars.right,
            bottom = b + maxOf(bars.bottom, ime.bottom)
        )
        insets
    }
    ViewCompat.requestApplyInsets(content)
}
