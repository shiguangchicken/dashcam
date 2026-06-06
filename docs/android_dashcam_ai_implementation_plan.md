# 旧手机 Android 行车记录仪 APP：AI 可实现方案与自动化测试设计

> 目标：在 Linux + Android Studio 环境下，设计并实现一个 Android APP，把旧手机变成行车记录仪。APP 同时支持“记录仪模式”和“远程查看模式”，并配套一套可由 AI 参与执行的自动化开发、测试、验证闭环。

---

## 1. 关键结论与可行性边界

### 1.1 推荐技术路线

- 开发语言：Kotlin。
- UI：Jetpack Compose。
- 相机：CameraX VideoCapture + ImageCapture。
- 后台录制：Foreground Service，声明 `camera` / `microphone` / `connectedDevice` / `dataSync` 等合适的 foreground service type。
- 视频编码：优先使用设备硬件编码 H.264 / H.265，避免软件编码。
- 存储：Room + MediaStore / App-specific external storage。
- 远程访问：记录仪端启动 LocalOnlyHotspot + 内置 HTTP/WebSocket 服务，查看端连接热点后访问局域网 API。
- 语音唤醒：离线 wake word / keyword spotting + 轻量命令识别，优先不要依赖云端语音识别。
- 自动化测试：Compose UI Test + UI Automator + Gradle Managed Devices + 真机测试矩阵 + Macrobenchmark + AI 视觉检查。

### 1.2 Android 平台限制

Android 14 之后，前台服务需要在 Manifest 中声明明确的 foreground service type，并根据类型申请相应权限。相机和麦克风持续工作必须使用前台服务和常驻通知，否则后台/息屏场景容易被系统限制或杀掉。

官方文档参考：

- Foreground service types：<https://developer.android.com/develop/background-work/services/fgs/service-types>
- Android 14 foreground service type requirements：<https://developer.android.com/about/versions/14/changes/fgs-types-required>

热点方面，普通第三方 APP 不应依赖隐藏 API 或反射强开系统热点。推荐使用 Android 官方 `WifiManager.LocalOnlyHotspot`，它可以创建一个仅本地通信、无互联网共享的 Wi-Fi 热点，适合“另一个手机连接后查看视频/照片”的场景。

官方文档参考：

- LocalOnlyHotspot：<https://developer.android.com/develop/connectivity/wifi/localonlyhotspot>
- Android Soft AP：<https://source.android.com/docs/core/connect/wifi-softap>

语音唤醒方面，长时间 always-on listening 需要非常注意功耗。推荐使用本地小模型/关键词检测，不建议一直开大模型语音识别。可以选 Picovoice Porcupine、Vosk、Whisper.cpp 小模型、PocketSphinx 或自研 TensorFlow Lite 关键词模型。

参考：

- Picovoice Porcupine：<https://github.com/Picovoice/porcupine>
- Android voice / hotword guide：<https://source.android.com/docs/automotive/voice/voice_interaction_guide/app_development>

---

## 2. 产品需求定义

### 2.1 两种 APP 运行角色

同一个 APP 支持两种模式：

1. **记录仪模式**
   - 安装在旧手机上。
   - 持续录制视频。
   - 根据模式切换帧率、码率、是否录音、是否开热点。
   - 提供 HTTP/WebSocket 服务供另一台手机访问。
   - 支持语音命令控制。

2. **远程查看模式**
   - 安装在日常使用手机上。
   - 连接记录仪手机的 Wi-Fi 热点。
   - 浏览记录仪端的视频、照片、设置。
   - 可远程下载、删除、播放视频。
   - 可远程切换驾驶模式/停车模式、打开/关闭录音、拍照等。

### 2.2 核心功能列表

| 功能 | 说明 | 优先级 |
|---|---|---|
| 息屏录制 | 锁屏/息屏后继续录制 | P0 |
| 驾驶模式 | 高帧率、高码率、循环录制 | P0 |
| 停车模式 | 低帧率、低码率、省电录制 | P0 |
| 循环录像 | 按时间分段，空间不足自动删除旧片段 | P0 |
| 拍照 | UI 或语音触发拍照 | P0 |
| 录音开关 | 支持录音/静音录制切换 | P0 |
| Wi-Fi 热点 | 记录仪端创建热点 | P0 |
| 远程查看 | 另一台手机连接热点查看照片/视频 | P0 |
| 语音控制 | 拍照、打开录音、关闭录音、打开热点、关闭热点、停车模式、驾驶模式 | P1 |
| 低功耗优化 | 停车模式降低帧率、关闭预览、分级唤醒 | P1 |
| 事件锁定 | 碰撞/手动保存重要视频不被循环删除 | P2 |
| AI 辅助测试 | 自动生成代码、运行测试、截图分析、修复 | P1 |

---

## 3. 总体架构

```text
+--------------------------------------------------------------+
|                         DashCam APP                           |
+--------------------------------------------------------------+
| UI Layer                                                       |
| - Compose Screens                                              |
| - Recorder Dashboard                                           |
| - Remote Browser                                               |
| - Settings                                                     |
+--------------------------------------------------------------+
| Domain Layer                                                   |
| - RecordingUseCase                                             |
| - ModeSwitchUseCase                                            |
| - VoiceCommandUseCase                                          |
| - HotspotUseCase                                               |
| - RemoteMediaUseCase                                           |
+--------------------------------------------------------------+
| Service Layer                                                  |
| - RecorderForegroundService                                    |
| - VoiceForegroundService                                       |
| - HotspotService                                               |
| - EmbeddedHttpServer                                           |
+--------------------------------------------------------------+
| Data Layer                                                     |
| - Room Database                                                |
| - Media Repository                                             |
| - Settings Repository                                          |
| - HTTP API Client / Server                                     |
+--------------------------------------------------------------+
| Hardware / Android APIs                                        |
| - CameraX                                                      |
| - MediaCodec / MediaRecorder                                   |
| - AudioRecord                                                  |
| - WifiManager.LocalOnlyHotspot                                 |
| - PowerManager / WakeLock                                      |
| - SensorManager                                                |
+--------------------------------------------------------------+
```

