package ar.edu.uade.go2rondas.data.local.entity

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

// Los enums del dominio se guardan por nombre (String); el mapper los convierte.
// Las entidades no salen de la capa de datos.

@Entity(tableName = "missions", indices = [Index("robotId", "siteId")])
data class MissionEntity(
    @PrimaryKey val id: String,
    val robotId: String,
    /** Sitio que tenía cargado el robot: el historial se muestra por robot y por sitio. */
    val siteId: String,
    val type: String,
    val total: Int,
    val startedAt: Long,
    val endedAt: Long? = null,
    val result: String? = null
)

@Entity(
    tableName = "checkpoint_results",
    foreignKeys = [
        ForeignKey(
            entity = MissionEntity::class,
            parentColumns = ["id"],
            childColumns = ["missionId"],
            // Un resultado no tiene sentido sin su ronda.
            onDelete = ForeignKey.CASCADE
        )
    ],
    indices = [Index("missionId")]
)
data class CheckpointResultEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String,
    val checkpointId: String,
    val status: String,
    val temperatureC: Double?,
    val humidityPct: Double?,
    val noiseDba: Double?,
    val noiseBaselineDba: Double?,
    val timestamp: Long
)

/**
 * Hechos de la ronda (alertas, órdenes, cortes, escalamientos) en una sola tabla:
 * cada tipo usa las columnas que le corresponden y deja el resto en null.
 * Sin clave foránea: un corte de conexión puede pasar con el robot en la base, sin ronda.
 */
@Entity(tableName = "events", indices = [Index("missionId")])
data class EventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val missionId: String?,
    val timestamp: Long,
    val kind: String,
    val alertCode: String? = null,
    val severity: String? = null,
    val checkpointId: String? = null,
    val commandType: String? = null,
    val commandStatus: String? = null,
    val rejectionReason: String? = null,
    val downtimeSeconds: Long? = null
)

/** Ronda con sus contadores, calculados en la consulta SQL. */
data class MissionSummaryRow(
    @Embedded val mission: MissionEntity,
    @ColumnInfo(name = "inspected") val inspected: Int,
    @ColumnInfo(name = "anomalies") val anomalies: Int,
    @ColumnInfo(name = "skipped") val skipped: Int
)
