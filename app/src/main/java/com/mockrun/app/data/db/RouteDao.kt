package com.mockrun.app.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface RouteDao {
    @Query("SELECT * FROM routes ORDER BY createdAt DESC")
    fun getAllRoutes(): Flow<List<RouteEntity>>

    @Query("SELECT * FROM routes WHERE id = :id LIMIT 1")
    suspend fun getRouteById(id: Long): RouteEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertRoute(route: RouteEntity): Long

    @Update
    suspend fun updateRoute(route: RouteEntity)

    @Query("DELETE FROM routes WHERE id = :id")
    suspend fun deleteRouteById(id: Long)
}
