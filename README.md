# 摘录入 Obsidian

一个 Android 分享中转 App：在任意 App 里分享划选的文字（或整篇笔记），
它按预设规则清洗、套模板，再写进 Obsidian 指定笔记的指定小节。

为一条真实工作流而做：**在 Kindle 里划一句 → 分享 → 落到对应书目笔记的
`## Quotes worth keeping` 下**，全程不用打字。

## 它做了什么

Kindle 分享出来的原文长这样：

```
我在 平野啓一郎 所著的《本心 (Japanese Edition)》中讀到以下這段引述時，就想到您：
「僕にはまだ、お母さんが必要なんだよ。」
開始免費閱讀這本書：https://read.amazon.co.jp/kp/kshare?asin=B092J53NPG&ref_=kar_wh_ca
```

App 会：

1. **认出书名与作者** —— 从引言里读出 `本心` / `平野啓一郎`，自动填好，
   并**去掉 `(Japanese Edition)` 这类版本标记**，所以会写进你已有的 `本心.md`
   而不是新建一篇。
2. **丢掉杂质** —— 分享面板的引言行、`開始免費閱讀這本書：https://…` 这类
   「说明文字＋链接」尾行、只有链接的行，全部删掉。
3. **合并硬换行** —— 被 EPUB/PDF 按屏幕宽度切断的句子重新接回一行
   （中日文不补空格，英文补空格，英文断词连字符也会接回去）。
4. **套模板并预览** —— 生成 `> 「僕にはまだ、お母さんが必要なんだよ。」`
   加一行出处 `> — 平野啓一郎《本心》 #reading`，发送前可随手改。
5. **写进指定笔记** —— 通过 `obsidian://` 交给 Obsidian 自己写，
   不碰文件系统，链接和索引都不会乱。

## 构建与安装

这台机器上 `PATH` 里没有 `java` / `gradle` / `adb`，要用绝对路径：

```bash
export JAVA_HOME="D:/DevEnv/AndroidStudioMy/jbr"
./gradlew :app:testDebugUnitTest          # 40 个纯逻辑单测，不需要设备
./gradlew :app:assembleDebug
"$LOCALAPPDATA/Android/Sdk/platform-tools/adb.exe" install -r app/build/outputs/apk/debug/app-debug.apk
```

版本矩阵锁定在本机 Gradle 缓存里已有的组合（AGP 8.2.2 / Kotlin 1.9.22 /
Compose 1.5.10 / compileSdk 34），干净构建不需要下载任何新依赖。

**安装被拒（`INSTALL_FAILED_USER_RESTRICTED`）** 是 HyperOS 的 USB 安装确认弹窗
没点到，不是包坏了 —— 重跑一次并留意手机屏幕。

## 使用

从任意 App 分享一段文字，或用文本选择菜单里的「处理文本」，选「摘录入 Obsidian」。
界面从上到下：

- **写入 Obsidian** —— 大按钮放在最上面：多数时候文本已经处理好了，一进来就能点。
- **目标笔记** —— 一行芯片切换去处：`当前书目` / `收件箱` / 你保存的预设目标。
- **将写入的内容** —— 发送前可改，改了会保留；动其它设置会重新生成。
- **书名 / 作者 / 年份 / 标签** —— 从分享内容认出来的会填好，可改。
- 书还没建过笔记时，这里会出现 **新建书目骨架**。

## 写入方式

两条路径，都在真机上实测过：

| | Advanced URI | 官方 URI |
| --- | --- | --- |
| 需要插件 | 是（Advanced URI） | 否 |
| 能定位到小节 | ✅ | ❌ 只能追加到文件末尾 |
| 长文本 | 剪贴板兜底 | 剪贴板兜底 |

默认走 Advanced URI。**没装插件时请在设置里切到官方 URI。**

> ⚠️ 两个实测出来的坑（官方文档没写）：
> - 官方 URI **不带 `append=true` 时，对已存在的文件是静默空操作** —— 不报错、什么都不写。
> - Advanced URI **找不到指定小节时也是静默空操作**，连 `x-error` 都不触发。
>   所以新书要先「新建书目骨架」，否则写入会悄悄落空。

## 设置

- **写入目标**：vault 名、小节标题、书目路径模板、收件箱笔记
- **输出格式**：模板（占位符 `{text} {title} {author} {year} {url} {source} {tags} {page} {date}`）、标签
- **写入方式**：Advanced / 官方、静默写入、**写入后返回来源 App**
- **清洗流水线**：每个步骤独立开关
- **预设目标**：保存常用去处（名称 / 路径 / 小节）
- **待发队列**：发送失败的条目在这里补发

> 「写入后返回来源 App」利用 Obsidian 处理完 URI 后回调我们自己的 `sharetoobsi://`
> 信号 —— 这是唯一能确定「Obsidian 已经处理完」的时机。

## 待做

- 发送历史界面（表已建好，待发队列的重发按钮已可用）
- 分享面板里的 Direct Share 快捷芯片（`shortcuts.xml`）
- **自定义 profile**：自己定清洗规则与出模模板
- **按 App 指定 profile**：把某个来源 App 固定到某个 profile

## 相关

写入约定、Bases 坑、frontmatter schema 以你 vault 里的 `Vault Guide.md` 为准。
构建环境、`obsidian://` 实测行为、代码布局见 `CLAUDE.md`。
