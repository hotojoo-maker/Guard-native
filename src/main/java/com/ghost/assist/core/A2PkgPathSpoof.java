package com.ghost.assist.core;

import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Parcel;
import android.util.Log;

import com.ghost.assist.BuildConfig;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * A2PkgPathSpoof — A2 防封 包名/路径轴（仅共存版）。
 *
 * 中间程序「掐 getPackageInfo / getApplicationInfo 咽喉」：共存包被官方检测/上报
 * 大血管（c$p / normsg / bu5 / t8）查自身时，把返回结果的 packageName 与
 * sourceDir/publicSourceDir/dataDir/nativeLibraryDir 灌成官方包态 → 让上报身份
 * 输入尽量正常（官方怎么判我方不预测）。签名轴见 A2SignatureSpoof（同闸、独立类）。
 *
 * 边界 / 评审条件（设计稿 A2接入设计稿_包名路径轴_20260629.md §9）：
 *   • R5 独立 sibling 类（不塞进 A2SignatureSpoof，单一职责）。
 *   • 只共存生效：self==官方包名（官替 flavor）整段不装（官替路径本就是官方，喂=白费）。
 *   • R3 必须 clone：业务方高频读 packageName/路径（provider/资源/文件），直改污染 PM
 *     缓存 → 业务方拿官方包名 → 找不到自身 .mn 数据目录 → 崩。故 Parcel 深拷贝、只改
 *     副本；clone 失败 fail-open（返回原对象、绝不改原对象）。
 *   • caller-selective：仅检测/上报大血管命中才喂；业务调用方原样返回。
 *   • 红线#2 不全局 hook getPackageName（365 处会崩）；红线#3 零环境读、不枚举包。
 *   • 全程 catch(Throwable)：异常只 fail-open 自身，绝不上抛连坐 ModuleMain 隐私 hook。
 */
public final class A2PkgPathSpoof {

    private static final String TAG = "NCL";
    private static final String OFFICIAL_PKG = "com.tencent.mm";

    // R1：检测/上报大血管锚点（混淆名，跨微信版本必漂 → 换版本须重核，进版本适配 SOP）。
    // 与研究线 dimmod_v2 同源（PKGNAME_AXIS_FEED_20260625 L1）。
    private static final String[] ANCHORS = {
            "normsg.c$p", "c.p.aa", "WCProbe", "t8.c0",
            "platformtools.t8", "plugin.normsg", "bu5.a", "SayHi",
    };

    // 真官方包 com.tencent.mm 的 ApplicationInfo（借其真路径喂料；查一次缓存）。
    private static volatile ApplicationInfo sOfficialAi;
    // 防重入：查官方 AI 时不再触发本 hook 的喂料分支。
    private static volatile boolean sInOfficialQuery;
    // 一次性喂料确认日志（L1 验证用，避免刷屏；新/老重载各首喂一行，对齐签名轴 NEW/OLD-overload）。
    private static volatile boolean sLoggedGpiOld;
    private static volatile boolean sLoggedGpiNew;
    private static volatile boolean sLoggedGaiOld;
    private static volatile boolean sLoggedGaiNew;

    private A2PkgPathSpoof() {}

