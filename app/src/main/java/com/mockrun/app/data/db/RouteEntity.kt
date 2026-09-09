package com.mockrun.app.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    /** Gson-serialized List<WayPoint>. */
    val waypointsJson: String,
    val createdAt: Long = System.currentTimeMillis()
)
