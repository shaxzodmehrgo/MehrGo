package uz.teamwork.mehrgodriver.presentation.auth.ui.introduce

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updateLayoutParams
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import androidx.viewpager2.widget.ViewPager2
import com.zhpan.indicator.enums.IndicatorSlideMode
import com.zhpan.indicator.enums.IndicatorStyle
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.shared_pref.IntroduceManager
import uz.teamwork.mehrgodriver.databinding.FragmentIntroduceBinding
import uz.teamwork.mehrgodriver.domain.model.Introduce
import uz.teamwork.mehrgodriver.presentation.auth.adapter.IntroduceAdapter

@AndroidEntryPoint
class IntroduceFragment : Fragment() {
    private var _binding: FragmentIntroduceBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val introduceVM: IntroduceVM by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (IntroduceManager.getIntroduce() != false) {
            if (IntroduceManager.getIntroduce() == true) {
                findNavController().navigate(IntroduceFragmentDirections.actionIntroduceFragmentToLoginFragment())
            }
        }
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentIntroduceBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge: nudge the "Skip" pill below the transparent status bar
        // (the illustration pager stays full-bleed behind it).
        val skipBaseTopMargin =
            (binding.tvSkip.layoutParams as ViewGroup.MarginLayoutParams).topMargin
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val statusTop = insets.getInsets(WindowInsetsCompat.Type.statusBars()).top
            binding.tvSkip.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                topMargin = skipBaseTopMargin + statusTop
            }
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)

        lifecycleScope.launch {
            introduceVM.getIntroduceData().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        val introduces = it.data?.data ?: emptyList()
                        if (introduces.isEmpty()) {
                            IntroduceManager.saveIntroduce(true)
                            findNavController().navigate(IntroduceFragmentDirections.actionIntroduceFragmentToLoginFragment())
                        } else {
                            screenVisible()
                            val introduceAdapter = IntroduceAdapter(introduces)
                            binding.viewPager.adapter = introduceAdapter
                            setupPageTransformer()
                            setIndicator()
                            observeViewPager(introduces)
                        }
                    }

                    is Resource.Error -> {}
                }
            }
        }

        onClick()
    }

    override fun onResume() {
        super.onResume()
        StatusBarHelper.applyAuthStyle(requireActivity())
    }

    private fun onClick() {
        binding.apply {
            tvSkip.setOnClickListener {
                IntroduceManager.saveIntroduce(true)
                findNavController().navigate(IntroduceFragmentDirections.actionIntroduceFragmentToLoginFragment())
            }

            tvStart.setOnClickListener {
                IntroduceManager.saveIntroduce(true)
                findNavController().navigate(IntroduceFragmentDirections.actionIntroduceFragmentToLoginFragment())
            }

            tvNext.setOnClickListener {
                viewPager.currentItem = viewPager.currentItem + 1
            }
        }
    }

    private fun observeViewPager(list: List<Introduce>) {
        binding.viewPager.registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
            override fun onPageSelected(position: Int) {
                super.onPageSelected(position)

                binding.apply {
                    if (position == list.size - 1) {
                        tvStart.visibility = View.VISIBLE
                        tvStart.isClickable = true

                        tvNext.visibility = View.INVISIBLE
                        tvNext.isClickable = false

                        tvSkip.visibility = View.INVISIBLE
                        tvSkip.isClickable = false
                    } else {
                        tvStart.visibility = View.INVISIBLE
                        tvStart.isClickable = false

                        tvNext.visibility = View.VISIBLE
                        tvNext.isClickable = true

                        tvSkip.visibility = View.VISIBLE
                        tvSkip.isClickable = true
                    }

                }
            }
        })

    }

    // Subtle swipe motion: the medallion drifts (parallax) and the slide cross-fades.
    private fun setupPageTransformer() {
        binding.viewPager.offscreenPageLimit = 1
        binding.viewPager.setPageTransformer { page, position ->
            page.findViewById<View?>(R.id.flMedallion)?.translationX =
                -position * (page.width / 5f)
            page.alpha = 1f - (kotlin.math.abs(position) * 0.5f).coerceAtMost(1f)
        }
    }

    private fun setIndicator() {
        binding.indicatorView.apply {
            setSliderWidth(resources.getDimension(R.dimen.dp_10))
            setSliderHeight(resources.getDimension(R.dimen.dp_10))
            setSlideMode(IndicatorSlideMode.WORM)
            setIndicatorStyle(IndicatorStyle.CIRCLE)
            setupWithViewPager(binding.viewPager)
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    fun loadingVisible() {
        binding.apply {
            progressBar.visibility = View.VISIBLE
            content.visibility = View.GONE
        }
    }

    fun screenVisible() {
        binding.apply {
            progressBar.visibility = View.GONE
            content.visibility = View.VISIBLE
        }
    }
}