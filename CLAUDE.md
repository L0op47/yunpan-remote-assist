# CLAUDE.md — 云畔项目严格约束

本文件对本仓库（`D:\self\remote-assist`）内的所有 Claude 会话生效。与本文冲突的用户即时指令优先；与 `docs/superpowers/specs/2026-11-11-phone-remote-assist-design.md`（下称 spec）冲突时，以 spec 为准，并修正本文件。

## 0. 项目与阶段

- 产品：**云畔**，手机远程协助 App——用户的一加（控制端）远程帮助父母的 iQOO（被控端）。
- 产品硬约束（不可协商）：**零月费、无自有服务器、不改路由器、不开公网端口**；被控端全程可见、需机主确认，**绝不做静默/隐藏控制**。
- 当前阶段 **M1**：手动 SDP 建链已有代码且文档记载已联通（Task 1–5）；Task 6 屏幕采集与本地预览代码已存在，待按真机验收核对；下一步重点是 Task 7 跨设备画面串流。
- 计划与进度：`docs/superpowers/plans/2026-11-11-phone-remote-assist-m0-m1.md`；执行记录在 `.superpowers/sdd/<plan>/progress.md`（git 忽略）。

## 1. 工作方式（先想后写，改动最小）

- 动手前先说明：要解决什么、打算怎么做、有哪些取舍；**假设必须显式说出**，不确定就问。
- 大批量改动（>2 个文件或 >100 行）前，先给「变更清单」（文件 + 目的）并等待用户确认；一次只交付一个可 review 的切片。
- **禁止未经请求的"顺手改进"**：不重构相邻代码、不改格式、不改注释、不改命名、不升级依赖。
- 只改与任务直接相关的行；**自己造成的未用 import/变量/函数必须清理**；发现与任务无关的死代码：报告，不删除。
- 更简单的方案存在时必须提出；写超过 50 行先自问"能不能更少"。

## 2. 架构边界（必须遵守）

| 模块 | 职责 | 硬性规定 |
|---|---|---|
| `:core` | 协议编解码、分片重组、坐标映射、SDP 聊天码 | **纯 Kotlin/JVM，禁止任何 Android 依赖**；全部纯逻辑单元测试必须在这里 |
| `:transport` | libwebrtc 封装（`WebRtcPeer`） | 只做 WebRTC 生命周期与数据通道；**禁止业务逻辑、禁止 UI 依赖、禁止把 `org.webrtc` 类型泄漏给上层** |
| `:app` | 唯一 App（`dev.remoteassist`），角色选择 + 两个角色界面 | 只做 UI 与状态编排；**禁止把协议/网络逻辑写进 Compose 层** |

- 依赖方向：`app → transport → core`（transport 目前不依赖 core）；`core` 不依赖任何模块。
- UI 文案一律中文、面向老人与家属；界面默认大按钮、少层级。

## 3. 协议规则（改协议 = 先改测试）

- 类型码固定：`1 Hello / 2 VideoChunk / 3 Tap / 4 Swipe / 5 LongPress / 6 Key / 7 Text / 8 Ping / 9 Pong / 10 Bye`。新增类型必须：两端同步实现、更新本表、补测试。
- `encode` 与 `decode` 必须严格对称；定长字段大端；变长字段带长度前缀；**末尾字段（如 payload）"吃到结尾"**。
- 一切坏数据必须转成 `ProtocolException`（`BufferUnderflowException`/`EOFException` 必须捕获翻译）；**禁止异常穿透导致崩溃**。
- 修改 `:core` 任何逻辑前后必须跑 `./gradlew :core:test` 并全绿；无测试的逻辑改动一律拒绝提交。

## 4. WebRTC 关键约束（踩过的坑，禁止回退）

- Manifest 必须同时声明 `INTERNET` 与 `ACCESS_NETWORK_STATE`；缺后者会触发 `network_thread` SIGABRT（历史事故）。
- 该版本 libwebrtc 在 `onIceGatheringChange(COMPLETE)` **之后**才回调候选地址，`localDescription` 不含 `a=candidate`。**必须保留**「收集 `onIceCandidate` → 延迟 300ms → 拼接候选进 SDP」的逻辑（`deliverLocalSdp`），删除会导致永远连不上。
- 收集兜底超时 `GATHER_TIMEOUT_MS = 8000`，不得调小。
- ICE 目前 **STUN-only**。`openrelay.metered.ca` 在国内被 DNS 污染（解析到 192.0.2.1/100::1），**禁止重新加回**该域名或任何付费 TURN；TURN 属 M3 议题，需先更新 spec Decision Log 并获用户同意。
- `WebRtcPeer.Listener` 所有回调统一 post 到主线程后交付；新增回调必须遵守。
- 调试日志已按可调试标志门控：`debugLoggingEnabled = (applicationInfo.flags and FLAG_DEBUGGABLE) != 0`，release 静默；新增日志必须走同一开关。

