package com.mockrun.app.util

import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * 中国大陆三种坐标系的**唯一**转换实现。
 *
 * ## 为什么要有这个文件
 *
 * 在此之前同一套 GCJ-02 算法存在两份拷贝：
 *  - `core/location/CoordinateConverter.kt`（应用侧，可被单元测试覆盖）
 *  - `hook/XposedLocationHook.kt` 里的一组 private 顶层函数（hook 侧，**无法**被单元测试覆盖，
 *    因为它们是文件私有且依赖 Xposed 运行时）
 *
 * 两份实现一旦漂移，注入坐标与地图显示坐标就会不一致，而 hook 侧那份没有任何测试能发现。
 * 这里把它收敛成一个**不依赖 Android、不依赖 Xposed、不依赖本项目任何其它类**的纯函数对象，
 * 应用侧与 hook 侧都从这里取算法 —— 于是同一份数学既只有一处，也能被 JVM 单元测试直接覆盖。
 *
 * ## 坐标系约定（本项目内部）
 *
 * | 场景 | 坐标系 |
 * | :--- | :--- |
 * | 内部存储 / 业务数据（WayPoint、MultiTargetRule、Route、收藏、Room 表） | **WGS-84** |
 * | Android `Location` / `LocationManager` / mock provider 注入 | **WGS-84**（平台契约，不可改） |
 * | 高德底图瓦片（`wprd*.is.autonavi.com`）显示与点选 | **GCJ-02** |
 * | 高德 SDK `AMapLocation.getLatitude()/getLongitude()` | **GCJ-02** |
 * | 腾讯 SDK `TencentLocation` | **GCJ-02** |
 * | 百度 SDK `BDLocation` | **BD-09** |
 * | 系统 `Geocoder`（国内返回 GCJ-02 语义的地址） | 入参需 **GCJ-02** |
 * | Photon / OSM Nominatim 网络接口 | **WGS-84** |
 *
 * 规则：只在「输入或输出坐标系明确」的边界上转换一次；内部一律 WGS-84，禁止叠加固定偏移。
 */
object GeoTransform {

    /** 克拉索夫斯基椭球长半轴（GCJ-02 偏移算法使用的常数）。 */
    private const val A = 6378245.0

    /** 椭球偏心率平方。 */
    private const val EE = 0.00669342162296594323

    private const val X_PI = Math.PI * 3000.0 / 180.0

    /** 坐标语义标签，用于在边界上把「这个数是哪个坐标系」写进签名与日志。 */
    enum class CoordSystem { WGS84, GCJ02, BD09 }

    /**
     * 是否落在 GCJ-02 偏移算法覆盖的范围内（中国大陆粗略外包框）。
     *
     * 注意：这个判断只应作用于**待转换的源坐标**。对已经是 GCJ-02/BD-09 的值调用正向偏移函数时
     * 也必须先判断，否则境外点会被叠加一次不存在的偏移。
     */
    fun outOfChina(lat: Double, lon: Double): Boolean =
        lon < 72.004 || lon > 137.8347 || lat < 0.8293 || lat > 55.8271

    private fun isInvalid(lat: Double, lon: Double): Boolean =
        lat.isNaN() || lon.isNaN() || lat.isInfinite() || lon.isInfinite()

    private fun transformLat(x: Double, y: Double): Double {
        var r = -100.0 + 2.0 * x + 3.0 * y + 0.2 * y * y + 0.1 * x * y + 0.2 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        r += (20.0 * sin(y * Math.PI) + 40.0 * sin(y / 3.0 * Math.PI)) * 2.0 / 3.0
        r += (160.0 * sin(y / 12.0 * Math.PI) + 320.0 * sin(y * Math.PI / 30.0)) * 2.0 / 3.0
        return r
    }

    private fun transformLon(x: Double, y: Double): Double {
        var r = 300.0 + x + 2.0 * y + 0.1 * x * x + 0.1 * x * y + 0.1 * sqrt(abs(x))
        r += (20.0 * sin(6.0 * x * Math.PI) + 20.0 * sin(2.0 * x * Math.PI)) * 2.0 / 3.0
        r += (20.0 * sin(x * Math.PI) + 40.0 * sin(x / 3.0 * Math.PI)) * 2.0 / 3.0
        r += (150.0 * sin(x / 12.0 * Math.PI) + 300.0 * sin(x / 30.0 * Math.PI)) * 2.0 / 3.0
        return r
    }

