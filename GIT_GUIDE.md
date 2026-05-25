# Git 速查 — 换电脑开发不丢代码

> 仓库地址：https://github.com/hotojoo-maker/Guard-native

## 核心概念（3 句话）

| 概念 | 大白话 |
|------|--------|
| **commit** | 存档点。每次 commit = 给所有文件拍一张快照 |
| **push** | 把本地存档上传到 GitHub |
| **pull** | 从 GitHub 下载最新存档到本地 |

GitHub 上可以看到**时间线**（每个存档谁、什么时候、改了什么）。

**是的，有了 GitHub 备份，就算本地改乱了，随时可以回到任意一个存档点。**

## 日常操作（只需 3 条命令）

### 下班前 — 存档 + 上传
```bash
git add -A                          # 把所有修改打包
git commit -m "一句话写清楚改了什么"   # 创建存档
git push                            # 上传到 GitHub
```

### 上班时 — 下载最新
```bash
git pull                            # 拉下最新代码，继续干活
```

## 救命操作

### 看时间线
```bash
git log --oneline                   # 所有存档列表
```

### 改乱了，回到上一个存档
```bash
git checkout -- .                   # 丢弃所有未存档的修改
```

### 改乱了，回退到某个存档
```bash
git checkout <存档ID> -- <文件名>    # 恢复单个文件到指定存档
```

## 换电脑设置

在新电脑上：
```bash
git clone https://github.com/hotojoo-maker/Guard-native.git
```
然后正常 `git pull` / `git commit` / `git push` 即可。

> **注意**：clone 需要 GitHub 登录。如果新电脑登录的是 `hotojoo-maker` 账号，用浏览器登录 GitHub 就行。如果换账号了，需要把新账号加为仓库协作者（Repo Settings → Collaborators）。

## 更新 .gitignore（换电脑后编译）

`.gitignore` 已配置忽略 `.cxx/`、`build/` 等编译产物。换电脑后正常编译，编译文件不会上传到 GitHub。
