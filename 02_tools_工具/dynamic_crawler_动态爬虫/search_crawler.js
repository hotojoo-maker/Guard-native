'use strict';
/**
 * SearchCrawler.js — WeChat 8.0.71 搜索 Hook 点动态发现工具
 * Version : 1.2  (性能修复版)
 * Date    : 2026-05-23
 *
 * 性能保护
 *   ① 全局 addAll hook 有 crawling 前置短路，idle 时零开销
 *   ② Java.enumerateLoadedClasses 分批 (BATCH=20)，每批间隔 50ms，不阻塞 UI
 *   ③ 每个 hook 有命中上限 (MAX_HITS_PER_METHOD=15)，超限自动解挂
 *   ④ dumpFieldsFlat 深度固定 1，禁止递归
 *   ⑤ MAX_HOOKS 降到 80，超限静默跳过
 *   ⑥ fz2.e ctor 最多记录 5 次，之后恢复原始实现
 *
 * 用法
 *   frida -U -n "com.tencent.mm" -l search_crawler.js
 *   打开微信搜索框 → 输入关键词 → 等 30 秒自动输出报告
 *   或手动：scStart("搜索词") / scReport()
 *
 * 禁止
 *   不改正式代码 / 不写 DB / 不打印完整聊天正文 / 不影响 111111 解锁
 */