## 5. 构建与验证（先有证据再声明完成）

- 环境：`JAVA_HOME="/d/Program Files/Android/Android Studio/jbr"`；adb：`/d/Android/Sdk/platform-tools/adb.exe`；SDK `D:\Android\Sdk`。
- 每次改动必跑：`./gradlew :core:test` 与 `./gradlew :app:assembleDebug`。
- 安卓侧行为以**真机现象**为准（安装、录屏授权、拒绝授权、切后台等验收清单见计划）；禁止"应该能跑了"式结论。
- 声明"完成/修好"之前必须贴出命令与实际输出；失败就如实报告，不掩饰。
- 提交信息使用 conventional 前缀（feat/fix/chore/refactor/docs/build），**不加任何 AI 署名（Co-Authored-By 等一律不加）**。

## 6. 发版规则

- 签名文件：`keystore/yunpan-release.jks` + `keystore.properties`（均在 `.gitignore`）。**永不提交、永不更换**（换签名=已装设备无法覆盖升级）。操作 release 配置前先确认这两个文件存在。
- 每次分发必须 bump `versionCode +1` 与 `versionName`。
- release 构建：`optimization { enable = false }`（AGP 9 `packageScope` 混淆曾导致启动 `IllegalAccessError`）；**重开 R8 必须先加 keep 规则、真机验证启动，并经用户同意**。
- 仅打包 `arm64-v8a`（`ndk.abiFilters`）；APK 约 20MB。
- 发布流程：`./gradlew :app:assembleRelease` → `gh release create vX.Y.Z "app/build/outputs/apk/release/app-release.apk" --target master --title ... --notes ...`；仓库 `L0op47/yunpan-remote-assist`（公开）。

## 7. 协作与交付

- 解释顺序：解决什么问题 → 关键决策 → 最易错处；用户要能读懂并质疑，**不得把用户当执行器**。
- 每个新/改的 Compose 界面**必须附 `@Preview(showBackground = true, widthDp = 393, heightDp = 851)`**（private composable 的预览写在同文件），并在交付说明里告知到 Split/Design 查看。
- 用户自己动手时（写代码/跑命令），只给步骤与 review，不代劳；用户明确说"你来做"才代做。
- IDE 爆红但命令行构建成功时，结论是 IDE 同步问题（Sync / Invalidate Caches），先对比再下判断。

## 8. 红线（任何理由都不许做）

- 静默/隐藏控制、无人值守绕过机主确认、把用户数据上传任何服务器。
- 引入付费云资源/服务器/域名依赖（违反零月费）；改 `applicationId`；改签名或动 keystore。
- 在 `:core` 引入 Android 依赖；在 UI 层写协议/网络逻辑；在 `:transport` 写业务逻辑。
- 未经确认增删依赖（`gradle/libs.versions.toml`）或修改 `minSdk/targetSdk`。
- 无测试证据就改 `:core` 逻辑；无真机验证就宣称安卓侧功能完成。

## 9. 已知欠账（记录在案，不擅自处理）

1. R8 重开（keep 规则）→ 目标 APK ≈14MB；
2. 版本 0.1.1 已 bump 但尚未构建发布（GitHub 附件仍为 1.0 的包）；
3. 跨网/远程联调受对方 NAT 限制，TURN 方案待 M3；
4. `D:\self\rmt-ast` 为向导生成的旧工程副本（仓库外），待用户确认后清理。

## 10. 文档同步

- 结构、协议、发布流程、安全模型的任何变更：**先更新 spec 的 Decision Log，再改代码**；随后同步本文件。
- 计划文档（plan）为历史记录，不回改；进度只写 `.superpowers/sdd/.../progress.md`。

## 11. 零基础学习协作（2026-11-16 补充）

- 用户正在通过本项目学习 Android；按 [学习与代码阅读手册](docs/learning/android-learning-companion.md) 组织教学，不能以“代码已由 AI 生成”代替用户理解。
- **教学模式**默认只讲一个功能/调用链：职责 → 前置知识 → 最小例子 → 用户练习 → 验收，不主动改代码；**开发模式**仍严格遵守上文的变更确认、测试与安全边界。
- 教学先从 `:core` 纯 Kotlin、`MainActivity` 的角色选择入手，再进入录屏 Service 与 `WebRtcPeer`，不要对零基础用户一上来逐行讲整个 WebRTC 文件。
- 每次交付区分“文档计划 / 代码实现 / 构建通过 / 真机验收”；M1 已有本地采集与预览代码，但跨设备视频链路不得在实际验收前宣称完成。
