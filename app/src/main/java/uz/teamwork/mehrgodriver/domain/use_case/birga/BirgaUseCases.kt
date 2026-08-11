package uz.teamwork.mehrgodriver.domain.use_case.birga

import com.google.gson.Gson
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import retrofit2.HttpException
import uz.teamwork.mehrgodriver.common.Helper
import uz.teamwork.mehrgodriver.common.Resource
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaCompleteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaError
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaLoginResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOnlineResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaOtpResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaPickupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRouteResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaRoutesResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSignupResponse
import uz.teamwork.mehrgodriver.domain.model.birga.BirgaSimpleResponse
import uz.teamwork.mehrgodriver.domain.repository.BirgaRepository
import java.io.IOException
import javax.inject.Inject

/**
 * Use-cases водителя Birga. Тот же паттерн, что и у остальных UC (LoginUC и т.п.):
 * Flow<Resource<T>> с воронкой HttpException/IOException. Отличие только в разборе ошибки —
 * бэкенд Birga отдаёт плоский {"ok":false,"error":"..."}, поэтому парсим BirgaError.error
 * (а не ErrorResponse.message). Emit — ПОСЛЕ парсинга (exception-transparency, как в LoginUC).
 */

internal fun HttpException.birgaError(gson: Gson): String? = try {
    gson.fromJson(response()?.errorBody()?.string(), BirgaError::class.java)?.error
} catch (parse: Exception) {
    null
}

class BirgaRequestOtpUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(phone: String): Flow<Resource<BirgaOtpResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.requestOtp(phone)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaLoginUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(
        phone: String,
        code: String,
        name: String? = null,
        vehicleClass: String? = null,
        capacity: Int? = null,
        plate: String? = null,
        carModel: String? = null,
        carColor: String? = null
    ): Flow<Resource<BirgaLoginResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.login(phone, code, name, vehicleClass, capacity, plate, carModel, carColor)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaSetOnlineUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(online: Boolean, lat: Double? = null, lon: Double? = null): Flow<Resource<BirgaOnlineResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.setOnline(online, lat, lon)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaRoutesUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(): Flow<Resource<BirgaRoutesResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.getRoutes()))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaRouteViewUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int): Flow<Resource<BirgaRouteResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.getRoute(id)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaSignupUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int): Flow<Resource<BirgaSignupResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.signup(id)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaAcceptUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int): Flow<Resource<BirgaRouteResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.accept(id)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaStartUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int): Flow<Resource<BirgaRouteResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.start(id)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaPickupUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int, childId: Int? = null, seq: Int? = null): Flow<Resource<BirgaPickupResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.pickup(id, childId, seq)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaCompleteUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int): Flow<Resource<BirgaCompleteResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.complete(id)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}

class BirgaTrackUC @Inject constructor(
    private val repo: BirgaRepository,
    private val gson: Gson
) {
    operator fun invoke(id: Int, lat: Double, lon: Double): Flow<Resource<BirgaSimpleResponse>> = flow {
        emit(Resource.Loading)
        try {
            emit(Resource.Success(repo.track(id, lat, lon)))
        } catch (e: HttpException) {
            val m = e.birgaError(gson); emit(Resource.Error(m ?: Helper.getServerError()))
        } catch (e: IOException) {
            emit(Resource.Error(Helper.getConnectionError()))
        }
    }
}
