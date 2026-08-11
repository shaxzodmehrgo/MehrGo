package uz.teamwork.mehrgodriver.data.remote

import retrofit2.Call
import retrofit2.http.GET
import retrofit2.http.Path
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations

interface RouteApiService {
    @GET("route/v1/driving/{lon1},{lat1};{lon2},{lat2}?steps=true")
    suspend fun getLocations(
        @Path("lon1") lon1: Double,
        @Path("lat1") lat1: Double,
        @Path("lon2") lon2: Double,
        @Path("lat2") lat2: Double,
    ): DirectionLocations

    @GET("route/v1/driving/{lon1},{lat1};{lon2},{lat2}")
    fun getDistance(
        @Path("lon1") lon1: Double,
        @Path("lat1") lat1: Double,
        @Path("lon2") lon2: Double,
        @Path("lat2") lat2: Double,
    ): Call<DirectionLocations>
}