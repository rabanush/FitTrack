package com.fittrack.app.data.dao

import androidx.room.*
import com.fittrack.app.data.model.CustomFood
import kotlinx.coroutines.flow.Flow

@Dao
interface CustomFoodDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(food: CustomFood): Long

    @Delete
    suspend fun delete(food: CustomFood)

    @Query("SELECT * FROM custom_foods ORDER BY name ASC")
    fun getAll(): Flow<List<CustomFood>>

    @Query("SELECT * FROM custom_foods WHERE LOWER(name) LIKE '%' || LOWER(TRIM(:query)) || '%' ORDER BY name ASC")
    suspend fun search(query: String): List<CustomFood>

    @Query("SELECT * FROM custom_foods WHERE barcode = :barcode LIMIT 1")
    suspend fun findByBarcode(barcode: String): CustomFood?

    @Query("SELECT COUNT(*) FROM custom_foods")
    suspend fun getCount(): Int
}