    /** WGS-84 -> GCJ-02。境外或非法输入原样返回。 */
    fun wgs84ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (isInvalid(lat, lon) || outOfChina(lat, lon)) return lat to lon
        var dLat = transformLat(lon - 105.0, lat - 35.0)
        var dLon = transformLon(lon - 105.0, lat - 35.0)
        val rLat = Math.toRadians(lat)
        var magic = sin(rLat)
        magic = 1 - EE * magic * magic
        val sqrtMagic = sqrt(magic)
        dLat = dLat * 180.0 / (A * (1 - EE) / (magic * sqrtMagic) * Math.PI)
        dLon = dLon * 180.0 / (A / sqrtMagic * cos(rLat) * Math.PI)
        return (lat + dLat) to (lon + dLon)
    }

    /**
     * GCJ-02 -> WGS-84（不动点迭代，约 1 cm 精度）。
     *
     * 迭代而不是用「一次反偏移」近似：一次近似在中国东部会残留 1~2 m 误差，叠加到地图选点上
     * 就是可见的落点漂移。
     */
    fun gcj02ToWgs84(lat: Double, lon: Double, iterations: Int = 10): Pair<Double, Double> {
        if (isInvalid(lat, lon) || outOfChina(lat, lon)) return lat to lon
        var wLat = lat
        var wLon = lon
        repeat(iterations.coerceAtLeast(1)) {
            val (gLat, gLon) = wgs84ToGcj02(wLat, wLon)
            wLat -= gLat - lat
            wLon -= gLon - lon
        }
        return wLat to wLon
    }

    /** GCJ-02 -> BD-09。 */
    fun gcj02ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        if (isInvalid(lat, lon)) return lat to lon
        val z = sqrt(lon * lon + lat * lat) + 0.00002 * sin(lat * X_PI)
        val theta = atan2(lat, lon) + 0.000003 * cos(lon * X_PI)
        return (z * sin(theta) + 0.006) to (z * cos(theta) + 0.0065)
    }

    /** BD-09 -> GCJ-02。 */
    fun bd09ToGcj02(lat: Double, lon: Double): Pair<Double, Double> {
        if (isInvalid(lat, lon)) return lat to lon
        val x = lon - 0.0065
        val y = lat - 0.006
        val z = sqrt(x * x + y * y) - 0.00002 * sin(y * X_PI)
        val theta = atan2(y, x) - 0.000003 * cos(x * X_PI)
        return (z * sin(theta)) to (z * cos(theta))
    }

    /**
     * WGS-84 -> BD-09（百度 SDK 对外契约）。
     *
     * 境外**不做偏移**：BD-09 是中国大陆的偏移基准，百度 SDK 在境外返回的就是 WGS-84。
     * 对境外点硬套 BD 偏移会凭空造出 ~500 m 误差。
     */
    fun wgs84ToBd09(lat: Double, lon: Double): Pair<Double, Double> {
        if (isInvalid(lat, lon) || outOfChina(lat, lon)) return lat to lon
        val (gLat, gLon) = wgs84ToGcj02(lat, lon)
        return gcj02ToBd09(gLat, gLon)
    }

    /** BD-09 -> WGS-84。境外输入（BD-09 只在中国大陆存在）原样返回。 */
    fun bd09ToWgs84(lat: Double, lon: Double): Pair<Double, Double> {
        if (isInvalid(lat, lon) || outOfChina(lat, lon)) return lat to lon
        val (gLat, gLon) = bd09ToGcj02(lat, lon)
        return gcj02ToWgs84(gLat, gLon)
    }

    /**
     * 把任意来源坐标转成 WGS-84（本项目内部统一坐标系）。
     *
     * 这是「边界收敛」的唯一入口：调用方必须显式声明它拿到的是什么坐标系的数，
     * 不允许再出现「按地图供应商猜坐标系」的隐式约定。
     */
    fun toWgs84(lat: Double, lon: Double, from: CoordSystem): Pair<Double, Double> = when (from) {
        CoordSystem.WGS84 -> lat to lon
        CoordSystem.GCJ02 -> gcj02ToWgs84(lat, lon)
        CoordSystem.BD09 -> bd09ToWgs84(lat, lon)
    }

    /** 把内部 WGS-84 转成某个边界需要的坐标系。 */
    fun fromWgs84(lat: Double, lon: Double, to: CoordSystem): Pair<Double, Double> = when (to) {
        CoordSystem.WGS84 -> lat to lon
        CoordSystem.GCJ02 -> wgs84ToGcj02(lat, lon)
        CoordSystem.BD09 -> wgs84ToBd09(lat, lon)
    }
}
