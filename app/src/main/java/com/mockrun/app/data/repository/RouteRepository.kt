package com.mockrun.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mockrun.app.data.db.RouteDao
import com.mockrun.app.data.db.RouteEntity
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.WayPoint
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import java.lang.reflect.Type
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RouteRepository @Inject constructor(
    private val routeDao: RouteDao,
    private val gson: Gson
) {
    val routes: Flow<List<Route>> = routeDao.getAllRoutes().map { list ->
        list.map { it.toDomain() }
    }

    suspend fun getRoute(id: Long): Route? = routeDao.getRouteById(id)?.toDomain()

    suspend fun saveRoute(route: Route): Long = routeDao.insertRoute(route.toEntity())

    suspend fun updateRoute(route: Route) = routeDao.updateRoute(route.toEntity())

    suspend fun deleteRoute(id: Long) = routeDao.deleteRouteById(id)

    private fun RouteEntity.toDomain(): Route {
        val wps: List<WayPoint> = runCatching {
            val parsed: List<WayPoint>? = gson.fromJson(waypointsJson, WAYPOINT_LIST_TYPE)
            parsed ?: emptyList()
        }.logFailure("RouteRepository", "parse waypoints for route '$name'", Diag.Level.DEBUG)
            .getOrDefault(emptyList())
        return Route(id = id, name = name, waypoints = wps, createdAt = createdAt)
    }

    private fun Route.toEntity() = RouteEntity(
        id = id, name = name,
        waypointsJson = gson.toJson(waypoints),
        createdAt = createdAt
    )

    private companion object {
        /**
         * `List<WayPoint>` 的泛型类型，运行时显式组装。
         *
         * 不能写成 `object : TypeToken<List<WayPoint>>() {}`：该匿名子类在 release
         * 构建被 R8 处理后泛型签名丢失，Gson 会抛
         * `IllegalStateException: TypeToken must be created with a type argument`，
         * 且该异常发生在 runCatching 之外，会直接终止进程（真机崩溃栈已确认）。
         * [TypeToken.getParameterized] 不依赖签名保留，行为与混淆无关。
         */
        private val WAYPOINT_LIST_TYPE: Type =
            TypeToken.getParameterized(List::class.java, WayPoint::class.java).type
    }
}