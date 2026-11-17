package ar.edu.uade.go2rondas.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import ar.edu.uade.go2rondas.data.local.dao.CheckpointResultDao
import ar.edu.uade.go2rondas.data.local.dao.EventDao
import ar.edu.uade.go2rondas.data.local.dao.MissionDao
import ar.edu.uade.go2rondas.data.local.entity.CheckpointResultEntity
import ar.edu.uade.go2rondas.data.local.entity.EventEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionEntity

@Database(
    entities = [MissionEntity::class, CheckpointResultEntity::class, EventEntity::class],
    version = 2,
    exportSchema = true
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun missionDao(): MissionDao
    abstract fun checkpointResultDao(): CheckpointResultDao
    abstract fun eventDao(): EventDao
}
