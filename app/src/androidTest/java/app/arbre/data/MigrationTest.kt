package app.arbre.data

import androidx.room.Room
import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Tests de migration Room. On passe `*ALL_MIGRATIONS` exposé par
 * `ArbreDatabase` pour rester aligné avec la prod — un nouveau Migration
 * ajouté est ainsi automatiquement couvert.
 */
@RunWith(AndroidJUnit4::class)
class MigrationTest {

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        ArbreDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate2To3_rewritesAbsolutePathToBasename() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO capture
                (id, arbreId, speciesIndex, remarquable, timestamp,
                 latitudeDevice, longitudeDevice, photoPath, season)
                VALUES (1, 42, 5, 0, 1700000000000,
                        48.85, 2.34, '/data/data/app.arbre/files/captures/abc-123.jpg', 0)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, ArbreDatabase.MIGRATION_2_3
        )
        migrated.query("SELECT photoPath FROM capture WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("abc-123.jpg", c.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrate2To3_idempotentOnAlreadyBasename() {
        helper.createDatabase(DB_NAME, 2).apply {
            execSQL(
                """
                INSERT INTO capture
                (id, arbreId, speciesIndex, remarquable, timestamp,
                 latitudeDevice, longitudeDevice, photoPath, season)
                VALUES (1, 42, 5, 0, 1700000000000,
                        48.85, 2.34, 'plain.jpg', 0)
                """.trimIndent()
            )
            close()
        }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, ArbreDatabase.MIGRATION_2_3
        )
        migrated.query("SELECT photoPath FROM capture WHERE id = 1").use { c ->
            c.moveToFirst()
            assertEquals("plain.jpg", c.getString(0))
        }
        migrated.close()
    }

    @Test
    fun migrate1To3_chained() {
        // V1 = asset DB sans table `capture` (install jamais ouverte). La
        // chaîne v1 → v2 → v3 doit appliquer les deux migrations sans erreur.
        helper.createDatabase(DB_NAME, 1).apply { close() }

        val migrated = helper.runMigrationsAndValidate(
            DB_NAME, 3, true, *ArbreDatabase.ALL_MIGRATIONS
        )
        migrated.query("SELECT COUNT(*) FROM capture").use { c ->
            c.moveToFirst()
            assertEquals(0, c.getInt(0))
        }
        migrated.close()
    }

    @Test
    fun openLatestDb_runsMigrationsCleanly() {
        // Couvre un schemaCheck Room qui rejetterait la table post-migration.
        helper.createDatabase(DB_NAME, 1).apply { close() }
        val ctx = InstrumentationRegistry.getInstrumentation().targetContext
        val db = Room.databaseBuilder(ctx, ArbreDatabase::class.java, DB_NAME)
            .addMigrations(*ArbreDatabase.ALL_MIGRATIONS)
            .build()
        db.openHelper.writableDatabase
        db.close()
    }

    private companion object {
        const val DB_NAME = "migration-test.db"
    }
}
