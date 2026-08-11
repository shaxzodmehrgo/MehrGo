package uz.teamwork.mehrgodriver.domain.repository

import uz.teamwork.mehrgodriver.domain.model.DirectionLocations

interface RouteRepository {
    suspend fun getLocations(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): DirectionLocations

    suspend fun getDistance(
        lon1: Double,
        lat1: Double,
        lon2: Double,
        lat2: Double
    ): DirectionLocations
}