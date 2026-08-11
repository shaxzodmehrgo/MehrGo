package uz.teamwork.mehrgodriver.data.di

import android.content.Context
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import com.google.gson.Gson
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.common.fare.GpsBatchSocketChannel
import uz.teamwork.mehrgodriver.common.fare.GpsBatchUploader
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import uz.teamwork.mehrgodriver.domain.repository.locale.PendingGpsPointsRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
class CommonModule {

    // Single lenient Gson shared by the Retrofit converters, the use-case error-body parser and
    // GPS-batch serialisation. See common/AppGson.kt: leaf numeric fields must not throw on
    // ""/null/wrong-type payloads (crash A + crash B).
    @Provides
    @Singleton
    fun getGson(): Gson = uz.teamwork.mehrgodriver.common.AppGson.gson

    @Provides
    @Singleton
    fun getFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(context)

    @Provides
    @Singleton
    fun provideGpsBatchSocketChannel(gson: Gson): GpsBatchSocketChannel =
        GpsBatchSocketChannel(gson)

    @Provides
    @Singleton
    fun provideGpsBatchUploader(
        repository: MainRepository,
        pendingRepo: PendingGpsPointsRepository,
        socketChannel: GpsBatchSocketChannel
    ): GpsBatchUploader = GpsBatchUploader(repository, pendingRepo, socketChannel)
}

