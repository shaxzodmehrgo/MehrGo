package uz.teamwork.mehrgodriver.presentation.main.ui.change_theme

import android.content.res.Configuration
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.THEME_DAY
import uz.teamwork.mehrgodriver.common.Constants.THEME_NIGHT
import uz.teamwork.mehrgodriver.common.Constants.THEME_SYSTEM
import uz.teamwork.mehrgodriver.common.shared_pref.ThemeManager
import uz.teamwork.mehrgodriver.databinding.FragmentChangeThemeBinding
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity

class ChangeThemeFragment : Fragment() {
    private var _binding: FragmentChangeThemeBinding? = null
    private val binding get() = _binding!!

    private var theme: String = THEME_SYSTEM

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        theme = ThemeManager.getTheme() ?: THEME_SYSTEM
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeThemeBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        setupCurrentTheme()

        binding.apply {

            mcvBack.setOnClickListener {
                findNavController().navigateUp()
            }

            // Theme applies immediately on tap — no confirm step.
            llDay.setOnClickListener {
                theme = THEME_DAY
                selectedDay()
                changeTheme()
            }

            llNight.setOnClickListener {
                theme = THEME_NIGHT
                selectedNight()
                changeTheme()
            }

            llSystem.setOnClickListener {
                theme = THEME_SYSTEM
                selectedSystem()
                changeTheme()
            }
        }
    }

    private fun setupCurrentTheme() {
        when (theme) {
            THEME_DAY -> selectedDay()
            THEME_NIGHT -> selectedNight()
            THEME_SYSTEM -> selectedSystem()
        }
    }

    private fun selectedDay() = render(day = true, night = false, system = false)

    private fun selectedNight() = render(day = false, night = true, system = false)

    private fun selectedSystem() = render(day = false, night = false, system = true)

    /** Highlights the active row (yellow stroke) and fills only its radio. */
    private fun render(day: Boolean, night: Boolean, system: Boolean) = binding.apply {
        llDay.setBackgroundResource(strokeFor(day))
        llNight.setBackgroundResource(strokeFor(night))
        llSystem.setBackgroundResource(strokeFor(system))

        markRadio(ivDayCheck, day)
        markRadio(ivNightCheck, night)
        markRadio(ivSystemCheck, system)
    }

    private fun strokeFor(selected: Boolean) =
        if (selected) R.drawable.bg_nav_option_selected else R.drawable.bg_nav_option

    private fun markRadio(view: android.widget.ImageView, selected: Boolean) {
        view.setImageResource(
            if (selected) R.drawable.baseline_radio_button_checked_24
            else R.drawable.baseline_radio_button_unchecked_24
        )
        view.setColorFilter(
            androidx.core.content.ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.app_color else R.color.gray
            )
        )
    }

    private fun changeTheme() {
        ThemeManager.saveTheme(theme)
        val act = activity as? MainActivity
        // setDefaultNightMode only recreates when the EFFECTIVE day/night flips — picking a theme
        // that resolves to the current mode (e.g. Day → System while the device is light) does NOT
        // recreate. Compare the resolved mode, not the stored string: capturing a snapshot for a
        // recreate that never fires would leak it and flash a STALE frame on a later recreate.
        val isNightNow =
            (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                    Configuration.UI_MODE_NIGHT_YES
        val targetNight = when (theme) {
            THEME_DAY -> false
            THEME_NIGHT -> true
            else -> ThemeManager.isSystemNightMode(requireContext())
        }
        if (act == null || targetNight == isNightNow) {
            // No visual flip → no recreate; just close back to the map.
            dismissToHome()
            ThemeManager.applyTheme(theme)
            return
        }
        // Crossfade the theme swap. ORDER MATTERS: close Settings FIRST, wait two frames so the
        // map has drawn, and only then freeze the frame + recreate. Capturing before the pop froze
        // the PICKER screen — after the recreate that frozen picker faded over the map, which read
        // as "settings pops up again for a moment, then closes". Now the frozen frame IS the map
        // (old theme), so the visible story is simply map → smooth fade → map in the new theme.
        val pickedTheme = theme
        dismissToHome()
        val decor = act.window.decorView
        decor.post {
            decor.post {
                if (act.isFinishing || act.isDestroyed) return@post
                act.recreateWithCrossfade { ThemeManager.applyTheme(pickedTheme) }
            }
        }
    }

    /** Pops the picker AND the Settings screen above the map in a single atomic
     *  transaction, landing back on the map/home. Two sequential popBackStack() calls
     *  race the FragmentManager — they flash a black/empty container and detach the
     *  cached MapView — so this must stay one call. */
    private fun dismissToHome() {
        val nav = findNavController()
        if (!nav.popBackStack(R.id.mapFragment, false)) {
            nav.popBackStack(R.id.navigation_home, false)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}

//class ChangeThemeFragment : Fragment() {
//    private var _binding: FragmentChangeThemeBinding? = null
//    private val binding get() = _binding!!
//    private var theme: String? = null
//
//    override fun onCreate(savedInstanceState: Bundle?) {
//        super.onCreate(savedInstanceState)
//
//        theme = ThemeManager.getTheme()
//    }
//
//    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
//        _binding = FragmentChangeThemeBinding.inflate(inflater, container, false)
//        return binding.root
//    }
//
//    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
//        super.onViewCreated(view, savedInstanceState)
//
//        binding.apply {
//            mcvBack.setOnClickListener {
//                findNavController().navigateUp()
//            }
//
//            llDay.setOnClickListener {
//                selectedDay()
//                theme = THEME_DAY
//            }
//
//            llNight.setOnClickListener {
//                selectedNight()
//                theme = THEME_NIGHT
//            }
//
//            llSystem.setOnClickListener {
//                selectedSystem()
//                theme = THEME_SYSTEM
//            }
//
//            mcvSubmit.setOnClickListener {
//                changeTheme()
//            }
//        }
//    }
//
//    private fun selectedDay() {
//        binding.apply {
//            llDay.setBackgroundResource(R.drawable.background_stroke_yellow)
//            llNight.setBackgroundResource(R.drawable.background_stroke_gray)
//            llSystem.setBackgroundResource(R.drawable.background_stroke_gray)
//        }
//    }
//
//    private fun selectedNight() {
//        binding.apply {
//            llDay.setBackgroundResource(R.drawable.background_stroke_gray)
//            llNight.setBackgroundResource(R.drawable.background_stroke_yellow)
//            llSystem.setBackgroundResource(R.drawable.background_stroke_gray)
//        }
//    }
//
//    private fun selectedSystem() {
//        binding.apply {
//            llDay.setBackgroundResource(R.drawable.background_stroke_gray)
//            llNight.setBackgroundResource(R.drawable.background_stroke_gray)
//            llSystem.setBackgroundResource(R.drawable.background_stroke_yellow)
//        }
//    }
//
//    private fun changeTheme() {
//        LanguageManager.saveLanguage(theme!!)
//    }
//
//    override fun onDestroyView() {
//        super.onDestroyView()
//        _binding = null
//        theme = null
//    }
//}