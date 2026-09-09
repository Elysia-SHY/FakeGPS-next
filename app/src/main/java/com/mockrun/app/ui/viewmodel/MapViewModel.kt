package com.mockrun.app.ui.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.mockrun.app.data.parser.GpxParser
import com.mockrun.app.data.repository.RouteRepository
import com.mockrun.app.domain.model.Route
import com.mockrun.app.domain.model.WayPoint
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.io.InputStream
import javax.inject.Inject

import com.mockrun.app.location.LocationSearchService
import com.mockrun.app.location.SearchResultItem
import com.mockrun.app.location.RoadRoutingHelper
import com.mockrun.app.location.RoadRouteResult
import com.mockrun.app.location.RoadMode

@HiltViewModel
class MapViewModel @Inject constructor(
    private val routeRepository: RouteRepository,
    private val gpxParser: GpxParser
) : ViewModel() {

    private val _drawnWaypoints = MutableStateFlow<List<WayPoint>>(emptyList())
    val drawnWaypoints: StateFlow<List<WayPoint>> = _drawnWaypoints.asStateFlow()

    private val _selectedRoute = MutableStateFlow<Route?>(null)
    val selectedRoute: StateFlow<Route?> = _selectedRoute.asStateFlow()

    val savedRoutes: StateFlow<List<Route>> = routeRepository.routes
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    // ---- Location Search State ----
    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _searchResults = MutableStateFlow<List<SearchResultItem>>(emptyList())
    val searchResults: StateFlow<List<SearchResultItem>> = _searchResults.asStateFlow()

    private val _isSearching = MutableStateFlow(false)
    val isSearching: StateFlow<Boolean> = _isSearching.asStateFlow()

    fun setSearchQuery(query: String) {
        _searchQuery.value = query
    }

    fun performSearch(query: String) {
        _searchQuery.value = query
        val trimmed = query.trim()
        if (trimmed.isEmpty()) {
            _searchResults.value = emptyList()
            return
        }
        viewModelScope.launch {
            _isSearching.value = true
            val items = LocationSearchService.search(trimmed)
            _searchResults.value = items
            _isSearching.value = false
        }
    }

    fun clearSearch() {
        _searchQuery.value = ""
        _searchResults.value = emptyList()
        _isSearching.value = false
    }

    // ---- Two-Point Road Routing State ----
    private val _roadOrigin = MutableStateFlow<WayPoint?>(null)
    val roadOrigin: StateFlow<WayPoint?> = _roadOrigin.asStateFlow()

    private val _roadDestination = MutableStateFlow<WayPoint?>(null)
    val roadDestination: StateFlow<WayPoint?> = _roadDestination.asStateFlow()

    private val _roadMode = MutableStateFlow(RoadMode.DRIVING)
    val roadMode: StateFlow<RoadMode> = _roadMode.asStateFlow()

    private val _isCalculatingRoute = MutableStateFlow(false)
    val isCalculatingRoute: StateFlow<Boolean> = _isCalculatingRoute.asStateFlow()

    private val _calculatedRoadResult = MutableStateFlow<RoadRouteResult?>(null)
    val calculatedRoadResult: StateFlow<RoadRouteResult?> = _calculatedRoadResult.asStateFlow()

    fun setRoadOrigin(wayPoint: WayPoint?) {
        _roadOrigin.value = wayPoint
    }

    fun setRoadDestination(wayPoint: WayPoint?) {
        _roadDestination.value = wayPoint
    }

    fun setRoadMode(mode: RoadMode) {
        _roadMode.value = mode
    }

    fun calculateRoadRoute(onSuccess: (RoadRouteResult) -> Unit = {}) {
        val orig = _roadOrigin.value ?: return
        val dest = _roadDestination.value ?: return
        val mode = _roadMode.value
        viewModelScope.launch {
            _isCalculatingRoute.value = true
            val result = RoadRoutingHelper.calculateRoute(
                originLat = orig.latitude,
                originLon = orig.longitude,
                destLat = dest.latitude,
                destLon = dest.longitude,
                mode = mode
            )
            _isCalculatingRoute.value = false
            if (result != null && result.waypoints.isNotEmpty()) {
                _calculatedRoadResult.value = result
                _drawnWaypoints.value = result.waypoints
                _selectedRoute.value = Route(
                    name = "沿路规划 (${mode.label} ${(result.distanceKm).format(1)}km)",
                    waypoints = result.waypoints
                )
                onSuccess(result)
            }
        }
    }

    fun clearRoadRoute() {
        _roadOrigin.value = null
        _roadDestination.value = null
        _calculatedRoadResult.value = null
    }

    private fun Double.format(decimals: Int): String = "%.${decimals}f".format(this)

    fun addWaypoint(latitude: Double, longitude: Double) {
        val newPoint = WayPoint(latitude, longitude)
        _drawnWaypoints.update { it + newPoint }
        updateSelectedRouteFromDrawn()
    }

    fun removeLastWaypoint() {
        _drawnWaypoints.update { if (it.isNotEmpty()) it.dropLast(1) else it }
        updateSelectedRouteFromDrawn()
    }

    fun clearWaypoints() {
        _drawnWaypoints.value = emptyList()
        _selectedRoute.value = null
    }

    fun selectRoute(route: Route) {
        _selectedRoute.value = route
        _drawnWaypoints.value = route.waypoints
    }

    fun saveCurrentRoute(name: String) {
        val points = _drawnWaypoints.value
        if (points.size < 2) return
        viewModelScope.launch {
            val route = Route(name = name.ifBlank { "未命名路线" }, waypoints = points)
            val id = routeRepository.saveRoute(route)
            _selectedRoute.value = route.copy(id = id)
        }
    }

    fun renameRoute(route: Route, newName: String) {
        if (newName.isBlank()) return
        viewModelScope.launch {
            val updated = route.copy(name = newName)
            routeRepository.updateRoute(updated)
            if (_selectedRoute.value?.id == route.id) {
                _selectedRoute.value = updated
            }
        }
    }

    fun deleteRoute(id: Long) {
        viewModelScope.launch {
            routeRepository.deleteRoute(id)
            if (_selectedRoute.value?.id == id) {
                clearWaypoints()
            }
        }
    }

    fun importGpx(inputStream: InputStream, defaultName: String = "导入路线") {
        viewModelScope.launch {
            val points = gpxParser.parse(inputStream)
            if (points.size >= 2) {
                _drawnWaypoints.value = points
                val route = Route(name = defaultName, waypoints = points)
                val id = routeRepository.saveRoute(route)
                _selectedRoute.value = route.copy(id = id)
            }
        }
    }

    fun exportRouteToGpx(route: Route): String {
        val sb = StringBuilder()
        sb.appendLine("<?xml version=\"1.0\" encoding=\"UTF-8\"?>")
        sb.appendLine("<gpx version=\"1.1\" creator=\"FakeGPS\" xmlns=\"http://www.topografix.com/GPX/1/1\">")
        sb.appendLine("  <trk>")
        sb.appendLine("    <name>${route.name}</name>")
        sb.appendLine("    <trkseg>")
        for (pt in route.waypoints) {
            sb.appendLine("      <trkpt lat=\"${pt.latitude}\" lon=\"${pt.longitude}\">")
            sb.appendLine("        <ele>${pt.altitude}</ele>")
            sb.appendLine("      </trkpt>")
        }
        sb.appendLine("    </trkseg>")
        sb.appendLine("  </trk>")
        sb.appendLine("</gpx>")
        return sb.toString()
    }

    private fun updateSelectedRouteFromDrawn() {
        val points = _drawnWaypoints.value
        if (points.size >= 2) {
            _selectedRoute.value = Route(name = "自定义绘制路线", waypoints = points)
        } else {
            _selectedRoute.value = null
        }
    }
}