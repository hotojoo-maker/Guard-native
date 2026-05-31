package com.ghost.assist.moduleD;

import android.util.Log;

import com.ghost.assist.core.Bridge;
import com.ghost.assist.core.StateMachine;

import java.lang.reflect.Field;
import java.util.Collection;
import java.util.Iterator;
import java.util.Set;

import de.robv.android.xposed.XC_MethodHook;
import de.robv.android.xposed.XposedBridge;
import de.robv.android.xposed.callbacks.XC_LoadPackage;

/**
 * P19B 通讯录【标签】成员列表密友过滤 —— HIDDEN 态藏密友 wxid（独立模块）。
 *
 * 与同包其他类的边界（防混淆）：
 *   - ContactFilter         = 通讯录主列表 fc5.g 过滤 + V↔H 热切（密友/密群）
 *   - ContactGroupHide      = 通讯录「群聊」独立页 ChatroomContactUI（cursor adapter）
 *   - ContactLabelHideGuard = 隐藏整个「标签」功能入口/管理页（开关 hclb，P19B/P26C 整标签隐藏）
 *   - 本类                  = 标签内「成员列表 / 添加搜索」里按 wxid 藏密友（门控 = isActive 三层门）
 *
 * 8.0.71 路径（P19B result.md，2026-05-24 装机 ✅；P_CV1 重构期从 ContactFilter 丢失，
 *              2026-05-31 按文档恢复并独立成模块，便于后期单独维护/排障）：
 *   UI         com.tencent.mm.ui.mvvm.MvvmContactListUI（标签成员 + 添加搜索）
 *   数据入口    ArrayList.addAll(Collection)  beforeHook
 *   item       ye5.j（≠ 主列表 fc5.g）
 *   wxid       ye5.j.d，String 形如 wxid_xxx-15-0 → 去尾部 -N-M 后缀
 *   动作        Iterator.remove()
 *   门控        StateMachine.isActive()（授权 + 密友总开关 f1 + HIDDEN）+ Bridge.allHiddenIds()
 *
 * 【门控锁定】只读 isActive() + allHiddenIds()；不写状态机 / 不碰授权 / 不刷 UI（纯 Filter 链）。
 */
public final class ContactLabelMemberFilter {

    private static final String TAG = ContactFilter.TAG; // "NCL"

    private static final String LABEL_ITEM_CLS   = "ye5.j";
    private static final String LABEL_WXID_FIELD = "d";

    private static volatile Field sLabelD = null;

    private ContactLabelMemberFilter() {}

    public static void install(XC_LoadPackage.LoadPackageParam lpparam) {
        try {
            XposedBridge.hookMethod(
                    java.util.ArrayList.class.getMethod("addAll", Collection.class),
                    new XC_MethodHook() {
                        @Override
                        @SuppressWarnings({"unchecked", "rawtypes"})
                        protected void beforeHookedMethod(MethodHookParam param) {
                            try {
                                if (!StateMachine.getInstance().isActive()) return;
                                Collection coll = (Collection) param.args[0];
                                if (coll == null || coll.isEmpty()) return;
                                Object first = coll.iterator().next();
                                if (first == null) return;
                                if (!LABEL_ITEM_CLS.equals(first.getClass().getName())) return; // 只处理 ye5.j

                                Set<String> hidden = Bridge.getInstance().allHiddenIds();
                                if (hidden.isEmpty()) return;

                                int before = coll.size();
                                int removed = 0;
                                Iterator it = coll.iterator();
                                while (it.hasNext()) {
                                    Object item = it.next();
                                    if (item == null) continue;
                                    String wxid = extractLabelWxid(item);
                                    if (wxid == null) continue;
                                    if (hidden.contains(wxid)) {
                                        try { it.remove(); removed++; }
                                        catch (UnsupportedOperationException ignored) {}
                                    }
                                }
                                if (removed > 0) {
                                    Log.i(TAG, "[CLM:label] removed=" + removed + "/" + before);
                                    Bridge.getInstance().addRawFeedLine(
                                            "[CLM:label] ye5.j removed=" + removed + "/" + before);
                                }
                            } catch (Throwable t) {
                                Log.w(TAG, "[CLM] addAll(ye5.j) filter err: " + t);
                            }
                        }
                    });
            Log.i(TAG, "[CLM] ArrayList.addAll(ye5.j) label-member hook ok");
        } catch (Throwable t) {
            Log.w(TAG, "[CLM] label-member hook install fail: " + t);
        }
    }

    /** ye5.j.d → wxid，形如 wxid_xxx-15-0，去尾部 -N-M 后缀（如 -15-0）。 */
    static String extractLabelWxid(Object item) {
        try {
            if (sLabelD == null) {
                Field f = item.getClass().getDeclaredField(LABEL_WXID_FIELD);
                f.setAccessible(true);
                sLabelD = f;
            }
            Object v = sLabelD.get(item);
            if (v == null) return null;
            String s = v.toString();
            if (s.isEmpty()) return null;
            return s.replaceFirst("-\\d+-\\d+$", "");
        } catch (Throwable e) {
            return null;
        }
    }
}
