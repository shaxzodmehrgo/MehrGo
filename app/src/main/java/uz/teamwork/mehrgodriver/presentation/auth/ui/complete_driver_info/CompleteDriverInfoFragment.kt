package uz.teamwork.mehrgodriver.presentation.auth.ui.complete_driver_info

import android.annotation.SuppressLint
import android.app.Dialog
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.net.Uri
import android.os.Bundle
import android.os.Environment
import android.text.InputFilter
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.Window
import android.widget.ArrayAdapter
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.FileProvider
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.updatePadding
import androidx.core.widget.addTextChangedListener
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.navigation.fragment.findNavController
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.datepicker.CalendarConstraints
import com.google.android.material.datepicker.DateValidatorPointBackward
import com.google.android.material.datepicker.MaterialDatePicker
import com.google.android.material.textfield.TextInputEditText
import dagger.hilt.android.AndroidEntryPoint
import id.zelory.compressor.Compressor
import kotlinx.coroutines.launch
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody
import timber.log.Timber
import uz.teamwork.mehrgodriver.R
import uz.teamwork.mehrgodriver.common.Constants.APPLICATION_ID
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.MetaEvents
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.StatusBarHelper
import uz.teamwork.mehrgodriver.common.fitSystemBars
import uz.teamwork.mehrgodriver.common.model.DriverLicense
import uz.teamwork.mehrgodriver.common.model.Gender
import uz.teamwork.mehrgodriver.common.setDebouncedClickListener
import uz.teamwork.mehrgodriver.common.shared_pref.UserManager
import uz.teamwork.mehrgodriver.common.showToast
import uz.teamwork.mehrgodriver.databinding.DialogBshChooseCameraGalleryBinding
import uz.teamwork.mehrgodriver.databinding.DialogSelectYearBinding
import uz.teamwork.mehrgodriver.databinding.FragmentCompleteDriverInfoBinding
import uz.teamwork.mehrgodriver.domain.model.CarBrand
import uz.teamwork.mehrgodriver.domain.model.CarColor
import uz.teamwork.mehrgodriver.domain.model.CarModel
import uz.teamwork.mehrgodriver.domain.model.Region
import java.io.File
import java.io.FileOutputStream
import java.util.Calendar
import java.util.TimeZone

@AndroidEntryPoint
class CompleteDriverInfoFragment : Fragment() {
    private var _binding: FragmentCompleteDriverInfoBinding? = null
    private val binding get() = _binding!!

    // For ViewModel
    private val completeDriverInfoVM: CompleteDriverInfoVM by viewModels()

    private var authKey: String? = null
    private var specialityId: String? = "1"

    private var myPhotoPath: String? = null
        set(value) {
            field = value
            refreshSubmitEnabled()
        }
    private var listPhotoDocuments: ArrayList<String> = ArrayList()
    private var photoLicence: String? = null
        set(value) {
            field = value
            refreshSubmitEnabled()
        }
    private var photoCar: String? = null
        set(value) {
            field = value
            refreshSubmitEnabled()
        }
    private var fromMyPhoto = 1
    private var fromPhotoLicence = 2
    private var fromPhotoCar = 3

    private var formattingPassport = false
    private var formattingPlate = false

    private var listRegions: List<Region>? = ArrayList()
    private var regionId: String? = null

    private var listCarBrands: ArrayList<CarBrand>? = ArrayList()
    private var carBrandId: Int? = null

    private var listCarModels: ArrayList<CarModel>? = ArrayList()
    private var carModelId: Int? = null

    private var listCarColors: List<CarColor>? = ArrayList()
    private var carColorId: Int? = null

    private var listGenderTypes: List<Gender>? = ArrayList()
    private var genderTypeId: String? = null

