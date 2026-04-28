package app.arbre.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [ArbreEntity::class], version = 1, exportSchema = false)
abstract class ArbreDatabase : RoomDatabase() {

    abstract fun arbreDao(): ArbreDao

    companion object {
        @Volatile
        private var INSTANCE: ArbreDatabase? = null

        fun get(context: Context): ArbreDatabase = INSTANCE ?: synchronized(this) {
            INSTANCE ?: Room.databaseBuilder(
                context.applicationContext,
                ArbreDatabase::class.java,
                "arbres-paris.db",
            )
                .createFromAsset("databases/arbres-paris.db")
                .fallbackToDestructiveMigration()
                .build()
                .also { INSTANCE = it }
        }
    }
}
