# Devin 项目侧约定（连接方式见平台 knowledge "local-access"）

> 本文不写 SSH / Tailscale / GitHub 凭据——那些在 Devin 平台 blueprint + 组织级 Secrets + knowledge 笔记里固化。
> 每个新对话进来先跑：`bash ~/connect_local.sh`（10-30 秒自动连本机），然后再读本文。

---

## 1. Worktree（绝不动主仓）

主仓 `C:\Users\Me\Desktop\guard_native` 有 ~96 处未提交脏改动，永远别碰。每个任务起独立 worktree：

```bash
cd /c/Users/Me/Desktop/guard_native
git fetch origin
git worktree add ../gn_<task-name> -b devin/<task-name> origin/main
cd ../gn_<task-name>
```

任务完成不删 worktree，留给用户检查。

## 2. 分支 / Commit / PR

| 项 | 规则 |
|---|---|
| 分支前缀 | `devin/<task-name>`，小写 + 短横线 |
| Base | 默认 `origin/main`；任务依赖前一个未合 PR 时改 base |
| Author | `Jordan Davis <user54b5deb8@yvhloju.cn>`（不改）+ 自动 Co-Authored-By Devin AI |
| 范围 | 只 `git add` 任务点名 / 实际改的文件，禁止 `git add -A` |
| Push | 直接 `git push -u origin devin/<task-name>`，失败别给 patch 让用户兜底，停手报告 |
| PR body | 必含：完成项 + 编译结果 + 装机依赖（如有）+ SSOT 落档清单 |

## 3. 完工 checklist

- [ ] 编译三变体（officialDebug / coexistDebug / officialRelease）全过
- [ ] 禁入区（`signing/` / `net/` / `*.inc`）未被读或写
- [ ] 主仓 96 处未提交改动未被碰
- [ ] commit hash + PR 链接报回用户
- [ ] PR body 列了 SSOT 落档清单（`TASK_BOARD.md` / `PROTECTION_MAP.md` / `<P任务>/worklog.md` 哪几行要改）

## 4. SSOT 落档分工

主仓 SSOT（`TASK_BOARD.md` / `PROTECTION_MAP.md` / `worklog.md`）由用户本机 Cursor 落档，Devin 不改。Devin 只在 PR body 列「要改哪几行」让 Cursor 一眼能填。

## 5. 失败模式

| 情况 | 动作 |
|---|---|
| `connect_local.sh` 没拉通本机 | 停手，报告，等用户开 sshd / 检 Tailscale |
| GitHub push 403 | 停手，报告，**禁止改 git config workaround** |
| 编译 fail | 停手，贴 error 原文 |
| 白名单外文件需要改 | 停手，问用户是否扩白名单 |
| `signing/` / `*.inc` / `.env*` / `keystore` 想读 | 立刻停，**绝不读** |
| 道德 / 政策疑虑 | 停手，明讲为什么拒，让用户决定切 Cursor 做 |
