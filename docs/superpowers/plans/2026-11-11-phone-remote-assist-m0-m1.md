# 云畔 M0–M1 实施计划（工程骨架 + 画面链路）

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.
>
> 本项目为学习项目：默认由用户本人亲手实现（教学式执行：先讲设计与关键 API，用户写核心代码，Claude 负责 review、调试协助与答疑）；如选择代理执行模式，仍需逐任务验收。

**Goal:** 搭好安卓多模块工程，并打通「被控端截屏 → WebRTC DataChannel → 控制端显示」的最小画面链路（M1 验收：同一 Wi-Fi 下两机互见画面 ≥5fps）。

**Architecture:** 单一 Gradle 工程四个模块：`:core`（纯 JVM 协议库，可在电脑上跑单元测试）、`:transport`（libwebrtc 封装）、`:app-agent`（被控端）、`:app-controller`（控制端）。M1 阶段用「手动复制粘贴 SDP 聊天码」代替信令服务器，专注验证采集、编码、通道与渲染。

**Tech Stack:** Kotlin、Jetpack Compose、协程、`io.github.webrtc-sdk:android`（libwebrtc 预编译）、JUnit4（`:core` 单元测试）。minSdk 26。

**Spec:** `docs/superpowers/specs/2026-11-11-phone-remote-assist-design.md`

**计划范围：** 本计划只覆盖设计文档的 M0–M1。M2（输入注入）、M3（MQTT 信令与配对）、M4（父母端体验与保活）、M5–M6 将在此计划完成后各自单独成计划——每个计划都交付可运行、可验收的软件。

## Global Constraints

- minSdk 26；targetSdk 使用创建时 Android Studio 稳定版默认值，并在 `gradle/libs.versions.toml` 记录实际值。
- 语言 Kotlin；UI 用 Jetpack Compose；异步用协程。
- 模块与包名：`:core` = `dev.remoteassist.core`（纯 JVM，不依赖 Android）；`:transport` = `dev.remoteassist.transport`（Android Library）；`:app-agent` = `dev.remoteassist.agent`（applicationId 同）；`:app-controller` = `dev.remoteassist.controller`（applicationId 同）。
- 零服务器、零月费：M1 只使用公共 STUN 与局域网直连，不引入任何付费服务。
- 界面文案中文，面向老人与家属。
- Git：单仓库（`D:\self\remote-assist`），每个任务结束提交一次；**提交信息不加任何 AI 署名（Co-Authored-By 等一律不加，这是用户规则）**。
- WebRTC 依赖：`io.github.webrtc-sdk:android` 的最新稳定版；选定版本写入 `libs.versions.toml` 并在首次引入的提交信息中注明。
- 真机：控制端 = 一加；被控端联调 = 红米 Note 10。所有安卓侧验收以真机为准。
- 协议常量：`PROTOCOL_VERSION = 1`。

## Review Focus

（五类最可能伤害真实用户的输入/故障；每行都已在对应任务中钉住测试或手动验收步骤）

1. 用户在系统录屏弹窗点「拒绝」或直接返回 → 服务不得启动、App 不崩溃，界面回到可重试状态。（Task 6 Step 4）
2. 聊天软件粘贴的 SDP 文本带首尾空白/换行/被截断 → 解码必须给出明确错误，而不是崩溃或静默连错。（Task 4 SdpCodecTest）
3. 大帧跨多个分片且中间丢片/乱序 → 只丢该帧，不花屏、不内存泄漏、不卡住后续帧。（Task 3 FrameAssemblerTest）
4. 会话中屏幕旋转导致采集内容尺寸变化 → M1 只要求不崩溃并提示「旋转后需重新连接」。（Task 6 Step 4）
5. 被控端进程被杀/服务停止 → 控制端须显示「连接已断开」，而非无限等待。（Task 7 Step 4）

---

### Task 1: 环境准备与工程骨架

**学习点：** Android Studio 工程结构、Gradle 多模块、adb 部署。

