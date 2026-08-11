package uz.teamwork.mehrgodriver.data.di.repository_module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.data.repository.MainRepositoryImpl
import uz.teamwork.mehrgodriver.domain.repository.MainRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface MainRepositoryModule {

    @Binds
    @Singleton
    fun bind(impl: MainRepositoryImpl): MainRepository
}