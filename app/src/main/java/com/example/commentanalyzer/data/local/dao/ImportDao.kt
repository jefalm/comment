package com.example.commentanalyzer.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import com.example.commentanalyzer.data.local.entity.ImportEntity

@Dao
interface ImportDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertImport(importEntity: ImportEntity)

    @Query("SELECT * FROM imports WHERE importId = :importId")
    suspend fun getImportById(importId: String): ImportEntity?
}
