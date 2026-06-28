package com.ghost.assist.moduleB;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.ghost.assist.BuildConfig;
import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.StateMachine;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P_IMPORT —— 密友 / 密群 批量导入。两条路径走不同 Activity（8.0.71 L1 实证）。
 *
 * ① 密友：复用 com.tencent.mm.ui.contact.SelectContactUI（选人器）。
 *      extras: list_type=1, list_attr=16471, already_select_contact=现有 wxid CSV（预选）,
 *              titile=标题（⚠️微信原拼写 titile，非 title）, from_select_contact=true
 *      返回 setResult(-1): Select_Contact = 选中全集 CSV → diff 增删一体。
 *      证据: bug排查/probe_selectcontact_IN_8071.log（仅 list_type=1 实证）
 *
 * ② 密群：用 com.tencent.mm.ui.contact.GroupCardSelectUI（选群器，与选人器不是一回事）。
 *      ⚠️ 旧注释「list_type=2=密群」是竞品 mn1(8.0.70.2)/A3 文档推断，8.0.71 已证伪：
 *         群不走 SelectContactUI；真实入口（群发助手→选择朋友→从群聊导入）= GroupCardSelectUI。
 *      extras: group_multi_select=true, group_select_need_result=true, group_select_type=true,
 *              max_limit_num=Integer.MAX_VALUE  （无「预选」key → 拉起时无法预勾已隐群）
 *      返回 setResult(-1): Select_Conv_User = 选中 @chatroom CSV。
 *      证据: bug排查/probe_groupselect_8071.log（2026-06-02 用户现场多选实证）
 *      因无预选 → 群侧只 union 加；删除走 SettingsEntry 密群管理面板（路线1）。
 *
 * 存储/门控：写 Bridge 密友(wxid)/密群(@chatroom) 集合（MMKV）；隐不隐跟随状态机 V/H。
 * 【门控锁定】只读/只写 Bridge 隐藏集合 + 调微信原生 Activity；不碰状态机/授权/口令。
 */
public final class ContactImportGuard {

    private static final String TAG = "NCL";

    private static final String SELECT_UI  = "com.tencent.mm.ui.contact.SelectContactUI";
    // 启动目标包名 = 宿主包（官替=com.tencent.mm / 共存=com.tencent.mn），随 flavor 自动注入。
    // 写死 com.tencent.mm 会让共存版跨包拉官方包选人器被系统拦截 → 加不进密友/密群。
    private static final String WECHAT_PKG = BuildConfig.GUARD_WX_PKG;

    // SelectContactUI Intent extra keys（L1 实证；titile 是微信原拼写错误，勿改）
    private static final String EX_LIST_TYPE = "list_type";
    private static final String EX_LIST_ATTR = "list_attr";
    private static final String EX_ALREADY   = "already_select_contact";
    private static final String EX_TITLE     = "titile";
    private static final String EX_FROM      = "from_select_contact";
    private static final String EX_RESULT    = "Select_Contact";

    private static final int LIST_TYPE_BUDDY = 1;
    private static final int LIST_TYPE_GROUP = 2;
    private static final int LIST_ATTR       = 16471;

    // GroupCardSelectUI（密群选群器）Intent keys（8.0.71 L1 实证 tools/sel_dump2.log）
    private static final String GROUP_SELECT_UI   = "com.tencent.mm.ui.contact.GroupCardSelectUI";
    private static final String EX_GROUP_MULTI    = "group_multi_select";
    private static final String EX_GROUP_NEED_RES = "group_select_need_result";
    private static final String EX_GROUP_TYPE     = "group_select_type";
    private static final String EX_MAX_LIMIT      = "max_limit_num";
    private static final String EX_CONV_RESULT    = "Select_Conv_User";

    /** 0=无 / 1=密友 / 2=密群 —— 只消费「我们自己发起」的那次 setResult，避免误吞微信其它选人场景。 */
    private static volatile int sExpect = 0;

    private ContactImportGuard() {}

    // ------------------------------------------------------------------
    // 安装：hook SelectContactUI 的 setResult，捕获用户选完的结果
    // ------------------------------------------------------------------
    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedHelpers.findAndHookMethod(Activity.class, "setResult",
                    int.class, Intent.class, new XC_MethodHook() {
                        @Override
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (sExpect == 0) return;
                                Object self = param.thisObject;
                                if (self == null) return;
                                String cls = self.getClass().getName();
                                Intent data = (Intent) param.args[1];
                                if (sExpect == LIST_TYPE_BUDDY && SELECT_UI.equals(cls)) {
                                    if (data == null) { sExpect = 0; return; }
                                    consumeResult(data.getStringExtra(EX_RESULT), LIST_TYPE_BUDDY);
                                } else if (sExpect == LIST_TYPE_GROUP && GROUP_SELECT_UI.equals(cls)) {
                                    if (data == null) { sExpect = 0; return; }
                                    consumeResult(data.getStringExtra(EX_CONV_RESULT), LIST_TYPE_GROUP);
                                }
                            } catch (Throwable t) {
                                sExpect = 0;
                                Log.w(TAG, "[CIG] capture err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[CIG] ContactImportGuard installed");
        } catch (Throwable t) {
            Log.w(TAG, "[CIG] install fail: " + t);
        }
    }

    // ------------------------------------------------------------------
    // 拉起选择器（由 SettingsEntry 的「添加密友 / 添加密群」onClick 调）
    // ------------------------------------------------------------------
    public static void launchSelectBuddy(Activity act) {
        launch(act, LIST_TYPE_BUDDY, Bridge.getInstance().getWxids(), "\u9009\u62e9\u5bc6\u53cb");
    }

