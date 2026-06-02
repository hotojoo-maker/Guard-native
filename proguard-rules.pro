# Guard Native — ProGuard / R8 rules (release build only; debug is minifyEnabled false)
#
# Phase 0 ② 收窄：从「-keep core.** {*;}」改为「只保必须的，其余混淆」。
# 目的：混掉 Java 业务逻辑（过滤器/状态/桥），抬高「抄 hook 点」成本；
#       但保住 JNI 链 / 枚举 / Xposed 入口 / 故意留亮的诱饵。

# ── LSPosed 入口（assets/xposed_init 按类名引用，禁止改名）──
-keep class com.ghost.assist.ModuleMain { *; }
-keep class * implements de.robv.android.xposed.IXposedHookLoadPackage { *; }
-keep class * implements de.robv.android.xposed.IXposedHookZygoteInit { *; }

# ── JNI 硬红线 ──
# libguardcore.so 用静态名字绑定：Java_com_ghost_assist_core_NativeBridge_native*
# （guard_core.cpp 无 RegisterNatives / JNI_OnLoad）。一旦 NativeBridge 类名或 native
# 方法名被混淆 → 运行时 UnsatisfiedLinkError → 整个 native 层失效。必须整类保留。
-keep class com.ghost.assist.core.NativeBridge { *; }
-keepclasseswithmembernames,includedescriptorclasses class * {
    native <methods>;
}

# ── 枚举：name() 持久化、valueOf() 还原（AppConfig.Mode / Bridge.NotifyPolicy 等）──
# 常量名被改 → valueOf("PROD") 抛异常 / 配置读取失效。全枚举保留。
-keep enum com.ghost.assist.** { *; }

# ── 蜜罐：故意把诱饵留亮，方便破解者关键词命中（真锁在 SO，不在这里）──
-keepclassmembernames class com.ghost.assist.core.StateMachine {
    boolean isVipAuthorized();
}

# 其余 com.ghost.assist.**（过滤器 / StateMachine 主体 / Bridge / AppConfig / debug / moduleB-D）
# 一律交给 R8 混淆，不再整包 keep。

# ── 第三方 ──
# MMKV
-keepclassmembers class com.tencent.mmkv.** { *; }
-dontwarn com.tencent.mmkv.**

# Xposed（compileOnly，运行时由框架提供）
-dontwarn de.robv.android.xposed.**
