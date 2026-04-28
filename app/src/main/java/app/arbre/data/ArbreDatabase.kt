package app.arbre.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [ArbreEntity::class, CaptureEntity::class],
    version = 2,
    exportSchema = false,
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
        private val MIGRATION_1_2 = object : Migration(1, 2) {
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

        fun get(context: Context): ArbreDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ArbreDatabase::class.java,
                "arbres-paris.db",
            )
                .createFromAsset("databases/arbres-paris.db")
                .addMigrations(MIGRATION_1_2)
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
