package com.example.data.db

import android.content.Context
import androidx.room.*

@Database(entities = [DocAnalysisEntity::class], version = 1, exportSchema = false)
@TypeConverters(DocConverters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun docAnalysisDao(): DocAnalysisDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "doc_intel_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