---

## 4. 模块设计

### 4.1 Android 工程结构

推荐使用多 module：

```text
dashcam/
  settings.gradle.kts
  build.gradle.kts
  app/
    src/main/
  core-common/
  core-database/
  core-media/
  core-network/
  core-voice/
  feature-recorder/
  feature-remote/
  feature-settings/
  benchmark/
  test-robot/
```

#### Module 说明

| Module | 职责 |
|---|---|
| `app` | Application、导航、DI 初始化 |
| `core-common` | 公共工具、Result、日志、权限封装 |
| `core-database` | Room 表结构、DAO、Migration |
| `core-media` | CameraX、录制、拍照、文件管理 |
| `core-network` | HTTP server/client、WebSocket、DTO |
| `core-voice` | 语音唤醒、命令识别、命令分发 |
| `feature-recorder` | 记录仪模式 UI 与业务 |
| `feature-remote` | 远程查看模式 UI 与业务 |
| `feature-settings` | 设置界面 |
| `benchmark` | Macrobenchmark、Baseline Profile |
| `test-robot` | 自动化测试 robot DSL |

---

## 5. 记录仪模式设计

### 5.1 前台服务

记录仪模式的核心是 `RecorderForegroundService`。

职责：

- 启动 CameraX 录制。
- 管理分段文件。
- 响应模式切换。
- 保持息屏录制。
- 显示常驻通知。
- 暴露 service command API。

Manifest 示例：

```xml
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.RECORD_AUDIO" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CAMERA" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_MICROPHONE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE" />
<uses-permission android:name="android.permission.POST_NOTIFICATIONS" />
<uses-permission android:name="android.permission.WAKE_LOCK" />

<service
    android:name=".recorder.RecorderForegroundService"
    android:exported="false"
    android:foregroundServiceType="camera|microphone|connectedDevice|dataSync" />
```

> 注意：如果用户关闭权限、关闭通知、系统电池策略限制后台运行，息屏录制可能失败。首次启动时需要引导用户完成权限和电池优化白名单设置。

### 5.2 录制参数

#### 驾驶模式

| 参数 | 推荐值 |
|---|---|
| 分辨率 | 1080p，旧手机性能差则 720p |
| 帧率 | 30 fps 或 60 fps |
| 视频编码 | H.264 优先，支持则 H.265 |
| 码率 | 8~16 Mbps for 1080p30 |
| 分段 | 1/3/5 分钟可选 |
| 录音 | 默认开启，可关闭 |
| GPS | 可选，默认关闭以省电 |
| 热点 | 默认关闭，语音/按钮打开 |

#### 停车模式

| 参数 | 推荐值 |
|---|---|
| 分辨率 | 720p 或 1080p 低码率 |
| 帧率 | 1/2/5 fps |
| 码率 | 0.5~2 Mbps |
| 分段 | 5/10 分钟 |
| 录音 | 默认关闭 |
| 预览 | 默认关闭 |
| 事件触发 | 可选：传感器检测震动后临时升帧 |

### 5.3 息屏录制策略

息屏后继续记录的实现策略：

1. 使用 Foreground Service。
2. Service 内绑定 CameraX `VideoCapture`。
3. 关闭 UI 预览 Surface，只保留编码管线。
4. 使用分段录制，避免单文件过大。
5. 仅在录制启动/切换模式/保存文件时短时间持有 WakeLock。
6. 通知栏显示当前状态：驾驶模式/停车模式、录音状态、存储剩余空间。
7. 监听电量和温度，过热时自动降码率/降帧。

不建议长期持有完整 WakeLock。长期 WakeLock 会显著耗电，并容易被系统限制。

---

## 6. 视频与照片存储设计

### 6.1 文件目录

建议使用 app-specific external storage：

```text
Android/data/<package>/files/DashCam/
  videos/
    driving/
      2026-05-27/
        20260527_153000_001.mp4
        20260527_153300_002.mp4
    parking/
      2026-05-27/
        20260527_230000_001.mp4
    locked/
      20260527_153100_event.mp4
  photos/
    2026-05-27/
      20260527_153120.jpg
  thumbnails/
    video_<id>.jpg
  logs/
```

### 6.2 Room 数据库

```sql
CREATE TABLE media_file (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    type TEXT NOT NULL,              -- video/photo
    mode TEXT NOT NULL,              -- driving/parking/manual/event
    path TEXT NOT NULL,
    thumbnail_path TEXT,
    created_at INTEGER NOT NULL,
    duration_ms INTEGER,
    size_bytes INTEGER NOT NULL,
    width INTEGER,
    height INTEGER,
    fps REAL,
    bitrate INTEGER,
    has_audio INTEGER NOT NULL,
    locked INTEGER NOT NULL DEFAULT 0,
    checksum TEXT,
    deleted INTEGER NOT NULL DEFAULT 0
);

CREATE TABLE app_settings (
    key TEXT PRIMARY KEY,
    value TEXT NOT NULL
);

CREATE TABLE record_session (
    id INTEGER PRIMARY KEY AUTOINCREMENT,
    started_at INTEGER NOT NULL,
    ended_at INTEGER,
    mode TEXT NOT NULL,
    reason TEXT
);
```

