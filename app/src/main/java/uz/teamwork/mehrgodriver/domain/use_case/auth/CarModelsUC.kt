package uz.teamwork.mehrgodriver.domain.use_case.auth

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.CarModel
import uz.teamwork.mehrgodriver.domain.model.base.BaseResponse
import uz.teamwork.mehrgodriver.domain.repository.AuthRepository
import java.io.IOException
import javax.inject.Inject

class CarModelsUC @Inject constructor(private val repository: AuthRepository) {

    operator fun invoke(): Flow<Resource<BaseResponse<List<CarModel>>>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.getCarModels()
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            emit(Resource.Error(Helper.getUnexpectedError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}