**Files:**
- Create（Android Studio 向导 + 手工调整）：`settings.gradle.kts`、根 `build.gradle.kts`、`gradle/libs.versions.toml`、`.gitignore`、Gradle wrapper，四个模块 `:core`、`:transport`、`:app-agent`、`:app-controller` 各自的 `build.gradle.kts` 与最小 `MainActivity`/`AndroidManifest.xml`。
- Create: `app-agent/src/main/java/dev/remoteassist/agent/AgentApp.kt`、`app-controller/src/main/java/dev/remoteassist/controller/ControllerApp.kt`（Application 占位，后续 `WebRtcPeer.initOnce` 用）。

**Interfaces:**
- Produces: 工程可通过 `./gradlew assembleDebug` 构建；四个模块的包名/命名空间固定为 `dev.remoteassist.{core,transport,agent,controller}`；两个 APK 可安装到真机启动。

- [ ] **Step 1: 安装开发环境**

安装最新稳定版 Android Studio（自带 JDK 与 SDK Manager）；在 SDK Manager 中安装对应 targetSdk 的最新稳定版 Platform 与 Platform-Tools。验证：
Run: `adb version`
Expected: 输出 `Android Debug Bridge version …`

- [ ] **Step 2: 创建工程**

Android Studio「New Project → Empty Activity（Compose）」创建工程 `remote-assist`，包名 `dev.remoteassist.controller`，保存到 `D:\self\remote-assist`（与已有 `docs/` 目录合并）。把根 `settings.gradle.kts` 中的 `include(":app")` 换成：

```kotlin
include(":core")
include(":transport")
include(":app-agent")
include(":app-controller")
```

并把向导生成的 `app/` 目录重命名为 `app-controller/`。

- [ ] **Step 3: 新建三个模块**