### 6.3 循环删除策略

配置项：

- 最大占用空间，例如 32GB。
- 最小保留空间，例如 2GB。
- 是否允许删除停车模式视频。
- locked 文件永不自动删除。

删除算法：

```text
when free_space < min_free_space or dashcam_dir_size > max_storage:
    candidates = media_file
        where locked = 0 and deleted = 0
        order by created_at asc
    delete oldest files until space recovered
```

---

## 7. 模式切换设计

### 7.1 状态机

```text
Idle
  -> RecordingDriving
  -> RecordingParking
  -> Error

RecordingDriving
  -> RecordingParking
  -> Paused
  -> Idle
  -> Error

RecordingParking
  -> RecordingDriving
  -> Paused
  -> Idle
  -> EventBoostRecording
  -> Error

EventBoostRecording
  -> RecordingParking
  -> RecordingDriving
```

### 7.2 命令接口

统一定义命令：

```kotlin
sealed interface DashCamCommand {
    data object StartDrivingMode : DashCamCommand
    data object StartParkingMode : DashCamCommand
    data object TakePhoto : DashCamCommand
    data object EnableAudio : DashCamCommand
    data object DisableAudio : DashCamCommand
    data object StartHotspot : DashCamCommand
    data object StopHotspot : DashCamCommand
    data object LockCurrentClip : DashCamCommand
    data object StopRecording : DashCamCommand
}
```

所有来源都转成 `DashCamCommand`：

- UI 按钮。
- 语音命令。
- 远程手机 API。
- 通知栏 action。
- 自动规则。

---

## 8. 语音唤醒与语音识别

### 8.1 推荐设计

不要持续运行完整语音识别。采用两段式：

1. **低功耗唤醒词检测**
   - 例：`小行车`、`记录仪`。
   - 本地 keyword spotting。
   - 只输出 wake event。

2. **短时间命令识别**
   - 唤醒后打开 3~5 秒命令识别窗口。
   - 识别固定命令。
   - 超时后回到低功耗监听。

### 8.2 支持命令

| 中文命令 | 内部命令 |
|---|---|
| 拍照 | `TakePhoto` |
| 打开录音 | `EnableAudio` |
| 关闭录音 | `DisableAudio` |
| 打开热点 | `StartHotspot` |
| 关闭热点 | `StopHotspot` |
| 停车模式 | `StartParkingMode` |
| 驾驶模式 | `StartDrivingMode` |
| 保存这段 | `LockCurrentClip` |
| 停止录像 | `StopRecording` |

### 8.3 技术选型

| 方案 | 优点 | 缺点 | 建议 |
|---|---|---|---|
| Picovoice Porcupine + Rhino | 低功耗、成熟、离线 | 授权/模型限制 | 推荐原型 |
| Vosk Android | 离线、支持中文 | 模型较大 | 可用于命令识别 |
| Whisper.cpp tiny | 准确率好 | 旧手机功耗高 | 不建议常驻 |
| TensorFlow Lite KWS | 可控、轻量 | 需训练数据 | 长期推荐 |
| Android SpeechRecognizer | 集成简单 | 可能依赖系统服务/网络，不适合 always-on | 仅作备用 |

---

## 9. Wi-Fi 热点与远程访问

### 9.1 记录仪端

记录仪端使用 `WifiManager.LocalOnlyHotspot` 创建热点。

流程：

```text
用户点击/语音 “打开热点”
  -> 请求 LocalOnlyHotspot
  -> 获取 SSID / password
  -> UI 和语音提示热点信息
  -> 启动 EmbeddedHttpServer
  -> 等待查看端连接
```

注意：LocalOnlyHotspot 的 SSID 和密码通常由系统分配，APP 对自定义 SSID/密码的控制有限。UI 中的“Wi-Fi 密码设置”需要分两种处理：

1. Android 官方 LocalOnlyHotspot：展示系统生成密码，不承诺完全自定义。
2. Root/系统签名/设备管理专用版本：可扩展自定义 Soft AP 配置。

### 9.2 内置服务

记录仪端启动本地 HTTP 服务，推荐 Ktor Server 或 NanoHTTPD。

服务监听：

```text
http://192.168.x.x:8080
```

API：

| Method | Path | 说明 |
|---|---|---|
| GET | `/api/status` | 当前状态 |
| POST | `/api/command` | 执行命令 |
| GET | `/api/media?type=video&date=...` | 查询视频 |
| GET | `/api/media/{id}/thumbnail` | 缩略图 |
| GET | `/api/media/{id}/stream` | 视频流播放 |
| GET | `/api/media/{id}/download` | 下载原文件 |
| DELETE | `/api/media/{id}` | 删除文件 |
| GET | `/api/settings` | 查询设置 |
| PUT | `/api/settings` | 修改设置 |
| WS | `/ws/events` | 状态/录制事件推送 |

### 9.3 连接与访问边界

远程查看使用 Android `startLocalOnlyHotspot()` 生成的临时 Wi-Fi 作为访问边界：

- 记录仪端启动 LocalOnlyHotspot 后读取系统生成的 SSID 和密码。
- 记录仪端启动本机 HTTP 服务，并把 SSID、密码、服务地址和端口编码到 QR code。
- 查看端扫描 QR code，请求连接该 Wi-Fi，然后访问 `http://192.168.x.x:8080`。
- 当前设计不再使用配对 token、配对码或 `Authorization: Bearer` header。
- 删除文件、切换模式、修改设置等写操作仅在已连接记录仪热点的局域网内开放。

---

