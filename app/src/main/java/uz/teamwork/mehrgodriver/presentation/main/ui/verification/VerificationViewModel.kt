package uz.teamwork.mehrgodriver.presentation.main.ui.verification

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import okhttp3.MultipartBody
import uz.teamwork.mehrgodriver.domain.use_case.main.UploadLicenseUC
import uz.teamwork.mehrgodriver.domain.use_case.main.VerificationStatusUC
import javax.inject.Inject

@HiltViewModel
class VerificationViewModel @Inject constructor(
    private val verificationStatusUC: VerificationStatusUC,
    private val uploadLicenseUC: UploadLicenseUC
) : ViewModel() {

    fun getStatus() = verificationStatusUC()

    fun uploadLicense(license: MultipartBody.Part) = uploadLicenseUC(license)
}
