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
 * Tests de migration Room. `MigrationTestHelper` s'occupe de
 * 1) créer une base à la version cible avec le schéma JSON committé,
 * 2) appliquer les migrations,
 * 3) valider le résultat contre le schéma de la version d'arrivée.
 *
 * On utilise `*ALL_MIGRATIONS` exposé par `ArbreDatabase` pour rester aligné
 * avec ce qui tourne en prod — éviter qu'un nouveau Migration ajouté ne soit
 * pas testé.
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
        // À la v1 la table `capture` n'existe pas encore — c'est exactement le
        // cas d'un install qui n'aurait jamais ouvert l'app depuis Phase 9. La
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
        // Sanity check : ouvrir une instance complète après migration ne lève
        // pas. Couvre le cas où le schemaCheck Room rejetterait la table
        // post-migration.
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
