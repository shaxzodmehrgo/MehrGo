package uz.teamwork.mehrgodriver.presentation.main.ui.home

import android.os.Bundle
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_INFO_COMPLETED
import uz.teamwork.mehrgodriver.common.Constants.DRIVER_TURNED_NOT_ACTIVE
import uz.teamwork.mehrgodriver.common.Constants.USER_INFO_DELETED
import uz.teamwork.mehrgodriver.common.Constants.VERIFY_CODE_CONFIRMED
import uz.teamwork.mehrgodriver.common.shared_pref.IntroduceManager
import uz.teamwork.mehrgodriver.common.shared_pref.LanguageManager
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager

/**
 * Launcher/router destination. The driver app is navigator-only: Home has no UI of its own — it
 * forwards to the auth/onboarding flow or to the map, all in [onCreate] before any view is shown.
 *
 * The list app-type mode (with its bottom-navigation shell: the old Home order list, MyOrders,
 * the bottom Settings/Notifications, OrdersFragment, MyDirectionFragment) was removed on
 * 2026-07-22. If it is ever reintroduced, that whole shell comes back with it; nothing of it
 * remains here.
 */
@AndroidEntryPoint
class HomeFragment : Fragment(R.layout.fragment_home) {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (Constants.IS_MAKTABGO) {
            // MaktabGo (школьный шаттл): свой OTP-поток, минуя таксишные экраны.
            // Уже вошли (есть токен) → сразу список рейсов; иначе → экран входа.
            if (UserManager.getUser() == null) {
                findNavController().navigate(R.id.action_navigation_home_to_maktabGoLoginFragment)
            } else {
                findNavController().navigate(R.id.action_navigation_home_to_maktabGoMapFragment)
            }
            return
        }

        when {
            LanguageManager.getLanguage() == null ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavigationHomeToLanguageFragment()
                )

            IntroduceManager.getIntroduce() == false ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavigationHomeToIntroduceFragment()
                )

            UserManager.getUser() == null ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavigationHomeToLoginFragment()
                )

            UserManager.getStatusValue() == VERIFY_CODE_CONFIRMED ->
                findNavController().navigate(
                    HomeFragmentDirections.actionNavigationHomeToCompleteDriverInfoFragment(
                        UserManager.getToken()!!
                    )
                )

            UserManager.getStatusValue() == DRIVER_INFO_COMPLETED ||
                    UserManager.getStatusValue() == DRIVER_TURNED_NOT_ACTIVE ||
                    UserManager.getStatusValue() == USER_INFO_DELETED ->
                findNavController().navigate(R.id.action_navigation_home_to_notActiveUserFragment)

            else ->
                findNavController().navigate(R.id.action_navigation_home_to_mapFragment)
        }
    }
}
