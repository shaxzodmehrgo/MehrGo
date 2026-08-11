package uz.teamwork.mehrgodriver.data.di.repository_module

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import uz.teamwork.mehrgodriver.data.repository.BirgaRepositoryImpl
import uz.teamwork.mehrgodriver.domain.repository.BirgaRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
interface BirgaRepositoryModule {

    @Binds
    @Singleton
    fun bind(impl: BirgaRepositoryImpl): BirgaRepository
}
