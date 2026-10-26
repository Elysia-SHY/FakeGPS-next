package com.mockrun.app.core.data.db

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.SupportSQLiteOpenHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import com.mockrun.app.domain.model.RouteMode
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/**
 * v1 → v3 迁移回归：
 * 1. 老库里的路线必须一条不少；
 * 2. 新增的 `mode` 列必须回填 `ONE_WAY`；
 * 3. 新建的 `tracks` 表必须可读（空表）；
 * 4. 迁移后 Room 的 schema 校验必须通过（否则打开就抛 IllegalStateException）。
 */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class RouteDatabaseMigrationTest {

    private val dbName = "migration-test.db"
    private lateinit var context: Context

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        context.deleteDatabase(dbName)
        createVersion1Database()
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    private fun createVersion1Database() {
        val callback = object : SupportSQLiteOpenHelper.Callback(1) {
            override fun onCreate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `routes` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`name` TEXT NOT NULL, " +
                        "`waypointsJson` TEXT NOT NULL, " +
                        "`createdAt` INTEGER NOT NULL)"
                )
                db.execSQL(
                    "INSERT INTO routes (name, waypointsJson, createdAt) VALUES ('旧路线', '[]', 1)"
                )
            }

            override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
        }
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(callback)
                .build()
        )
        helper.writableDatabase
        helper.close()
    }

    @Test
    fun `migrating v1 to v3 keeps routes and creates tracks`() = runBlocking {
        val db = Room.databaseBuilder(context, RouteDatabase::class.java, dbName)
            .addMigrations(RouteDatabase.MIGRATION_1_2, RouteDatabase.MIGRATION_2_3)
            .build()

        try {
            val routes = db.routeDao().getAllRoutes().first()
            assertEquals("老库里的路线必须保留", 1, routes.size)
            assertEquals("旧路线名不能被改", "旧路线", routes.first().name)
            assertEquals(
                "新增列必须回填默认运行方式",
                RouteMode.ONE_WAY.name,
                routes.first().mode
            )
            assertTrue("新增的 tracks 表必须可读", db.trackDao().observeSummaries().first().isEmpty())
        } finally {
            db.close()
        }
    }
}