Java.perform(function () {

    // =========================================================================
    // § 0  配置（修改这里）
    // =========================================================================

    const HIDDEN_WXIDS = new Set([
        'wxid_lzd2va16jd1622',
    ]);

    const CRAWL_MS           = 30000;   // 爬取窗口
    const MAX_HOOKS          = 80;      // ⚠️ 不要超过 100，容易卡死
    const MAX_HITS_PER_METHOD = 15;     // 每个 hook 命中上限，超限自动解挂
    const ENUM_BATCH_SIZE    = 20;      // 每批枚举类数量
    const ENUM_BATCH_DELAY   = 60;      // 批间隔 ms（给 UI 线程喘气）
    const STACK_DEPTH        = 4;
    const DUMP_DEPTH         = 1;       // ⚠️ 固定 1，禁止改成 2（卡死根因）
    const FZ2_CTOR_MAX       = 5;       // fz2.e 构造器最多记录次数

    // =========================================================================
    // § 1  状态
    // =========================================================================

    let candidates  = {};
    let seenHooks   = new Set();
    let hookCount   = 0;
    let crawling    = false;
    let searchKw    = '';
    let enumDone    = false;
    let fz2CtorCount = 0;

    // =========================================================================
    // § 2  工具函数
    // =========================================================================

    const WXID_RE   = /^(wxid_[A-Za-z0-9_]+|[A-Za-z0-9_]+@chatroom|[A-Za-z0-9_]+@app)$/;
    const UIN_RE    = /^\d{5,12}$/;
    const SOITEM_RE = /^SOSItemRelevant:(.+)$/;

    function isWxidLike(s) { return typeof s === 'string' && WXID_RE.test(s); }
    function isUinLike(s)  { return typeof s === 'string' && UIN_RE.test(s); }
    function stripPrefix(s) {
        let m = s && SOITEM_RE.exec(s);
        return m ? m[1] : s;
    }

    function shortStack() {
        try {
            return Java.use('android.util.Log').getStackTraceString(
                Java.use('java.lang.Exception').$new()
            ).split('\n').slice(2, 2 + STACK_DEPTH).join(' > ');
        } catch(e) { return '(stack err)'; }
    }

    /**
     * 反射读取对象字段 — depth 固定 1，禁止递归调用 depth=2
     * 超时保护：字段数量超过 30 时截断
     */
    function dumpFieldsFlat(obj) {
        let result = {};
        if (obj == null) return result;
        try {
            let cls = obj.getClass();
            let total = 0;
            while (cls != null && cls.getName() !== 'java.lang.Object') {
                let fields;
                try { fields = cls.getDeclaredFields(); } catch(e) { break; }
                for (let i = 0; i < fields.length; i++) {
                    if (total++ > 30) return result; // 截断防爆
                    try {
                        fields[i].setAccessible(true);
                        let v = fields[i].get(obj);
                        if (v != null) result[fields[i].getName()] = v.toString().substring(0, 100);
                    } catch(e) {}
                }
                cls = cls.getSuperclass();
            }
        } catch(e) {}
        return result;
    }

    function analyzeFields(fields) {
        let wxids = [], uins = [], kwHits = [], hiddenHits = [];
        for (let [k, v] of Object.entries(fields)) {
            if (typeof v !== 'string') continue;
            let s = stripPrefix(v);
            if (isWxidLike(s)) {
                wxids.push(k + '=' + s);
                if (HIDDEN_WXIDS.has(s)) hiddenHits.push(k + '=' + s);
            } else if (isUinLike(v)) {
                uins.push(k + '=' + v);
            }
            if (searchKw && v.toLowerCase().includes(searchKw.toLowerCase())) kwHits.push(k);
        }
        return { wxids, uins, kwHits, hiddenHits };
    }

    // =========================================================================
    // § 3  候选记录 & 评分
    // =========================================================================

    function getOrCreate(key) {
        if (!candidates[key]) {
            candidates[key] = {
                key, calls: 0, hitCount: 0,
                wxids: new Set(), uins: new Set(),
                hiddenHits: 0, kwHits: 0,
                returnsRemovableList: false,
                calledDuringSearch: false,
                stacks: [], score: 0,
                _hook: null,     // 存 hook 引用，用于超限解挂
            };
        }
        return candidates[key];
    }

    function recordHit(key, analysis, retObj, stack) {
        let c = getOrCreate(key);
        c.calls++;
        c.hitCount++;
        if (crawling) c.calledDuringSearch = true;
        analysis.wxids.forEach(w => c.wxids.add(w));
        analysis.uins.forEach(u => c.uins.add(u));
        c.hiddenHits += analysis.hiddenHits.length;
        c.kwHits     += analysis.kwHits.length;
        if (retObj != null) {
            try {
                let rn = retObj.getClass().getName();
                if (rn === 'java.util.ArrayList' || rn === 'java.util.LinkedList')
                    c.returnsRemovableList = true;
            } catch(e) {}
        }
        if (c.stacks.length < 2) c.stacks.push(stack);
        return c.hitCount;  // 返回命中次数，用于超限判断
    }

    function computeScore(c) {
        let s = 0;
        s += c.hiddenHits             * 100;
        s += c.wxids.size             *  30;
        s += c.returnsRemovableList   ?  50 : 0;
        s += c.calledDuringSearch     ?  20 : 0;
        s += c.uins.size              *  10;
        s += c.kwHits                 *   5;
        if (c.calls > 50 && c.wxids.size === 0 && c.uins.size === 0) s -= 20;
        return s;
    }

    // =========================================================================
    // § 4  动态 hook（含超限自动解挂）
    // =========================================================================

    function hookMethod(jcls, method) {
        let clsName = jcls.getName();
        let mName   = method.getName();
        let pts     = method.getParameterTypes();
        let sigArr  = Array.from({length: pts.length}, (_, j) => pts[j].getName());
        let hkey    = clsName + '#' + mName + '(' + sigArr.join(',') + ')';

        if (seenHooks.has(hkey) || hookCount >= MAX_HOOKS) return;
        seenHooks.add(hkey);
        hookCount++;

        let ckey = hkey;
        let c = getOrCreate(ckey);

        try {
            let javaClass = Java.use(clsName);
            let fn = javaClass[mName];
            if (!fn) return;
            let overloaded = sigArr.length > 0 ? fn.overload.apply(fn, sigArr) : fn.overload();

            // 保存原始实现，超限后恢复
            let origImpl = overloaded.implementation;

            overloaded.implementation = function() {
                // ⚡ 超限 → 自动解挂恢复
                if (c.hitCount >= MAX_HITS_PER_METHOD) {
                    try { overloaded.implementation = origImpl; } catch(e) {}
                    return origImpl ? origImpl.apply(this, arguments)
                                   : this[mName].apply(this, arguments);
                }

                let ret;
                try { ret = this[mName].apply(this, arguments); } catch(e) { return; }

                // 只在爬取窗口内分析
                if (!crawling) return ret;

                try {
                    let fields = dumpFieldsFlat(this);
                    // 扫参数（仅第一个参数）
                    if (arguments.length > 0 && arguments[0] != null) {
                        let af = dumpFieldsFlat(arguments[0]);
                        for (let [k, v] of Object.entries(af)) fields['arg0.' + k] = v;
                    }
                    let analysis = analyzeFields(fields);
                    if (analysis.wxids.length > 0 || analysis.uins.length > 0
                            || analysis.hiddenHits.length > 0 || analysis.kwHits.length > 0) {
                        recordHit(ckey, analysis, ret, shortStack());
                    }
                } catch(e) {}
                return ret;
            };
        } catch(e) {
            // 签名不匹配或不可访问 — 静默跳过
            hookCount--;
            seenHooks.delete(hkey);
        }
    }

    function shouldHookMethod(method) {
        let mname = method.getName();
        let pts   = method.getParameterTypes();
        let rt    = method.getReturnType().getName();

        // 方法名过滤（混淆方法名 ≤3 字符也通过）
        const METHOD_RE = /search|query|result|contact|msg|fts|find|get|load|bind|set/i;
        if (mname.length > 3 && !METHOD_RE.test(mname)) return false;

        // 参数 / 返回值类型过滤
        let hasInterestingParam = false;
        for (let j = 0; j < pts.length; j++) {
            let pn = pts[j].getName();
            if (pn === 'java.lang.String' || pn === 'java.lang.CharSequence'
                    || pn.includes('List') || pn.includes('Cursor')) {
                hasInterestingParam = true; break;
            }
        }
        let hasInterestingReturn = rt.includes('List') || rt.includes('Cursor')
            || rt.includes('Adapter') || rt === 'java.lang.Object';

        return hasInterestingParam || hasInterestingReturn;
    }

    // =========================================================================
    // § 5  ArrayList.addAll 宽探针（⚡ crawling 前置短路）
    // =========================================================================

    const KNOWN_CLS = new Set([
        'fz2.e','kc5.y','jw1.d','ik3.i','ts4.e','af4.a','c1','f9'
    ]);
    let addAllSeenCls = new Set(KNOWN_CLS);

    Java.use('java.util.ArrayList').addAll
        .overload('java.util.Collection')
        .implementation = function(coll) {
        let ret = this.addAll(coll);

        // ⚡ 非爬取期间 → 零开销返回
        if (!crawling) return ret;

        try {
            if (!coll || coll.size() === 0) return ret;
            let first = coll.iterator().next();
            if (!first) return ret;
            let cls = first.getClass().getName();
            if (addAllSeenCls.has(cls)) return ret;
            addAllSeenCls.add(cls);

            let fields   = dumpFieldsFlat(first);
            let analysis = analyzeFields(fields);
            console.log('[SC:addAll] NEW cls=' + cls + ' sz=' + coll.size()
                + ' wxids=' + JSON.stringify(analysis.wxids)
                + ' uins='  + JSON.stringify(analysis.uins)
                + ' hidden='+ analysis.hiddenHits.length);

            if (analysis.wxids.length > 0 || analysis.uins.length > 0) {
                recordHit('ArrayList.addAll#' + cls, analysis, this, shortStack());
            }
        } catch(e) {}
        return ret;
    };

    // =========================================================================
    // § 6  fz2.e 专项探针（⚡ 最多记录 FZ2_CTOR_MAX 次）
    // =========================================================================

    try {
        let fz2e = Java.use('fz2.e');

        // 无参构造
        fz2e.$init.overload().implementation = function() {
            this.$init();
            if (fz2CtorCount >= FZ2_CTOR_MAX) return;
            try {
                let fields = dumpFieldsFlat(this);
                if (fields['c'] === '3' && fields['g'] && isUinLike(fields['g'])) {
                    fz2CtorCount++;
                    let stack = shortStack();
                    console.log('[SC:fz2ctor] #' + fz2CtorCount
                        + ' c=3 UIN=' + fields['g'] + ' stack=' + stack);
                    if (fz2CtorCount >= FZ2_CTOR_MAX) {
                        // 恢复原始实现，不再 hook
                        fz2e.$init.overload().implementation = null;
                        console.log('[SC:fz2ctor] 达到上限 ' + FZ2_CTOR_MAX + '，自动解挂');
                    }
                }
            } catch(e) {}
        };

        // 有参构造（全部重载）
        let ctors = fz2e.class.getDeclaredConstructors();
        for (let i = 0; i < ctors.length; i++) {
            let pts = ctors[i].getParameterTypes();
            if (pts.length === 0) continue;
            let sigArr = Array.from({length: pts.length}, (_, j) => pts[j].getName());
            try {
                fz2e.$init.overload.apply(fz2e.$init, sigArr)
                    .implementation = function() {
                    this.$init.apply(this, arguments);
                    if (fz2CtorCount >= FZ2_CTOR_MAX) return;
                    try {
                        let fields = dumpFieldsFlat(this);
                        if (fields['g'] && isUinLike(fields['g'])) {
                            fz2CtorCount++;
                            console.log('[SC:fz2ctor' + pts.length + '] #' + fz2CtorCount
                                + ' c=' + fields['c'] + ' UIN=' + fields['g']
                                + ' stack=' + shortStack());
                        }
                    } catch(e) {}
                };
            } catch(e) {}
        }
        console.log('[SC] fz2.e ctor hooks 已安装，上限=' + FZ2_CTOR_MAX);
    } catch(e) {
        console.log('[SC] fz2.e ctor hook err: ' + e);
    }

    // =========================================================================
    // § 7  分批异步类枚举（⚡ 每批 ENUM_BATCH_SIZE 个，间隔 ENUM_BATCH_DELAY ms）
    // =========================================================================

    const CLASS_RE = /FTS|Search|SOS|Finder|Contact|Result|Message|Chat|fts|search/;

    function enumAndHookClassesBatched() {
        if (enumDone) {
            console.log('[SC] 类枚举已完成，跳过');
            return;
        }
        enumDone = true;
        console.log('[SC] 开始分批类枚举 (batch=' + ENUM_BATCH_SIZE
            + ' delay=' + ENUM_BATCH_DELAY + 'ms MAX_HOOKS=' + MAX_HOOKS + ')');

        let matched = [];
        try {
            Java.enumerateLoadedClasses({
                onMatch:    function(name) { if (CLASS_RE.test(name)) matched.push(name); },
                onComplete: function() {
                    console.log('[SC] 枚举完成 matched=' + matched.length
                        + '，开始分批 hook...');
                    processBatch(matched, 0);
                }
            });
        } catch(e) {
            console.log('[SC] 枚举失败: ' + e);
        }
    }

    function processBatch(list, offset) {
        if (offset >= list.length || hookCount >= MAX_HOOKS) {
            console.log('[SC] hook 安装完成 hookCount=' + hookCount);
            return;
        }
        let end = Math.min(offset + ENUM_BATCH_SIZE, list.length);
        for (let i = offset; i < end && hookCount < MAX_HOOKS; i++) {
            let name = list[i];
            if (seenHooks.has('cls:' + name)) continue;
            seenHooks.add('cls:' + name);
            try {
                let jcls = Java.use(name).class;
                let methods = jcls.getDeclaredMethods();
                for (let j = 0; j < methods.length && hookCount < MAX_HOOKS; j++) {
                    if (shouldHookMethod(methods[j])) hookMethod(jcls, methods[j]);
                }
            } catch(e) {}
        }
        // 下一批用 setTimeout 让出 UI 线程
        setTimeout(function() { processBatch(list, end); }, ENUM_BATCH_DELAY);
    }

    // =========================================================================
    // § 8  搜索上下文检测（Activity onResume / onPause）
    // =========================================================================

    Java.use('android.widget.EditText').setText
        .overload('java.lang.CharSequence')
        .implementation = function(text) {
        this.setText(text);
        if (text != null) {
            let s = text.toString();
            if (s.length > 0 && s.length < 50) searchKw = s;
        }
    };

    Java.use('android.app.Activity').onResume.implementation = function() {
        this.onResume();
        let name = this.getClass().getSimpleName();
        if ((name.includes('FTS') || name.toLowerCase().includes('search')) && !crawling) {
            crawling = true;
            console.log('[SC] === 爬取开始 act=' + name + ' duration=' + CRAWL_MS + 'ms ===');
            // 分批枚举（不阻塞 UI）
            setTimeout(function() { enumAndHookClassesBatched(); }, 200);
            // 定时结束
            setTimeout(function() { crawling = false; printReport(); }, CRAWL_MS);
        }
    };

    Java.use('android.app.Activity').onPause.implementation = function() {
        this.onPause();
        let name = this.getClass().getSimpleName();
        if (name.includes('FTS') || name.toLowerCase().includes('search')) {
            crawling = false;
        }
    };

    // =========================================================================
    // § 9  报告输出
    // =========================================================================

    function printReport() {
        console.log('\n');
        console.log('╔══════════════════════════════════════════════════════════╗');
        console.log('║     SearchCrawler v1.2  WeChat 8.0.71  结果报告          ║');
        console.log('╚══════════════════════════════════════════════════════════╝');
        console.log('关键词: ' + (searchKw || '(未检测到)'));
        console.log('候选数: ' + Object.keys(candidates).length
            + '  hook总数: ' + hookCount + '  addAll新类: '
            + (addAllSeenCls.size - KNOWN_CLS.size));

        let sorted = Object.values(candidates).map(c => {
            c.score = computeScore(c); return c;
        }).sort((a, b) => b.score - a.score);

        console.log('\n── Top 候选 ───────────────────────────────────────────────');
        sorted.slice(0, 10).forEach((c, i) => {
            if (c.score <= 0) return;
            console.log('#' + (i+1) + ' [' + c.score + '] ' + c.key);
            console.log('  calls=' + c.calls + ' search=' + c.calledDuringSearch
                + ' list=' + c.returnsRemovableList);
            if (c.wxids.size)    console.log('  wxids:  ' + [...c.wxids].join(', '));
            if (c.uins.size)     console.log('  uins:   ' + [...c.uins].join(', '));
            if (c.hiddenHits)    console.log('  *** HIDDEN HIT x' + c.hiddenHits + ' ***');
            if (c.stacks[0])     console.log('  stack:  ' + c.stacks[0]);
        });

        console.log('\n── fz2.e c=3 UIN 来源 ─────────────────────────────────────');
        if (fz2CtorCount === 0) {
            console.log('  未捕获到 fz2.e c=3 构造器调用');
            console.log('  建议：在可见态搜索含 UIN 匹配联系人触发');
        }
        let uinC = sorted.filter(c => c.uins.size > 0);
        uinC.forEach(c => console.log('  ' + c.key + ' uins=' + [...c.uins].join(',')));

        console.log('\n── addAll 新类（搜索期间首次出现）─────────────────────────');
        [...addAllSeenCls].filter(n => !KNOWN_CLS.has(n))
            .forEach(n => console.log('  ' + n));

        console.log('\n[SC] 将以上内容粘贴到对应 P 任务 result.md');
    }

    // =========================================================================
    // § 10  手动触发接口
    // =========================================================================

    global.scReport = printReport;
    global.scStart  = function(kw) {
        searchKw = kw || '';
        if (crawling) { console.log('[SC] 已在爬取中'); return; }
        crawling = true;
        console.log('[SC] 手动启动 kw=' + searchKw);
        enumAndHookClassesBatched();
        setTimeout(function() { crawling = false; printReport(); }, CRAWL_MS);
    };

    console.log('[SC] SearchCrawler v1.2 (性能修复版) loaded');
    console.log('[SC] MAX_HOOKS=' + MAX_HOOKS
        + ' CRAWL_MS=' + CRAWL_MS
        + ' DUMP_DEPTH=1 (固定)'
        + ' BATCH=' + ENUM_BATCH_SIZE + '+' + ENUM_BATCH_DELAY + 'ms');
    console.log('[SC] 用法: 打开微信搜索框 → 输入关键词 → 等 30s');
    console.log('[SC] 手动: scStart("搜索词") | scReport()');
});
