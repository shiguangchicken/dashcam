# DashCam 开发环境与模拟器测试流程

> 来源：`docs/android_dashcam_ai_implementation_plan.md` 13.2，以及阶段 0 实际构建与模拟器测试记录。

## Linux 环境基线

当前开发机已安装 Android Studio，优先复用 Android Studio 自带 JBR 与 Android SDK，避免再单独安装系统 JDK、adb 或 SDK 工具。

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
| Gradle wrapper | 已创建 | 当前工程使用 `./gradlew`，已验证 `assembleDebug`、`testDebugUnitTest` 和 `connectedDebugAndroidTest` |

建议写入 shell 配置，例如 `~/.bashrc` 或 `~/.zshrc`：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export JAVA_HOME="$HOME/Software/android-studio/jbr"
export PATH="$JAVA_HOME/bin:$ANDROID_SDK_ROOT/cmdline-tools/latest/bin:$ANDROID_SDK_ROOT/platform-tools:$ANDROID_SDK_ROOT/emulator:$PATH"
```

当前机器不建议再用 `apt` 安装 `adb` 或 `openjdk-17-jdk` 作为首选路径，避免和 Android Studio / SDK 自带工具混用。

## 环境检查命令

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

项目工具检查：

```bash
./gradlew --version
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

## 模拟器查询、启动、测试与停止

已验证可用 AVD：

```bash
~/Android/Sdk/emulator/emulator -list-avds
```

当前输出：

```text
Pixel_2
```

启动模拟器：

```bash
~/Android/Sdk/emulator/emulator -avd Pixel_2 -no-window -no-audio -no-snapshot -gpu swiftshader_indirect
```

等待 ADB 连接：

```bash
~/Android/Sdk/platform-tools/adb wait-for-device
```

确认系统完成启动：

```bash
~/Android/Sdk/platform-tools/adb shell getprop sys.boot_completed
```

期望输出：

```text
1
```

运行阶段 0 Compose / Android instrumentation 测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

已验证结果：

```text
Finished 2 tests on Pixel_2(AVD) - 15
BUILD SUCCESSFUL
```

停止模拟器：

```bash
~/Android/Sdk/platform-tools/adb emu kill
```

期望输出：

```text
OK: killing emulator, bye bye
OK
```

## 测试适用范围

模拟器适合：

- Compose UI 测试
- 设置页和普通交互测试
- Room / Repository 的 JVM 或 instrumentation 测试
- 不依赖真实相机、热点、麦克风环境的集成测试

真机仍然必须覆盖：

- CameraX 录制
- 息屏录制
- Wi-Fi 热点 / LocalOnlyHotspot
- 双机远程查看
- 语音唤醒
- 功耗与发热
