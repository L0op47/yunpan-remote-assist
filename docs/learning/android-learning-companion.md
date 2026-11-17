# 云畔 · Android 零基础学习与代码阅读手册

> 适用对象：没有 Android 基础、借助 AI 开发本项目、读 AI 代码吃力的项目维护者。目标是**能解释、修改、测试自己的项目**，不是先学完整本 Android 教程。
> 定位：本文件管“学习”；[设计规范](../superpowers/specs/2026-11-11-phone-remote-assist-design.md)管“做什么”；[M0–M1 实施计划](../superpowers/plans/2026-11-11-phone-remote-assist-m0-m1.md)管“如何交付”；根目录 [CLAUDE.md](../../CLAUDE.md)管“开发约束”。冲突时按项目既有约束执行。

## 1. 先看清实际架构与进度
- **一个 APK、两个角色、三个 Gradle 模块**：`:app`（Compose UI / Android 组件 / 屏幕采集）、`:transport`（WebRTC 生命周期和 DataChannel）、`:core`（无 Android 依赖的协议、分片、坐标换算）。
- 角色由 `app/.../MainActivity.kt` 选择；被控端入口是 `agent/AgentScreen.kt`，控制端入口是 `controller/ControllerScreen.kt`。
- 当前仓库已有**手动 SDP 建链和 PING/PONG 代码**，以及**被控端本地采集、JPEG 转换和预览代码**；这不等于“跨设备画面已经打通”。
- M1 还需按 Task 7 将 **采集 → 分片 → DataChannel → 重组 → 显示** 串起来；M2 输入注入、M3 自动信令/配对及后续功能仍以实际代码和真机验收为准。
- 旧 Plan 保留了“四模块、双 APK”的历史文字；2026-11-13 的 Spec 决策记录和当前代码已改为**三模块、单 APK 双角色**，不要照旧 Plan 寻找 `app-agent`/`app-controller`。
- **区分四个状态**：文档计划中 / 仓库已有代码 / 构建或单测通过 / 两台真机验收通过。任何阶段“完成”都需证据。

## 2. 从已有代码出发的学习顺序（达标再前进）
| 顺序 | 学什么 | 先读哪份真实文件 | 自己完成的验收 |
|---|---|---|---|
| 1 | Kotlin：变量、函数、参数、类、空值、`data class`/`sealed interface` | `core/.../protocol/WireMessage.kt` | 说清 Hello、Tap、VideoChunk 的字段；独立增加一个纯 Kotlin 练习数据类（不改正式协议） |
| 2 | Kotlin：条件、运算、`Pair`、可空返回值、集合 | `core/.../screen/CoordinateMapper.kt` | 手算黑边坐标；写 3 个输入测试并预测结果 |
| 3 | Android 项目：`Application`、`Activity`、Manifest、Gradle | `app/.../App.kt`、`MainActivity.kt`、`AndroidManifest.xml` | 画出“点击图标 → 打开角色选择”的调用与初始化路线 |
| 4 | Compose：`@Composable`、`remember`、`mutableStateOf`、点击事件 | `MainActivity.kt`、`ui/FeedbackButton.kt` | 解释角色变化后为何重新显示界面；独立改一个练习页面的按钮文案与状态 |
| 5 | Android 生命周期：权限、Intent、Service、前台通知 | `agent/AgentScreen.kt`、`capture/ScreenCaptureService.kt` | 画出“点击采集 → 系统授权 → 启服务 → 停止”的流程，说明拒绝授权应如何处理 |
| 6 | 线程与数据流：`HandlerThread`、回调、`StateFlow`、图像基础 | `capture/JpegFrameProducer.kt`、`CapturePreviewBus.kt` | 说清后台采集线程如何通知 Compose 更新预览 |
| 7 | 数据协议：`ByteArray`、编解码、单测、分片重组 | `core/.../protocol/MessageCodec.kt`、`VideoChunker.kt`、`FrameAssembler.kt` | 解释一张 JPEG 为什么拆成多个包；运行并解释 `:core:test` |
| 8 | WebRTC：回调、Offer/Answer、SDP、ICE、DataChannel | `transport/.../WebRtcPeer.kt`、两侧 Screen | 画出 PING → PONG 的两机调用链，分清“建链”和“传画面” |
| 9 | 综合：M1 Task 7 与 M2/M3 逐步推进 | 对照 Spec + 最新代码 | 在两台真机验证画面后，再学输入注入、配对与安全；不跳过验收 |