`:core` 用 Kotlin/JVM library（`build.gradle.kts` 使用 `kotlin("jvm")` 插件）；`:transport` 用 Android Library；`:app-agent` 用 Phone & Tablet 应用模块。各模块 `namespace`/`applicationId` 按 Global Constraints。`app-agent` 的 `applicationId` 设为 `dev.remoteassist.agent`。验证：
Run: `./gradlew :core:build :transport:assembleDebug :app-agent:assembleDebug :app-controller:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: 占位界面与 Application**

两个 App 各用 Compose 显示占位文案（被控端「帮爸妈 · 被控端」、控制端「帮爸妈 · 控制端」）；各建 `Application` 子类并注册到各自 manifest。验证：安装到两台真机并启动不闪退：
Run: `adb -s <设备序列号> install -r app-agent/build/outputs/apk/debug/app-agent-debug.apk`
Expected: `Success`；打开 App 看到占位文案。

- [ ] **Step 5: 初始化 Git 并提交**

Run:
```bash
git init
git add .
git commit -m "chore: Android 多模块工程骨架"
```
Expected: 提交成功，`git log --oneline` 可见。

---

### Task 2: :core 消息编解码（TDD）

**学习点：** 二进制协议设计、大端序、`ByteBuffer`、快速单元测试（不用手机就能跑）。

**Files:**
- Create: `../../../core/src/main/kotlin/dev/remoteassist/core/protocol/WireMessage.kt`
- Create: `core/src/main/kotlin/dev/remoteassist/core/protocol/MessageCodec.kt`
- Test: `core/src/test/kotlin/dev/remoteassist/core/protocol/MessageCodecTest.kt`

**Interfaces:**
- Produces（后续所有任务依赖，名字不得改动）:

```kotlin
sealed interface WireMessage {
    data class Hello(val version: Int, val screenWidth: Int, val screenHeight: Int, val rotation: Int, val deviceName: String) : WireMessage
    data class VideoChunk(val frameId: Int, val chunkIndex: Int, val chunkCount: Int, val payload: ByteArray) : WireMessage
    data class Tap(val x: Float, val y: Float) : WireMessage
    data class Swipe(val x1: Float, val y1: Float, val x2: Float, val y2: Float, val durationMs: Int) : WireMessage
    data class LongPress(val x: Float, val y: Float, val durationMs: Int) : WireMessage
    data class Key(val key: GlobalKey) : WireMessage
    data class Text(val text: String) : WireMessage
    data class Ping(val timestampMs: Long) : WireMessage
    data class Pong(val timestampMs: Long) : WireMessage
    data class Bye(val reasonCode: Int) : WireMessage
}
enum class GlobalKey { BACK, HOME, RECENTS }
class ProtocolException(message: String) : Exception(message)
object MessageCodec {
    fun encode(message: WireMessage): ByteArray
    fun decode(bytes: ByteArray): WireMessage  // 未知类型/长度不符 → ProtocolException
}
```

- [ ] **Step 1: 写失败测试**

```kotlin
package dev.remoteassist.core.protocol

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class MessageCodecTest {
    @Test fun `hello round trip`() {
        val msg = WireMessage.Hello(1, 1080, 2400, 0, "iQOO")
        val decoded = MessageCodec.decode(MessageCodec.encode(msg)) as WireMessage.Hello
        assertEquals(1, decoded.version)
        assertEquals(1080, decoded.screenWidth)
        assertEquals(2400, decoded.screenHeight)
        assertEquals(0, decoded.rotation)
        assertEquals("iQOO", decoded.deviceName)
    }

    @Test fun `video chunk round trip`() {
        val payload = ByteArray(100) { it.toByte() }
        val decoded = MessageCodec.decode(MessageCodec.encode(WireMessage.VideoChunk(7, 1, 3, payload))) as WireMessage.VideoChunk
        assertEquals(7, decoded.frameId)
        assertEquals(1, decoded.chunkIndex)
        assertEquals(3, decoded.chunkCount)
        assertArrayEquals(payload, decoded.payload)
    }

    @Test fun `all small messages round trip`() {
        val msgs = listOf(
            WireMessage.Tap(1.5f, 2.5f),
            WireMessage.Swipe(0f, 0f, 100f, 200f, 300),
            WireMessage.LongPress(10f, 20f, 500),
            WireMessage.Key(GlobalKey.BACK),
            WireMessage.Text("你好，爸妈"),
            WireMessage.Ping(123456789L),
            WireMessage.Pong(123456789L),
            WireMessage.Bye(1),
        )
        for (m in msgs) assertEquals(m, MessageCodec.decode(MessageCodec.encode(m)))
    }

    @Test fun `unknown type throws`() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(byteArrayOf(0x7F)) }
    }

    @Test fun `empty input throws`() {
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(ByteArray(0)) }
    }

    @Test fun `truncated hello throws`() {
        val full = MessageCodec.encode(WireMessage.Hello(1, 1080, 2400, 0, "iQOO"))
        assertThrows(ProtocolException::class.java) { MessageCodec.decode(full.copyOf(full.size - 2)) }
    }

    @Test fun `text with emoji round trip`() {
        val msg = WireMessage.Text("📱 帮爸妈")
        assertEquals(msg, MessageCodec.decode(MessageCodec.encode(msg)))
    }
}
```

（编码格式自定：1 字节类型码 + 大端定长字段 + 变长载荷；测试不锁定具体类型码字节值。）

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :core:test --tests "dev.remoteassist.core.protocol.MessageCodecTest"`
Expected: 编译失败或测试 FAIL（`MessageCodec` 尚未实现）

- [ ] **Step 3: 实现 `WireMessage.kt` 与 `MessageCodec.kt`**

