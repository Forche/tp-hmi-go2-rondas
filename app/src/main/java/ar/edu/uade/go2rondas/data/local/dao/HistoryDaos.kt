package ar.edu.uade.go2rondas.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import ar.edu.uade.go2rondas.data.local.entity.CheckpointResultEntity
import ar.edu.uade.go2rondas.data.local.entity.EventEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionEntity
import ar.edu.uade.go2rondas.data.local.entity.MissionSummaryRow
import kotlinx.coroutines.flow.Flow

private const val MISSION_SUMMARY = """
    SELECT m.*,
        (SELECT COUNT(*) FROM checkpoint_results c WHERE c.missionId = m.id AND c.status IN ('OK', 'ANOMALY')) AS inspected,
        (SELECT COUNT(*) FROM checkpoint_results c WHERE c.missionId = m.id AND c.status = 'ANOMALY') AS anomalies,
        (SELECT COUNT(*) FROM checkpoint_results c WHERE c.missionId = m.id AND c.status = 'SKIPPED') AS skipped
    FROM missions m
"""

@Dao
interface MissionDao {
    @Query("$MISSION_SUMMARY ORDER BY m.startedAt DESC")
    fun observeAll(): Flow<List<MissionSummaryRow>>

    @Query("$MISSION_SUMMARY WHERE m.id = :missionId")
    fun observeById(missionId: String): Flow<MissionSummaryRow?>

    // El robot puede reenviar el inicio al reconectar: el duplicado se ignora a propósito.
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(mission: MissionEntity)

    @Query("UPDATE missions SET endedAt = :endedAt, result = :result WHERE id = :missionId")
    suspend fun finish(missionId: String, endedAt: Long, result: String): Int

    @Query("UPDATE missions SET endedAt = :endedAt, result = :result WHERE robotId = :robotId AND endedAt IS NULL")
    suspend fun closeOpen(robotId: String, endedAt: Long, result: String): Int
}

@Dao
interface CheckpointResultDao {
    @Insert
    suspend fun insert(result: CheckpointResultEntity)

    @Query("SELECT * FROM checkpoint_results WHERE missionId = :missionId ORDER BY timestamp")
    fun observeByMission(missionId: String): Flow<List<CheckpointResultEntity>>
}

@Dao
interface EventDao {
    @Insert
    suspend fun insert(event: EventEntity)

    @Query("SELECT * FROM events WHERE missionId = :missionId ORDER BY timestamp")
    fun observeByMission(missionId: String): Flow<List<EventEntity>>
}
