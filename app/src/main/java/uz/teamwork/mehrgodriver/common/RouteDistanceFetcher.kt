package uz.teamwork.mehrgodriver.common

import com.google.android.gms.maps.model.LatLng
import retrofit2.Call
import retrofit2.Callback
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uz.teamwork.mehrgodriver.common.Constants.BASE_URL_ROUTE
import uz.teamwork.mehrgodriver.data.remote.RouteApiService
import uz.teamwork.mehrgodriver.domain.model.DirectionLocations

/**
 * Shared driver→point ROAD-distance fetcher over the route server.
 *
 * The order LIST rows show the road distance (route API); screens that previewed the
 * same order with a straight-line estimate (the offer sheet's "to client" header)
 * disagreed with the list — e.g. 22.2 km on the card vs 18.2 km on the sheet. Use this
 * so every surface shows the SAME road number.
 */
object RouteDistanceFetcher {
    private val api: RouteApiService by lazy {
        Retrofit.Builder()
            .baseUrl(BASE_URL_ROUTE)
            .addConverterFactory(GsonConverterFactory.create(AppGson.gson))
            .build()
            .create(RouteApiService::class.java)
    }

    /** Road metres from [from] to [to]; [onResult] runs on the main thread with the metres,
     *  or null when the route server has no route / the call fails (caller keeps its fallback). */
    fun fetch(from: LatLng, to: LatLng, onResult: (Int?) -> Unit) {
        api.getDistance(from.longitude, from.latitude, to.longitude, to.latitude)
            .enqueue(object : Callback<DirectionLocations> {
                override fun onResponse(
                    call: Call<DirectionLocations>,
                    response: Response<DirectionLocations>
                ) {
                    onResult(response.body()?.routes?.firstOrNull()?.distance?.toInt())
                }

                override fun onFailure(call: Call<DirectionLocations>, t: Throwable) {
                    onResult(null)
                }
            })
    }
}
