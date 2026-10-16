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

    /**
     * 原子写入指纹包。
     *
     * 先写 `$id.json.tmp` 再 `renameTo` 覆盖：`renameTo` 在同一文件系统内是原子的，
     * 因此进程被杀/掉电时要么是完整的旧文件、要么是完整的新文件，
     * 不会留下一个被写了一半、之后 `load()` 解析失败的 JSON。
     *
     * @return 是否真正落盘。**不再吞异常返回 Unit**——调用方必须能知道保存失败，
     *         否则会出现「保存失败仍提示已采集成功」。
     */
    fun save(ctx: Context, id: String, fp: EnvironmentFingerprint): Boolean {
        val target = File(dir(ctx), "$id.json")
        val tmp = File(dir(ctx), "$id.json.tmp")
        return runCatching {
            tmp.writeText(gson.toJson(fp))
            if (tmp.renameTo(target)) {
                true
            } else {
                // renameTo 在目标已存在且平台不支持覆盖时会失败，退回显式删除再重命名
                Diag.w(TAG, "renameTo failed for $id — retrying after delete")
                if (target.exists() && !target.delete()) return@runCatching false
                tmp.renameTo(target)
            }
        }.logFailure(TAG, "save fingerprint $id", Diag.Level.ERROR).getOrDefault(false)
            .also { ok -> if (!ok) runCatching { tmp.delete() } }
    }

    /**
     * 读取指纹包。
     *
     * 读失败（文件损坏、JSON 非法、磁盘错误）返回 null，不抛异常也不崩。
     * 解析成功后再做一次 [normalize]：老指纹包缺少本次新增的诊断字段，
     * 而 Gson 用 Unsafe 分配对象、不走 Kotlin 构造器，默认值不生效，
     * 缺字段读出来是 null —— normalize 把 null 统一成「未知」，绝不当成 0。
     */
    fun load(ctx: Context, id: String): EnvironmentFingerprint? = runCatching {
        val f = File(dir(ctx), "$id.json")
        if (!f.exists()) return@runCatching null
        val parsed = gson.fromJson(f.readText(), EnvironmentFingerprint::class.java) ?: return@runCatching null
        normalize(parsed)
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

    // ---------------------------------------------------------------- 容错

    /**
     * 把反序列化出来的对象修成可用形状。
     *
     * Gson 绕过构造器，所以即使 Kotlin 声明了非空 `List<WifiAp>`，
     * JSON 里写成 `"wifi": null` 也会真的塞进 null，之后任何 `fp.wifi.size` 都会崩。
     * 这里统一收口。
     */
    private fun normalize(fp: EnvironmentFingerprint): EnvironmentFingerprint = fp.copy(
        wifi = safeList(fp.wifi),
        cells = safeList(fp.cells).map { it.normalized() },
        ble = safeList(fp.ble),
        bonded = safeList(fp.bonded),
        networkOperatorMccMnc = fp.networkOperatorMccMnc.orEmpty(),
        simOperatorMccMnc = fp.simOperatorMccMnc.orEmpty(),
        networkOperatorName = fp.networkOperatorName.orEmpty(),
        // 状态名未知或缺失 → null（未知），不回落成 OK
        wifiState = SourceState.fromName(fp.wifiState)?.name,
        cellsState = SourceState.fromName(fp.cellsState)?.name,
        bleState = SourceState.fromName(fp.bleState)?.name,
        bondedState = SourceState.fromName(fp.bondedState)?.name,
        // 负数错误码无意义；0 是 ScanCallback.SCAN_FAILED_ALREADY_STARTED 的真实取值，保留
        bleScanError = fp.bleScanError?.takeIf { it >= 0 },
        originSource = fp.originSource?.takeIf { it.isNotBlank() }
    )

    /** 非 NR 制式上的 nci 是噪音，清掉，避免回放端误判。 */
    private fun CellTower.normalized(): CellTower =
        if (type.equals("NR", ignoreCase = true)) this else copy(nci = null)

    /** 接住 Gson 塞进来的 null。参数声明为可空，避免对非空属性做冗余判断。 */
    private fun <T> safeList(list: List<T>?): List<T> = list ?: emptyList()

    // ---------------------------------------------------------------- 摘要

    /**
     * 指纹包的可读摘要，用于 UI 列表。
     *
     * 格式：`Wi-Fi 保存数/发现数 · 基站 保存数/发现数 · 蓝牙 n · 配对 n`。
     * 「保存数/发现数」让被截断这件事可见（保留最强 20 条是设计，不是数据）；
     * 发现数为未知（旧指纹包）时只显示保存数。
     *
     * 四类全为 0 时不输出成看起来成功的 `0 · 0 · 0 · 0`，
     * 而是明确写出「未采到任何数据」并附上每一类的状态短语。
     */
    fun summary(fp: EnvironmentFingerprint): String {
        val wifi = count("Wi-Fi", fp.wifi.size, fp.wifiDiscovered, fp.wifiState)
        val cells = count("基站", fp.cells.size, fp.cellsDiscovered, fp.cellsState)
        val ble = count("蓝牙", fp.ble.size, null, fp.bleState)
        val bonded = count("配对", fp.bonded.size, null, fp.bondedState)

        val line = "$wifi · $cells · $ble · $bonded"

        val empty = fp.wifi.isEmpty() && fp.cells.isEmpty() && fp.ble.isEmpty() && fp.bonded.isEmpty()
        if (!empty) return line

        val states = listOf(
            state("Wi-Fi", fp.wifiState),
            state("基站", fp.cellsState),
            state("蓝牙", fp.bleState),
            state("配对", fp.bondedState)
        ).filterNotNull()

        return if (states.isEmpty()) {
            "$line（未采到任何数据）"
        } else {
            "$line（未采到任何数据：${states.joinToString(" / ")}）"
        }
    }

    private fun count(name: String, saved: Int, discovered: Int?, state: String?): String {
        val base = if (discovered != null && discovered != saved) "$name $saved/$discovered" else "$name $saved"
        // 保存数为 0 时把状态附在括号里，避免「0」被读成「这里真的没有」
        val label = if (saved == 0) SourceState.fromName(state)?.label else null
        return if (label != null) "$base（$label）" else base
    }

    private fun state(name: String, state: String?): String? {
        val label = SourceState.fromName(state)?.label ?: return null
        return "$name $label"
    }
}
