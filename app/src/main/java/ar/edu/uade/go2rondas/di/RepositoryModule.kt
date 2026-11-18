package ar.edu.uade.go2rondas.di

import ar.edu.uade.go2rondas.data.repository.HistoryRepositoryImpl
import ar.edu.uade.go2rondas.data.repository.PhotoRepositoryImpl
import ar.edu.uade.go2rondas.data.repository.RobotRepositoryImpl
import ar.edu.uade.go2rondas.data.repository.SimulationRepositoryImpl
import ar.edu.uade.go2rondas.domain.repository.HistoryRepository
import ar.edu.uade.go2rondas.domain.repository.PhotoRepository
import ar.edu.uade.go2rondas.domain.repository.RobotRepository
import ar.edu.uade.go2rondas.domain.repository.SimulationRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindRobotRepository(impl: RobotRepositoryImpl): RobotRepository

    @Binds
    @Singleton
    abstract fun bindSimulationRepository(impl: SimulationRepositoryImpl): SimulationRepository

    @Binds
    @Singleton
    abstract fun bindHistoryRepository(impl: HistoryRepositoryImpl): HistoryRepository

    @Binds
    @Singleton
    abstract fun bindPhotoRepository(impl: PhotoRepositoryImpl): PhotoRepository
}
