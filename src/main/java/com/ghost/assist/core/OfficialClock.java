package com.ghost.assist.core;

import android.util.Log;

import java.lang.reflect.Method;

/**
 * OfficialClock — 借官方包自己的眼睛读「官方对时」jy0.hd.b()（= MicroMsg.TimeHelper 地板值），
 * 作 LeaseClock 第二独立可信时间源（防「屏蔽我方服务器 + 反复重启冻结 trustedNow」的高手白嫖）。
 *
 * 为什么是 hd.b() 不是 hd.c()（L1 · recon/convtime_L1_evidence_20260627.log · G92/F86）：
 *   • hd.b() = 服务器锚定(MMKV "time") + elapsedRealtime 外推 + 跨重启不回退 + 抗墙钟改
 *     （改钟 +1h / 回拨 2 天，hd.b 只按真实秒走、纹丝不动）。这正是要的防冻特性。
 *   • hd.c() 跟墙钟（改钟即被骗）→ 禁用。
 *   • 全新装 / 未登录 / 未同步 → 无真服务器锚 → 预期返回 0 或墙钟 → 调用方按「无值」fail-open。
 *
 * 边界（安全官红线 #7 + 铁律 23 + 铁律 5）：
 *   • 纯读 / observe-only —— 反射读静态方法 b()，绝不改官方对时本体、不注入官方 JNI 链、不 hook。
 *   • 零新增检测面：只读官方包自己的 TimeHelper，不读 ro.boot.* / 不新增环境读取面（守 KPI）。
 *   • 异常一律吞掉返回 0（= fail-open 信号），绝不让读取失败影响冷启动 / 误杀正版。
 *   • 不进任何已验证 hook 回调体（铁律 29 / F-31）；仅 ModuleMain 冷启动反射调用一次。
 */
public final class OfficialClock {

    private static final String TAG = "NCL";
    private static final String CLS     = "jy0.hd";  // MicroMsg.TimeHelper（8.0.71 混淆名，L1 2026-06-27 同机同版）
    private static final String M_FLOOR = "b";       // public static long b() 无参 → epoch ms（抗改表地板）

    private OfficialClock() {}

    /**
     * 读官方对时 hd.b()。返回 epoch ms；读不到 / 异常 / 无值（≤0）→ 0（调用方 fail-open）。
     *
     * @param cl 必须传 app.getClassLoader()——8.0.71 tinker 热补丁下 jy0.hd 由 app loader 持有。
     */
    public static long readOfficialNowMs(ClassLoader cl) {
        if (cl == null) return 0L;
        try {
            Class<?> hd = cl.loadClass(CLS);
            Method b = hd.getMethod(M_FLOOR);
            Object v = b.invoke(null);               // 静态方法，receiver = null
            if (v instanceof Long) {
                long ms = (Long) v;
                return ms > 0L ? ms : 0L;
            }
            return 0L;
        } catch (Throwable t) {
            Log.w(TAG, "[oclk] read official time skip: " + t.getClass().getSimpleName());
            return 0L;
        }
    }
}
