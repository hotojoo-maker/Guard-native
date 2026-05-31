package com.ghost.assist.moduleB;

import android.app.Activity;
import android.content.Intent;
import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.RefreshBus;
import com.ghost.assist.core.StateMachine;

import java.util.HashSet;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedHelpers;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P_IMPORT —— 密友 / 密群 批量导入（复用微信官方多选选择器 SelectContactUI）。
 *
 * 机制（L1 frida 实证 2026-05-29，证据 bug排查/probe_selectcontact_IN_8071.log）：
 *   拉起：Intent → com.tencent.mm.ui.contact.SelectContactUI，extras：
 *     list_type              = 1（密友）/ 2（密群）
 *     list_attr              = 16471
 *     already_select_contact = 现有集合 CSV（预选，进去就勾上 → 增删一体的前提）
 *     titile                 = 标题（⚠️微信原拼写 titile，非 title）
 *     from_select_contact    = true
 *   返回：SelectContactUI.setResult(-1, Intent)，extra Select_Contact = 选中全集 CSV。
 *
 * 增删一体：返回的是「当前完整选中集」（非增量）→ diff 已存集：新增的 add、缺失的 remove。
 * 删除天然由微信原生 UI 处理（点顶部头像取消），我们不另做删除页。
 *
 * 存储/门控：写 Bridge 现有 密友(wxid)/密群(@chatroom) 集合（MMKV）；隐不隐跟随状态机 V/H。
 *
 * 【门控锁定】只读/只写 Bridge 的隐藏集合 + 调微信原生 Activity；不碰状态机/授权/口令。
 *
 * 接线（待 guard-auth-review 后做）：
 *   1. ModuleMain.install() → ContactImportGuard.install(lpparam)
 *   2. SettingsEntry「添加密友」onClick → ContactImportGuard.launchSelectBuddy(activity)
 *      SettingsEntry「添加密群」onClick → ContactImportGuard.launchSelectGroup(activity)
 */
public final class ContactImportGuard {

    private static final String TAG = "NCL";

    private static final String SELECT_UI  = "com.tencent.mm.ui.contact.SelectContactUI";
    private static final String WECHAT_PKG = "com.tencent.mm";

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
                                if (self == null
                                        || !SELECT_UI.equals(self.getClass().getName())) return;
                                Intent data = (Intent) param.args[1];
                                if (data == null) { sExpect = 0; return; }
                                consumeResult(data.getStringExtra(EX_RESULT));
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
        launch(act, LIST_TYPE_GROUP, Bridge.getInstance().getGroupIds(), "\u9009\u62e9\u5bc6\u7fa4");
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
    private static void consumeResult(String csv) {
        int mode = sExpect;
        sExpect = 0;
        // 授权门兜底：未授权不落库（正常路径 launch 已挡，这里防御 sExpect 异常置位）
        if (!StateMachine.getInstance().isVipAuthorized()) {
            Log.i(TAG, "[CIG] result dropped: not authorized");
            return;
        }
        Set<String> result = parseCsv(csv);
        Bridge br = Bridge.getInstance();

        if (mode == LIST_TYPE_BUDDY) {
            Set<String> cur = new HashSet<>(br.getWxids());
            int added = 0, removed = 0;
            for (String id : result) if (!cur.contains(id)) { br.addWxid(id);    added++; }
            for (String id : cur)    if (!result.contains(id)) { br.removeWxid(id); removed++; }
            Log.i(TAG, "[CIG] buddy import result=" + result.size()
                    + " added=" + added + " removed=" + removed);
        } else if (mode == LIST_TYPE_GROUP) {
            Set<String> cur = new HashSet<>(br.getGroupIds());
            int added = 0, removed = 0;
            for (String id : result) if (!cur.contains(id)) { br.addGroupId(id);    added++; }
            for (String id : cur)    if (!result.contains(id)) { br.removeGroupId(id); removed++; }
            Log.i(TAG, "[CIG] group import result=" + result.size()
                    + " added=" + added + " removed=" + removed);
        } else {
            return;
        }
        RefreshBus.getInstance().notifyHiddenChanged(StateMachine.getInstance().isActive());
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
