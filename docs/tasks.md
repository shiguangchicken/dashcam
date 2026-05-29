# Android 行车记录仪 APP 实现任务清单

> 来源：`docs/android_dashcam_ai_implementation_plan.md`
>
> 执行原则：按本文顺序逐项完成。每个任务完成后先运行对应验收命令或真机验收，再进入下一项。所有交互式 Compose 组件必须提供稳定 `testTag`。硬件相关能力使用公开 Android API，不使用隐藏 API 或反射强开系统热点。

## 阶段 0：工程与环境基线

### Task 01：创建 Android 多模块工程骨架

- [x] 创建 Kotlin + Jetpack Compose Android 工程。
- [x] 创建模块：`app`、`core-common`、`core-database`、`core-media`、`core-network`、`core-voice`、`feature-recorder`、`feature-remote`、`feature-settings`、`benchmark`、`test-robot`。
- [x] 配置 Gradle wrapper、Android Gradle Plugin、Kotlin、Compose、Room、CameraX、Ktor、Hilt、测试依赖。
- [x] 配置统一 namespace、minSdk、targetSdk、compileSdk。
- [x] 配置 `ktlint` 或等价格式检查。
- [x] 在 `app` 中创建 `DashCamApplication`、`MainActivity`、基础 Compose Theme 和导航入口。

验收：

```bash
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

### Task 02：配置应用权限、Manifest 与基础启动流程

- [x] 声明相机、麦克风、通知、前台服务、WakeLock、网络、Wi-Fi 状态等权限。
- [x] 为 Android 14+ 声明明确的 foreground service type：`camera|microphone|connectedDevice|dataSync`。
- [x] 实现首次启动角色选择页：记录仪模式、远程查看模式。
- [x] 将本机角色保存到设置存储中。
- [x] 增加权限引导页，覆盖 Camera、Microphone、Post Notifications、电池优化提示、后台运行说明、驾驶安全提示。
- [x] 所有按钮添加 `testTag`：`role_recorder_button`、`role_remote_button`、`permission_continue_button`。

验收：

```bash
./gradlew :app:connectedDebugAndroidTest
```

## 阶段 1：核心数据与设置

### Task 03：实现公共领域模型与命令接口

- [x] 在 `core-common` 定义 `DashCamCommand`、录制模式、录制状态、媒体类型、错误类型。
- [x] 定义统一 `Result` 或错误封装。
- [x] 定义日志工具和时间/文件大小格式化工具。
- [x] 为命令和状态添加单元测试。

验收：

```bash
./gradlew :core-common:testDebugUnitTest
```

### Task 04：实现 Room 数据库

- [x] 在 `core-database` 创建 `MediaFileEntity`、`RecordSessionEntity`、`AppSettingEntity`。
- [x] 创建 DAO：媒体文件查询/插入/删除标记、会话开始/结束、设置读写。
- [x] 创建数据库、Migration、Repository。
- [x] 媒体文件字段覆盖：类型、模式、路径、缩略图、创建时间、时长、大小、分辨率、fps、码率、录音、locked、checksum、deleted。
- [x] 为 DAO 和 Repository 添加单元测试或 instrumented test。

验收：

```bash
./gradlew :core-database:testDebugUnitTest
```

### Task 05：实现设置系统与设置页

- [x] 在 `feature-settings` 实现设置页 UI。
- [x] 支持设置项：本机角色、驾驶/停车分辨率、帧率、码率、分段时长、最大存储空间、最小保留空间、录音开关、语音唤醒开关、唤醒词、热点信息、配对 token。
- [x] 在 `core-database` 或独立 repository 中提供 `SettingsRepository`。
- [x] 提供默认设置和非法值回退。
- [x] 所有设置项添加稳定 `testTag`。
- [x] 添加设置读写测试和 Compose UI 测试。

验收：

```bash
./gradlew :feature-settings:connectedDebugAndroidTest
```

## 阶段 2：记录仪本机 MVP

### Task 06：实现记录仪首页 UI 与状态展示

- [x] 在 `feature-recorder` 创建记录仪首页。
- [x] 展示当前模式、录制状态、当前片段时长、剩余空间、电量、温度、录音状态、热点状态。
- [x] 提供开始/停止、驾驶模式、停车模式、拍照、录音开关、热点开关、查看文件、设置入口。
- [x] 接入 fake ViewModel，先用假数据驱动 UI。
- [x] 添加 `testTag`：`recorder_start_button`、`mode_driving_button`、`mode_parking_button`、`take_photo_button`、`audio_toggle_button`、`hotspot_toggle_button`、`current_mode_text`、`recording_status_text`。
- [x] 添加 Compose UI 测试覆盖按钮可见性和状态切换。

验收：

```bash
./gradlew :feature-recorder:connectedDebugAndroidTest
```

### Task 07：实现模式切换状态机

- [x] 在 `core-media` 实现 `DashCamStateMachine`。
- [x] 支持状态：`Idle`、`RecordingDriving`、`RecordingParking`、`Paused`、`EventBoostRecording`、`Error`。
- [x] 支持命令：开始驾驶、开始停车、拍照、开关录音、开关热点、锁定当前片段、停止录制。
- [x] 将非法迁移返回明确错误。
- [x] 单元测试覆盖设计文档中的所有状态迁移。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*StateMachine*'
```

