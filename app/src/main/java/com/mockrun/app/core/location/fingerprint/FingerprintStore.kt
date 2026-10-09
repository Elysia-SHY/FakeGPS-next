package com.mockrun.app.core.location.fingerprint

import android.content.Context
import com.google.gson.Gson
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure
import java.io.File

/**
 * 指纹包的持久化与导出。
 *
 * 落盘在 filesDir/fingerprints/<id>.json；
 * 导出到 /data/local/tmp/fakegps_fp_<id>.json 供 hook 侧读取
 * （system_server 的 SELinux 上下文读不到 app 私有目录）。
 */
object FingerprintStore {

    private const val TAG = "FingerprintStore"
    private const val DIR = "fingerprints"
    private val gson = Gson()

    private fun dir(ctx: Context): File = File(ctx.filesDir, DIR).apply { mkdirs() }

    fun save(ctx: Context, id: String, fp: EnvironmentFingerprint) {
        runCatching {
            File(dir(ctx), "$id.json").writeText(gson.toJson(fp))
        }.logFailure(TAG, "save fingerprint $id", Diag.Level.ERROR)
    }

    fun load(ctx: Context, id: String): EnvironmentFingerprint? = runCatching {
        val f = File(dir(ctx), "$id.json")
        if (!f.exists()) return@runCatching null
        gson.fromJson(f.readText(), EnvironmentFingerprint::class.java)
    }.logFailure(TAG, "load fingerprint $id").getOrNull()

    fun list(ctx: Context): List<String> =
        dir(ctx).listFiles { f -> f.extension == "json" }
            ?.map { it.nameWithoutExtension }
            ?.sorted()
            ?: emptyList()

    fun delete(ctx: Context, id: String) {
        runCatching { File(dir(ctx), "$id.json").delete() }
            .logFailure(TAG, "delete fingerprint $id", Diag.Level.DEBUG)
    }

    /** 导出为 JSON 文本，供 root 侧写入 /data/local/tmp。 */
    fun exportJson(ctx: Context, id: String): String? {
        val fp = load(ctx, id) ?: return null
        return gson.toJson(fp)
    }

    /** 指纹包的可读摘要，用于 UI 列表。 */
    fun summary(fp: EnvironmentFingerprint): String =
        "Wi-Fi ${fp.wifi.size} · 基站 ${fp.cells.size} · 蓝牙 ${fp.ble.size} · 配对 ${fp.bonded.size}"
}
