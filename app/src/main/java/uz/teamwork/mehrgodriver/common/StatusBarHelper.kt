package uz.teamwork.mehrgodriver.common

import android.app.Activity
import android.content.res.Configuration
import android.os.Build
import androidx.core.content.ContextCompat
import androidx.core.view.WindowInsetsControllerCompat
import uz.teamwork.mehrgodriver.R

object StatusBarHelper {

    /**
     * Neutral screens (auth, main app): system bars blend with the canvas —
     * warm off-white in light mode, deep dark in dark mode. Icons flip to
     * match contrast on each surface.
     */
    fun applyAuthStyle(activity: Activity) {
        val isNight =
            (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        val bgColor =
            ContextCompat.getColor(activity, R.color.gray_light_black_light)

        applyDecorBackground(activity, bgColor)

        // On Android 15 (VANILLA_ICE_CREAM) these setters are deprecated no-ops
        // and Google Play flags any call to them. Decor-view background does the
        // tinting on 15+; for older versions we still need the setters.
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = bgColor
            // Nav bar keeps the uniform theme colour (white_black) + divider from the theme — it is
            // NEVER tinted per-screen, so the bottom bar looks identical everywhere.
        }

        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = !isNight
            isAppearanceLightNavigationBars = !isNight
        }
    }

    /**
     * Yellow toolbar screens (instruction/video, etc.): status bar matches the
     * app_color toolbar with dark icons. Navigation bar also tints to app_color
     * so the whole window reads as a single yellow surface.
     */
    fun applyToolbarStyle(activity: Activity) {
        val toolbarColor = ContextCompat.getColor(activity, R.color.app_color)

        applyDecorBackground(activity, toolbarColor)

        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.VANILLA_ICE_CREAM) {
            @Suppress("DEPRECATION")
            activity.window.statusBarColor = toolbarColor
            // Nav bar keeps the uniform theme colour (white_black) + divider — never tinted yellow.
        }

        val isNight =
            (activity.resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        WindowInsetsControllerCompat(activity.window, activity.window.decorView).apply {
            isAppearanceLightStatusBars = true
            // White/dark nav bar → dark icons in light mode, light icons in dark mode.
            isAppearanceLightNavigationBars = !isNight
        }
    }

    /**
     * Paints the decor view in the requested color and replaces any prior
     * inset listener with one that re-applies the same color. On Android 15+
     * `statusBarColor`/`navigationBarColor` are deprecated no-ops, so the
     * decor background is what actually colors the system bar areas.
     */
    private fun applyDecorBackground(activity: Activity, color: Int) {
        val decor = activity.window.decorView
        decor.setBackgroundColor(color)
        decor.setOnApplyWindowInsetsListener { view, insets ->
            view.setBackgroundColor(color)
            insets
        }
    }
}
