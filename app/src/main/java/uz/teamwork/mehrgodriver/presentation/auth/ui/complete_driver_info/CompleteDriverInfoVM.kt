package uz.teamwork.mehrgodriver.presentation.auth.ui.complete_driver_info

import androidx.lifecycle.ViewModel
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import okhttp3.MultipartBody
import okhttp3.RequestBody
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.common.model.DriverLicense
import uz.teamwork.mehrgodriver.common.model.Gender
import uz.teamwork.mehrgodriver.domain.model.CarBrand
import uz.teamwork.mehrgodriver.domain.model.CarColor
import uz.teamwork.mehrgodriver.domain.model.CarModel
import uz.teamwork.mehrgodriver.domain.model.Region
import uz.teamwork.mehrgodriver.domain.model.User
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.use_case.auth.CarBrandsUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.CarColorsUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.CarModelsUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.RegionsUC
import uz.teamwork.mehrgodriver.domain.use_case.auth.UploadDriverInfoUC
import javax.inject.Inject

@HiltViewModel
class CompleteDriverInfoVM @Inject constructor(
    private val regionsUC: RegionsUC,
    private val carBrandsUC: CarBrandsUC,
    private val carModelsUC: CarModelsUC,
    private val carColorsUC: CarColorsUC,
    private val uploadDriverInfoUC: UploadDriverInfoUC
) : ViewModel() {

    fun getRegions(): Flow<Resource<BaseResponse<List<Region>>>> {
        return regionsUC.invoke()
    }

    fun getCarColors(): Flow<Resource<BaseResponse<List<CarColor>>>> {
        return carColorsUC.invoke()
    }

    fun getCarBrands(): Flow<Resource<BaseResponse<List<CarBrand>>>> {
        return carBrandsUC.invoke()
    }

    fun getCarModels(): Flow<Resource<BaseResponse<List<CarModel>>>> {
        return carModelsUC.invoke()
    }

    /**
     * Gender labels are read from string resources at the call site (Fragment) so the
     * dropdown shows values in the currently selected interface language. Only the
     * IDs are stable across locales — those are what the backend expects.
     */
    fun getGenderTypes(maleLabel: String, femaleLabel: String): Flow<List<Gender>> {
        return flow {
            emit(listOf(Gender("6", maleLabel), Gender("8", femaleLabel)))
        }
    }

    fun getDriverLicenseTypes(): Flow<List<DriverLicense>> {
        return flow {
            emit(
                listOf(
                    DriverLicense("1", "A"),
                    DriverLicense("2", "B"),
                    DriverLicense("3", "C"),
                    DriverLicense("4", "D"),
                    DriverLicense("5", "E"),
                    DriverLicense("6", "BC"),
                    DriverLicense("7", "BCD")
                )
            )
        }
    }

    fun uploadDriverInfo(
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
        photo: MultipartBody.Part,
        photos: List<MultipartBody.Part>
    ): Flow<Resource<BaseResponse<User>>> {
        return uploadDriverInfoUC.invoke(
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
            photo,
            photos
        )
    }
}