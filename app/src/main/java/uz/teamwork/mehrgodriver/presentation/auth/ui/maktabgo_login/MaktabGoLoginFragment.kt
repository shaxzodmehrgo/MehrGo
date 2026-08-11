package uz.teamwork.mehrgodriver.presentation.auth.ui.maktabgo_login

import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import dagger.hilt.android.AndroidEntryPoint
import kotlinx.coroutines.launch
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.PHONE_NUMBER_SIZE
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.FragmentMaktabgoLoginBinding
import uz.teamwork.mehrgodriver.domain.model.User

/**
 * OTP-вход/регистрация водителя MaktabGo (школьный шаттл).
 * Шаг 1: телефон → «Получить код» (/driver/otp). Шаг 2: код (+ поля нового водителя) → «Войти»
 * (/driver/login). Токен сохраняем как User(authKey=token) → HeaderInterceptor шлёт Bearer.
 * Навигация на список рейсов будет добавлена, когда появится экран рейсов (TODO ниже).
 */
@AndroidEntryPoint
class MaktabGoLoginFragment : Fragment() {

    private var _binding: FragmentMaktabgoLoginBinding? = null
    private val binding get() = _binding!!
    private val vm: MaktabGoLoginVM by viewModels()

    private val vehicleClasses = listOf("car7", "car4", "van9", "van10", "van20")
    private val capacityByClass = mapOf("car4" to 4, "car7" to 7, "van9" to 9, "van10" to 10, "van20" to 20)

    private var phoneFormatted: String = ""

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        _binding = FragmentMaktabgoLoginBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        // Edge-to-edge: Activity рисует под системными барами (setDecorFitsSystemWindows(false) в
        // MainActivity), поэтому системный adjustResize НЕ поднимает контент над клавиатурой.
        // Компенсируем сами: верх — статус-бар, низ — max(клавиатура, навбар). clipToPadding=false
        // в разметке → скролл раскрывает нижние поля/кнопку «Войти» над клавиатурой.
        ViewCompat.setOnApplyWindowInsetsListener(binding.scrollContent) { v, insets ->
            val sb = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime()).bottom
            v.setPadding(v.paddingLeft, sb.top, v.paddingRight, maxOf(ime, sb.bottom))
            insets
        }

        binding.spVehicleClass.adapter = ArrayAdapter(
            requireContext(), android.R.layout.simple_spinner_dropdown_item, vehicleClasses
        )
        binding.cbNewDriver.setOnCheckedChangeListener { _, checked ->
            binding.llNewDriver.visibility = if (checked) View.VISIBLE else View.GONE
        }
        binding.tvGetCode.setOnClickListener { requestOtp() }
        binding.tvResend.setOnClickListener { requestOtp() }
        binding.tvLogin.setOnClickListener { doLogin() }
    }

    private fun currentPhoneRaw(): String = "+${binding.etPhoneNumber.unMasked}"

    private fun requestOtp() {
        if (binding.etPhoneNumber.unMasked.length < PHONE_NUMBER_SIZE - 1) {
            showToast(getString(R.string.enter_phone_number)); return
        }
        phoneFormatted = Helper.formatPhoneNumber(currentPhoneRaw())
        viewLifecycleOwner.lifecycleScope.launch {
            vm.requestOtp(phoneFormatted).collect {
                val b = _binding ?: return@collect
                when (it) {
                    is Resource.Loading -> { b.pbGetCode.visibility = View.VISIBLE; b.tvGetCode.text = "" }
                    is Resource.Success -> {
                        b.pbGetCode.visibility = View.GONE
                        b.tvGetCode.setText(R.string.get_code)
                        b.llCodeSection.visibility = View.VISIBLE
                        b.tvOtpHint.text = it.data?.message ?: ""
                        val dev = it.data?.devCode
                        if (!dev.isNullOrEmpty()) b.etCode.setText(dev) // dev-режим: автозаполнение кода
                        Timber.tag("MaktabGo").d("otp channel=%s dev=%s", it.data?.channel, dev)
                    }
                    is Resource.Error -> {
                        b.pbGetCode.visibility = View.GONE
                        b.tvGetCode.setText(R.string.get_code)
                        showToast(it.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    private fun doLogin() {
        val code = binding.etCode.text?.toString()?.trim().orEmpty()
        if (code.isEmpty()) { showToast(getString(R.string.input_code)); return }
        if (phoneFormatted.isEmpty()) phoneFormatted = Helper.formatPhoneNumber(currentPhoneRaw())

        val isNew = binding.cbNewDriver.isChecked
        val name = if (isNew) binding.etName.text?.toString()?.trim()?.ifEmpty { null } else null
        val vClass = if (isNew) binding.spVehicleClass.selectedItem?.toString() else null
        val capacity = vClass?.let { capacityByClass[it] }
        val plate = if (isNew) binding.etPlate.text?.toString()?.trim()?.ifEmpty { null } else null
        val model = if (isNew) binding.etCarModel.text?.toString()?.trim()?.ifEmpty { null } else null
        val color = if (isNew) binding.etCarColor.text?.toString()?.trim()?.ifEmpty { null } else null

        viewLifecycleOwner.lifecycleScope.launch {
            vm.login(phoneFormatted, code, name, vClass, capacity, plate, model, color).collect {
                val b = _binding ?: return@collect
                when (it) {
                    is Resource.Loading -> { b.pbLogin.visibility = View.VISIBLE; b.tvLogin.text = "" }
                    is Resource.Success -> {
                        b.pbLogin.visibility = View.GONE
                        b.tvLogin.setText(R.string.login)
                        val token = it.data?.token
                        if (token.isNullOrEmpty()) { showToast(getString(R.string.error)); return@collect }
                        val d = it.data?.driver
                        // Сохраняем как User → HeaderInterceptor берёт Bearer из authKey.
                        UserManager.saveUser(
                            User(
                                id = d?.id,
                                firstName = d?.name,
                                phone = d?.phone,
                                authKey = token,
                                balance = d?.balance,
                                workStatus = User.WorkStatus(d?.status ?: "pending")
                            )
                        )
                        val verified = it.data?.verified == true
                        showToast(getString(if (verified) R.string.maktabgo_login_done else R.string.maktabgo_verify_pending))
                        Timber.tag("MaktabGo").d("login OK verified=%s driverId=%s", verified, d?.id)
                        // Открываем список рейсов (снимаем экран входа из стека).
                        findNavController().navigate(R.id.action_maktabGoLoginFragment_to_maktabGoMapFragment)
                    }
                    is Resource.Error -> {
                        b.pbLogin.visibility = View.GONE
                        b.tvLogin.setText(R.string.login)
                        showToast(it.message ?: getString(R.string.error))
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }
}
