package uz.teamwork.mehrgodriver.domain.use_case.route

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import uz.teamwork.mehrgodriver.domain.repository.RouteRepository
import java.io.IOException
import javax.inject.Inject

class RouteUC @Inject constructor(private val repository: RouteRepository) {
    operator fun invoke(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): Flow<Resource<DirectionLocations>> = flow {
        emit(Resource.Loading)

        try {
            val response = repository.getLocations(lon1, lat1, lon2, lat2)
            emit(Resource.Success(response))
        } catch (e: HttpException) {
            emit(Resource.Error(Helper.getUnexpectedError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}