## 10. 远程查看模式设计

### 10.1 连接流程

```text
打开 APP
  -> 选择“远程查看模式”
  -> 扫描记录仪端 QR code
  -> 使用 QR 中的 SSID/密码连接 LocalOnlyHotspot
  -> 使用 QR 中的 baseUrl 访问记录仪 HTTP 服务
  -> GET /api/status
  -> 成功后进入远程首页
```

服务发现方式：

1. 优先：QR code 中的 baseUrl。
2. 备用：mDNS / NSD。
3. 备用：默认网关 + 端口 8080。
4. 调试：手动输入 IP。

### 10.2 页面

| 页面 | 功能 |
|---|---|
| 远程首页 | 当前模式、录制状态、空间、电量、热点状态 |
| 视频列表 | 按日期、模式筛选视频 |
| 照片列表 | 查看照片 |
| 播放页面 | HTTP Range 流式播放 |
| 远程控制 | 拍照、录音开关、模式切换、热点控制 |
| 远程设置 | 分辨率、帧率、码率、分段时间、空间限制 |

---

## 11. UI 设计

### 11.1 启动页

首次启动选择：

```text
你想如何使用本机？
[作为行车记录仪]
[作为远程查看器]
```

保存到 settings，可随时切换。

### 11.2 记录仪首页

显示：

- 当前模式：驾驶/停车。
- 录制状态：录制中/暂停/错误。
- 当前片段时长。
- 存储剩余空间。
- 电池温度/电量。
- 录音状态。
- 热点状态。

按钮：

- 开始/停止。
- 驾驶模式。
- 停车模式。
- 拍照。
- 录音开关。
- 热点开关。
- 查看文件。
- 设置。

### 11.3 设置页

基础设置：

- 本机角色：记录仪/远程查看器。
- 驾驶模式分辨率。
- 驾驶模式帧率。
- 驾驶模式码率。
- 停车模式分辨率。
- 停车模式帧率。
- 停车模式码率。
- 分段时长。
- 最大存储空间。
- 最小保留空间。
- 是否启用录音。
- 是否启用语音唤醒。
- 唤醒词。
- 热点状态和密码显示。
- 记录仪热点 QR code 连接信息显示。

### 11.4 测试友好要求

所有关键 Compose 组件都必须设置 `testTag`：

```kotlin
Modifier.testTag("recorder_start_button")
Modifier.testTag("mode_driving_button")
Modifier.testTag("mode_parking_button")
Modifier.testTag("take_photo_button")
Modifier.testTag("hotspot_toggle_button")
Modifier.testTag("media_video_list")
Modifier.testTag("settings_wifi_password_field")
```

---

## 12. AI 可实现的开发任务拆分

### 12.1 任务切片原则

每个任务必须满足：

- 输入明确。
- 输出文件明确。
- 验证命令明确。
- 有单元测试或 UI 测试。
- 可由 AI 独立完成。

### 12.2 AI 开发任务列表

#### Task 01：创建工程骨架

输入：

```text
创建 Kotlin + Jetpack Compose Android 工程，包含 app/core-common/core-database/core-media/core-network/core-voice/feature-recorder/feature-remote/feature-settings/benchmark/test-robot modules。
```

验收：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

#### Task 02：实现 Room 数据库

输出：

- `MediaFileEntity`
- `RecordSessionEntity`
- `AppSettingEntity`
- DAO
- Migration
- Repository

验收：

```bash
./gradlew :core-database:testDebugUnitTest
```

#### Task 03：实现设置系统

输出：

- `SettingsRepository`
- 默认设置。
- 设置页 UI。
- 设置读写测试。

验收：

```bash
./gradlew :feature-settings:connectedDebugAndroidTest
```

#### Task 04：实现 CameraX 录制原型

输出：

- `CameraRecorderManager`
- `startDrivingRecording()`
- `startParkingRecording()`
- `stopRecording()`
- `takePhoto()`

验收：

- 真机能录制 30 秒。
- 文件写入数据库。
- 生成缩略图。

#### Task 05：实现 Foreground Service

输出：

- `RecorderForegroundService`
- 通知栏 action。
- 息屏录制验证脚本。

验收：

```bash
adb shell input keyevent 26
sleep 120
adb shell input keyevent 26
adb shell ls /sdcard/Android/data/<package>/files/DashCam/videos
```

#### Task 06：实现模式切换状态机

输出：

- `DashCamStateMachine`
- `DashCamCommand`
- 单元测试覆盖所有状态迁移。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*StateMachine*'
```

#### Task 07：实现热点服务

输出：

- `HotspotController`
- `LocalOnlyHotspot` 启停。
- 热点状态 UI。

验收：

- 真机能打开热点。
- 另一台手机可连接。
- APP 显示 SSID/密码。

#### Task 08：实现 HTTP API Server

输出：

- `/api/status`
- `/api/command`
- `/api/media`
- `/api/media/{id}/thumbnail`
- `/api/media/{id}/stream`

验收：

```bash
curl http://<phone-ip>:8080/api/status
curl http://<phone-ip>:8080/api/media
```

#### Task 09：实现远程查看模式

输出：

- 服务发现。
- 远程视频列表。
- 远程照片列表。
- 远程播放。
- 远程命令控制。

验收：

- 两台手机联调成功。
- 查看端能播放记录仪端视频。

#### Task 10：实现语音命令

输出：

- `VoiceWakeService`
- `CommandRecognizer`
- 语音命令到 `DashCamCommand` 的映射。

验收：

- 语音“拍照”成功触发拍照。
- 语音“停车模式”成功切换停车模式。
- 错误识别率有日志记录。

#### Task 11：实现循环删除

输出：

- `StoragePolicyManager`
- locked 文件保护。
- 单元测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*StoragePolicy*'
```