按 Interfaces 定义实现。字节序用 `java.nio.ByteBuffer`（`BIG_ENDIAN`）；变长字段先写长度前缀。解码时严格校验：空输入、未知类型、字段截断、长度越界 → `ProtocolException`；`Text` 用 UTF-8。

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :core:test --tests "dev.remoteassist.core.protocol.MessageCodecTest"`
Expected: PASS（7 个测试）

- [ ] **Step 5: 提交**

```bash
git add core/
git commit -m "feat(core): 消息编解码与协议错误处理"
```

---

### Task 3: 视频帧分片与重组（TDD）

**学习点：** 为什么要分片（DataChannel 单条消息不宜过大）、分片与重组的状态机、丢包下的降级策略。

**Files:**
- Create: `core/src/main/kotlin/dev/remoteassist/core/protocol/VideoChunker.kt`
- Create: `core/src/main/kotlin/dev/remoteassist/core/protocol/FrameAssembler.kt`
- Test: `core/src/test/kotlin/dev/remoteassist/core/protocol/VideoChunkerTest.kt`
- Test: `core/src/test/kotlin/dev/remoteassist/core/protocol/FrameAssemblerTest.kt`

**Interfaces:**
- Consumes: `WireMessage.VideoChunk`（Task 2）
- Produces:

```kotlin
object VideoChunker {
    const val DEFAULT_MAX_CHUNK_BYTES = 12_000
    fun chunk(frameId: Int, jpeg: ByteArray, maxChunkBytes: Int = DEFAULT_MAX_CHUNK_BYTES): List<WireMessage.VideoChunk>
}
class FrameAssembler {
    fun accept(chunk: WireMessage.VideoChunk): ByteArray?  // 帧完整时返回 JPEG 并清空；更新的 frameId 到来时丢弃旧的未完成帧
    fun reset()
}
```

- [ ] **Step 1: 写失败测试**

```kotlin
// VideoChunkerTest 关键断言：
val small = VideoChunker.chunk(1, ByteArray(1000))
assertEquals(1, small.size)
val chunks = VideoChunker.chunk(2, ByteArray(25_000) { it.toByte() })
assertEquals(3, chunks.size)
assertEquals(listOf(0, 1, 2), chunks.map { it.chunkIndex })
assertEquals(3, chunks[0].chunkCount)
assertEquals(25_000, chunks.sumOf { it.payload.size })

// FrameAssemblerTest 关键用例：
// 1) 顺序到达 → 返回完整帧且内容一致
// 2) 乱序到达 → 仍返回完整帧
// 3) 缺片 → 返回 null
// 4) 新 frameId 到来 → 旧帧被丢弃，之后迟到的旧片被忽略
// 5) reset() 后可复用
```

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :core:test --tests "dev.remoteassist.core.protocol.*"`
Expected: FAIL（类不存在）

- [ ] **Step 3: 实现两个类**

`FrameAssembler` 持有 `currentFrameId`、`chunkCount`、`Array<ByteArray?>` 与已收计数；`chunkIndex` 越界或重复到达时忽略；凑齐后拼接返回并清空。

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :core:test`
Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
git add core/
git commit -m "feat(core): 视频帧分片与重组，弱网丢片只丢单帧"
```

---

### Task 4: 坐标映射与 SDP 聊天码（TDD）

**学习点：** 等比缩放与黑边坐标变换（为 M2 触摸映射打基础）、Base64 与健壮的文本编解码。

**Files:**
- Create: `core/src/main/kotlin/dev/remoteassist/core/screen/CoordinateMapper.kt`
- Create: `core/src/main/kotlin/dev/remoteassist/core/signal/SdpCodec.kt`
- Test: `core/src/test/kotlin/dev/remoteassist/core/screen/CoordinateMapperTest.kt`
- Test: `core/src/test/kotlin/dev/remoteassist/core/signal/SdpCodecTest.kt`

**Interfaces:**
- Produces:

```kotlin
object CoordinateMapper {
    // 把控制端视图上的触摸点换算成被控端屏幕坐标；触摸落在黑边上时返回 null
    fun map(containerWidth: Float, containerHeight: Float, videoWidth: Float, videoHeight: Float, touchX: Float, touchY: Float): Pair<Float, Float>?
}
object SdpCodec {
    fun encode(sdp: String): String   // 输出 "RSDP1:" + 单行 Base64，便于微信复制粘贴
    fun decode(text: String): String  // 容忍首尾空白与换行；格式不符 → ProtocolException
}
```

- [ ] **Step 1: 写失败测试**

