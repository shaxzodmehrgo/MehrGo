package uz.teamwork.mehrgodriver.presentation.main.ui.my_profile

import android.annotation.SuppressLint
import android.content.res.ColorStateList
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.DriverAvatar
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.databinding.FragmentMyProfileBinding
import uz.teamwork.mehrgodriver.domain.model.Order
import uz.teamwork.mehrgodriver.domain.model.User
import java.util.Locale

private const val DASH = "—" // em dash, shown when a value is missing (matches iOS)

@AndroidEntryPoint
class MyProfileFragment : Fragment() {
    private var _binding: FragmentMyProfileBinding? = null
    private val binding get() = _binding!!

    private val viewModel: MyProfileViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentMyProfileBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.mcvBack.setOnClickListener {
            findNavController().navigateUp()
        }

        // Show the cached user immediately, then refresh from user/me (iOS parity).
        UserManager.getUser()?.let { bindUser(it) }
        refreshUser()
    }

    private fun refreshUser() {
        viewLifecycleOwner.lifecycleScope.launch {
            viewModel.getUser().collect {
                when (it) {
                    is Resource.Loading -> {}
                    is Resource.Success -> {
                        val data = it.data?.data
                        if (data != null) {
                            UserManager.saveUser(data)
                            bindUser(data)
                        }
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }

    /** Shared with the settings header — see [DriverAvatar]. */
    private fun bindAvatar(user: User) =
        DriverAvatar.bind(user.photo, binding.ivAvatar, binding.ivAvatarPlaceholder)

    @SuppressLint("SetTextI18n")
    private fun bindUser(user: User) {
        bindAvatar(user)
        binding.apply {
            // ---- Hero: name + phone + status badge ----
            val fullName = listOf(user.lastName, user.firstName, user.fatherName)
                .mapNotNull { part -> part?.trim()?.takeIf { it.isNotEmpty() } }
                .joinToString(" ")
            tvFullName.text = fullName.ifEmpty { DASH }

            val phone = user.phone?.trim().orEmpty()
            if (phone.isNotEmpty()) {
                tvPhone.visibility = View.VISIBLE
                tvPhone.text = phone
            } else {
                tvPhone.visibility = View.GONE
            }

            val statusLabel = user.status?.valueText?.trim().orEmpty()
            if (statusLabel.isNotEmpty()) {
                val color = statusColor(user.status?.valueNumber)
                cvStatusBadge.visibility = View.VISIBLE
                cvStatusBadge.setCardBackgroundColor(ColorUtils.setAlphaComponent(color, 31))
                vStatusDot.backgroundTintList = ColorStateList.valueOf(color)
                tvStatus.setTextColor(color)
                tvStatus.text = statusLabel
            } else {
                cvStatusBadge.visibility = View.GONE
            }

            // ---- Account ----
            tvBalance.text =
                "${Helper.formatPrice((user.balance ?: 0).toString())}${getString(R.string.sum)}"
            tvRating.text = user.rating?.let { String.format(Locale.US, "%.1f", it) } ?: DASH
            tvMemberSince.text = user.createdAt?.trim()?.takeIf { it.isNotEmpty() } ?: DASH

            // ---- Personal info ----
            tvFirstName.text = user.firstName?.takeIf { it.isNotBlank() } ?: DASH
            tvFatherName.text = user.fatherName?.takeIf { it.isNotBlank() } ?: DASH
            tvLastName.text = user.lastName?.takeIf { it.isNotBlank() } ?: DASH

            // ---- Vehicle (card or empty state) ----
            val car = user.car
            if (car != null && hasAnyCarData(car)) {
                cvVehicle.visibility = View.VISIBLE
                cvNoVehicle.visibility = View.GONE
                tvCarModel.text = displayName(car.carModel?.name, car.carModel?.secondName)
                tvCarColor.text = displayName(car.carColor?.name, car.carColor?.secondName)
                tvCarNumber.text = car.carNumber?.takeIf { it.isNotBlank() } ?: DASH
            } else {
                cvVehicle.visibility = View.GONE
                cvNoVehicle.visibility = View.VISIBLE
            }

            // ---- Working branch ----
            tvRegion.text = (user.branch?.name?.takeIf { it.isNotBlank() }
                ?: user.branch?.city?.takeIf { it.isNotBlank() }) ?: DASH

            // ---- Tariffs the driver works on (from user/me `tariffs`) ----
            val tariffNames = user.tariffs
                ?.mapNotNull { it.name?.trim()?.takeIf { name -> name.isNotEmpty() } }
                ?.distinct()
                .orEmpty()
            // One chip each instead of a comma-joined line — reuses the order-offer service
            // chip (adapter_chip is a plain TextView, so ChipGroup just flows them).
            chipGroupTariffs.removeAllViews()
            for (name in tariffNames) {
                val chip = layoutInflater
                    .inflate(R.layout.adapter_chip, chipGroupTariffs, false) as TextView
                chip.text = name
                chipGroupTariffs.addView(chip)
            }
            chipGroupTariffs.visibility = if (tariffNames.isEmpty()) View.GONE else View.VISIBLE
            tvTariffsEmpty.visibility = if (tariffNames.isEmpty()) View.VISIBLE else View.GONE
            tvTariffsEmpty.text = DASH
        }
    }

    /** Badge color by driver-account status (green active / amber onboarding / red blocked). */
    private fun statusColor(valueNumber: Int?): Int {
        val colorRes = when (valueNumber) {
            Constants.DRIVER_ACTIVE -> R.color.green
            Constants.VERIFY_CODE_CONFIRMED,
            Constants.DRIVER_INFO_COMPLETED,
            Constants.USER_INFO_COMPLETED -> R.color.status_amber

            Constants.DRIVER_TURNED_NOT_ACTIVE,
            Constants.USER_INFO_DELETED -> R.color.red

            else -> R.color.gray_full
        }
        return ContextCompat.getColor(requireContext(), colorRes)
    }

    private fun hasAnyCarData(car: Order.Car): Boolean {
        return !car.carNumber.isNullOrBlank() ||
                !car.carModel?.name.isNullOrBlank() ||
                !car.carModel?.secondName.isNullOrBlank() ||
                !car.carColor?.name.isNullOrBlank() ||
                !car.carColor?.secondName.isNullOrBlank()
    }

    /**
     * Combines the canonical (`name`) and transliterated (`second_name`) labels the
     * backend ships for car model / color. Returns the primary when one is missing or
     * both match (case-insensitive), otherwise "Primary (Secondary)" (matches iOS).
     */
    private fun displayName(primary: String?, secondary: String?): String {
        val p = primary?.trim()?.takeIf { it.isNotEmpty() }
        val s = secondary?.trim()?.takeIf { it.isNotEmpty() }
        return when {
            p != null && s != null -> if (p.equals(s, ignoreCase = true)) p else "$p ($s)"
            p != null -> p
            s != null -> s
            else -> DASH
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
