package ar.edu.uade.go2rondas.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

/**
 * Versión 2: cada ronda guarda el sitio en el que se hizo. Hasta la versión 1 había un único sitio,
 * el data center del Edificio A, así que las rondas que ya estaban quedan asociadas a ese.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE missions ADD COLUMN siteId TEXT NOT NULL DEFAULT '$SITE_BEFORE_V2'")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_missions_robotId_siteId ON missions (robotId, siteId)")
    }
}

private const val SITE_BEFORE_V2 = "dc-edificio-a"