CoordinateMapperTest：等比例 1:1 时 `(100,200)` 映射为自身；容器比视频宽（左右黑边）时映射正确、点黑边返回 null；容器比视频高（上下黑边）同理。
SdpCodecTest：多行 SDP 往返一致；`decode` 容忍首尾空白与换行；非 Base64 输入抛 `ProtocolException`。

- [ ] **Step 2: 运行确认失败**

Run: `./gradlew :core:test --tests "dev.remoteassist.core.*"`
Expected: FAIL

- [ ] **Step 3: 实现**

`map`：先算等比缩放后的显示矩形（letterbox），触摸落在矩形外返回 null，否则按比例换算。`SdpCodec` 用 `java.util.Base64`（`getMimeEncoder/Decoder` 关闭换行）。

- [ ] **Step 4: 运行测试通过**

Run: `./gradlew :core:test`
Expected: 全部 PASS

- [ ] **Step 5: 提交**

```bash
git add core/
git commit -m "feat(core): 触摸坐标映射与 SDP 聊天码编解码"
```

---

### Task 5: :transport —— WebRTC 封装与手动 SDP 连接

**学习点：** WebRTC 三个核心概念（PeerConnection / SDP / ICE）、DataChannel 用法、库的线程模型。

**Files:**
- Modify: `transport/build.gradle.kts`（加 `io.github.webrtc-sdk:android` 依赖）
- Create: `transport/src/main/kotlin/dev/remoteassist/transport/WebRtcPeer.kt`
- Modify: `app-agent` 与 `app-controller` 的 `AgentApp.kt`/`ControllerApp.kt`（调用 `initOnce`）与 `MainActivity.kt`（手动 SDP 流程 UI）
- Create: `app-agent/src/main/java/dev/remoteassist/agent/ManualConnectSection.kt`
- Create: `app-controller/src/main/java/dev/remoteassist/controller/ManualConnectSection.kt`

**Interfaces:**
- Consumes: `SdpCodec`（Task 4）、`MessageCodec`/`WireMessage.kt`（Task 2）
- Produces（后续任务依赖，名字不得改动）:

```kotlin
class WebRtcPeer private constructor(...) {
    interface Listener {
        fun onLocalSdpReady(sdp: String)              // 非 trickle：ICE 收集完成（或 5 秒超时）后的完整 SDP
        fun onDataConnected()
        fun onMessage(bytes: ByteArray)
        fun onDisconnected()
    }
    companion object {
        fun initOnce(context: Context)
        fun asOfferer(context: Context, listener: Listener): WebRtcPeer
        fun asAnswerer(context: Context, offerSdp: String, listener: Listener): WebRtcPeer
        fun defaultIceServers(): List<String>  // ["stun:stun.l.google.com:19302", "stun:stun.miwifi.com:3478"]
    }
    fun setAnswer(answerSdp: String)   // 仅 offerer 使用
    fun send(bytes: ByteArray): Boolean
    fun bufferedAmount(): Long
    fun close()
}
```

- [ ] **Step 1: 加依赖并实现 `WebRtcPeer`**

`initOnce` 每进程初始化一次 `PeerConnectionFactory`。`RTCConfiguration` 用 `defaultIceServers()` 构造 `PeerConnection.IceServer`。DataChannel 标签 `"app"`，有序可靠。Offerer 流程：建 DataChannel → `createOffer` → `setLocalDescription` → 等 `onIceGatheringChange(COMPLETE)`，**最长等 5 秒**，超时用当前已收集的 SDP → `onLocalSdpReady`。Answerer：`setRemoteDescription(offer)` 成功 → `createAnswer` → `setLocalDescription` → 同样等待后回调。`onDataChannel` 或 DataChannel 状态 `OPEN` 时回调 `onDataConnected`。`onConnectionChange(FAILED/CLOSED)` 回调 `onDisconnected`。

- [ ] **Step 2: 两个 App 接手动流程 UI**

