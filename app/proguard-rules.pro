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