    public static void launchSelectGroup(Activity act) {
        // 密群走 GroupCardSelectUI（非 SelectContactUI）。
        // L1 实证(bug排查/probe_groupkeys_8071.log)：读 already_select_contact → 传现有密群 = 预选 → 增删一体。
        if (!StateMachine.getInstance().isVipAuthorized()) {
            Log.i(TAG, "[CIG] group launch blocked: not authorized");
            return;
        }
        if (act == null) {
            Log.w(TAG, "[CIG] group launch abort: no activity");
            return;
        }
        try {
            Set<String> preset = Bridge.getInstance().getGroupIds();
            Intent it = new Intent();
            it.setClassName(WECHAT_PKG, GROUP_SELECT_UI);
            it.putExtra(EX_GROUP_MULTI, true);
            it.putExtra(EX_GROUP_NEED_RES, true);
            it.putExtra(EX_GROUP_TYPE, true);
            it.putExtra(EX_MAX_LIMIT, Integer.MAX_VALUE);
            it.putExtra(EX_ALREADY, join(preset));   // 预选已隐密群 → 进去就勾上（增删一体）
            sExpect = LIST_TYPE_GROUP;
            act.startActivity(it);
            Log.i(TAG, "[CIG] launch GroupCardSelectUI preset=" + preset.size());
        } catch (Throwable t) {
            sExpect = 0;
            Log.w(TAG, "[CIG] launchSelectGroup fail: " + t);
        }
    }

    private static void launch(Activity act, int listType, Set<String> preset, String title) {
        // 授权门（总判定，复用 StateMachine.isVipAuthorized；与 AntiRecall 杂项功能同口径）：
        // 没授权 → 添加不进去。v1 stub=true，v2 接 LicenseGate 后自动生效。
        if (!StateMachine.getInstance().isVipAuthorized()) {
            Log.i(TAG, "[CIG] launch blocked: not authorized");
            return;
        }
        if (act == null) {
            Log.w(TAG, "[CIG] launch abort: no activity");
            return;
        }
        try {
            Intent it = new Intent();
            it.setClassName(WECHAT_PKG, SELECT_UI);
            it.putExtra(EX_LIST_TYPE, listType);
            it.putExtra(EX_LIST_ATTR, LIST_ATTR);
            it.putExtra(EX_ALREADY, join(preset));
            it.putExtra(EX_TITLE, title);
            it.putExtra(EX_FROM, true);
            sExpect = listType;
            act.startActivity(it);
            Log.i(TAG, "[CIG] launch type=" + listType + " preset=" + preset.size());
        } catch (Throwable t) {
            sExpect = 0;
            Log.w(TAG, "[CIG] launch fail: " + t);
        }
    }

    // ------------------------------------------------------------------
    // 结果落地：返回全集 diff 已存集 → add/remove → 热切刷新
    // ------------------------------------------------------------------
    private static void consumeResult(String csv, int mode) {
        sExpect = 0;
        // 授权门兜底：未授权不落库（正常路径 launch 已挡，这里防御 sExpect 异常置位）
        if (!StateMachine.getInstance().isVipAuthorized()) {
            Log.i(TAG, "[CIG] result dropped: not authorized");
            return;
        }
        Set<String> result = parseCsv(csv);
        Bridge br = Bridge.getInstance();

        if (mode == LIST_TYPE_BUDDY) {
            // SelectContactUI 预选 already_select_contact → 返回 = 完整选中集 → diff 增删一体
            Set<String> cur = new HashSet<>(br.getWxids());
            int added = 0, removed = 0;
            for (String id : result) if (!cur.contains(id)) { br.addWxid(id);    added++; }
            for (String id : cur)    if (!result.contains(id)) { br.removeWxid(id); removed++; }
            Log.i(TAG, "[CIG] buddy import result=" + result.size()
                    + " added=" + added + " removed=" + removed);
        } else if (mode == LIST_TYPE_GROUP) {
            // GroupCardSelectUI 已传 already_select_contact 预选 → 返回 = 完整选中集 → diff 增删一体。
            // 过滤只收 @chatroom（防御非群 id 混入）。
            Set<String> cur = new HashSet<>(br.getGroupIds());
            int added = 0, removed = 0, skipped = 0;
            for (String id : result) {
                if (id == null || !Bridge.isGroupId(id)) { skipped++; continue; }
                if (!cur.contains(id)) { br.addGroupId(id); added++; }
            }
            for (String id : cur) if (!result.contains(id)) { br.removeGroupId(id); removed++; }
            Log.i(TAG, "[CIG] group import (diff) result=" + result.size()
                    + " added=" + added + " removed=" + removed + " skipped=" + skipped);
        } else {
            return;
        }
        RefreshBus.getInstance().notifyHiddenChanged(StateMachine.getInstance().isActive());
        // P_IMPORT: 即时刷新设置面板「密友/密群列表 已选择 N 个」计数（修导入成功 UI 不立刻刷新）。
        try { SettingsEntry.refreshImportCounts(); } catch (Throwable ignored) {}
    }

    // ------------------------------------------------------------------
    private static String join(Set<String> set) {
        if (set == null || set.isEmpty()) return "";
        StringBuilder sb = new StringBuilder();
        for (String s : set) {
            if (s == null || s.isEmpty()) continue;
            if (sb.length() > 0) sb.append(',');
            sb.append(s);
        }
        return sb.toString();
    }

    private static Set<String> parseCsv(String csv) {
        Set<String> out = new HashSet<>();
        if (csv == null || csv.isEmpty()) return out;
        for (String s : csv.split(",")) {
            String t = s.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }
}
