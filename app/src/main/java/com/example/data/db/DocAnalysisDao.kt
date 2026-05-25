package com.example.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface DocAnalysisDao {
    @Query("SELECT * FROM doc_analyses ORDER BY uploadTime DESC")
    fun getAllAnalyses(): Flow<List<DocAnalysisEntity>>

    @Query("SELECT * FROM doc_analyses WHERE id = :id LIMIT 1")
    suspend fun getAnalysisById(id: Long): DocAnalysisEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAnalysis(entity: DocAnalysisEntity): Long

    @Query("DELETE FROM doc_analyses WHERE id = :id")
    suspend fun deleteAnalysisById(id: Long)

    @Query("DELETE FROM doc_analyses")
    suspend fun deleteAll()
}