Agent：按钮「① 生成邀请码」→ `asOfferer` → 用 `SdpCodec.encode` 显示并可复制到剪贴板；输入框粘贴应答码 →「③ 应用应答」→ `setAnswer`；连接后按钮「发送 PING」发送 `MessageCodec.encode(Ping(System.currentTimeMillis()))`，收到 `Pong` 显示往返毫秒。
Controller：粘贴邀请码 →「② 生成应答」→ `asAnswerer` → 显示应答码并可复制；收到 `Ping` 自动回 `Pong`。

- [ ] **Step 3: 编译并安装**

Run: `./gradlew :app-agent:assembleDebug :app-controller:assembleDebug`
Expected: `BUILD SUCCESSFUL`；`adb install -r` 安装到两台真机。

- [ ] **Step 4: 真机手动验收（同一 Wi-Fi）**

Agent 生成邀请码 → 复制 → 通过微信发给自己 → Controller 复制粘贴 → 生成应答码 → 发回 → Agent 粘贴应用。预期：两端显示「通道已连接」；Agent 点「发送 PING」后 1 秒内显示往返毫秒，连续 5 次成功。

- [ ] **Step 5: 提交**

```bash
git add transport/ app-agent/ app-controller/ gradle/
git commit -m "feat(transport): WebRTC 封装与手动 SDP 建链（M1 调试通道）"
```

---

### Task 6: :app-agent 采集管线（MediaProjection → JPEG）

**学习点：** MediaProjection 授权模型、VirtualDisplay/ImageReader 图像管线、前台服务与通知。

**Files:**
- Modify: `app-agent/src/main/AndroidManifest.xml`（权限与服务声明）
- Create: `app-agent/src/main/java/dev/remoteassist/agent/capture/ScreenCaptureService.kt`
- Create: `app-agent/src/main/java/dev/remoteassist/agent/capture/JpegFrameProducer.kt`
- Create: `app-agent/src/main/java/dev/remoteassist/agent/capture/NotificationHelper.kt`
- Modify: `app-agent/src/main/java/dev/remoteassist/agent/MainActivity.kt`（录屏授权 + 本机预览）

**Interfaces:**
- Produces（Task 7 依赖）:

```kotlin
class JpegFrameProducer(
    private val context: Context,
    private val mediaProjection: MediaProjection,
    private val listener: Listener,
) {
    interface Listener {
        fun onFrame(jpeg: ByteArray, sourceWidth: Int, sourceHeight: Int, rotation: Int)
        fun onStopped()
    }
    fun start(targetFps: Int = 10, maxWidth: Int = 960, jpegQuality: Int = 60)
    fun stop()
}
```

- [ ] **Step 1: Manifest 权限与服务**

声明 `INTERNET`、`FOREGROUND_SERVICE`、`FOREGROUND_SERVICE_MEDIA_PROJECTION`、`POST_NOTIFICATIONS`；`ScreenCaptureService` 加 `android:foregroundServiceType="mediaProjection"`、`android:exported="false"`。

- [ ] **Step 2: 实现采集服务与 JPEG 生产器**

`ScreenCaptureService`：`onStartCommand` 先 `startForeground(通知)`（Android 14+ 要求先于 `getMediaProjection`）→ `getMediaProjection(resultCode, data)` → 注册 `MediaProjection.Callback` → 用 `ImageReader.newInstance(宽, 高, PixelFormat.RGBA_8888, 2)` 建 `VirtualDisplay`（真实屏幕尺寸与 dpi）→ 启动 `JpegFrameProducer`。`JpegFrameProducer`：`onImageAvailable` 中 `acquireLatestImage()` → 转 `Bitmap` → 缩放到 `maxWidth` → `compress(JPEG, quality)` → 按帧间隔丢帧限频 → 回调。`stop()` 释放全部资源。

- [ ] **Step 3: 授权入口与本机预览**

`MainActivity`：按钮「开始采集」→ `rememberLauncherForActivityResult` 拉起 `createScreenCaptureIntent()`；`RESULT_OK` 启动服务（传 resultCode/resultData）；`RESULT_CANCELED` 回到初始状态并提示「未授权，可重试」。Android 13+ 先请求 `POST_NOTIFICATIONS` 运行时权限。服务用 `StateFlow` 暴露最新帧与帧率，界面用 `Image(bitmap)` 预览。

