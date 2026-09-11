package com.mockrun.app.data.repository

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.provider.Settings
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.mockrun.app.domain.model.MultiTargetRule
import com.mockrun.app.domain.model.TargetMockMode
import com.mockrun.app.location.RootSuBridge
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import javax.inject.Inject
import javax.inject.Singleton

data class InstalledAppItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable?,
    val isSystem: Boolean = false,
    val userId: Int = 0
)

@Singleton
class MultiTargetRepository @Inject constructor(
    @ApplicationContext private val context: Context,
    private val rootBridge: RootSuBridge
) {
    companion object {
        private const val PREFS_NAME = "multi_target_prefs"
        private const val KEY_RULES_JSON = "key_rules_json"
        const val SETTINGS_GLOBAL_KEY = "fake_gps_multitarget"
        const val SYSTEM_FILE_PATH = "/data/system/fake_gps_multitarget.json"
        const val LOCAL_TMP_FILE_PATH = "/data/local/tmp/fake_gps_multitarget.json"
    }

    private val gson = Gson()
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())

    private val _rules = MutableStateFlow<List<MultiTargetRule>>(emptyList())
    val rules: StateFlow<List<MultiTargetRule>> = _rules.asStateFlow()

    init {
        loadRules()
    }

    private fun loadRules() {
        val sp = context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
        val json = sp.getString(KEY_RULES_JSON, null)
        if (!json.isNullOrEmpty()) {
            runCatching {
                val type = object : TypeToken<List<MultiTargetRule>>() {}.type
                val list: List<MultiTargetRule> = gson.fromJson(json, type)
                _rules.value = list
            }
        }
    }

    fun addOrUpdateRule(rule: MultiTargetRule) {
        val current = _rules.value.toMutableList()
        val index = current.indexOfFirst { it.key == rule.key }
        if (index >= 0) {
            current[index] = rule
        } else {
            current.add(rule)
        }
        persistRules(current)
    }

    fun removeRule(key: String) {
        val current = _rules.value.filterNot { it.key == key }
        persistRules(current)
    }

    fun toggleRule(key: String, isEnabled: Boolean) {
        val current = _rules.value.map {
            if (it.key == key) it.copy(isEnabled = isEnabled) else it
        }
        persistRules(current)
    }

    fun updateCoordinates(key: String, lat: Double, lon: Double) {
        val current = _rules.value.map {
            if (it.key == key) it.copy(latitude = lat, longitude = lon) else it
        }
        persistRules(current)
    }

    fun setRuleMode(key: String, mode: TargetMockMode) {
        val current = _rules.value.map {
            if (it.key == key) it.copy(mode = mode) else it
        }
        persistRules(current)
    }

    private fun persistRules(list: List<MultiTargetRule>) {
        _rules.value = list
        val json = gson.toJson(list)

        // 1. SharedPreferences
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            .edit()
            .putString(KEY_RULES_JSON, json)
            .apply()

        // 2. Settings.Global (Zero-IPC fast channel)
        runCatching {
            Settings.Global.putString(context.contentResolver, SETTINGS_GLOBAL_KEY, json)
        }

        // 3. Local Cache File
        runCatching {
            val cacheFile = java.io.File(context.cacheDir, "multitarget_rules.json")
            cacheFile.writeText(json)
            cacheFile.setReadable(true, false)
        }

        // 4. Asynchronous Root push to system_server directories
        scope.launch {
            if (rootBridge.isRootAvailable()) {
                val escapedJson = json.replace("'", "'\\''")
                val cmd = buildString {
                    append("echo '$escapedJson' > $SYSTEM_FILE_PATH 2>/dev/null; ")
                    append("chmod 666 $SYSTEM_FILE_PATH 2>/dev/null; ")
                    append("echo '$escapedJson' > $LOCAL_TMP_FILE_PATH 2>/dev/null; ")
                    append("chmod 666 $LOCAL_TMP_FILE_PATH 2>/dev/null; ")
                    append("settings put global $SETTINGS_GLOBAL_KEY '$escapedJson' 2>/dev/null")
                }
                rootBridge.executeCommand(cmd)
            }
        }
    }

    /**
     * Query all installed applications to populate the application selector.
     * Prioritizes user applications over system applications and guarantees complete enumeration.
     */
    suspend fun getInstalledUserApps(): List<InstalledAppItem> = withContext(Dispatchers.IO) {
        val pm = context.packageManager
        val items = mutableListOf<InstalledAppItem>()
        val seenPackages = mutableSetOf<String>()

        // 1. Query all launchable activities
        val mainIntent = android.content.Intent(android.content.Intent.ACTION_MAIN, null).apply {
            addCategory(android.content.Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = runCatching {
            pm.queryIntentActivities(mainIntent, 0)
        }.getOrDefault(emptyList())

        for (resolve in resolveInfos) {
            val pkg = resolve.activityInfo?.packageName ?: continue
            if (pkg == context.packageName) continue // Skip Fake GPS itself
            if (seenPackages.add(pkg)) {
                val appName = runCatching { resolve.loadLabel(pm).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg
                val icon = runCatching { resolve.loadIcon(pm) }.getOrNull()
                val appInfo = resolve.activityInfo?.applicationInfo
                val isSystem = if (appInfo != null) {
                    ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) &&
                            ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                } else false

                items.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        isSystem = isSystem,
                        userId = 0
                    )
                )
            }
        }

        // 2. Query all installed applications to discover apps without standard launcher entry
        val allInstalled = runCatching {
            pm.getInstalledApplications(PackageManager.GET_META_DATA)
        }.getOrDefault(emptyList())

        for (appInfo in allInstalled) {
            val pkg = appInfo.packageName ?: continue
            if (pkg == context.packageName) continue
            if (seenPackages.add(pkg)) {
                val isSystem = ((appInfo.flags and ApplicationInfo.FLAG_SYSTEM) != 0) &&
                        ((appInfo.flags and ApplicationInfo.FLAG_UPDATED_SYSTEM_APP) == 0)
                val appName = runCatching { pm.getApplicationLabel(appInfo).toString() }.getOrNull()
                    ?.takeIf { it.isNotBlank() } ?: pkg
                val icon = runCatching { pm.getApplicationIcon(appInfo) }.getOrNull()

                items.add(
                    InstalledAppItem(
                        packageName = pkg,
                        appName = appName,
                        icon = icon,
                        isSystem = isSystem,
                        userId = 0
                    )
                )
            }
        }

        // Prioritize non-system (user-installed) apps first, then alphabetical
        items.sortedWith(
            compareBy<InstalledAppItem> { it.isSystem }
                .thenBy { it.appName.lowercase() }
        )
    }
}