    /**
     * 安装包名/路径轴。调用方（ModuleMain §6.7）须先过
     * AntiBanGate.isAntiBanReady() 闸（与签名轴同闸）。官替 flavor（self 即官方包名）
     * 自动跳过。
     */
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        final String self = BuildConfig.GUARD_WX_PKG;
        if (OFFICIAL_PKG.equals(self)) {
            Log.i(TAG, "[A2PKG] skip: self is official pkg (官替不需要包名/路径轴)");
            return;
        }
        // 共用 afterHook：getPackageInfo 改 packageName+路径；getApplicationInfo 改路径。fail-open。
        final XC_MethodHook gpiHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try { feedGetPackageInfo(param, self); } catch (Throwable ignored) { }
            }
        };
        final XC_MethodHook gaiHook = new XC_MethodHook() {
            @Override
            protected void afterHookedMethod(MethodHookParam param) {
                try { feedGetApplicationInfo(param, self); } catch (Throwable ignored) { }
            }
        };
        // 与签名轴 A2SignatureSpoof 同病同治：A11 只有 (String,int)；A13+（含 A16）多出
        // PackageInfoFlags/ApplicationInfoFlags 重载。2026-07-08e A15 L1 已证 c$p 真走新重载读
        // getPackageInfo；共存包名经 getPackageInfo(读 packageName) + getApplicationInfo(读 sourceDir)
        // 同咽喉，故两方法各补新重载。缺失重载 hookOne 内 catch 计 0（A11 无→不崩，铁律25）。
        int n = 0;
        n += hookOne(lpparam, "getPackageInfo",
                new Object[]{ String.class, int.class }, gpiHook);
        n += hookOne(lpparam, "getPackageInfo",
                new Object[]{ String.class, "android.content.pm.PackageManager$PackageInfoFlags" }, gpiHook);
        n += hookOne(lpparam, "getApplicationInfo",
                new Object[]{ String.class, int.class }, gaiHook);
        n += hookOne(lpparam, "getApplicationInfo",
                new Object[]{ String.class, "android.content.pm.PackageManager$ApplicationInfoFlags" }, gaiHook);
        Log.i(TAG, "[A2PKG] installed hooks=" + n + "/4 (self=" + self + ")");
    }

    /**
     * Hook one getPackageInfo/getApplicationInfo overload with a shared afterHook.
     * A11 lacks the *Flags variant → missing overload swallowed (count 0), never
     * crash init (铁律25). Mirrors A2SignatureSpoof.hookSig.
     */
    private static int hookOne(XC_LoadPackage.LoadPackageParam lpparam,
                               String method, Object[] sig, XC_MethodHook hook) {
        try {
            Object[] params = new Object[sig.length + 1];
            System.arraycopy(sig, 0, params, 0, sig.length);
            params[sig.length] = hook;
            XposedHelpers.findAndHookMethod(
                    "android.app.ApplicationPackageManager", lpparam.classLoader, method, params);
            return 1;
        } catch (Throwable t) {
            return 0;
        }
    }

    /** getPackageInfo(self) afterHook：检测 caller 命中时，clone 后改 packageName + 路径。 */
    private static void feedGetPackageInfo(XC_MethodHook.MethodHookParam param, String self) {
        if (param.args == null || param.args.length < 1) return;
        Object pkgArg = param.args[0];
        if (pkgArg == null || !self.equals(pkgArg.toString())) return;   // 只动自身包
        if (!isDetectionCaller()) return;                                // caller-selective
        Object r = param.getResult();
        if (!(r instanceof PackageInfo)) return;
        PackageInfo copy = deepCopy((PackageInfo) r);                    // R3：clone 深拷贝
        if (copy == null) return;                                        // clone 失败 → fail-open（不改原对象）
        copy.packageName = OFFICIAL_PKG;
        if (copy.applicationInfo != null) {
            feedOfficialPaths(copy.applicationInfo, param.thisObject);
        }
        param.setResult(copy);
        boolean gpiNew = param.args.length >= 2 && !(param.args[1] instanceof Integer);
        if (gpiNew ? !sLoggedGpiNew : !sLoggedGpiOld) {
            if (gpiNew) sLoggedGpiNew = true; else sLoggedGpiOld = true;
            String sd = (copy.applicationInfo != null) ? copy.applicationInfo.sourceDir : "?";
            Log.i(TAG, "[A2PKG] fed getPackageInfo via " + (gpiNew ? "NEW" : "OLD")
                    + "-overload pkg=" + copy.packageName + " sourceDir=" + sd);
        }
    }

    /** getApplicationInfo(self) afterHook：c$p.aa / normsg.u 组读 sourceDir 走此。 */
    private static void feedGetApplicationInfo(XC_MethodHook.MethodHookParam param, String self) {
        if (param.args == null || param.args.length < 1) return;
        Object pkgArg = param.args[0];
        if (pkgArg == null || !self.equals(pkgArg.toString())) return;
        if (!isDetectionCaller()) return;
        Object r = param.getResult();
        if (!(r instanceof ApplicationInfo)) return;
        ApplicationInfo copy = deepCopy((ApplicationInfo) r);            // R3：clone 深拷贝
        if (copy == null) return;
        feedOfficialPaths(copy, param.thisObject);
        param.setResult(copy);
        boolean gaiNew = param.args.length >= 2 && !(param.args[1] instanceof Integer);
        if (gaiNew ? !sLoggedGaiNew : !sLoggedGaiOld) {
            if (gaiNew) sLoggedGaiNew = true; else sLoggedGaiOld = true;
            Log.i(TAG, "[A2PKG] fed getApplicationInfo via " + (gaiNew ? "NEW" : "OLD")
                    + "-overload pkg=" + copy.packageName + " sourceDir=" + copy.sourceDir);
        }
    }

    /** R4：覆盖全部 4 路径字段；优先借真官方包 AI，拿不到则字符串兜底。 */
    private static void feedOfficialPaths(ApplicationInfo ai, Object pmObj) {
        if (ai == null) return;
        ai.packageName = OFFICIAL_PKG;
        ApplicationInfo off = officialAi(pmObj);
        if (off != null) {
            ai.sourceDir = off.sourceDir;
            ai.publicSourceDir = off.publicSourceDir;
            ai.dataDir = off.dataDir;
            ai.nativeLibraryDir = off.nativeLibraryDir;
        } else {
            ai.sourceDir = repl(ai.sourceDir);
            ai.publicSourceDir = repl(ai.publicSourceDir);
            ai.dataDir = repl(ai.dataDir);
            ai.nativeLibraryDir = repl(ai.nativeLibraryDir);
        }
    }

    private static String repl(String s) {
        return (s == null) ? null : s.replace(BuildConfig.GUARD_WX_PKG, OFFICIAL_PKG);
    }

    /** 借真官方包 com.tencent.mm 的 ApplicationInfo（查一次缓存；官方包未装→null→字符串兜底）。 */
    private static ApplicationInfo officialAi(Object pmObj) {
        ApplicationInfo cached = sOfficialAi;
        if (cached != null) return cached;
        if (sInOfficialQuery) return null;
        try {
            sInOfficialQuery = true;
            if (pmObj instanceof PackageManager) {
                sOfficialAi = ((PackageManager) pmObj).getApplicationInfo(OFFICIAL_PKG, 0);
            }
        } catch (Throwable t) {
            // 官方包未装 / 不可见 → 走字符串兜底
        } finally {
            sInOfficialQuery = false;
        }
        return sOfficialAi;
    }

    /** R3：Parcel 往返深拷贝；任何异常返回 null（调用方据此 fail-open，绝不改原对象）。 */
    private static PackageInfo deepCopy(PackageInfo pi) {
        Parcel p = null;
        try {
            p = Parcel.obtain();
            pi.writeToParcel(p, 0);
            p.setDataPosition(0);
            return PackageInfo.CREATOR.createFromParcel(p);
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) p.recycle();
        }
    }

    private static ApplicationInfo deepCopy(ApplicationInfo ai) {
        Parcel p = null;
        try {
            p = Parcel.obtain();
            ai.writeToParcel(p, 0);
            p.setDataPosition(0);
            return ApplicationInfo.CREATOR.createFromParcel(p);
        } catch (Throwable t) {
            return null;
        } finally {
            if (p != null) p.recycle();
        }
    }

    /** caller-selective：当前调用栈是否检测/上报大血管发起（ANCHORS 命中）。 */
    private static boolean isDetectionCaller() {
        try {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            for (StackTraceElement e : st) {
                String s = e.getClassName() + "." + e.getMethodName();
                for (String a : ANCHORS) {
                    if (s.indexOf(a) >= 0) return true;
                }
            }
        } catch (Throwable ignored) {
        }
        return false;
    }
}
