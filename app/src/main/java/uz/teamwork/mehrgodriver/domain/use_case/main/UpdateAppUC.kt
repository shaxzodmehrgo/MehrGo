package uz.teamwork.mehrgodriver.domain.use_case.main

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.UpdateApp
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import java.io.IOException
import javax.inject.Inject

class UpdateAppUC @Inject constructor(private val repository: MainRepository) {
    operator fun invoke(token: String?, deviceToken: String? = null): Flow<Resource<UpdateApp>> =
        flow {
            emit(Resource.Loading)

            try {
                val response = repository.updateApp(token, deviceToken)
                emit(Resource.Success(response))
            } catch (e: HttpException) {
                emit(Resource.Error(Helper.getUnexpectedError()))
            } catch (e: IOException) {
                emit(Resource.Error(Helper.getConnectionError()))
            }
        }
}