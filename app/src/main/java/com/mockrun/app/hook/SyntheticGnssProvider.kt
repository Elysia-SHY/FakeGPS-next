package com.mockrun.app.hook

import android.location.GnssStatus
import android.os.Build
import androidx.annotation.RequiresApi
import java.lang.reflect.Method
import kotlin.random.Random

/**
 * High-fidelity Synthetic GNSS Constellation Generator.
 *
 * Generates dynamic BDS (BeiDou), GPS and GLONASS constellations with realistic SNR (C/N0),
 * azimuth, elevation, and carrier lock statuses.
 * Universally compatible across Android 7.0 through Android 15+.
 * Bypasses enterprise anti-cheat and security SDKs checking for 0-satellite anomalies.
 */
object SyntheticGnssProvider {

    private val random = Random(System.currentTimeMillis())

    // BDS (BeiDou) Satellite Catalog: (svid, baseElevation, baseAzimuth)
    private val BdsSatellites = listOf(
        Triple(1, 45f, 60f),
        Triple(2, 65f, 130f),
        Triple(3, 38f, 210f),
        Triple(4, 78f, 315f),
        Triple(5, 52f, 95f),
        Triple(6, 28f, 175f),
        Triple(7, 42f, 260f),
        Triple(8, 83f, 40f),
        Triple(9, 33f, 150f),
        Triple(10, 57f, 290f),
        Triple(11, 22f, 80f),
        Triple(12, 49f, 225f)
    )

    // GPS Satellite Catalog: (svid, baseElevation, baseAzimuth)
    private val GpsSatellites = listOf(
        Triple(2, 55f, 45f),
        Triple(6, 32f, 115f),
        Triple(12, 70f, 205f),
        Triple(19, 41f, 300f),
        Triple(24, 62f, 85f),
        Triple(25, 29f, 160f),
        Triple(29, 81f, 235f),
        Triple(31, 36f, 330f)
    )

    // GLONASS Satellite Catalog
    private val GlonassSatellites = listOf(
        Triple(1, 48f, 75f),
        Triple(7, 60f, 190f),
        Triple(14, 35f, 280f),
        Triple(22, 74f, 25f)
    )

    private val addSatelliteMethod: Method? by lazy {
        runCatching {
            val methods = GnssStatus.Builder::class.java.declaredMethods
            methods.firstOrNull { it.name == "addSatellite" && it.parameterTypes.size == 12 }
                ?: methods.firstOrNull { it.name == "addSatellite" && it.parameterTypes.size == 9 }
                ?: methods.firstOrNull { it.name == "addSatellite" && it.parameterTypes.size == 8 }
                ?: methods.firstOrNull { it.name == "addSatellite" }
        }.getOrNull()
    }

    private fun addSatelliteUniversal(
        builder: GnssStatus.Builder,
        constellationType: Int,
        svid: Int,
        cn0DbHz: Float,
        elevation: Float,
        azimuth: Float,
        hasEphemeris: Boolean,
        hasAlmanac: Boolean,
        usedInFix: Boolean,
        carrierFreqHz: Float = 1575420000.0f
    ) {
        val method = addSatelliteMethod
        if (method != null) {
            when (method.parameterTypes.size) {
                12 -> {
                    method.invoke(
                        builder,
                        constellationType,
                        svid,
                        cn0DbHz,
                        elevation,
                        azimuth,
                        hasEphemeris,
                        hasAlmanac,
                        usedInFix,
                        true, // hasCarrierFrequency
                        carrierFreqHz,
                        true, // hasBasebandCn0DbHz
                        (cn0DbHz - 2.5f).coerceAtLeast(15f)
                    )
                }
                9 -> {
                    method.invoke(
                        builder,
                        constellationType,
                        svid,
                        cn0DbHz,
                        elevation,
                        azimuth,
                        hasEphemeris,
                        hasAlmanac,
                        usedInFix,
                        carrierFreqHz
                    )
                }
                8 -> {
                    method.invoke(
                        builder,
                        constellationType,
                        svid,
                        cn0DbHz,
                        elevation,
                        azimuth,
                        hasEphemeris,
                        hasAlmanac,
                        usedInFix
                    )
                }
                else -> {
                    builder.addSatellite(
                        constellationType, svid, cn0DbHz, elevation, azimuth,
                        hasEphemeris, hasAlmanac, usedInFix,
                        true, carrierFreqHz, true, (cn0DbHz - 2.5f).coerceAtLeast(15f)
                    )
                }
            }
        } else {
            builder.addSatellite(
                constellationType, svid, cn0DbHz, elevation, azimuth,
                hasEphemeris, hasAlmanac, usedInFix,
                true, carrierFreqHz, true, (cn0DbHz - 2.5f).coerceAtLeast(15f)
            )
        }
    }