    private var listDriverLicenseTypes: List<DriverLicense>? = ArrayList()
    private var driverLicenseTypeId: String? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        authKey = arguments?.getString("auth_key")
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        _binding = FragmentCompleteDriverInfoBinding.inflate(inflater, container, false)

        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        applyImeInsets()
        onClick()
        setupPassportFormat()
        setupCarNumberFormat()
        binding.tieLicenseNumber.filters = arrayOf(InputFilter.AllCaps())
        refreshSubmitEnabled()
        getRegions()
        getCarBrands()
        getCarModels()
        getCarColors()
        getGenderTypes()
        getDriverLicenseTypes()
    }

    private fun applyImeInsets() {
        val basePaddingTop = binding.content.paddingTop
        val basePaddingBottom = binding.content.paddingBottom
        ViewCompat.setOnApplyWindowInsetsListener(binding.root) { _, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            val ime = insets.getInsets(WindowInsetsCompat.Type.ime())
            // Status bar is hidden in immersive mode → getInsets().top is 0; use
            // getInsetsIgnoringVisibility so the form still clears the notch/status area.
            val statusTop =
                insets.getInsetsIgnoringVisibility(WindowInsetsCompat.Type.statusBars()).top
            binding.content.updatePadding(
                top = basePaddingTop + statusTop,
                bottom = basePaddingBottom + maxOf(bars.bottom, ime.bottom)
            )
            insets
        }
        ViewCompat.requestApplyInsets(binding.root)
    }

    /**
     * Uzbek passport series/number — two upper-case letters then seven digits (e.g. AA1234567).
     * Forces upper-case, keeps only valid characters for each position, and caps length at 9.
     */
    private fun setupPassportFormat() {
        binding.tiePassportNumber.addTextChangedListener(afterTextChanged = { editable ->
            if (!formattingPassport && editable != null) {
                formattingPassport = true
                val formatted = buildString {
                    for (ch in editable.toString().uppercase()) {
                        val pos = length
                        if (pos >= 9) break
                        if (pos < 2) {
                            if (ch in 'A'..'Z') append(ch)
                        } else {
                            if (ch in '0'..'9') append(ch)
                        }
                    }
                }
                if (formatted != editable.toString()) {
                    binding.tiePassportNumber.setText(formatted)
                    binding.tiePassportNumber.setSelection(formatted.length)
                }
                formattingPassport = false
            }
        })
    }

    /**
     * Material date picker that follows the app's day/night theme. Pre-selects the field's
     * current value (if any), blocks future dates, and writes the result back as yyyy-MM-dd.
     */
    private fun showDatePicker(field: TextInputEditText, tag: String, defaultYearsAgo: Int = 0) {
        val constraints = CalendarConstraints.Builder()
            .setValidator(DateValidatorPointBackward.now())
            .build()
        // Re-open on the value already chosen, otherwise start in a sensible decade
        // (e.g. ~25 years back for date of birth) so the user isn't scrolling from today.
        val selection = parseDateToUtcMillis(field.text?.toString())
            ?: Calendar.getInstance(TimeZone.getTimeZone("UTC"))
                .apply { add(Calendar.YEAR, -defaultYearsAgo) }
                .timeInMillis
        val picker = MaterialDatePicker.Builder.datePicker()
            .setTheme(R.style.AppMaterialDatePicker)
            .setCalendarConstraints(constraints)
            .setSelection(selection)
            .build()
        picker.addOnPositiveButtonClickListener { millis ->
            val utc =
                Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply { timeInMillis = millis }
            val month = Helper.addNolIsNeeded(utc.get(Calendar.MONTH) + 1)
            val day = Helper.addNolIsNeeded(utc.get(Calendar.DAY_OF_MONTH))
            field.setText("${utc.get(Calendar.YEAR)}-$month-$day")
        }
        picker.show(childFragmentManager, tag)
    }

    /** Parses a yyyy-MM-dd string into UTC millis (start of day) for date-picker pre-selection. */
    private fun parseDateToUtcMillis(value: String?): Long? {
        if (value.isNullOrBlank()) return null
        val parts = value.split("-")
        if (parts.size != 3) return null
        return try {
            Calendar.getInstance(TimeZone.getTimeZone("UTC")).apply {
                clear()
                set(parts[0].toInt(), parts[1].toInt() - 1, parts[2].toInt())
            }.timeInMillis
        } catch (e: Exception) {
            null
        }
    }

    /**
     * Uzbek vehicle plate — 8 chars, either "01L777LL" (2 digits, letter, 3 digits, 2 letters)
     * or "01777LLL" (2 digits, 3 digits, 3 letters). The character at index 2 (letter vs digit)
     * decides which layout applies; only characters valid for each position are kept.
     */
    private fun setupCarNumberFormat() {
        binding.tieCarNumber.addTextChangedListener(afterTextChanged = { editable ->
            if (!formattingPlate && editable != null) {
                formattingPlate = true
                val sb = StringBuilder()
                for (ch in editable.toString().uppercase()) {
                    val pos = sb.length
                    if (pos >= 8) break
                    val isDigit = ch in '0'..'9'
                    val isLetter = ch in 'A'..'Z'
                    val accept = when {
                        pos < 2 -> isDigit
                        pos == 2 -> isDigit || isLetter
                        sb[2] in 'A'..'Z' -> if (pos in 3..5) isDigit else isLetter
                        else -> if (pos in 3..4) isDigit else isLetter
                    }
                    if (accept) sb.append(ch)
                }
                val formatted = sb.toString()
                if (formatted != editable.toString()) {
                    binding.tieCarNumber.setText(formatted)
                    binding.tieCarNumber.setSelection(formatted.length)
                }
                formattingPlate = false
            }
        })
    }

    override fun onResume() {
        super.onResume()

        StatusBarHelper.applyAuthStyle(requireActivity())

        // if needed that will work
        if (myPhotoPath != null) {
            binding.civMyPhoto.setImageURI(Uri.fromFile(File(myPhotoPath!!)))
        }
    }

    @SuppressLint("SetTextI18n")
    private fun onClick() {
        binding.apply {
            civMyPhoto.setDebouncedClickListener {
                createBottomSheetDialog(fromMyPhoto)
            }

            atvRegion.setOnItemClickListener { _, _, position, _ ->
                regionId = listRegions!![position].id
                Timber.d(regionId)
            }

            atvBrand.setOnItemClickListener { _, _, position, _ ->
                carBrandId = listCarBrands!![position].id
                Timber.d(carBrandId.toString())

                // Set car models in car brand
                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.item_dropdown,
                    listCarModels!!.filter { carModel -> carModel.parentId == carBrandId }
                        .map { carModel -> carModel.name })
                binding.atvModel.setAdapter(adapter)
            }

            atvModel.setOnItemClickListener { _, _, position, _ ->
                carModelId = listCarModels!![position].id
                Timber.d(carModelId.toString())
            }

            atvColor.setOnItemClickListener { _, _, position, _ ->
                carColorId = listCarColors!![position].id
                Timber.d(carColorId.toString())
            }

            atvGender.setOnItemClickListener { _, _, position, _ ->
                genderTypeId = listGenderTypes!![position].id
                Timber.d(genderTypeId)
            }

            atvDriverLicense.setOnItemClickListener { _, _, position, _ ->
                driverLicenseTypeId = listDriverLicenseTypes!![position].name
                Timber.d(driverLicenseTypeId)
            }

            tieBirthday.setDebouncedClickListener {
                showDatePicker(tieBirthday, "date_picker_birthday", defaultYearsAgo = 25)
            }

            tiePassportGiveDate.setDebouncedClickListener {
                showDatePicker(tiePassportGiveDate, "date_picker_passport")
            }

            tieCarMade.setDebouncedClickListener {
                val dialog = Dialog(requireContext())
                val dialogBinding = DialogSelectYearBinding.inflate(layoutInflater)
                dialog.apply {
                    requestWindowFeature(Window.FEATURE_NO_TITLE)
                    setContentView(dialogBinding.root)
                    window?.setBackgroundDrawable(ColorDrawable(android.graphics.Color.TRANSPARENT))
                    setCanceledOnTouchOutside(false)
                }

                dialogBinding.apply {
                    npSelectYear.maxValue = Calendar.getInstance().get(Calendar.YEAR)
                    npSelectYear.minValue = 2010

                    // Pre-select the already-chosen year, otherwise open on the CURRENT (latest)
                    // year — not the min (2010), which is what an unset NumberPicker defaults to.
                    // NumberPicker clamps out-of-range values, so a stale value can't crash.
                    npSelectYear.value =
                        binding.tieCarMade.text.toString().toIntOrNull() ?: npSelectYear.maxValue

                    tvCancel.setOnClickListener {
                        dialog.dismiss()
                    }

                    tvSubmit.setOnClickListener {
                        binding.tieCarMade.setText(dialogBinding.npSelectYear.value.toString())
                        dialog.dismiss()
                    }
                }

                dialog.show()
            }

            clPhotoLicence.setDebouncedClickListener {
//                galleryPhotoLicenceResultListener.launch("image/*")
                createBottomSheetDialog(fromPhotoLicence)
            }

            ivCloseLicence.setOnClickListener {
                listPhotoDocuments.remove(photoLicence)
                photoLicence = null
                clearLicencePreview()
            }

            clPhotoCar.setDebouncedClickListener {
//                galleryPhotoCarResultListener.launch("image/*")
                createBottomSheetDialog(fromPhotoCar)
            }

            ivCloseCar.setOnClickListener {
                listPhotoDocuments.remove(photoCar)
                photoCar = null
                clearCarPreview()
            }

            tvSubmit.setDebouncedClickListener {
                val birthday = tieBirthday.text.toString()
                val address = tieAddress.text.toString()
                val passportNumber = tiePassportNumber.text.toString()
                val passportGiveBy = tiePassportGiveBy.text.toString()
                val passportGiveDate = tiePassportGiveDate.text.toString()
                val licenseNumber = tieLicenseNumber.text.toString()
                val carNumber = tieCarNumber.text.toString()
                val carMade = tieCarMade.text.toString()

                validate(
                    birthday,
                    address,
                    passportNumber,
                    passportGiveBy,
                    passportGiveDate,
                    licenseNumber,
                    carNumber,
                    carMade
                )
            }
        }
    }

    private fun getRegions() {
        lifecycleScope.launch {
            completeDriverInfoVM.getRegions().collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        listRegions = it.data?.data ?: emptyList()

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.item_dropdown,
                            listRegions!!.map { region -> region.name })
                        binding.atvRegion.setAdapter(adapter)
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }

    private fun getCarBrands() {
        lifecycleScope.launch {
            completeDriverInfoVM.getCarBrands().collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()
                        val data = it.data?.data ?: emptyList()

                        data.forEach { brand ->
                            if (brand.parentId == null) {
                                listCarBrands!!.add(brand)
                            }
                        }

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.item_dropdown,
                            listCarBrands!!.map { carBrand -> carBrand.name })
                        binding.atvBrand.setAdapter(adapter)
                    }

                    is Resource.Error -> {
                        errorVisible()
                    }
                }
            }
        }
    }

    private fun getCarModels() {
        lifecycleScope.launch {
            completeDriverInfoVM.getCarModels().collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        val data = it.data?.data ?: emptyList()

                        data.forEach { brand ->
                            if (brand.parentId != null) {
                                listCarModels!!.add(brand)
                            }
                        }
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }

    private fun getCarColors() {
        lifecycleScope.launch {
            completeDriverInfoVM.getCarColors().collect {
                when (it) {
                    is Resource.Loading -> {}

                    is Resource.Success -> {
                        listCarColors = it.data?.data ?: emptyList()

                        val adapter = ArrayAdapter(
                            requireContext(),
                            R.layout.item_dropdown,
                            listCarColors!!.map { region -> region.name })
                        binding.atvColor.setAdapter(adapter)
                    }

                    is Resource.Error -> {}
                }
            }
        }
    }

    private fun getGenderTypes() {
        lifecycleScope.launch {
            val male = getString(R.string.gender_male)
            val female = getString(R.string.gender_female)
            completeDriverInfoVM.getGenderTypes(male, female).collect {
                listGenderTypes = it

                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.item_dropdown,
                    listGenderTypes!!.map { gender -> gender.name })
                binding.atvGender.setAdapter(adapter)
            }
        }
    }

    private fun getDriverLicenseTypes() {
        lifecycleScope.launch {
            completeDriverInfoVM.getDriverLicenseTypes().collect {
                listDriverLicenseTypes = it

                val adapter = ArrayAdapter(
                    requireContext(),
                    R.layout.item_dropdown,
                    listDriverLicenseTypes!!.map { driverLicense -> driverLicense.name })
                binding.atvDriverLicense.setAdapter(adapter)
            }
        }
    }

    private fun createBottomSheetDialog(from: Int) {
        val bshdChooseCameraGallery = BottomSheetDialog(requireContext()).fitSystemBars()
        val bindingChooseCameraGallery =
            DialogBshChooseCameraGalleryBinding.inflate(LayoutInflater.from(requireContext()))

        bshdChooseCameraGallery.apply {
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
            setContentView(bindingChooseCameraGallery.root)
            window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        }

        bindingChooseCameraGallery.apply {
            when (from) {
                fromMyPhoto -> {
                    tvCamera.setDebouncedClickListener {
                        takeMyPhotoCamera()
                        bshdChooseCameraGallery.dismiss()
                    }

                    tvGallery.setDebouncedClickListener {
                        galleryMyPhotoResultListener.launch("image/*")
                        bshdChooseCameraGallery.dismiss()
                    }
                }

                fromPhotoLicence -> {
                    tvCamera.setDebouncedClickListener {
                        takePhotoLicenceCamera()
                        bshdChooseCameraGallery.dismiss()
                    }

                    tvGallery.setDebouncedClickListener {
                        galleryPhotoLicenceResultListener.launch("image/*")
                        bshdChooseCameraGallery.dismiss()
                    }
                }

                fromPhotoCar -> {
                    tvCamera.setDebouncedClickListener {
                        takePhotoCarCamera()
                        bshdChooseCameraGallery.dismiss()
                    }

                    tvGallery.setDebouncedClickListener {
                        galleryPhotoCarResultListener.launch("image/*")
                        bshdChooseCameraGallery.dismiss()
                    }
                }
            }
        }

        bshdChooseCameraGallery.show()
    }

    private fun validate(
        birthday: String,
        address: String,
        passportNumber: String,
        passportGiveBy: String,
        passportGiveDate: String,
        licenseNumber: String,
        carNumber: String,
        carMade: String
    ) {
        listPhotoDocuments.clear()
        if (photoLicence != null) {
            listPhotoDocuments.add(photoLicence!!)
        }
        if (photoCar != null) {
            listPhotoDocuments.add(photoCar!!)
        }

        binding.apply {
            if (myPhotoPath == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.please_take_photo_with_passport),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (listPhotoDocuments.size < 1) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.upload_images_of_licence),
                    Toast.LENGTH_SHORT
                ).show()
            } else if (regionId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.select_city),
                    Toast.LENGTH_SHORT
                ).show()
                atvRegion.requestFocus()
                atvRegion.error = getString(R.string.do_not_empty)
            } else if (genderTypeId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.select_gender),
                    Toast.LENGTH_SHORT
                ).show()
                atvGender.requestFocus()
                atvGender.error = getString(R.string.do_not_empty)
            } else if (birthday.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.select_birthday),
                    Toast.LENGTH_SHORT
                ).show()
                tieBirthday.error = ""
            } else if (address.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_address),
                    Toast.LENGTH_SHORT
                ).show()
                tieAddress.requestFocus()
                tieAddress.error = getString(R.string.do_not_empty)
            } else if (passportNumber.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_passport_series),
                    Toast.LENGTH_SHORT
                ).show()
                tiePassportNumber.requestFocus()
                tiePassportNumber.error = getString(R.string.do_not_empty)
            } else if (passportGiveBy.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_passport_given_by),
                    Toast.LENGTH_SHORT
                ).show()
                tiePassportGiveBy.requestFocus()
                tiePassportGiveBy.error = ""
            } else if (passportGiveDate.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_passport_given_when),
                    Toast.LENGTH_SHORT
                ).show()
                tiePassportGiveDate.error = ""
            } else if (licenseNumber.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_driver_licence_given_by),
                    Toast.LENGTH_SHORT
                ).show()
                tieLicenseNumber.requestFocus()
                tieLicenseNumber.error = getString(R.string.do_not_empty)
            } else if (driverLicenseTypeId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.select_driver_licence_type),
                    Toast.LENGTH_SHORT
                ).show()
                atvDriverLicense.requestFocus()
                atvDriverLicense.error = getString(R.string.do_not_empty)
            } else if (carBrandId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.choose_car_brand),
                    Toast.LENGTH_SHORT
                ).show()
                atvBrand.requestFocus()
                atvBrand.error = getString(R.string.do_not_empty)
            } else if (carModelId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.choose_car_model),
                    Toast.LENGTH_SHORT
                ).show()
                atvModel.requestFocus()
                atvModel.error = getString(R.string.do_not_empty)
            } else if (carColorId == null) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.choose_car_color),
                    Toast.LENGTH_SHORT
                ).show()
                atvColor.requestFocus()
                atvColor.error = getString(R.string.do_not_empty)
            } else if (carNumber.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.input_car_number),
                    Toast.LENGTH_SHORT
                ).show()
                tieCarNumber.requestFocus()
                tieCarNumber.error = getString(R.string.do_not_empty)
            } else if (carMade.isEmpty()) {
                Toast.makeText(
                    requireContext(),
                    getString(R.string.when_made_car),
                    Toast.LENGTH_SHORT
                ).show()
                tieCarMade.error = ""
            } else {
                convert(
                    birthday,
                    address,
                    passportNumber,
                    passportGiveBy,
                    passportGiveDate,
                    licenseNumber,
                    carNumber,
                    carMade
                )
            }
        }
    }

    private fun convert(
        birthday: String,
        address: String,
        passportNumber: String,
        passportGiveBy: String,
        passportGiveDate: String,
        licenseNumber: String,
        carNumber: String,
        carMade: String
    ) {
        // Convert text to RequestBody
        val authKeyRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), authKey!!)
        val specialityIdRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), specialityId!!)
        val regionIdRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), regionId!!)
        val genderTypeIdRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), genderTypeId!!)
        val birthdayRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), birthday)
        val addressRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), address)
        val passportNumberRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), passportNumber)
        val passportGiveByRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), passportGiveBy)
        val passportGiveDateRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), passportGiveDate)
        val licenseNumberRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), licenseNumber)
        val licenseTypeIdRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), driverLicenseTypeId!!)
        val carModelIdRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), carModelId.toString())
        val carColorIdRB =
            RequestBody.create("multipart/form-data".toMediaTypeOrNull(), carColorId.toString())
        val carNumberRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), carNumber)
        val carMadeRB = RequestBody.create("multipart/form-data".toMediaTypeOrNull(), carMade)

        // Convert file to MultipartBody
        lifecycleScope.launch {
            val myPhotoF = File(myPhotoPath!!)
            val myPhotoCompressedF = Compressor.compress(requireContext(), myPhotoF)
            val myPhotoRb =
                RequestBody.create("multipart/form-data".toMediaTypeOrNull(), myPhotoCompressedF)
            val myPhotoMB = MultipartBody.Part.createFormData("photo", myPhotoF.name, myPhotoRb)

            // Photos
            val photoDocumentsMb = ArrayList<MultipartBody.Part>()
            listPhotoDocuments.forEach { photoDocument ->
                val photoDocumentF = File(photoDocument)
                val photoDocumentCompressedF = Compressor.compress(requireContext(), photoDocumentF)
                val photoDocumentRb = RequestBody.create(
                    "multipart/form-data".toMediaTypeOrNull(),
                    photoDocumentCompressedF
                )
                val photoDocumentMB = MultipartBody.Part.createFormData(
                    "pics[]",
                    photoDocumentF.name,
                    photoDocumentRb
                )

                photoDocumentsMb.add(photoDocumentMB)
            }

            upload(
                authKeyRB,
                specialityIdRB,
                regionIdRB,
                genderTypeIdRB,
                birthdayRB,
                addressRB,
                passportNumberRB,
                passportGiveByRB,
                passportGiveDateRB,
                licenseNumberRB,
                licenseTypeIdRB,
                carModelIdRB,
                carColorIdRB,
                carNumberRB,
                carMadeRB,
                myPhotoMB,
                photoDocumentsMb
            )
        }
    }

    private fun upload(
        authKey: RequestBody,
        specialityId: RequestBody,
        regionId: RequestBody,
        genderTypeId: RequestBody,
        birthday: RequestBody,
        address: RequestBody,
        passportNumber: RequestBody,
        passportGiveBy: RequestBody,
        passportGiveDate: RequestBody,
        licenseNumber: RequestBody,
        licenseTypeId: RequestBody,
        carModelId: RequestBody,
        carColorId: RequestBody,
        carNumber: RequestBody,
        carMade: RequestBody,
        myPhoto: MultipartBody.Part,
        photos: List<MultipartBody.Part>
    ) {
        lifecycleScope.launch {
            completeDriverInfoVM.uploadDriverInfo(
                authKey,
                specialityId,
                regionId,
                genderTypeId,
                birthday,
                address,
                passportNumber,
                passportGiveBy,
                passportGiveDate,
                licenseNumber,
                licenseTypeId,
                carModelId,
                carColorId,
                carNumber,
                carMade,
                myPhoto,
                photos
            ).collect {
                when (it) {
                    is Resource.Loading -> {
                        loadingVisible()
                    }

                    is Resource.Success -> {
                        screenVisible()

                        UserManager.saveUser(it.data?.data!!)
                        MetaEvents.logCompletedRegistration(requireContext())
                        clearObjects()
                        findNavController().navigate(CompleteDriverInfoFragmentDirections.actionCompleteDriverInfoFragmentToNavigationHome())
                    }

                    is Resource.Error -> {
                        errorVisible()
                        showToast(it.message!!)
                    }
                }
            }
        }
    }

    override fun onDestroyView() {
        super.onDestroyView()
        _binding = null
    }

    // MyPhoto
    private fun takeMyPhotoCamera() {
        val file = try {
            createImageMyPhotoFile()
        } catch (e: Exception) {
            null
        }

        val uriForFile = file?.let {
            FileProvider.getUriForFile(requireContext(), APPLICATION_ID, it)
        }

        cameraMyPhotoResultListener.launch(uriForFile)
    }

    private fun createImageMyPhotoFile(): File {
        val externalFilesDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("${System.currentTimeMillis()}", ".jpg", externalFilesDir)
            .apply {
                // "myPhotoPath" was got from camera
                myPhotoPath = absolutePath
                Timber.d("From camera: $myPhotoPath")
            }
    }

    private val cameraMyPhotoResultListener =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && myPhotoPath != null) {
                binding.civMyPhoto.setImageURI(Uri.fromFile(File(myPhotoPath!!)))
            } else {
                myPhotoPath = null
                binding.civMyPhoto.setImageResource(R.drawable.avatar_placeholder)
            }
        }

    private val galleryMyPhotoResultListener =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            binding.civMyPhoto.setImageURI(it)
            val openInputStream = requireActivity().contentResolver.openInputStream(it)
            val file = File(requireActivity().filesDir, "${System.currentTimeMillis()}.jpg")
            val fileOutputStream = FileOutputStream(file)
            openInputStream?.copyTo(fileOutputStream)
            openInputStream?.close()

            // "myPhotoPath" was got from gallery
            myPhotoPath = file.absolutePath
            Timber.d("From gallery: $myPhotoPath")
        }

    // Licence photo
    private fun takePhotoLicenceCamera() {
        val file = try {
            createImagePhotoLicenceFile()
        } catch (e: Exception) {
            null
        }

        val uriForFile = file?.let {
            FileProvider.getUriForFile(requireContext(), APPLICATION_ID, it)
        }

        cameraPhotoLicenceResultListener.launch(uriForFile)
    }

    private fun createImagePhotoLicenceFile(): File {
        val externalFilesDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("${System.currentTimeMillis()}", ".jpg", externalFilesDir)
            .apply {
                photoLicence = absolutePath
            }
    }

    private val cameraPhotoLicenceResultListener =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && photoLicence != null) {
                showLicencePreview()
            } else {
                // Remove the stale path BEFORE nulling, or remove(null) is a no-op.
                listPhotoDocuments.remove(photoLicence)
                photoLicence = null
                clearLicencePreview()
            }
        }

    private val galleryPhotoLicenceResultListener =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            val openInputStream = requireActivity().contentResolver.openInputStream(it)
            val file = File(requireActivity().filesDir, "${System.currentTimeMillis()}.jpg")
            val fileOutputStream = FileOutputStream(file)
            openInputStream?.copyTo(fileOutputStream)
            openInputStream?.close()

            photoLicence = file.absolutePath
            showLicencePreview()
        }

    // Car photo
    private fun takePhotoCarCamera() {
        val file = try {
            createImagePhotoCarFile()
        } catch (e: Exception) {
            null
        }

        val uriForFile = file?.let {
            FileProvider.getUriForFile(requireContext(), APPLICATION_ID, it)
        }

        cameraPhotoCarResultListener.launch(uriForFile)
    }

    private fun createImagePhotoCarFile(): File {
        val externalFilesDir = requireActivity().getExternalFilesDir(Environment.DIRECTORY_PICTURES)
        return File.createTempFile("${System.currentTimeMillis()}", ".jpg", externalFilesDir)
            .apply {
                photoCar = absolutePath
            }
    }

    private val cameraPhotoCarResultListener =
        registerForActivityResult(ActivityResultContracts.TakePicture()) { isSuccess ->
            if (isSuccess && photoCar != null) {
                showCarPreview()
            } else {
                // Remove the stale path BEFORE nulling, or remove(null) is a no-op.
                listPhotoDocuments.remove(photoCar)
                photoCar = null
                clearCarPreview()
            }
        }

    private val galleryPhotoCarResultListener =
        registerForActivityResult(ActivityResultContracts.GetContent()) {
            if (it == null) return@registerForActivityResult
            val openInputStream = requireActivity().contentResolver.openInputStream(it)
            val file = File(requireActivity().filesDir, "${System.currentTimeMillis()}.jpg")
            val fileOutputStream = FileOutputStream(file)
            openInputStream?.copyTo(fileOutputStream)
            openInputStream?.close()

            photoCar = file.absolutePath
            showCarPreview()
        }

    /**
     * Licence/car photo tiles (task 11): picked → full-bleed thumbnail with a scrim label
     * and a removable X badge; empty → icon + label + "choose photo" hint, so the state
     * is unmistakable either way.
     */
    private fun showLicencePreview() {
        val path = photoLicence ?: return
        binding.apply {
            ivPreviewLicence.setImageURI(Uri.fromFile(File(path)))
            cvPreviewLicence.visibility = View.VISIBLE
            llEmptyLicence.visibility = View.GONE
            ivCloseLicence.visibility = View.VISIBLE
        }
    }

    private fun clearLicencePreview() {
        binding.apply {
            ivPreviewLicence.setImageDrawable(null)
            cvPreviewLicence.visibility = View.GONE
            llEmptyLicence.visibility = View.VISIBLE
            ivCloseLicence.visibility = View.GONE
        }
    }

    private fun showCarPreview() {
        val path = photoCar ?: return
        binding.apply {
            ivPreviewCar.setImageURI(Uri.fromFile(File(path)))
            cvPreviewCar.visibility = View.VISIBLE
            llEmptyCar.visibility = View.GONE
            ivCloseCar.visibility = View.VISIBLE
        }
    }

    private fun clearCarPreview() {
        binding.apply {
            ivPreviewCar.setImageDrawable(null)
            cvPreviewCar.visibility = View.GONE
            llEmptyCar.visibility = View.VISIBLE
            ivCloseCar.visibility = View.GONE
        }
    }

    // Others
    private fun loadingVisible() {
        binding.apply {
            tvSubmit.text = ""
            tvSubmit.isClickable = false
            pbSubmit.visibility = View.VISIBLE
        }
    }

    private fun screenVisible() {
        binding.apply {
            tvSubmit.setText(R.string.submit)
            pbSubmit.visibility = View.GONE
            tvSubmit.isClickable = true
        }
    }

    private fun errorVisible() {
        binding.apply {
            tvSubmit.setText(R.string.submit)
            pbSubmit.visibility = View.GONE
            tvSubmit.isClickable = true
        }
    }

    /**
     * Keep the submit button disabled until the required photos are uploaded — the
     * passport selfie plus at least one document (licence/car) — so the application
     * can't be sent before the documents are attached.
     */
    private fun refreshSubmitEnabled() {
        val b = _binding ?: return
        b.tvSubmit.isEnabled = myPhotoPath != null && (photoLicence != null || photoCar != null)
    }

    private fun clearObjects() {
        authKey = null
        specialityId = null

        myPhotoPath = null

        listRegions = null
        regionId = null

        listCarBrands = null
        carBrandId = null

        listCarModels = null
        carModelId = null

        listCarColors = null
        carColorId = null

        listGenderTypes = null
        genderTypeId = null

        listDriverLicenseTypes = null
        driverLicenseTypeId = null
    }
}