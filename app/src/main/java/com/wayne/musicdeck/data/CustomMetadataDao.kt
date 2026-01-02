package com.wayne.musicdeck.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomMetadataDao {
    
    @Query("SELECT * FROM custom_metadata WHERE songId = :songId")
    suspend fun getCustomMetadata(songId: Long): CustomMetadata?
    
    @Query("SELECT * FROM custom_metadata")
    suspend fun getAllCustomMetadata(): List<CustomMetadata>
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(metadata: CustomMetadata)
    
    @Query("DELETE FROM custom_metadata WHERE songId = :songId")
    suspend fun delete(songId: Long)
    
    @Query("DELETE FROM custom_metadata")
    suspend fun deleteAll()
}
