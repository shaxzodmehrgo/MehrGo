package uz.teamwork.mehrgodriver.presentation.auth.ui.terms_of_use

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.databinding.FragmentTermsOfUseBinding

@AndroidEntryPoint
class TermsOfUseFragment : Fragment() {
    private var _binding: FragmentTermsOfUseBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val termsOfUseVM: TermsOfUseVM by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentTermsOfUseBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge: keep the scrolling terms below the transparent status bar.
        val nsvBasePaddingTop = binding.nsv.paddingTop
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            // getInsetsIgnoringVisibility: the status bar is hidden (immersive), so plain
            // getInsets() returns 0 and the terms would slide under the notch/status area.
            val statusTop =
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            binding.nsv.updatePadding(top = nsvBasePaddingTop + statusTop)
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        binding.tvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        lifecycleScope.launch {
            termsOfUseVM.termsOfUse().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        binding.tvTermsOfUse.text = it.data?.data?.text.toString()
                    }

                    is Resource.Error -> {
                        errorVisible()
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    private fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
        }
    }

    private fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }

    private fun errorVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }
}