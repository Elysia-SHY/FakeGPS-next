# Keep OSMDroid classes
-keep class org.osmdroid.** { *; }

# Keep Xposed Hook classes (required for LSPosed entrypoint reflection)
-keep class com.mockrun.app.hook.** { *; }

# Keep Room DB & Entities
-keep class com.mockrun.app.data.db.** { *; }

# Keep Gson serialized model classes
-keep class com.mockrun.app.domain.model.** { *; }
-keepattributes Signature
-keepattributes *Annotation*

# Keep Gson TypeToken generic signatures.
# An anonymous `object : TypeToken<List<T>>() {}` loses its generic superclass
# signature under R8, which makes Gson throw
# "TypeToken must be created with a type argument" at runtime.
# The repositories now build their types via TypeToken.getParameterized(), so they
# no longer depend on this; the rules stay as a second line of defence for any
# future TypeToken subclass.
-keep,allowobfuscation,allowshrinking class com.google.gson.reflect.TypeToken
-keep,allowobfuscation,allowshrinking class * extends com.google.gson.reflect.TypeToken
-keep class sun.misc.Unsafe { *; }
-dontwarn sun.misc.**


# Keep 环境指纹的持久化模型。
#
# 为什么必须保留：指纹 JSON 由 Gson 按**字段名**写出（FingerprintStore 里
# gson.toJson(fp)），而回放端 FingerprintReplayer 按硬编码字符串键读取
# （optString("originLat")、optJSONArray("wifi")、optInt("rssi")…共 30 余个键）。
# R8 默认会把这些字段重命名成单字母（实测 originLat -> h、wifi -> l、wifiState -> v），
# 于是 release 包里写出的 JSON 键与读取端期望的键完全对不上：
# 回放读到的坐标是 0.0、列表是空，且不报错 —— 静默失效，最难排查的那一类。
#
# 实测证据（assembleRelease + dexdump 反查 DEX）：
#   修复前 EnvironmentFingerprint -> Li4/d;，字段为 h/i/j/k/l/m/n/o/p/q/r/s/t/u/v/w/x/y/z/A/B
# 这些字段名与 JSON 键是同一份契约，因此不能被混淆。
-keep class com.mockrun.app.core.location.fingerprint.** { *; }

# SourceState 的枚举常量名会以 name 形式写进 JSON（wifiState/cellsState/...），
# 读回时用 SourceState.fromName() 按名字解析，同样不能被混淆。
-keepclassmembers enum com.mockrun.app.core.location.fingerprint.SourceState {
    public static **[] values();
    public static ** valueOf(java.lang.String);
}