#### Task 12：实现自动化测试和 AI 验证闭环

输出：

- Compose UI tests。
- UI Automator tests。
- Macrobenchmark。
- adb 脚本。
- 截图采集。
- AI 分析报告模板。

验收：

```bash
./gradlew test connectedDebugAndroidTest
./gradlew :benchmark:connectedBenchmarkAndroidTest
python tools/ai_test_runner.py
```

---

## 13. Linux + Android Studio 自动化测试方案

### 13.1 测试层次

```text
Unit Test
  -> Repository / StateMachine / StoragePolicy / CommandParser

Instrumented Test
  -> Room / Camera facade fake / HTTP server / Settings DataStore

Compose UI Test
  -> 单页面交互、导航、设置读写

UI Automator
  -> 权限弹窗、系统设置、息屏、通知栏、跨 APP 行为

Macrobenchmark
  -> 启动耗时、列表滚动、远程视频列表加载

真机长稳测试
  -> 2h/8h 录制、息屏、过热、存储循环删除

双机联调测试
  -> 记录仪手机 + 查看手机
```

官方参考：

- Compose UI Testing：<https://developer.android.com/develop/ui/compose/testing>
- UI Automator：<https://developer.android.com/training/testing/other-components/ui-automator>
- Gradle Managed Devices：<https://developer.android.com/studio/test/managed-devices>
- Baseline Profiles / Macrobenchmark：<https://developer.android.com/topic/performance/baselineprofiles/overview>

### 13.2 Linux 环境准备

当前开发机已安装 Android Studio，可优先复用 Android Studio 自带 JBR 与 SDK，避免再单独安装系统 JDK/adb。

已检查到的环境：

| 项目 | 当前状态 | 说明 |
|---|---|---|
| Android Studio | 已安装 | `/home/meng/Software/android-studio` |
| JDK | 已安装 | Android Studio JBR：`/home/meng/Software/android-studio/jbr/bin/java`，版本 `OpenJDK 21.0.9` |
| Android SDK | 已安装 | `ANDROID_SDK_ROOT=/home/meng/Android/Sdk` |
| adb | 已安装 | `/home/meng/Android/Sdk/platform-tools/adb`，版本 `37.0.0-14910828` |
| platform-tools | 已安装 | 包含 `adb`、`fastboot` |
| Android platforms | 已安装 | `android-36`、`android-36.1` |
| build-tools | 已安装 | `35.0.0`、`36.0.0`、`36.1.0` |
| emulator | 已安装 | `/home/meng/Android/Sdk/emulator/emulator` |
| system-images | 已安装 | `android-35/google_apis_playstore/x86_64`、`android-36.1/google_apis_playstore/x86_64` |
| KVM | 已启用设备节点 | `/dev/kvm` 存在，若模拟器无法启动再检查当前用户是否在 `kvm` 组 |
| git/curl/unzip | 已安装 | `/usr/bin/git`、`/usr/bin/curl`、`/usr/bin/unzip` |
| Python | 已安装 | `Python 3.12.3`，`python3 -m venv` 可用 |
| ffmpeg | 已安装 | `ffmpeg 6.1.1` |
| ImageMagick | 已安装 | `convert` 可用，版本 `6.9.12-98`；`magick` 命令未发现，Ubuntu 上使用 `convert` 即可 |
| Android cmdline-tools | 已安装 | `/home/meng/Android/Sdk/cmdline-tools/latest/bin`，`sdkmanager --version` 为 `20.0` |
| sdkmanager/avdmanager | 已安装 | `sdkmanager`、`avdmanager` 均在 `cmdline-tools/latest/bin` 下且可执行 |
| Gradle wrapper | 当前仓库未创建 | 当前目录只有 `docs/`，创建 Android 工程后再检查 `./gradlew` |

建议写入 shell 配置，例如 `~/.bashrc` 或 `~/.zshrc`：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export JAVA_HOME="$HOME/Software/android-studio/jbr"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
```

当前机器不建议再用 apt 安装 `adb` 或 `openjdk-17-jdk` 作为首选路径，避免和 Android Studio/SDK 自带工具混用。需要确认环境时使用：

```bash
java -version
adb version
sdkmanager --version
avdmanager --help
python3 --version
ffmpeg -version
convert -version
```

Android SDK 命令行工具路径检查：

```bash
ls "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/sdkmanager"
ls "$ANDROID_SDK_ROOT/cmdline-tools/latest/bin/avdmanager"
```

创建 Android 工程后再检查项目工具：

```bash
./gradlew --version
./gradlew assembleDebug
```

### 13.3 Gradle Managed Devices

用于普通 UI 测试，不用于真实相机/热点测试。

`build.gradle.kts` 示例：

```kotlin
android {
    testOptions {
        managedDevices {
            localDevices {
                create("pixel6Api35") {
                    device = "Pixel 6"
                    apiLevel = 35
                    systemImageSource = "google"
                }
            }
        }
    }
}
```

运行：

```bash
./gradlew pixel6Api35DebugAndroidTest
```

### 13.4 真机测试矩阵

| 测试类型 | 是否需要真机 | 原因 |
|---|---:|---|
| Compose UI | 否 | 模拟器足够 |
| 设置页 | 否 | 模拟器足够 |
| Room/Repository | 否 | JVM/模拟器即可 |
| CameraX 录制 | 是 | 模拟器相机不可靠 |
| 息屏录制 | 是 | 依赖厂商系统策略 |
| Wi-Fi 热点 | 是 | 模拟器不适合 |
| 双机远程查看 | 是 | 需要真实 Wi-Fi 网络 |
| 语音唤醒 | 是 | 需要麦克风和环境噪声验证 |
| 功耗/发热 | 是 | 模拟器无意义 |

### 13.5 adb 自动化脚本

`tools/device_smoke_test.sh`：

```bash
#!/usr/bin/env bash
set -euo pipefail