> 路径中的 `...` 仅为表格省略：真实文件均在对应模块的 `src/main` 下；用 Android Studio 的 **Shift 两次**搜索文件名即可打开。

## 3. 每节课固定用“6 步闭环”
1. **定目标**：这一节只理解一个概念或一条调用链（例如“选择被控端”），不一次解释整个文件。
2. **画流程**：先说这个功能的输入、输出、入口、调用关系；再看重要的 10–30 行代码。
3. **补前置**：遇到陌生 Kotlin/Android 语法先做最小独立示例，再回到项目代码。
4. **动手验证**：先预测修改结果，自己在练习代码或独立分支改一点点，再运行。
5. **口头复述**：不用 AI，能说出“谁调用谁、数据去哪、失败怎么办”。
6. **记录证据**：写下文件、概念、运行命令、实际结果、遗留问题；不能运行时明确记录“未验证”。

### 通过标准
- **L1 看过**：能认出术语；**L2 理解**：能解释调用链；**L3 会用**：能独立小改并调试；**L4 迁移**：面对新需求能解释取舍。
- 当前阶段核心知识至少达到 **L3** 再继续；无需记住所有 API，允许查官方文档。

## 4. 首轮四周安排（每周约 5–8 小时，可调整）
| 周次 | 主线 | 周末前的成果 |
|---|---|---|
| 1 | Kotlin + `WireMessage`/`CoordinateMapper` | 能看懂类型、函数、可空值；手写 3 个坐标测试 |
| 2 | Android 入口 + Compose + `MainActivity` | 自己解释角色选择、`remember` 状态变化和入口文件 |
| 3 | 录屏授权 + Service + 线程与 StateFlow | 自己画出被控端采集/停止流程，分析拒绝授权分支 |
| 4 | 协议 + WebRTC PING/PONG | 讲清从按钮到另一台设备回复的完整消息流程 |

## 5. 与 AI 合作：明确区分“开发模式”和“教学模式”
- **开发模式**：先给“问题/最小方案/修改文件/风险与验证计划”；遵守根目录 CLAUDE.md 中的改动范围、确认与测试约束。AI 可以代写经授权的功能，但必须交付文件级解释和真实测试证据。
- **教学模式**：除非你明确说“你来改”，否则 AI **不改仓库**；按“功能 → 调用链 → 必要语法 → 小实验 → 练习 → 验收”讲解，每次一小段；**不要直接给练习完整答案**。
- 提问模版：`请用教学模式讲解 <文件/函数>：先说明它在云畔中的职责；只挑我现在需要的前置知识；画 5 步内的调用链；给一个最小实验、一个我自己完成的练习和验收问题。不要修改代码。`
- 开发交付模版：`本次修改目标是什么？动了哪些文件？最关键的调用链和失败分支是什么？执行了哪些命令、输出是什么？哪些仍需真机测试？`
- 遇到看不懂的回调：先写下 **谁注册、谁触发、在哪个线程执行、回调后修改什么状态**，再要求 AI 解释。
- 项目学习优先使用本仓库，不为了“学最新技术”无故升级依赖、重构架构或引入新框架。

## 6. 学习记录模板（每节复制一份到自己的笔记；不提交设备隐私）
- 日期 / 知识点 / 对应文件与函数：
- 我理解的调用链（不看 AI，3–5 步）：
- 我不理解的语法或 Android 概念：
- 我亲自修改或验证的最小实验：
- 预期结果 / 实际结果 / 日志或单测证据：
- 能力等级（L1–L4）/ 下次要复习的点：

## 7. 验证、安全与后续求职衔接
- 纯 Kotlin 协议逻辑：`./gradlew :core:test`；Android 构建：`./gradlew :app:assembleDebug`。构建成功**不能替代**两台手机的录屏授权、切后台、断线等真机验证。
- 只在设备所有者明确授权的前提下联调；不做隐蔽控制、绕过权限或记录/外传私人屏幕内容。公开日志、截图、SDP 和配对数据前先检查敏感信息。
- 完成 M1/M2 后，再系统补 Java/XML/View 阅读、Git、测试、性能、安全、架构和面试表达；这些对国内 Android 就业同样重要，但不应妨碍眼下把项目读懂。
- 官方长期资料：[Android Developers](https://developer.android.com/) · [Kotlin Docs](https://kotlinlang.org/docs/home.html) · [Android Architecture](https://developer.android.com/topic/architecture)。