### Task 08：实现文件目录、媒体仓库与缩略图基础能力

- [x] 在 `core-media` 创建 app-specific external storage 目录结构：`videos/driving`、`videos/parking`、`videos/locked`、`photos`、`thumbnails`、`logs`。
- [x] 实现媒体文件命名规则和日期目录。
- [x] 实现媒体写入完成后再入库。
- [x] 实现视频/照片列表查询。
- [x] 实现缩略图生成接口，先支持图片缩略图，视频缩略图可用 Android API 或后续 CameraX 输出补齐。
- [x] 添加文件命名、目录创建、入库逻辑测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest
```

验证记录：

- [x] 2026-05-28 使用 `JAVA_HOME=/home/meng/Software/android-studio-2025.2.3.9/android-studio/jbr` 运行 `./gradlew :core-media:testDebugUnitTest` 通过。

### Task 09：实现 CameraX 录制与拍照原型

- [x] 在 `core-media` 实现 `CameraRecorderManager`。
- [x] 支持 `startDrivingRecording()`、`startParkingRecording()`、`stopRecording()`、`takePhoto()`。
- [x] 驾驶模式使用高帧率/高码率配置，停车模式使用低帧率/低码率配置。
- [x] 支持录音开关。
- [x] 录制完成后写入 Room，并生成缩略图。
- [x] 处理相机权限缺失、编码器不支持、存储不可写等错误。
- [x] 提供 fake camera facade 方便单元测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest
./gradlew assembleDebug
```

真机验收：

```bash
adb install -r app/build/outputs/apk/debug/app-debug.apk
adb shell am start -n com.firmmy.dashcam/.MainActivity
```

手动验证：驾驶模式录制 30 秒，停车模式录制 30 秒，拍照成功，文件入库并可在文件列表看到。

验证记录：

- [x] 2026-05-28 使用 `JAVA_HOME=/home/meng/Software/android-studio-2025.2.3.9/android-studio/jbr` 运行 `./gradlew :core-media:testDebugUnitTest` 通过。
- [x] 2026-05-28 使用 `JAVA_HOME=/home/meng/Software/android-studio-2025.2.3.9/android-studio/jbr` 运行 `./gradlew assembleDebug` 通过。
- [x] 2026-05-28 重新授权 USB 安装后，Mi 10 上 `adb install -r app/build/outputs/apk/debug/app-debug.apk` 通过，`adb shell am start -n com.firmmy.dashcam/.MainActivity` 通过，Activity 进入 resumed 状态。
- [x] 2026-05-28 将 `MainActivity` 录制页接入 `CameraRecorderManager`/`CameraXCameraFacade`，首次进入权限页后请求相机、麦克风和 Android 13+ 通知权限；Mi 10 上确认 `CAMERA`、`RECORD_AUDIO`、`POST_NOTIFICATIONS` 均已授权。
- [x] 2026-05-28 Mi 10 手动验收通过：驾驶模式录制约 40 秒生成 `DashCam/videos/driving/2026-05-28/20260528_221254_001.mp4` 和视频缩略图；停车模式录制约 32 秒生成 `DashCam/videos/parking/2026-05-28/20260528_221352_001.mp4` 和视频缩略图；拍照生成 `DashCam/photos/2026-05-28/20260528_221427.jpg` 和图片缩略图；拉取 Room 数据库确认 3 条媒体记录均含文件路径、缩略图路径、大小，视频记录含时长。
- [x] 2026-05-28 重新安装测试 APK 后，设置 `adb shell appops set com.firmmy.dashcam 10021 allow` 和 `adb shell appops set com.firmmy.dashcam RUN_ANY_IN_BACKGROUND allow`，直接运行 `adb shell am instrument -w -r com.firmmy.dashcam.test/androidx.test.runner.AndroidJUnitRunner` 通过，2 个 app instrumentation 测试均通过。
- [x] 2026-05-28 在 Mi 10 为 app 开启“允许自启动”后，使用 `JAVA_HOME=/home/meng/Software/android-studio-2025.2.3.9/android-studio/jbr` 重新运行 `./gradlew :app:connectedDebugAndroidTest` 通过，Gradle/UTP 显示 `Finished 2 tests on Mi 10 - 13`。

