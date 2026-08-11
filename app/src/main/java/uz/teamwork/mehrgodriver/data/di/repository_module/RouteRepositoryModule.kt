package uz.teamwork.mehrgodriver.data.di.repository_module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.data.repository.RouteRepositoryImpl
import uz.teamwork.mehrgodriver.domain.repository.RouteRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface RouteRepositoryModule {

    @Binds
    @Singleton
    fun bind(impl: RouteRepositoryImpl): RouteRepository
}