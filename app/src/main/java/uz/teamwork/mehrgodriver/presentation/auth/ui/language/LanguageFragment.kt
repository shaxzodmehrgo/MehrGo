package uz.teamwork.mehrgodriver.presentation.auth.ui.language

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Localisation
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.databinding.FragmentLanguageBinding

class LanguageFragment : Fragment() {
    private var _binding: FragmentLanguageBinding? = null
    private val binding get() = _binding!!

    private var selectedLanguage: String = Constants.LANGUAGE_UZBEK

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (LanguageManager.getLanguage() != null) {
            findNavController().navigate(LanguageFragmentDirections.actionLanguageFragmentToIntroduceFragment())
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentLanguageBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        onClick()
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun onClick() {
        binding.apply {
            llUzbek.setOnClickListener { selectLanguage(Constants.LANGUAGE_UZBEK) }
            llKazakh.setOnClickListener { selectLanguage(Constants.LANGUAGE_KAZAKH) }
            llKyrgyz.setOnClickListener { selectLanguage(Constants.LANGUAGE_KYRGYZ) }
            llRussian.setOnClickListener { selectLanguage(Constants.LANGUAGE_RUSSIAN) }
            btnContinue.setOnClickListener { confirm() }
        }
        // Show the default selection without navigating; the user confirms with the button.
        selectLanguage(selectedLanguage)
    }

    private fun selectLanguage(language: String) {
        selectedLanguage = language
        binding.apply {
            setRowState(llUzbek, ivCheckUzbek, language == Constants.LANGUAGE_UZBEK)
            setRowState(llKazakh, ivCheckKazakh, language == Constants.LANGUAGE_KAZAKH)
            setRowState(llKyrgyz, ivCheckKyrgyz, language == Constants.LANGUAGE_KYRGYZ)
            setRowState(llRussian, ivCheckRussian, language == Constants.LANGUAGE_RUSSIAN)
        }
        // Preview the title + subtitle in the *selected* language (not the current app locale), so
        // tapping a row instantly shows that language's heading.
        val ctx = localizedContext(language)
        binding.tvTitle.text = ctx.getString(R.string.choose_your_language)
        binding.tvSubtitle.text = ctx.getString(R.string.choose_your_language_sub)
        binding.btnContinue.text = ctx.getString(R.string.continue_action)
    }

    /** A Context whose resources resolve for [language] (uz/kk/ky/ru), letting us read a string in a
     *  specific language without changing the whole app locale. */
    private fun localizedContext(language: String): android.content.Context {
        val config = android.content.res.Configuration(resources.configuration)
        config.setLocale(java.util.Locale.forLanguageTag(language))
        return requireContext().createConfigurationContext(config)
    }

    private fun setRowState(row: View, radio: ImageView, selected: Boolean) {
        if (selected) {
            row.setBackgroundResource(R.drawable.bg_lang_option)
            radio.setImageResource(R.drawable.baseline_radio_button_checked_24)
            radio.setColorFilter(ContextCompat.getColor(requireContext(), R.color.app_color))
        } else {
            row.setBackgroundResource(R.drawable.bg_lang_unselected)
            radio.setImageResource(R.drawable.baseline_radio_button_unchecked_24)
            radio.setColorFilter(ContextCompat.getColor(requireContext(), R.color.gray))
        }
    }

    private fun confirm() {
        findNavController().currentDestination?.getAction(R.id.action_languageFragment_to_introduceFragment)
            ?.let {
                // Show the spinner on the button (locale change recreates the activity,
                // so this gives feedback during the first-launch transition).
                binding.btnContinue.text = ""
                binding.btnContinue.isClickable = false
                binding.pbContinue.visibility = View.VISIBLE

                // A live config change can't re-translate already-inflated views, so recreate: the
                // recreated LanguageFragment.onCreate auto-forwards to Introduce (language is now set),
                // inflated in the chosen language.
                LanguageManager.saveLanguage(selectedLanguage)
                Localisation.applyToConfig(requireActivity(), selectedLanguage)
                requireActivity().recreate()
            }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}