PKG="com.example.dashcam"
MAIN="com.example.dashcam/.MainActivity"

adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am force-stop "$PKG"
adb shell am start -n "$MAIN"
sleep 3

# 授权，实际权限名按 target SDK 调整
adb shell pm grant "$PKG" android.permission.CAMERA || true
adb shell pm grant "$PKG" android.permission.RECORD_AUDIO || true
adb shell pm grant "$PKG" android.permission.POST_NOTIFICATIONS || true

# 启动驾驶模式
adb shell am broadcast -a com.example.dashcam.COMMAND --es command START_DRIVING
sleep 30

# 息屏继续录制
adb shell input keyevent 26
sleep 60
adb shell input keyevent 26
sleep 3

# 拍照
adb shell am broadcast -a com.example.dashcam.COMMAND --es command TAKE_PHOTO
sleep 3

# 停止
adb shell am broadcast -a com.example.dashcam.COMMAND --es command STOP_RECORDING
sleep 3

# 拉取日志和文件列表
adb shell run-as "$PKG" ls files || true
adb logcat -d > build/device_smoke_logcat.txt
adb exec-out screencap -p > build/final_screen.png
```

---

## 14. AI 全自动化开发与验证闭环

### 14.1 总体流程

```text
需求文档
  -> AI 生成任务计划
  -> AI 修改代码
  -> Gradle 编译
  -> 单元测试
  -> UI 测试
  -> 真机 adb 测试
  -> 截图/日志/视频文件分析
  -> AI 读取失败日志
  -> AI 修复代码
  -> 重复直到通过
```

### 14.2 AI Runner 设计

`tools/ai_test_runner.py`：

职责：

1. 执行构建。
2. 执行测试。
3. 收集错误日志。
4. 收集截图。
5. 用 AI 分析失败原因。
6. 生成修复建议或补丁。
7. 重新执行验证。

伪代码：

```python
import subprocess
from pathlib import Path

COMMANDS = [
    "./gradlew ktlintCheck",
    "./gradlew testDebugUnitTest",
    "./gradlew assembleDebug",
    "./gradlew connectedDebugAndroidTest",
]

def run(cmd: str) -> tuple[int, str]:
    p = subprocess.run(cmd, shell=True, text=True, capture_output=True)
    return p.returncode, p.stdout + "\n" + p.stderr

def main():
    report = []
    for cmd in COMMANDS:
        code, output = run(cmd)
        report.append((cmd, code, output[-12000:]))
        if code != 0:
            Path("build/ai_failure_report.txt").write_text(str(report))
            print("FAILED:", cmd)
            print(output[-4000:])
            return 1
    print("ALL PASSED")
    return 0

if __name__ == "__main__":
    raise SystemExit(main())
```

### 14.3 AI 可读测试报告格式

每次失败输出：

```markdown
# AI Test Failure Report

## Command

`./gradlew connectedDebugAndroidTest`

## Failure Summary

- Test: RecorderScreenTest.startDrivingMode_showsRecordingState
- Error: Node with tag `recording_status_text` not found

## Relevant Logs

```text
...
```

## Screenshot

`build/screenshots/failure_001.png`

## Expected Behavior

点击驾驶模式按钮后，状态文本显示“录制中 · 驾驶模式”。

## Actual Behavior

页面停留在权限提示状态。

## Suggested Fix Area

