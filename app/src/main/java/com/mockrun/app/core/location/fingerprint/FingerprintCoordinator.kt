package com.mockrun.app.core.location.fingerprint

import android.content.Context
import com.mockrun.app.hook.HookStateBridge
import com.mockrun.app.util.Diag
import com.mockrun.app.util.logFailure

/**
 * 环境指纹的采集 / 启用 / 停用编排。
 *
 * 启用时做三件事：
 *  1. 导出指纹 JSON 到 /data/local/tmp/fakegps_fp_<id>.json（hook 侧读这里）
 *  2. 用 setprop 发布 fingerprint id（root 可用时）
 *  3. 落一份到 Settings.Global 作为无 root 回退
 */
object FingerprintCoordinator {

    private const val TAG = "FingerprintCoordinator"

    /** hook 侧读取的 SystemProperty 键，必须与 FingerprintReplayer 一致 */
    private const val PROP_FP_ID = "debug.fakegps.fingerprint_id"

    /** Settings.Global 回退键 */
    const val SETTINGS_KEY = "fake_gps_fingerprint_id"

    /**
     * 采集并保存。调用方必须确保虚拟定位已停止。
     */
    suspend fun captureAndSave(
        context: Context,
        id: String,
        lat: Double,
        lon: Double
    ): EnvironmentFingerprint {
        val fp = FingerprintCollector(context).collect(lat, lon)
        FingerprintStore.save(context, id, fp)
        Diag.i(TAG, "captured fingerprint '$id': ${FingerprintStore.summary(fp)}")
        return fp
    }

    /**
     * 启用指纹：导出给 hook 侧并发布 id。
     *
     * @return 是否导出成功
     */
    fun activate(context: Context, id: String): Boolean {
        val json = FingerprintStore.exportJson(context, id)
        if (json.isNullOrEmpty()) {
            Diag.w(TAG, "activate('$id') failed: fingerprint not found")
            return false
        }

        val exported = exportToTmp(json, id)
        publishId(context, id)

        if (!exported) {
            Diag.w(
                TAG,
                "activate('$id'): /data/local/tmp export failed — hook side will read nothing. " +
                    "Root is required for this channel."
            )
        }
        return exported
    }

    /** 停用指纹：清掉发布的 id，hook 侧随即回落到真实值。 */
    fun deactivate(context: Context) {
        runCatching {
            HookStateBridge.runSu("setprop $PROP_FP_ID '' 2>/dev/null; " +
                "settings delete global $SETTINGS_KEY 2>/dev/null; " +
                "rm -f /data/local/tmp/fakegps_fp_*.json 2>/dev/null")
        }.logFailure(TAG, "deactivate fingerprint", Diag.Level.DEBUG)
    }

    /**
     * 把指纹 JSON 写到 /data/local/tmp。
     *
     * 必须走 root：应用进程无法直接写 /data/local/tmp
     * （那是 shell_data_file，且 app 的 SELinux 域无写权限）。
     */
    private fun exportToTmp(json: String, id: String): Boolean {
        val path = "/data/local/tmp/fakegps_fp_$id.json"
        // 单引号内的 JSON 不含单引号（Gson 默认不转义为 \u0027 但字段值里没有撇号），
        // 仍然做一次替换以防万一。
        val safe = json.replace("'", "'\\''")
        val cmd = buildString {
            append("rm -f /data/local/tmp/fakegps_fp_*.json 2>/dev/null; ")
            append("echo '$safe' > $path 2>/dev/null; ")
            append("chmod 644 $path 2>/dev/null; ")
            append("ls -l $path")
        }
        val out = HookStateBridge.runSu(cmd) ?: return false
        return out.contains(path)
    }

    private fun publishId(context: Context, id: String) {
        runCatching {
            HookStateBridge.runSu("setprop $PROP_FP_ID '$id' 2>/dev/null")
        }.logFailure(TAG, "publish fingerprint id via setprop", Diag.Level.DEBUG)

        // 无 root 回退：Settings.Global 任何进程都可读
        runCatching {
            android.provider.Settings.Global.putString(context.contentResolver, SETTINGS_KEY, id)
        }.logFailure(TAG, "publish fingerprint id via Settings.Global", Diag.Level.DEBUG)
    }

    /**
     * 从 Settings.Global 读回 fingerprint id（hook 侧无 root 时的通道）。
     */
    fun readPublishedId(context: Context): String =
        runCatching {
            android.provider.Settings.Global.getString(context.contentResolver, SETTINGS_KEY)
        }.getOrNull().orEmpty()
}
