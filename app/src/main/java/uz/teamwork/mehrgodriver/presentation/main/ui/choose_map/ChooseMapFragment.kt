package uz.teamwork.mehrgodriver.presentation.main.ui.choose_map

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
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
import uz.teamwork.mehrgodriver.common.shared_pref.MapTypeManager
import uz.teamwork.mehrgodriver.databinding.FragmentChooseMapBinding

class ChooseMapFragment : Fragment() {
    private var _binding: FragmentChooseMapBinding? = null
    private val binding get() = _binding!!

    private var lastType: String? = null
    private var type: String? = null

    private var lastMapChoose: Boolean = false
    private var chooseMap: Boolean = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        lastType = MapTypeManager.getType()
        type = MapTypeManager.getType()

        lastMapChoose = MapTypeManager.getChoose()
        chooseMap = MapTypeManager.getChoose()
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentChooseMapBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.switchChooseMap.isChecked = chooseMap

        render()

        onClick()
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // Functions
    private fun onClick() {
        binding.apply {
            mcvBack.setOnClickListener {
                findNavController().navigateUp()
            }

            llGoogle.setOnClickListener {
                if (isAppInstalled(PKG_GOOGLE)) {
                    type = Constants.GOOGLE
                    render()
                } else {
                    redirectToPlayStore(PKG_GOOGLE)
                }
            }
            llYandex.setOnClickListener {
                if (isAppInstalled(PKG_YANDEX)) {
                    type = Constants.YANDEX
                    render()
                } else {
                    redirectToPlayStore(PKG_YANDEX)
                }
            }

            llYandexNavi.setOnClickListener {
                if (isAppInstalled(PKG_YANDEX_NAVI)) {
                    type = Constants.YANDEX_NAVI
                    render()
                } else {
                    redirectToPlayStore(PKG_YANDEX_NAVI)
                }
            }

            ll2GIS.setOnClickListener {
                if (isAppInstalled(PKG_2GIS)) {
                    type = Constants.TWO_GIS
                    render()
                } else {
                    redirectToPlayStore(PKG_2GIS)
                }
            }
            llWaze.setOnClickListener {
                if (isAppInstalled(PKG_WAZE)) {
                    type = Constants.WAZE
                    render()
                } else {
                    redirectToPlayStore(PKG_WAZE)
                }
            }

            llSwitchChooseMap.setOnClickListener {
                chooseMap = !chooseMap
                switchChooseMap.isChecked = chooseMap

                MapTypeManager.saveChoose(chooseMap)
            }

            mcvSubmit.setOnClickListener {
                MapTypeManager.saveType(type!!)
                findNavController().navigateUp()
            }
        }
    }

    private fun isAppInstalled(packageName: String): Boolean {
        return try {
            requireContext().packageManager.getPackageInfo(packageName, 0)
            true
        } catch (e: PackageManager.NameNotFoundException) {
            false
        }
    }

    private fun redirectToPlayStore(packageName: String) {
        try {
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=$packageName"))
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        } catch (e: android.content.ActivityNotFoundException) {
            val intent = Intent(
                Intent.ACTION_VIEW,
                Uri.parse("https://play.google.com/store/apps/details?id=$packageName")
            )
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            startActivity(intent)
        }
    }

    /**
     * Repaints every row from the current [type]: the selected row gets the yellow
     * stroke, and each row's trailing indicator shows a filled radio (installed +
     * selected), a hollow radio (installed) or an "open in store" glyph (not installed).
     */
    private fun render() = binding.apply {
        llGoogle.setBackgroundResource(strokeFor(type == Constants.GOOGLE))
        llYandex.setBackgroundResource(strokeFor(type == Constants.YANDEX))
        llYandexNavi.setBackgroundResource(strokeFor(type == Constants.YANDEX_NAVI))
        ll2GIS.setBackgroundResource(strokeFor(type == Constants.TWO_GIS))
        llWaze.setBackgroundResource(strokeFor(type == Constants.WAZE))

        indicator(ivGoogleCheck, PKG_GOOGLE, type == Constants.GOOGLE)
        indicator(ivYandexCheck, PKG_YANDEX, type == Constants.YANDEX)
        indicator(ivYandexNaviCheck, PKG_YANDEX_NAVI, type == Constants.YANDEX_NAVI)
        indicator(iv2GISCheck, PKG_2GIS, type == Constants.TWO_GIS)
        indicator(ivWazeCheck, PKG_WAZE, type == Constants.WAZE)
    }

    private fun strokeFor(selected: Boolean) =
        if (selected) R.drawable.bg_nav_option_selected else R.drawable.bg_nav_option

    private fun indicator(view: ImageView, packageName: String, selected: Boolean) {
        if (!isAppInstalled(packageName)) {
            view.setImageResource(R.drawable.baseline_open_in_new_24)
            view.setColorFilter(color(R.color.gray))
        } else {
            view.setImageResource(
                if (selected) R.drawable.baseline_radio_button_checked_24
                else R.drawable.baseline_radio_button_unchecked_24
            )
            view.setColorFilter(color(if (selected) R.color.app_color else R.color.gray))
        }
    }

    private fun color(resId: Int) = ContextCompat.getColor(requireContext(), resId)

    companion object {
        private const val PKG_GOOGLE = "com.google.android.apps.maps"
        private const val PKG_YANDEX = "ru.yandex.yandexmaps"
        private const val PKG_YANDEX_NAVI = "ru.yandex.yandexnavi"
        private const val PKG_2GIS = "ru.dublgis.dgismobile"
        private const val PKG_WAZE = "com.waze"
    }
}