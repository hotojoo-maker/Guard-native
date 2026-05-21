# Guard Native — ProGuard rules (release build)

# Keep LSPosed entry point
-keep class com.ghost.assist.ModuleMain { *; }

# Keep all IXposedHookLoadPackage implementations
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }

# Keep StateMachine, AppConfig, Bridge (reflected at runtime)
-keep class com.ghost.assist.core.** { *; }

# Keep debug classes in DEV builds only — release strips them via minify
# (DebugServer / OverlayWindow / StatusNotification are Android Service/View subclasses
#  and will be kept by the Android plugin automatically)

# MMKV
-keepclassmembers class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# Xposed
-dontwarn de.robv.android.xposed.**