- [ ] **Step 4: 真机验收**

红米 Note 10：授权后预览出现、帧率 8–10fps；切到别的 App 再回来预览仍更新；系统弹窗点「拒绝」时不崩溃且有重试提示；旋转屏幕不崩溃（提示需重连）。

- [ ] **Step 5: 提交**

```bash
git add app-agent/
git commit -m "feat(agent): MediaProjection 采集与 JPEG 帧管线"
```

---

### Task 7: 端到端画面串流（M1 验收）

**学习点：** 端到端数据流（采集→分片→通道→重组→解码→渲染）、背压与丢帧策略。

**Files:**
- Create: `app-agent/src/main/java/dev/remoteassist/agent/StreamSession.kt`
- Create: `app-controller/src/main/java/dev/remoteassist/controller/VideoScreen.kt`
- Modify: `app-agent/.../MainActivity.kt`、`app-controller/.../MainActivity.kt`（把建链、采集、显示接起来）

**Interfaces:**
- Consumes: Task 2/3/4/5/6 的全部接口。
- Produces: M1 可运行形态——手动建链后 Agent 发送 `Hello` 与分片 `VideoChunk`；Controller 重组、解码显示；断链可见。

- [ ] **Step 1: Agent 发送 `Hello` 与视频分片**

`onDataConnected` → 发送 `Hello(PROTOCOL_VERSION, 宽, 高, rotation, Build.MODEL)`；每帧 `VideoChunker.chunk(frameId++, jpeg)` 逐片 `send`；发送前若 `bufferedAmount() > 512 * 1024` 则整帧丢弃（`frameId` 不递增）。界面提供「暂停/继续串流」开关与发送帧率显示。

- [ ] **Step 2: Controller 重组与显示**

`onMessage` → `MessageCodec.decode`；`VideoChunk` 交给 `FrameAssembler`；完整帧 `BitmapFactory.decodeByteArray` 成功才更新界面；失败静默丢帧并计数。显示最近一帧 + 「帧率 / 丢帧数 / 通道状态」。

- [ ] **Step 3: 编译安装**

Run: `./gradlew :app-agent:assembleDebug :app-controller:assembleDebug`
Expected: `BUILD SUCCESSFUL`

- [ ] **Step 4: M1 真机验收（同一 Wi-Fi）**

按 Task 5 步骤建链 → Agent 开采集 → Controller 看到实时画面，帧率 ≥5fps，画面延迟体感 ≤1s；结束 Agent App 进程后 Controller 显示「连接已断开」。

- [ ] **Step 5: 提交与记录**

```bash
git add .
git commit -m "feat: M1 端到端画面串流（手动 SDP）"
```
在设计文档的决策记录追加一行：M1 验收结果（帧率、延迟体感、异常表现）。

---

## Self-Review（已自查，无待修项）

- **Spec 覆盖：** 设计文档 M0（骨架）与 M1（画面链路、手动 SDP、≥5fps 验收）的全部条目都有对应任务；M2–M6 按 Plan 范围声明留待后续独立计划。核心协议（含 M2 才用到的输入消息类型）一次性在 Task 2 定义并测试，避免后续改动编解码器。
- **步骤扫描：** 每个步骤一个动作、一个可检查结果；无「TBD / 处理边界情况」类空话；纯逻辑均有测试代码，安卓侧均有真机验收标准。
- **类型一致性：** `MessageCodec`、`VideoChunker`、`FrameAssembler`、`CoordinateMapper`、`SdpCodec`、`WebRtcPeer`、`JpegFrameProducer` 在定义任务与使用任务中的名称、参数、返回类型一致。
- **Review Focus：** 五条均对应到 Task 3/4 的单元测试或 Task 6/7 的真机验收步骤。
- **比例：** 计划聚焦决策与接口，未转写实现细节；Task 5 的库内部调用留给实现者按官方 API 完成。
