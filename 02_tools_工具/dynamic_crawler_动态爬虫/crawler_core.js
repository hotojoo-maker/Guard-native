'use strict';
/**
 * crawler_core.js — 动态爬虫公共工具函数
 * Version : 1.0
 * Date    : 2026-05-23
 *
 * 各 crawler 脚本通过 require 或直接内联此文件使用。
 * Frida 环境不支持 CommonJS require，各 crawler 脚本已将核心函数内联。
 * 本文件作为参考/维护源，修改后需同步到各 crawler。
 */

// ─────────────────────────────────────────────────────────────────────────────
// 正则常量
// ─────────────────────────────────────────────────────────────────────────────

const WXID_RE   = /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom|[A-Za-z0-9_]+@app)$/;
const UIN_RE    = /^\d{5,12}$/;
const SOITEM_RE = /^SOSItemRelevant:(.+)$/;

// ─────────────────────────────────────────────────────────────────────────────
// 工具函数
// ─────────────────────────────────────────────────────────────────────────────

/** wxid 格式判断（wxid_xxx / @chatroom / @app） */
function isWxidLike(s) { return typeof s === 'string' && WXID_RE.test(s); }

/** UIN 格式判断（5-12 位纯数字） */
function isUinLike(s)  { return typeof s === 'string' && UIN_RE.test(s); }

/** 去掉 "SOSItemRelevant:" 前缀 */
function stripPrefix(s) {
    if (!s) return s;
    let m = SOITEM_RE.exec(s);
    return m ? m[1] : s;
}

/**
 * 反射读取对象字段，返回 {dotPath: stringValue}
 * @param {Object} obj - Java 对象
 * @param {number} maxDepth - 最大递归深度（建议 1-2）
 * @param {string} prefix - 字段路径前缀
 */
function dumpFieldsFlat(obj, maxDepth, prefix) {
    let result = {};
    if (obj == null || maxDepth <= 0) return result;
    prefix = prefix || '';
    try {
        let cls = obj.getClass();
        while (cls != null && cls.getName() !== 'java.lang.Object') {
            let fields = cls.getDeclaredFields();
            for (let i = 0; i < fields.length; i++) {
                let f = fields[i];
                try {
                    f.setAccessible(true);
                    let v = f.get(obj);
                    if (v == null) continue;
                    let key = prefix ? prefix + '.' + f.getName() : f.getName();
                    let vs  = v.toString().substring(0, 120);
                    result[key] = vs;
                    // 递归（排除标准库对象，避免爆炸）
                    let vcls = v.getClass().getName();
                    if (maxDepth > 1
                            && !vcls.startsWith('java.')
                            && !vcls.startsWith('android.')
                            && !vcls.startsWith('[')) {
                        let sub = dumpFieldsFlat(v, maxDepth - 1, key);
                        Object.assign(result, sub);
                    }
                } catch(e) {}
            }
            cls = cls.getSuperclass();
        }
    } catch(e) {}
    return result;
}

/**
 * 从字段 map 中提取 wxid / UIN / 关键词命中
 * @param {Object} fields - dumpFieldsFlat 返回值
 * @param {string} kw - 当前搜索关键词（可空）
 * @param {Set} hiddenWxids - 已知密友 wxid 集合
 */
function analyzeFields(fields, kw, hiddenWxids) {
    let wxids = [], uins = [], kwHits = [], hiddenHits = [];
    for (let [k, v] of Object.entries(fields)) {
        if (typeof v !== 'string') continue;
        let stripped = stripPrefix(v);
        if (isWxidLike(stripped)) {
            wxids.push(k + '=' + stripped);
            if (hiddenWxids && hiddenWxids.has(stripped)) {
                hiddenHits.push(k + '=' + stripped);
            }
        } else if (isUinLike(v)) {
            uins.push(k + '=' + v);
        }
        if (kw && v.toLowerCase().includes(kw.toLowerCase())) {
            kwHits.push(k);
        }
    }
    return { wxids, uins, kwHits, hiddenHits };
}

/**
 * 获取 Java 调用栈（前 N 层）
 * @param {number} depth
 */
function shortStack(depth) {
    depth = depth || 5;
    try {
        return Java.use('android.util.Log').getStackTraceString(
            Java.use('java.lang.Exception').$new()
        ).split('\n').slice(2, 2 + depth).join(' | ');
    } catch(e) { return '(stack err)'; }
}

/**
 * 评分计算
 * @param {Object} candidate - { hiddenHits, wxids, uins, returnsRemovableList,
 *                               calledDuringSearch, kwHits, calls }
 */
function computeScore(candidate) {
    let s = 0;
    s += (candidate.hiddenHits || 0)           * 100;
    s += (candidate.wxids ? candidate.wxids.size : 0) * 30;
    s += candidate.returnsRemovableList ? 50 : 0;
    s += candidate.calledDuringSearch   ? 20 : 0;
    s += (candidate.uins ? candidate.uins.size : 0) * 10;
    s += (candidate.kwHits || 0)               *  5;
    if (candidate.calls > 50
            && (!candidate.wxids || candidate.wxids.size === 0)
            && (!candidate.uins  || candidate.uins.size  === 0)) {
        s -= 20;
    }
    return s;
}

// ─────────────────────────────────────────────────────────────────────────────
// 已知类列表（addAll 探针去重用）
// ─────────────────────────────────────────────────────────────────────────────

const KNOWN_CLASSES_8071 = new Set([
    'fz2.e',    // FTS 搜索结果（联系人 c≠3 / 聊天记录 c=3）
    'kc5.y',    // 会话/搜索会话结果
    'jw1.d',    // FTSMainUI 内部 helper（4 primitives，非结果类）
    'ik3.i',    // 枚举（Delete/Insert/Update）
    'ts4.e',    // UI 组件
    'af4.a',    // TaskBar ViewHolder
    'c1',       // TaskBar UI 行
    'f9',       // 消息 DB 记录
]);
