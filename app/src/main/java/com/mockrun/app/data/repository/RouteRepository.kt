package com.mockrun.app.data.repository

import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mockrun.app.data.db.RouteDao
import com.mockrun.app.data.db.RouteEntity
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.WayPoint
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
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
        val type = object : TypeToken<List<WayPoint>>() {}.type
        val wps: List<WayPoint> = runCatching {
            val parsed: List<WayPoint>? = gson.fromJson(waypointsJson, type)
            parsed ?: emptyList()
        }.getOrDefault(emptyList())
        return Route(id = id, name = name, waypoints = wps, createdAt = createdAt)
    }

    private fun Route.toEntity() = RouteEntity(
        id = id, name = name,
        waypointsJson = gson.toJson(waypoints),
        createdAt = createdAt
    )
}