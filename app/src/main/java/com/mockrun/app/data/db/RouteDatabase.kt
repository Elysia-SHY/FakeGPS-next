package com.mockrun.app.data.db

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(entities = [RouteEntity::class], version = 1, exportSchema = false)
abstract class RouteDatabase : RoomDatabase() {
    abstract fun routeDao(): RouteDao
}