- Permission gate
- RecorderScreen state collection
- testTag naming
```

### 14.4 截图视觉检查

UI 测试失败时自动截图：

```kotlin
fun takeScreenshot(name: String) {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    val file = File("/sdcard/Download/$name.png")
    device.takeScreenshot(file)
}
```

拉取：

```bash
adb pull /sdcard/Download/failure.png build/screenshots/failure.png
```

AI 检查点：

- 页面是否进入预期 screen。
- 按钮是否可见。
- 是否卡在权限弹窗。
- 是否有崩溃对话框。
- 文案是否正确。
- 远程视频列表是否加载。

---

## 15. 自动化测试用例设计

### 15.1 单元测试

#### StateMachineTest

| 用例 | 期望 |
|---|---|
| Idle + StartDriving | RecordingDriving |
| Idle + StartParking | RecordingParking |
| RecordingDriving + StartParking | RecordingParking |
| RecordingParking + StartDriving | RecordingDriving |
| RecordingParking + LockCurrentClip | locked 当前片段 |
| Error + StopRecording | Idle |

#### StoragePolicyTest

| 用例 | 期望 |
|---|---|
| 空间足够 | 不删除 |
| 空间不足 | 删除最旧 unlocked 文件 |
| locked 文件最旧 | 不删除 locked，删除下一个 |
| 删除后仍不足 | 继续删除 |

#### VoiceCommandParserTest

| 输入 | 输出 |
|---|---|
| 拍照 | TakePhoto |
| 打开录音 | EnableAudio |
| 关闭录音 | DisableAudio |
| 打开热点 | StartHotspot |
| 关闭热点 | StopHotspot |
| 停车模式 | StartParkingMode |
| 驾驶模式 | StartDrivingMode |

### 15.2 Compose UI Test

示例：

```kotlin
@Test
fun recorderScreen_switchToParkingMode() {
    composeRule.onNodeWithTag("mode_parking_button").performClick()
    composeRule.onNodeWithTag("current_mode_text")
        .assertTextContains("停车模式")
}
```

必须覆盖：

- 首次选择记录仪模式。
- 首次选择远程查看模式。
- 记录仪首页按钮。
- 设置页修改参数。
- 视频列表筛选。
- 照片列表查看。
- 远程控制按钮。

### 15.3 UI Automator 测试

覆盖系统级行为：

- 权限弹窗允许。
- 按 Home 后服务继续。
- 息屏后服务继续。
- 通知栏存在。
- 通知栏停止按钮有效。
- 打开系统 Wi-Fi 页面提示用户连接热点。

示例：

```kotlin
@Test
fun recordingContinuesAfterScreenOff() {
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
    startDrivingMode()
    device.pressKeyCode(KeyEvent.KEYCODE_POWER)
    Thread.sleep(60_000)
    device.pressKeyCode(KeyEvent.KEYCODE_POWER)
    unlockDevice()
    assertLatestVideoDurationAtLeast(60_000)
}
```

### 15.4 HTTP API 测试

使用 fake media repository：

```kotlin
@Test
fun statusApi_returnsRecordingState() = testApplication {
    val response = client.get("/api/status")
    assertEquals(HttpStatusCode.OK, response.status)
    assertTrue(response.bodyAsText().contains("mode"))
}
```

真机测试：

```bash
curl http://$DASHCAM_IP:8080/api/status
curl "http://$DASHCAM_IP:8080/api/media?type=video"
```

### 15.5 双机联调测试

需要两台 Android 手机：

| 设备 | 角色 |
|---|---|
| Device A | 记录仪模式 |
| Device B | 远程查看模式 |

流程：

```text
Device A 安装 APP
Device B 安装 APP
Device A 选择记录仪模式
Device A 打开热点
Device B 连接热点
Device B 打开远程查看模式
Device B 发现 Device A
Device B 查看状态
Device B 触发拍照
Device B 查看照片列表
Device B 播放视频
```

自动化脚本思路：

```bash
export RECORDER_SERIAL=xxxx
export VIEWER_SERIAL=yyyy

adb -s $RECORDER_SERIAL install -r app-debug.apk
adb -s $VIEWER_SERIAL install -r app-debug.apk

adb -s $RECORDER_SERIAL shell am start -n com.example.dashcam/.MainActivity
adb -s $VIEWER_SERIAL shell am start -n com.example.dashcam/.MainActivity

# 记录仪端打开热点和 HTTP server
adb -s $RECORDER_SERIAL shell am broadcast -a com.example.dashcam.COMMAND --es command START_HOTSPOT

# 查看端连接 Wi-Fi 这一步通常需要人工或厂商特定能力。
# 若测试机支持 adb shell cmd wifi connect-network，可脚本化。
```

---

## 16. 性能、功耗与稳定性测试

### 16.1 关键指标

| 指标 | 驾驶模式目标 | 停车模式目标 |
|---|---:|---:|
| 连续录制 | ≥ 8 小时 | ≥ 12 小时 |
| 文件分段误差 | < 2 秒 | < 5 秒 |
| 丢帧率 | < 1% | < 1% |
| APP 崩溃 | 0 | 0 |
| UI 卡顿 | 主要页面无明显 jank | 主要页面无明显 jank |
| 温度保护 | 超阈值自动降级 | 超阈值自动降级 |
| 空间回收 | 不误删 locked 文件 | 不误删 locked 文件 |

### 16.2 长稳测试脚本

```bash
#!/usr/bin/env bash
set -euo pipefail
PKG="com.example.dashcam"
DURATION_MINUTES=${1:-480}

adb shell am force-stop "$PKG"
adb shell am start -n "$PKG/.MainActivity"
sleep 5
adb shell am broadcast -a com.example.dashcam.COMMAND --es command START_DRIVING
adb shell input keyevent 26

for i in $(seq 1 "$DURATION_MINUTES"); do
  echo "minute=$i"
  adb shell dumpsys battery | grep -E 'level|temperature' || true
  adb shell dumpsys meminfo "$PKG" | head -40 || true
  sleep 60
done

adb shell input keyevent 26
adb shell am broadcast -a com.example.dashcam.COMMAND --es command STOP_RECORDING
adb logcat -d > build/long_run_logcat.txt
```

### 16.3 视频文件验证

使用 `ffprobe`：

```bash
ffprobe -v error -show_entries format=duration,size -show_streams video.mp4
```

自动检查：

- duration 是否接近分段时长。
- 是否有 video stream。
- 开启录音时是否有 audio stream。
- fps 是否符合模式。
- 文件是否能播放。

---

## 17. 权限与用户引导

首次启动记录仪模式时需要引导：

1. Camera 权限。
2. Microphone 权限。
3. Notification 权限。
4. 文件访问策略说明。
5. 电池优化白名单提示。
6. 后台运行说明。
7. 过热风险提示。
8. 驾驶安全提示。

重要：APP 不应在用户不知情的情况下偷偷录音录像。前台服务通知必须清楚显示当前正在录制。

---

## 18. AI 代码生成规范

### 18.1 Prompt 模板

```text
你是 Android Kotlin 高级工程师。请在当前仓库中完成以下任务：

任务：<任务名称>

背景：这是一个旧手机行车记录仪 APP，使用 Kotlin、Jetpack Compose、CameraX、Room、Ktor/NanoHTTPD。