    @RequiresApi(Build.VERSION_CODES.N)
    fun createSyntheticGnssStatus(): GnssStatus? {
        return runCatching {
            val builder = GnssStatus.Builder()

            // 1. Add BDS Constellation (Type 5 - B1I / 1561.098 MHz)
            BdsSatellites.forEachIndexed { index, (svid, elev, azim) ->
                val jitter = random.nextFloat() * 2.4f - 1.2f
                val cn0 = (34.0f + (elev / 90.0f) * 8.0f + jitter).coerceIn(28.0f, 43.5f)
                val usedInFix = index < 9 // Top 9 used in navigation solution

                addSatelliteUniversal(
                    builder = builder,
                    constellationType = GnssStatus.CONSTELLATION_BEIDOU,
                    svid = svid,
                    cn0DbHz = cn0,
                    elevation = elev + random.nextFloat() * 0.4f - 0.2f,
                    azimuth = (azim + random.nextFloat() * 0.6f - 0.3f) % 360f,
                    hasEphemeris = true,
                    hasAlmanac = true,
                    usedInFix = usedInFix,
                    carrierFreqHz = 1561098000f
                )
            }

            // 2. Add GPS Constellation (Type 1 - L1 / 1575.42 MHz)
            GpsSatellites.forEachIndexed { index, (svid, elev, azim) ->
                val jitter = random.nextFloat() * 2.0f - 1.0f
                val cn0 = (32.0f + (elev / 90.0f) * 7.5f + jitter).coerceIn(27.0f, 41.0f)
                val usedInFix = index < 6 // Top 6 used in fix

                addSatelliteUniversal(
                    builder = builder,
                    constellationType = GnssStatus.CONSTELLATION_GPS,
                    svid = svid,
                    cn0DbHz = cn0,
                    elevation = elev + random.nextFloat() * 0.4f - 0.2f,
                    azimuth = (azim + random.nextFloat() * 0.6f - 0.3f) % 360f,
                    hasEphemeris = true,
                    hasAlmanac = true,
                    usedInFix = usedInFix,
                    carrierFreqHz = 1575420000f
                )
            }

            // 3. Add GLONASS Constellation (Type 3 - G1 / 1602.0 MHz)
            GlonassSatellites.forEachIndexed { index, (svid, elev, azim) ->
                val jitter = random.nextFloat() * 2.0f - 1.0f
                val cn0 = (30.0f + (elev / 90.0f) * 6.0f + jitter).coerceIn(26.0f, 38.0f)
                val usedInFix = index < 3

                addSatelliteUniversal(
                    builder = builder,
                    constellationType = GnssStatus.CONSTELLATION_GLONASS,
                    svid = svid,
                    cn0DbHz = cn0,
                    elevation = elev,
                    azimuth = azim,
                    hasEphemeris = true,
                    hasAlmanac = true,
                    usedInFix = usedInFix,
                    carrierFreqHz = 1602000000f
                )
            }

            builder.build()
        }.getOrNull()
    }
}
