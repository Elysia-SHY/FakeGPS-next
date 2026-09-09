package com.mockrun.app.di

import android.content.Context
import androidx.room.Room
import com.google.gson.Gson
import com.mockrun.app.data.db.RouteDao
import com.mockrun.app.data.db.RouteDatabase
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
        ).fallbackToDestructiveMigration().build()
    }

    @Provides
    fun provideRouteDao(database: RouteDatabase): RouteDao {
        return database.routeDao()
    }
}
