/**
 * search_probe.js — 搜索结果类发现探针 v1
 *
 * 目标（4 个问题）：
 *   Q1. 全局搜索 (FTS) 用什么 item 类？字段名？
 *   Q2. 联系人搜索结果 item 类？
 *   Q3. 聊天记录搜索结果 item 类？
 *   Q4. z15.ef6 里哪个字段 = wxid？(不是查询词)
 *
 * 使用方法：
 *   frida -U -f com.tencent.mm -l search_probe.js --no-pause
 *   然后在微信里做以下操作（每步等 2s）：
 *     1. 点放大镜进 FTS 搜索页
 *     2. 搜索密友的昵称（如 "测试"）
 *     3. 搜索密友的 wxid（如 "wxid_xxx"）
 *     4. 点击「联系人」tab
 *     5. 点击「聊天记录」tab
 *     6. 点击「朋友圈」tab
 *   观察 adb logcat TAG=SP 输出
 *
 * 日志格式：
 *   [SP:addAll] NEW cls=z15.ef6 sz=5 fields={d:"...",e:"...",o:"..."} wxid_fields=[]
 *   [SP:LLadd]  NEW cls=xxx.yyy sz=1 fields={...} wxid_fields=[talker]
 *   [SP:z15]    item#0 ALL_FIELDS={...}  ← z15.ef6 详细字段 dump
 *   [SP:bool]   hooked cls=xxx.yyy method=zzz arg=wxid_xxx → (observe return)
 */

'use strict';

// ─────────────────────────────────────────────────────────────────────────────
// 配置
// ─────────────────────────────────────────────────────────────────────────────
const CONFIG = {
    TAG: '[SP]',
    // 已知测试密友 wxid（改成你自己的）
    KNOWN_WXID: 'wxid_lzd2va16jd1622',
    // 忽略这些包前缀（太多噪声）
    SKIP_PKGS: ['java.', 'android.', 'kotlin.', 'com.ghost.',
                 'androidx.', 'okhttp3.', 'retrofit2.', 'com.google.',
                 'kotlinx.', 'dalvik.', 'libcore.'],
    // 最多打印的 addAll 新类数量
    MAX_NEW_CLASSES: 30,
    // z15.ef6 item 字段 dump 最多打几个 item
    MAX_ITEMS_DUMP: 3,
};

// ─────────────────────────────────────────────────────────────────────────────
// 全局状态
// ─────────────────────────────────────────────────────────────────────────────
const seenAddAllClasses = new Set();
const seenLLAddClasses  = new Set();
let   inSearchContext   = false;  // FTSMainUI 是否在前台
let   newClassCount     = 0;

// ─────────────────────────────────────────────────────────────────────────────
// 辅助：是否像 wxid
// ─────────────────────────────────────────────────────────────────────────────
function isWxidLike(s) {
    if (!s || typeof s !== 'string' || s.length < 4 || s.length > 80) return false;
    if (s.includes(' ') || s.includes('<') || s.includes('\n')) return false;
    return s.startsWith('wxid_') || s.startsWith('gh_') ||
           s.startsWith('filehelper') || s.startsWith('weixin') ||
           /^[a-zA-Z0-9_\-@\.]{5,60}$/.test(s);
}

// ─────────────────────────────────────────────────────────────────────────────
// 辅助：dump 对象所有字符串字段（深度 1）
// ─────────────────────────────────────────────────────────────────────────────
function dumpStringFields(obj) {
    const out = {};
    try {
        let c = obj.getClass();
        while (c && !c.getName().equals('java.lang.Object')) {
            const fields = c.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                const f = fields[i];
                try {
                    f.setAccessible(true);
                    const v = f.get(obj);
                    if (v === null) {
                        out[f.getName()] = null;
                        continue;
                    }
                    const vn = v.getClass().getName();
                    if (vn === 'java.lang.String') {
                        out[f.getName()] = v.toString();
                    } else if (vn === 'java.lang.Integer' || vn === 'java.lang.Long' ||
                               vn === 'java.lang.Boolean') {
                        out[f.getName()] = '[' + vn.split('.').pop() + ']' + v.toString();
                    } else if (!vn.startsWith('java.') && !vn.startsWith('android.') &&
                               !vn.startsWith('kotlin.')) {
                        out[f.getName()] = '{' + vn + '}';
                    }
                } catch (e) {
                    out[f.getName()] = '?err';
                }
            }
            try { c = c.getSuperclass(); } catch(e) { break; }
        }
    } catch (e) { out['_err'] = e.toString(); }
    return out;
}

