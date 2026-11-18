package ar.edu.uade.go2rondas.di

import android.content.Context
import androidx.room.Room
import ar.edu.uade.go2rondas.data.local.AppDatabase
import ar.edu.uade.go2rondas.data.local.MIGRATION_1_2
import ar.edu.uade.go2rondas.data.local.MIGRATION_2_3
import ar.edu.uade.go2rondas.data.local.dao.CheckpointResultDao
import ar.edu.uade.go2rondas.data.local.dao.EventDao
import ar.edu.uade.go2rondas.data.local.dao.MissionDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    // Una sola instancia para toda la app, con el contexto de la aplicación (no el de una Activity).
    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, "go2-rondas.db")
            .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
            .build()

    @Provides
    fun provideMissionDao(db: AppDatabase): MissionDao = db.missionDao()

    @Provides
    fun provideCheckpointResultDao(db: AppDatabase): CheckpointResultDao = db.checkpointResultDao()

    @Provides
    fun provideEventDao(db: AppDatabase): EventDao = db.eventDao()
}
