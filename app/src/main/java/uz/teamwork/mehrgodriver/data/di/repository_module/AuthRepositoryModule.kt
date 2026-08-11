package uz.teamwork.mehrgodriver.data.di.repository_module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.data.repository.AuthRepositoryImpl
import uz.teamwork.mehrgodriver.domain.repository.AuthRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface AuthRepositoryModule {

    @Binds
    @Singleton
    fun bind(impl: AuthRepositoryImpl): AuthRepository
}