// ─────────────────────────────────────────────────────────────────────────────
// 辅助：是否应跳过此包名
// ─────────────────────────────────────────────────────────────────────────────
function skipPkg(cls) {
    for (let i = 0; i < CONFIG.SKIP_PKGS.length; i++) {
        if (cls.startsWith(CONFIG.SKIP_PKGS[i])) return true;
    }
    return false;
}

// ─────────────────────────────────────────────────────────────────────────────
// 辅助：提取所有 wxid-like 字段
// ─────────────────────────────────────────────────────────────────────────────
function wxidFields(fields) {
    const hits = [];
    for (const k in fields) {
        const v = fields[k];
        if (v && typeof v === 'string' && isWxidLike(v)) {
            hits.push(k + '=' + v.substring(0, 40));
        }
    }
    return hits;
}

// ─────────────────────────────────────────────────────────────────────────────
// 辅助：是否包含已知测试 wxid
// ─────────────────────────────────────────────────────────────────────────────
function containsKnownWxid(fields) {
    for (const k in fields) {
        const v = fields[k];
        if (v && typeof v === 'string' && v.includes(CONFIG.KNOWN_WXID)) return true;
    }
    return false;
}

// ─────────────────────────────────────────────────────────────────────────────
// 主 hook 区
// ─────────────────────────────────────────────────────────────────────────────
Java.perform(function () {

    // ── 1. FTSMainUI context tracker ─────────────────────────────────────────
    // 监控 FTSMainUI（全局搜索 Activity）进入/退出
    try {
        const Activity = Java.use('android.app.Activity');
        Activity.onResume.implementation = function () {
            const name = this.getClass().getName();
            if (name.toLowerCase().includes('fts') || name.toLowerCase().includes('search')) {
                inSearchContext = true;
                console.log(CONFIG.TAG + ' [ctx] IN_SEARCH act=' + name);
            }
            return this.onResume();
        };
        Activity.onPause.implementation = function () {
            const name = this.getClass().getName();
            if (name.toLowerCase().includes('fts') || name.toLowerCase().includes('search')) {
                inSearchContext = false;
                console.log(CONFIG.TAG + ' [ctx] EXIT_SEARCH act=' + name);
            }
            return this.onPause();
        };
        console.log(CONFIG.TAG + ' Activity.onResume/onPause hooked');
    } catch (e) {
        console.log(CONFIG.TAG + ' Activity hook fail: ' + e);
    }

    // ── 2. ArrayList.addAll — 核心：发现搜索结果批量插入 ─────────────────────
    try {
        const ArrayList = Java.use('java.util.ArrayList');
        ArrayList.addAll.overload('java.util.Collection').implementation = function (coll) {
            const result = this.addAll(coll);
            try {
                if (!coll || coll.isEmpty()) return result;
                const first = coll.iterator().next();
                if (!first) return result;

                const cls = first.getClass().getName();
                if (skipPkg(cls)) return result;

                const isNew = !seenAddAllClasses.has(cls);

                // z15.ef6 专项：总是打印（即使 VISIBLE）
                if (cls === 'z15.ef6') {
                    if (isNew) {
                        seenAddAllClasses.add(cls);
                        console.log(CONFIG.TAG + ' [addAll] KNOWN z15.ef6 sz=' + coll.size());
                    }
                    // dump 前 3 个 item 的所有字段（找 wxid 字段）
                    const iter = coll.iterator();
                    let idx = 0;
                    while (iter.hasNext() && idx < CONFIG.MAX_ITEMS_DUMP) {
                        const item = iter.next();
                        const fields = dumpStringFields(item);
                        const wxids  = wxidFields(fields);
                        const hasKnown = containsKnownWxid(fields);
                        console.log(CONFIG.TAG + ' [z15.ef6] item#' + idx +
                                    (hasKnown ? ' ★KNOWN_WXID' : '') +
                                    '\n  fields=' + JSON.stringify(fields).substring(0, 400) +
                                    '\n  wxid_candidates=' + JSON.stringify(wxids));
                        idx++;
                    }
                    return result;
                }

                // 其他类：仅在搜索上下文中打印新类
                if (isNew && (inSearchContext || newClassCount < 5)) {
                    if (newClassCount >= CONFIG.MAX_NEW_CLASSES) return result;
                    seenAddAllClasses.add(cls);
                    newClassCount++;

                    const fields = dumpStringFields(first);
                    const wxids  = wxidFields(fields);
                    const hasKnown = containsKnownWxid(fields);
                    console.log(CONFIG.TAG + ' [addAll] NEW cls=' + cls +
                                ' sz=' + coll.size() +
                                (hasKnown ? ' ★KNOWN_WXID' : '') +
                                '\n  fields=' + JSON.stringify(fields).substring(0, 400) +
                                '\n  wxid_candidates=' + JSON.stringify(wxids));
                }
            } catch (e) {
                console.log(CONFIG.TAG + ' [addAll] err: ' + e);
            }
            return result;
        };
        console.log(CONFIG.TAG + ' ArrayList.addAll hooked');
    } catch (e) {
        console.log(CONFIG.TAG + ' ArrayList.addAll hook fail: ' + e);
    }

    // ── 3. LinkedList.add — 搜索结果单项插入 ─────────────────────────────────
    try {
        const LinkedList = Java.use('java.util.LinkedList');
        LinkedList.add.overload('java.lang.Object').implementation = function (item) {
            const result = this.add(item);
            try {
                if (!item) return result;
                const cls = item.getClass().getName();
                if (skipPkg(cls)) return result;

                const isNew = !seenLLAddClasses.has(cls);
                if (isNew && inSearchContext) {
                    seenLLAddClasses.add(cls);
                    const fields = dumpStringFields(item);
                    const wxids  = wxidFields(fields);
                    const hasKnown = containsKnownWxid(fields);
                    if (wxids.length > 0 || hasKnown) {
                        console.log(CONFIG.TAG + ' [LLadd] NEW cls=' + cls +
                                    (hasKnown ? ' ★KNOWN_WXID' : '') +
                                    '\n  fields=' + JSON.stringify(fields).substring(0, 400) +
                                    '\n  wxid_candidates=' + JSON.stringify(wxids));
                    }
                }
            } catch (e) {}
            return result;
        };
        console.log(CONFIG.TAG + ' LinkedList.add hooked');
    } catch (e) {
        console.log(CONFIG.TAG + ' LinkedList.add hook fail: ' + e);
    }

    // ── 4. 探针：(String)→boolean 方法探测（hookSearchContact 候选）──────────
    // 策略：hook java.util.HashSet.contains / ArrayList.contains 来看谁在查 wxid
    // 因为 WeChat 内部的密友列表查找最终会 .contains(wxid) 在某个集合上
    try {
        const HashSet = Java.use('java.util.HashSet');
        const origContains = HashSet.contains.overload('java.lang.Object');
        origContains.implementation = function (obj) {
            const result = origContains.call(this, obj);
            try {
                if (inSearchContext && obj !== null) {
                    const s = obj.toString();
                    if (isWxidLike(s) && s.length > 6) {
                        // 这是在搜索上下文中查 wxid-like 字符串
                        const stack = Java.use('java.lang.Thread').currentThread().getStackTrace();
                        let caller = '?';
                        for (let i = 2; i < Math.min(8, stack.length); i++) {
                            const f = stack[i].toString();
                            if (!f.includes('java.util') && !f.includes('com.ghost') &&
                                !f.includes('de.robv')) {
                                caller = f;
                                break;
                            }
                        }
                        console.log(CONFIG.TAG + ' [contains] wxid=' + s +
                                    ' result=' + result + ' caller=' + caller);
                    }
                }
            } catch (e) {}
            return result;
        };
        console.log(CONFIG.TAG + ' HashSet.contains hooked (contact check probe)');
    } catch (e) {
        console.log(CONFIG.TAG + ' HashSet.contains hook fail: ' + e);
    }

    // ── 5. 探针：RecyclerView.Adapter.getItemCount — 观察搜索 Adapter ────────
    // 在搜索结果显示时，Adapter.getItemCount 会被频繁调用
    // 找到具体的 Adapter 类名后，可以进一步 hook 其 onBindViewHolder
    try {
        const RecyclerViewAdapter = Java.use('androidx.recyclerview.widget.RecyclerView$Adapter');
        const seenAdapters = new Set();
        RecyclerViewAdapter.getItemCount.implementation = function () {
            const result = this.getItemCount();
            try {
                if (inSearchContext && result > 0) {
                    const cls = this.getClass().getName();
                    if (!seenAdapters.has(cls) && !skipPkg(cls)) {
                        seenAdapters.add(cls);
                        console.log(CONFIG.TAG + ' [adapter] NEW cls=' + cls + ' count=' + result);
                    }
                }
            } catch (e) {}
            return result;
        };
        console.log(CONFIG.TAG + ' RecyclerView.Adapter.getItemCount hooked');
    } catch (e) {
        console.log(CONFIG.TAG + ' RecyclerView.Adapter.getItemCount hook fail: ' + e);
    }

    console.log(CONFIG.TAG + ' ── 探针就绪。请进入微信 FTS 搜索页，分别搜索：昵称 / wxid / 聊天关键词 ──');
});