要求：
1. 只修改与任务相关的文件。
2. 保持可测试性，业务逻辑不要写死在 UI 中。
3. 所有 Compose 可交互组件必须加 testTag。
4. 新增功能必须添加单元测试或 UI 测试。
5. 不能引入隐藏 API，热点使用 LocalOnlyHotspot。
6. 相机后台录制必须通过 Foreground Service。

输出：
- 修改的文件列表。
- 核心实现说明。
- 测试命令。

验收命令：
<gradle command>
```

### 18.2 AI 修复 Prompt 模板

```text
以下是 Android 项目的测试失败报告。请分析失败原因并给出最小修复补丁。

限制：
1. 不要删除测试。
2. 不要跳过测试。
3. 不要用 Thread.sleep 修复同步问题，除非是 UI Automator 系统级测试。
4. Compose 测试优先使用 semantics/testTag。
5. 修复后说明需要运行的验证命令。

失败报告：
<failure report>
```

---

## 19. MVP 版本计划

### MVP-1：本机行车记录仪

功能：

- 记录仪模式。
- 驾驶模式录制。
- 停车模式录制。
- 拍照。
- 录音开关。
- 视频/照片列表。
- 设置页。
- Foreground Service。
- 循环删除。

验收：

- 真机息屏录制 2 小时。
- 分段文件正常。
- 文件可播放。
- 空间不足自动删除旧文件。

### MVP-2：远程查看

功能：

- LocalOnlyHotspot。
- HTTP API。
- 远程查看模式。
- 视频列表。
- 照片列表。
- 视频流播放。
- 远程命令。

验收：

- 第二台手机连接热点。
- 能查看/播放记录仪视频。
- 能远程拍照和切换模式。

### MVP-3：语音控制

功能：

- 唤醒词。
- 固定命令识别。
- 命令执行反馈。
- 语音识别日志。

验收：

- 安静环境下命令成功率 ≥ 90%。
- 行车噪声环境下命令成功率 ≥ 75%。
- 待机功耗可接受。

### MVP-4：AI 自动开发验证闭环

功能：

- 一键构建测试。
- 截图采集。
- 日志采集。
- AI 失败报告。
- 真机长稳测试。

验收：

- 每个 PR 自动运行单测和 UI 测试。
- 真机每日夜间长稳测试。
- 自动生成测试报告。

---

## 20. 风险清单与规避方案

| 风险 | 影响 | 规避方案 |
|---|---|---|
| 厂商系统杀后台 | 息屏录制中断 | Foreground Service + 电池白名单引导 + 真机兼容测试 |
| 旧手机过热 | 自动停录/损坏设备 | 温度监控 + 降帧降码率 + 高温停止 |
| LocalOnlyHotspot 不能自定义密码 | 用户体验受限 | UI 展示系统生成密码；高级版本再考虑系统权限 |
| 语音常驻耗电 | 停车模式续航差 | 两段式唤醒，低功耗 KWS，小窗口命令识别 |
| 视频文件损坏 | 证据丢失 | 短分段录制；写入完成后再入库；异常恢复扫描 |
| 空间占满 | 停止录制 | 循环删除 + 最小保留空间 |
| 双机连接复杂 | 用户不会用 | 引导页 + 二维码/配对码 + 自动发现 |
| Android API 差异 | 部分设备不可用 | API capability 检测 + 降级路径 |
| 自动化无法测真实硬件 | 漏 bug | 模拟器测 UI，真机测相机/热点/息屏 |

---

## 21. 推荐依赖

```kotlin
// CameraX
androidx.camera:camera-core
androidx.camera:camera-camera2
androidx.camera:camera-lifecycle
androidx.camera:camera-video
androidx.camera:camera-view

// Compose
androidx.compose.ui:ui
androidx.compose.material3:material3
androidx.navigation:navigation-compose

// Room
androidx.room:room-runtime
androidx.room:room-ktx
androidx.room:room-compiler

// Network
io.ktor:ktor-server-core
io.ktor:ktor-server-cio
io.ktor:ktor-client-core
io.ktor:ktor-client-okhttp

// DI
com.google.dagger:hilt-android
androidx.hilt:hilt-navigation-compose

// Test
androidx.compose.ui:ui-test-junit4
androidx.test.uiautomator:uiautomator
androidx.benchmark:benchmark-macro-junit4
junit:junit
io.mockk:mockk
```

---

## 22. 最小验收标准

项目达到可用状态至少需要通过以下验收：

1. 旧手机可连续录制驾驶模式 2 小时。
2. 息屏后录制不中断。
3. 停车模式可低帧率录制。
4. 可拍照。
5. 可打开/关闭录音。
6. 可打开热点。
7. 另一台手机连接热点后能查看视频和照片。
8. 远程手机可播放视频。
9. 远程手机可触发拍照和切换模式。
10. 语音“拍照/打开录音/关闭录音/打开热点/关闭热点/停车模式/驾驶模式”可执行。
11. 设置页可修改核心参数。
12. 循环删除不删除 locked 文件。
13. 自动化测试能覆盖主要 UI 和核心逻辑。
14. 真机长稳测试有报告。
15. AI 能根据失败日志和截图定位并修复常见问题。

---

## 23. 建议下一步

第一阶段不要直接做全部功能。建议按下面顺序实现：

```text
1. 工程骨架 + Compose 页面 + testTag
2. Room 数据库 + 设置系统
3. CameraX 本机录制 + 分段 + 缩略图
4. Foreground Service + 息屏录制
5. 驾驶/停车模式状态机
6. 文件列表 + 循环删除
7. LocalOnlyHotspot + HTTP API
8. 远程查看模式
9. 语音命令
10. AI 自动化测试闭环
```

这样可以保证每一步都有可运行、可测试、可交付的结果。
