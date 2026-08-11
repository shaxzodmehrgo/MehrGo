package uz.teamwork.mehrgodriver.data.repository

import uz.teamwork.mehrgodriver.data.remote.RouteApiService
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations
import uz.teamwork.mehrgodriver.domain.repository.RouteRepository
import javax.inject.Inject
import javax.inject.Named

class RouteRepositoryImpl @Inject constructor(@Named("provide_route_api") private val routeApiService: RouteApiService) :
    RouteRepository {
    override suspend fun getLocations(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): DirectionLocations {
        return routeApiService.getLocations(lon1, lat1, lon2, lat2)
    }

    override suspend fun getDistance(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): DirectionLocations {
        return routeApiService.getLocations(lon1, lat1, lon2, lat2)
    }
}