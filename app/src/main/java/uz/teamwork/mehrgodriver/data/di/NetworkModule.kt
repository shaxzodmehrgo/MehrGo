package uz.teamwork.mehrgodriver.data.di

import android.content.Context
import com.chuckerteam.chucker.api.ChuckerInterceptor
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import uz.teamwork.mehrgodriver.common.Constants
import uz.teamwork.mehrgodriver.common.HeaderInterceptor
import uz.teamwork.mehrgodriver.common.httpLogLevel
import uz.teamwork.mehrgodriver.data.remote.ApiService
import uz.teamwork.mehrgodriver.data.remote.BirgaApiService
import uz.teamwork.mehrgodriver.data.remote.RouteApiService
import java.util.concurrent.TimeUnit
import javax.inject.Named
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class NetworkModule {

    @Provides
    @Singleton
    fun getChuckInterceptor(@ApplicationContext context: Context): ChuckerInterceptor =
        ChuckerInterceptor.Builder(context).build()

    @Provides
    @Singleton
    fun getHeaderInterceptor(): HeaderInterceptor = HeaderInterceptor()

    // Logcat HTTP tracker — full request & response (URL, headers, body, status, timing) under
    // tag "OkHttp", DEBUG BUILDS ONLY. This client carries HeaderInterceptor's
    // `Authorization: Bearer <token>`, and the auth calls carry the password and the OTP code, so
    // a release build logging at BODY level hands all three to anyone who can read logcat.
    // Level comes from httpLogLevel() — see common/HttpLogging.kt for why it is centralised.
    // Pair with Chucker for an in-app inspector that doesn't need adb.
    @Provides
    @Singleton
    fun getHttpLoggingInterceptor(): HttpLoggingInterceptor =
        HttpLoggingInterceptor().apply { level = httpLogLevel() }

    // Add Interceptors
    @Provides
    @Singleton
    fun getOkHTTPClient(
        headerInterceptor: HeaderInterceptor,
        chuckInterceptor: ChuckerInterceptor,
        httpLoggingInterceptor: HttpLoggingInterceptor,
    ) = OkHttpClient.Builder()
        .addInterceptor(chuckInterceptor)
        .addInterceptor(headerInterceptor)
        .addInterceptor(httpLoggingInterceptor)
        .connectTimeout(20, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .readTimeout(40, TimeUnit.SECONDS)
        .build()

    // Main ApiService
    @Provides
    @Singleton
    fun retrofit(okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()

    @Provides
    @Singleton
    fun provideApiService(retrofit: Retrofit): ApiService = retrofit.create(ApiService::class.java)

    // Birga driver API — ОТДЕЛЬНЫЙ Retrofit на Constants.MAKTABGO_BASE_URL (https://maktabgo.uz/api/),
    // тот же OkHttp с HeaderInterceptor (Bearer из UserManager). Не трогает таксишный BASE_URL.
    @Named("retrofit_birga")
    @Provides
    @Singleton
    fun retrofitBirga(okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(Constants.MAKTABGO_BASE_URL)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()

    @Provides
    @Singleton
    fun provideBirgaApiService(@Named("retrofit_birga") retrofit: Retrofit): BirgaApiService =
        retrofit.create(BirgaApiService::class.java)

    // Route ApiService
    @Named("retrofit_route")
    @Provides
    @Singleton
    fun retrofitRoute(okHttpClient: OkHttpClient, gson: Gson): Retrofit = Retrofit.Builder()
        .baseUrl(Constants.BASE_URL_ROUTE)
        .addConverterFactory(GsonConverterFactory.create(gson))
        .client(okHttpClient)
        .build()

    @Named("provide_route_api")
    @Provides
    @Singleton
    fun provideDirectionApiService(@Named("retrofit_route") retrofit: Retrofit): RouteApiService =
        retrofit.create(RouteApiService::class.java)
}

