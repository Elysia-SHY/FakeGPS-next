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

