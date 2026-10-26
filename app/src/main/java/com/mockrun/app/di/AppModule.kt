package com.mockrun.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.mockrun.app.core.data.db.RouteDao
import com.mockrun.app.core.data.db.RouteDatabase
import com.mockrun.app.core.data.db.TrackDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideGson(): Gson = Gson()

    @Provides
    @Singleton
    fun provideRouteDatabase(
        @ApplicationContext context: Context
    ): RouteDatabase {
        return Room.databaseBuilder(
            context,
            RouteDatabase::class.java,
            "mockrun_routes.db"
        )
            .addMigrations(RouteDatabase.MIGRATION_1_2, RouteDatabase.MIGRATION_2_3)
            // 只兜「装回旧版本」这一种情况；升级路径必须走 Migration，否则启动即崩
            .fallbackToDestructiveMigrationOnDowngrade()
            .build()
    }

    @Provides
    fun provideRouteDao(database: RouteDatabase): RouteDao {
        return database.routeDao()
    }

    @Provides
    fun provideTrackDao(database: RouteDatabase): TrackDao {
        return database.trackDao()
    }
}
