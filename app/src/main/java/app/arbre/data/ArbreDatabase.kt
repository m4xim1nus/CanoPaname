package app.arbre.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ArbreEntity::class, CaptureEntity::class],
    version = 3,
    exportSchema = true,
)
abstract class ArbreDatabase : RoomDatabase() {

    abstract fun arbreDao(): ArbreDao
    abstract fun captureDao(): CaptureDao

    companion object {
        @Volatile
        private var INSTANCE: ArbreDatabase? = null

        // L'asset DB ship en v1 (table `arbre` seule). À la 1re ouverture, Room
        // exécute MIGRATION_1_2 qui ajoute la table `capture` + ses indexes.
        // Le CREATE TABLE doit matcher exactement ce que Room génère pour
        // `CaptureEntity` (sinon le schemaCheck rejette au runtime).
        internal val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS `capture` (
                      `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                      `arbreId` INTEGER NOT NULL,
                      `speciesIndex` INTEGER NOT NULL,
                      `remarquable` INTEGER NOT NULL,
                      `timestamp` INTEGER NOT NULL,
                      `latitudeDevice` REAL NOT NULL,
                      `longitudeDevice` REAL NOT NULL,
                      `photoPath` TEXT NOT NULL,
                      `season` INTEGER NOT NULL
                    )
                    """.trimIndent()
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_arbreId` " +
                        "ON `capture` (`arbreId`)"
                )
                db.execSQL(
                    "CREATE INDEX IF NOT EXISTS `index_capture_speciesIndex` " +
                        "ON `capture` (`speciesIndex`)"
                )
            }
        }

        // Avant v3, `capture.photoPath` stockait un chemin absolu. Ça crée une
        // dépendance fragile au sandbox path (cassée par device-transfer,
        // multi-user, debug↔release applicationId). On rewrite chaque row au
        // basename pur ; le chemin absolu est reconstitué côté lecture par
        // `Capture.resolvedFile(context)`. Aucun DDL — la diff est sémantique,
        // pas structurelle. Idempotent : `substringAfterLast('/')` sur un
        // basename retourne le basename.
        internal val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                val rewrites = mutableListOf<Pair<Long, String>>()
                db.query("SELECT id, photoPath FROM capture").use { c ->
                    while (c.moveToNext()) {
                        val id = c.getLong(0)
                        val path = c.getString(1)
                        val basename = path.substringAfterLast('/')
                        if (basename != path) rewrites += id to basename
                    }
                }
                val stmt = db.compileStatement("UPDATE capture SET photoPath = ? WHERE id = ?")
                rewrites.forEach { (id, basename) ->
                    stmt.bindString(1, basename)
                    stmt.bindLong(2, id)
                    stmt.executeUpdateDelete()
                    stmt.clearBindings()
                }
            }
        }

        internal val ALL_MIGRATIONS: Array<Migration> = arrayOf(MIGRATION_1_2, MIGRATION_2_3)

        fun get(context: Context): ArbreDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ArbreDatabase::class.java,
                "arbres-paris.db",
            )
                .createFromAsset("databases/arbres-paris.db")
                .addMigrations(*ALL_MIGRATIONS)
                .build()
                .also { INSTANCE = it }
        }
    }
}
