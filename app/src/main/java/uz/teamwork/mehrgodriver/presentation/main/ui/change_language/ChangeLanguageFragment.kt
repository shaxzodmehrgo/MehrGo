package uz.teamwork.mehrgodriver.presentation.main.ui.change_language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.Toast
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_KAZAKH
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_KYRGYZ
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_RUSSIAN
import uz.teamwork.mehrgodriver.common.Constants.LANGUAGE_UZBEK
import uz.teamwork.mehrgodriver.common.Localisation
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.databinding.FragmentChangeLanguageBinding
import uz.teamwork.mehrgodriver.domain.use_case.main.ChangeLanguageUC
import uz.teamwork.mehrgodriver.presentation.activity.main.MainActivity
import javax.inject.Inject

@AndroidEntryPoint
class ChangeLanguageFragment : Fragment() {

    @Inject
    lateinit var changeLanguageUC: ChangeLanguageUC

    private var _binding: FragmentChangeLanguageBinding? = null
    private val binding get() = _binding!!
    private var language: String? = null
    private var applying = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        language = LanguageManager.getLanguage()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChangeLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applySelection()
        onClick()
    }

    private fun onClick() {
        binding.apply {
            mcvBack.setOnClickListener { findNavController().navigateUp() }

            // Rows only select (highlight). The change is applied via the Apply button,
            // which shows an in-button spinner and lands on the map once it finishes.
            llUzbek.setOnClickListener { selectLanguage(LANGUAGE_UZBEK) }
            llKazakh.setOnClickListener { selectLanguage(LANGUAGE_KAZAKH) }
            llKyrgyz.setOnClickListener { selectLanguage(LANGUAGE_KYRGYZ) }
            llRussian.setOnClickListener { selectLanguage(LANGUAGE_RUSSIAN) }

            mcvSubmit.setOnClickListener { applyLanguage() }
        }
    }

    private fun selectLanguage(lang: String) {
        if (applying) return
        language = lang
        applySelection()
    }

    private fun applySelection() = binding.apply {
        setRow(llUzbek, ivUzbekCheck, language == LANGUAGE_UZBEK)
        setRow(llKazakh, ivKazakhCheck, language == LANGUAGE_KAZAKH)
        setRow(llKyrgyz, ivKyrgyzCheck, language == LANGUAGE_KYRGYZ)
        setRow(llRussian, ivRussianCheck, language == LANGUAGE_RUSSIAN)
    }

    /** Highlights the selected row (yellow stroke) and fills only its radio. */
    private fun setRow(row: View, radio: ImageView, selected: Boolean) {
        row.setBackgroundResource(
            if (selected) R.drawable.bg_nav_option_selected
            else R.drawable.bg_nav_option
        )
        radio.setImageResource(
            if (selected) R.drawable.baseline_radio_button_checked_24
            else R.drawable.baseline_radio_button_unchecked_24
        )
        radio.setColorFilter(
            ContextCompat.getColor(
                requireContext(),
                if (selected) R.color.app_color else R.color.gray
            )
        )
    }

    /**
     * Confirm step. Shows a spinner inside the Apply button while GET /user/change-language
     * runs, then — only on success — persists the locale, dismisses to the map and applies
     * the new locale (which recreates the activity). We collect the request here (instead of
     * the fire-and-forget FcmTokenSync.applyLanguageChange) so the spinner reflects the real
     * call, and the recreate fires only AFTER it resolves, so nothing is cancelled mid-flight.
     * The map masks its own GL surface during the recreate (flMapLoadingMask), so the landing
     * never flashes black.
     */
    private fun applyLanguage() {
        if (applying) return
        val newLang = language ?: return
        val current = LanguageManager.getLanguage()

        // Same language → nothing to apply, just return to the map.
        if (newLang == current) {
            dismissToHome()
            return
        }

        applying = true
        setSubmitLoading(true)

        changeLanguageUC.invoke(newLang)
            .onEach { resource ->
                when (resource) {
                    is Resource.Loading -> Unit // spinner already showing

                    is Resource.Success -> {
                        Timber.tag("FCM").d("change-language OK, applying locale %s", newLang)
                        LanguageManager.saveLanguage(newLang)
                        // Android can't re-translate already-drawn TextViews from a config change
                        // (that left the map's app-string labels in the old language). The only way to
                        // switch their language is to re-inflate them, so recreate the Activity:
                        // attachBaseContext re-inflates every screen in the new language. No
                        // brandedReload mask (that logo_splash mask was the fake "splash"); the map's
                        // one GL rebuild just shows its own background briefly. applyToConfig keeps the
                        // app/service config localized too.
                        Localisation.applyToConfig(requireActivity(), newLang)
                        // Close the WHOLE Settings navigation (pop the language picker AND Settings) and
                        // land on the map, then recreate — crossfaded so it doesn't flash white or
                        // flicker the system bars. ORDER MATTERS: pop FIRST, wait two frames so the map
                        // has drawn, then freeze the frame + recreate. Capturing before the pop froze
                        // the PICKER, which faded over the map after the recreate and read as "settings
                        // shows again for a moment, then closes".
                        val act = activity as? MainActivity
                        if (act != null) {
                            dismissToHome()
                            val decor = act.window.decorView
                            decor.post {
                                decor.post {
                                    if (act.isFinishing || act.isDestroyed) return@post
                                    act.recreateWithCrossfade { act.recreate() }
                                }
                            }
                        } else {
                            dismissToHome()
                            requireActivity().recreate()
                        }
                    }

                    is Resource.Error -> {
                        applying = false
                        setSubmitLoading(false)
                        Toast.makeText(requireContext(), resource.message, Toast.LENGTH_SHORT)
                            .show()
                    }
                }
            }
            .launchIn(viewLifecycleOwner.lifecycleScope)
    }

    /** Swaps the Apply label for a spinner and blocks further taps while applying. */
    private fun setSubmitLoading(loading: Boolean) = binding.apply {
        tvSubmit.visibility = if (loading) View.INVISIBLE else View.VISIBLE
        pbSubmit.visibility = if (loading) View.VISIBLE else View.GONE
        mcvSubmit.isClickable = !loading
        mcvSubmit.isEnabled = !loading
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
        language = null
    }
}