### Task 10：实现分段录制

- [x] 支持 1/3/5/10 分钟分段配置。
- [x] 分段切换时保证上一段完成写入后再入库。
- [x] 异常中断后启动时扫描未入库文件并恢复索引。
- [x] 记录 `RecordSessionEntity`。
- [x] 添加分段调度和异常恢复测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*Segment*'
```

真机验收：设置 1 分钟分段，连续录制 3 分钟，生成 3 个左右可播放视频文件。

验证记录：

- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew :core-media:testDebugUnitTest --tests '*Segment*'` 通过。
- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew testDebugUnitTest` 通过。
- [x] 2026-05-29 Mi 10 真机使用 1 分钟分段启动驾驶模式录制，生成 `20260529_205501_001.mp4`、`20260529_205601_002.mp4`、`20260529_205702_003.mp4` 等分段文件；拉取 `20260529_205501_001.mp4` 后用 `ffprobe` 验证 `duration=59.341267`、`size=88971070`，文件可读。

### Task 11：实现 Foreground Service 与息屏录制

- [x] 在 `app` 或 `core-media` 实现 `RecorderForegroundService`。
- [x] Service 负责启动/停止 CameraX 录制、响应命令、管理通知。
- [x] 通知展示驾驶/停车模式、录音状态、剩余空间，并提供停止、拍照、切换模式 action。
- [x] 仅在启动/切换模式/保存文件等关键阶段短时间使用 WakeLock。
- [x] 支持广播或 service command API，供 adb、通知栏、远程 API 调用。
- [x] 添加 UI Automator 或 device smoke 脚本验证息屏后继续录制。

验收：

```bash
./gradlew assembleDebug
```

真机验收：

```bash
adb shell input keyevent 26
sleep 120
adb shell input keyevent 26
adb shell ls /sdcard/Android/data/com.firmmy.dashcam/files/DashCam/videos
```

验证记录：

- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew assembleDebug` 通过。
- [x] 2026-05-29 Mi 10 真机安装 `app-debug.apk` 后授权 Camera、Record Audio、Post Notifications，通过 adb service command 启动 `RecorderForegroundService`，`dumpsys activity services` 确认 `isForeground=true`、通知 ID `1001`。
- [x] 2026-05-29 Mi 10 真机执行息屏 120 秒验证，唤醒后 `DashCam/videos/driving/2026-05-29` 下存在 `20260529_205501_001.mp4`、`20260529_205601_002.mp4`、`20260529_205702_003.mp4`、`20260529_205802_004.mp4`，说明息屏期间继续录制并分段。
- [x] 2026-05-29 增加 `test-robot/scripts/foreground_recording_smoke.sh`，用于安装、授权、启动 1 分钟分段、息屏等待、列出文件并停止服务。

### Task 12：实现媒体列表、本机播放与文件操作

- [x] 在 `feature-recorder` 实现视频列表、照片列表。
- [x] 支持按日期、模式、类型筛选。
- [x] 支持本机播放视频、查看照片、删除文件、锁定/解锁文件。
- [x] 删除文件需要同步更新数据库 `deleted` 状态。
- [x] 锁定文件移动或标记到 protected/locked 路径。
- [x] 添加 `media_video_list`、`media_photo_list`、`media_filter_date` 等 `testTag`。
- [x] 添加 Compose UI 测试。

验收：

```bash
./gradlew :feature-recorder:connectedDebugAndroidTest
```

验证记录：

- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew testDebugUnitTest` 通过，覆盖媒体仓库删除、锁定移动和循环删除单元测试编译运行。
- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew assembleDebug` 通过。
- [ ] 2026-05-29 `./gradlew :feature-recorder:connectedDebugAndroidTest` 未能执行测试：Mi 10 安装测试 APK 时返回 `INSTALL_FAILED_USER_RESTRICTED: Install canceled by user`，Gradle 显示 `Starting 0 tests` / `Finished 0 tests`。这是设备 USB 安装限制，非代码编译错误。
- [ ] 2026-05-29 唤醒并解锁 Mi 10 后重跑 `./gradlew :feature-recorder:connectedDebugAndroidTest`，测试 APK 可安装且 Gradle 显示 `Starting 4 tests on Mi 10 - 13`，但 240 秒超时前一直停留在 `Tests 0/4 completed`，未产出通过/失败结果；随后已 force-stop 测试进程。再次按单测方法重跑时又被 `INSTALL_FAILED_USER_RESTRICTED` 阻止安装。

### Task 13：实现循环删除与存储策略

- [x] 在 `core-media` 实现 `StoragePolicyManager`。
- [x] 支持最大占用空间、最小保留空间、是否允许删除停车模式视频。
- [x] 删除候选只包含 `locked = false` 且 `deleted = false` 的旧文件。
- [x] 删除直到恢复空间或没有可删文件。
- [x] 删除动作写日志并更新数据库。
- [x] 添加空间足够、空间不足、locked 保护、连续删除等单元测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*StoragePolicy*'
```

验证记录：

- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew :core-media:testDebugUnitTest --tests '*StoragePolicy*'` 通过。
- [x] 2026-05-29 使用 `ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 运行 `./gradlew ktlintCheck` 通过。

## 阶段 3：热点、局域网 API 与远程查看

### Task 14：实现热点控制

- [ ] 在 `core-network` 实现 `HotspotController`。
- [ ] 使用 `WifiManager.LocalOnlyHotspot` 启停本地热点。
- [ ] 暴露热点状态、SSID、password。
- [ ] UI 中明确展示系统生成的 SSID/密码，不承诺自定义密码。
- [ ] 处理不支持 LocalOnlyHotspot、权限缺失、系统限制等错误。
- [ ] 记录热点启停日志。

验收：

```bash
./gradlew :core-network:testDebugUnitTest
```

真机验收：记录仪端能打开热点，另一台手机能连接，APP 显示 SSID/密码。

### Task 15：实现配对 token 与基础认证

- [ ] 首次启用远程访问时生成 token 和配对码。
- [ ] 在设置页展示、刷新、复制配对信息。
- [ ] HTTP 请求支持 `Authorization: Bearer <token>`。
- [ ] 删除文件、切换模式、修改设置等写操作必须鉴权。
- [ ] 添加认证成功、失败、缺失 token 测试。

验收：

```bash
./gradlew :core-network:testDebugUnitTest --tests '*Auth*'
```

### Task 16：实现内置 HTTP/WebSocket 服务

- [ ] 使用 Ktor Server 或 NanoHTTPD 实现 `EmbeddedHttpServer`。
- [ ] 实现 `GET /api/status`。
- [ ] 实现 `POST /api/command`。
- [ ] 实现 `GET /api/media?type=video&date=...`。
- [ ] 实现 `GET /api/media/{id}/thumbnail`。
- [ ] 实现 `GET /api/media/{id}/stream`，支持 HTTP Range。
- [ ] 实现 `GET /api/media/{id}/download`。
- [ ] 实现 `DELETE /api/media/{id}`。
- [ ] 实现 `GET /api/settings`、`PUT /api/settings`。
- [ ] 实现 `WS /ws/events` 推送状态和录制事件。
- [ ] 使用 fake repository 添加 API 测试。

验收：

```bash
./gradlew :core-network:testDebugUnitTest
```

真机验收：

```bash
curl -H "Authorization: Bearer $TOKEN" http://$DASHCAM_IP:8080/api/status
curl -H "Authorization: Bearer $TOKEN" "http://$DASHCAM_IP:8080/api/media?type=video"
```

### Task 17：实现远程 API Client 与服务发现

- [ ] 在 `core-network` 实现 HTTP client。
- [ ] 支持 status、command、media、thumbnail、stream、download、delete、settings API。
- [ ] 实现服务发现：优先 NSD/mDNS，备用默认网关 + 8080，最后支持手动 IP。
- [ ] 处理连接超时、认证失败、服务不可达。
- [ ] 添加 client fake 和服务发现测试。

验收：

```bash
./gradlew :core-network:testDebugUnitTest --tests '*Remote*'
```

### Task 18：实现远程查看模式 UI

- [ ] 在 `feature-remote` 实现远程首页，展示记录仪状态、模式、空间、电量、热点状态。
- [ ] 实现远程视频列表、远程照片列表、远程播放页、远程控制页、远程设置页。
- [ ] 支持远程拍照、录音开关、驾驶/停车模式切换、热点控制、删除文件。
- [ ] 支持视频 HTTP Range 流式播放。
- [ ] 添加 `remote_status_screen`、`remote_video_list`、`remote_photo_list`、`remote_take_photo_button`、`remote_mode_parking_button` 等 `testTag`。
- [ ] 添加 Compose UI 测试，使用 fake remote client。

验收：

```bash
./gradlew :feature-remote:connectedDebugAndroidTest
```

### Task 19：完成双机联调脚本与验收流程

- [ ] 创建 `tools/two_device_smoke_test.sh`。
- [ ] 支持 `RECORDER_SERIAL`、`VIEWER_SERIAL` 环境变量。
- [ ] 记录仪端安装、启动、选择记录仪模式、打开热点和 HTTP server。
- [ ] 查看端安装、启动、进入远程查看模式。
- [ ] 对 Wi-Fi 连接步骤提供人工提示或兼容 `adb shell cmd wifi connect-network` 的自动路径。
- [ ] 验证查看端能读取状态、查看媒体、触发拍照、播放视频。

验收：

```bash
RECORDER_SERIAL=<serial-a> VIEWER_SERIAL=<serial-b> tools/two_device_smoke_test.sh
```

## 阶段 4：语音控制

### Task 20：实现语音命令解析器

- [ ] 在 `core-voice` 实现固定中文命令到 `DashCamCommand` 的映射。
- [ ] 支持命令：拍照、打开录音、关闭录音、打开热点、关闭热点、停车模式、驾驶模式、保存这段、停止录像。
- [ ] 支持常见同义词和前后空白/标点清理。
- [ ] 对无法识别命令返回明确原因并记录日志。
- [ ] 添加 `VoiceCommandParserTest`。

验收：

```bash
./gradlew :core-voice:testDebugUnitTest --tests '*VoiceCommandParser*'
```

### Task 21：实现语音唤醒与短窗口命令识别服务

- [ ] 在 `core-voice` 实现 `VoiceWakeService`。
- [ ] 采用两段式流程：低功耗唤醒词检测，唤醒后 3-5 秒命令识别窗口。
- [ ] 原型优先接入 Picovoice Porcupine/Rhino 或可替换接口；无法接入授权模型时提供 fake/local fallback 以保持可测试。
- [ ] 语音命令统一分发为 `DashCamCommand`。
- [ ] 记录识别成功、失败、超时、置信度等日志。
- [ ] 设置页支持启用/关闭语音唤醒和配置唤醒词。

验收：

```bash
./gradlew :core-voice:testDebugUnitTest
./gradlew assembleDebug
```

真机验收：语音“拍照”和“停车模式”能触发对应命令，错误识别有日志记录。

### Task 22：语音服务与前台录制集成

- [ ] 将语音服务命令接入 `RecorderForegroundService`。
- [ ] 在记录仪首页显示语音监听状态。
- [ ] 避免录制音频和语音识别的麦克风资源冲突，必要时按模式降级或提示用户。
- [ ] 停车模式下优先低功耗监听，过热或低电量时自动暂停语音唤醒。
- [ ] 添加集成测试或 fake service 测试。

验收：

```bash
./gradlew testDebugUnitTest
```

真机验收：录制中语音命令可执行，不导致录制崩溃。

## 阶段 5：稳定性、性能与自动化闭环

### Task 23：实现电量、温度与降级策略

- [ ] 监听电池电量和温度。
- [ ] 通知栏和记录仪首页展示电量、温度。
- [ ] 过热时自动降低码率/帧率；严重过热时停止录制并提示。
- [ ] 低电量时可提示关闭热点、语音或进入停车模式。
- [ ] 添加策略单元测试。

验收：

```bash
./gradlew :core-media:testDebugUnitTest --tests '*Thermal*'
```

### Task 24：完善自动化测试矩阵

- [ ] 为核心逻辑补齐单元测试：Repository、StateMachine、StoragePolicy、CommandParser。
- [ ] 为设置、记录仪首页、媒体列表、远程查看补齐 Compose UI 测试。
- [ ] 为权限弹窗、Home 后录制、息屏录制、通知栏 action 增加 UI Automator 测试。
- [ ] 配置 Gradle Managed Devices。
- [ ] 配置 `benchmark` 模块和 Baseline Profile。

验收：

```bash
./gradlew test connectedDebugAndroidTest
./gradlew :benchmark:connectedBenchmarkAndroidTest
```

### Task 25：实现设备 smoke、长稳与视频文件验证脚本

- [ ] 创建 `tools/device_smoke_test.sh`。
- [ ] 创建 `tools/long_run_recording_test.sh`。
- [ ] 创建 `tools/verify_media_files.py`，使用 `ffprobe` 检查 duration、size、video stream、audio stream、fps。
- [ ] 脚本收集 logcat、截图、文件列表、温度/电量、内存信息。
- [ ] 输出到 `build/reports/device/`。

验收：

```bash
tools/device_smoke_test.sh
tools/long_run_recording_test.sh 120
python3 tools/verify_media_files.py build/reports/device
```

### Task 26：实现 AI 测试 Runner 与失败报告模板

- [ ] 创建 `tools/ai_test_runner.py`。
- [ ] Runner 执行格式检查、单元测试、构建、UI 测试、benchmark 可选项。
- [ ] 失败时收集最后日志、截图路径、失败测试名、建议修复区域。
- [ ] 生成 `build/ai_failure_report.md`。
- [ ] 创建截图采集工具和报告模板。

验收：

```bash
python3 tools/ai_test_runner.py
```

### Task 27：完成 MVP-1 本机记录仪验收

- [ ] 记录仪模式可用。
- [ ] 驾驶模式录制可用。
- [ ] 停车模式录制可用。
- [ ] 拍照可用。
- [ ] 录音开关可用。
- [ ] 视频/照片列表可用。
- [ ] 设置页可用。
- [ ] Foreground Service 可用。
- [ ] 循环删除不删除 locked 文件。
- [ ] 真机息屏录制 2 小时，分段文件正常且可播放。

验收：

```bash
./gradlew test connectedDebugAndroidTest
tools/long_run_recording_test.sh 120
```

### Task 28：完成 MVP-2 远程查看验收

- [ ] LocalOnlyHotspot 可打开。
- [ ] HTTP API 可访问。
- [ ] 远程查看模式可发现或手动连接记录仪端。
- [ ] 第二台手机能查看视频和照片。
- [ ] 第二台手机能播放记录仪视频。
- [ ] 第二台手机能远程拍照、切换模式、开关录音。
- [ ] 鉴权保护生效。

验收：

```bash
RECORDER_SERIAL=<serial-a> VIEWER_SERIAL=<serial-b> tools/two_device_smoke_test.sh
```

### Task 29：完成 MVP-3 语音控制验收

- [ ] 唤醒词可触发短窗口命令识别。
- [ ] “拍照/打开录音/关闭录音/打开热点/关闭热点/停车模式/驾驶模式/保存这段/停止录像”可执行。
- [ ] 安静环境命令成功率不低于 90%。
- [ ] 行车噪声环境命令成功率不低于 75%。
- [ ] 语音识别失败和误识别有日志可查。
- [ ] 待机功耗可接受。

验收：真机手动测试并生成 `build/reports/voice_acceptance.md`。

### Task 30：完成 MVP-4 自动化验证闭环

- [ ] 单测、UI 测试、真机 smoke、长稳测试均可一键运行。
- [ ] 失败时自动生成 AI 可读报告。
- [ ] 每次修复后可重复运行同一套验证。
- [ ] 文档记录测试设备、系统版本、已知限制和规避方式。

验收：

```bash
python3 tools/ai_test_runner.py
```

## 最终完成标准

- [ ] 旧手机可连续录制驾驶模式 2 小时以上。
- [ ] 息屏后录制不中断。
- [ ] 停车模式低帧率录制可用。
- [ ] 拍照可用。
- [ ] 录音开关可用。
- [ ] 可打开热点。
- [ ] 另一台手机连接热点后能查看视频和照片。
- [ ] 远程手机可播放视频。
- [ ] 远程手机可触发拍照和切换模式。
- [ ] 语音核心命令可执行。
- [ ] 设置页可修改核心参数。
- [ ] 循环删除不删除 locked 文件。
- [ ] 自动化测试覆盖主要 UI 和核心逻辑。
- [ ] 真机长稳测试有报告。
- [ ] AI Runner 能根据失败日志和截图输出定位信息。
