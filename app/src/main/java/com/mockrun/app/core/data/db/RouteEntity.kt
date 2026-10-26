package com.mockrun.app.core.data.db

import androidx.room.ColumnInfo
import androidx.room.Entity
import androidx.room.PrimaryKey
import com.mockrun.app.domain.model.RouteMode

@Entity(tableName = "routes")
data class RouteEntity(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val name: String,
    /** Gson-serialized List<WayPoint>. */
    val waypointsJson: String,
    val createdAt: Long = System.currentTimeMillis(),
    /** RouteMode.name；默认 ONE_WAY，兼容 v2.2.0 及更早写入的旧数据。 */
    @ColumnInfo(defaultValue = "ONE_WAY")
    val mode: String = RouteMode.ONE_WAY.name,
    /** 录制轨迹转路线时记录来源轨迹 id。 */
    val sourceTrackId: Long? = null
)
