# DashCam 开发环境与真机测试流程

> 本文件记录当前电脑环境，检查日期：2026-05-28。当前项目是 Android Gradle/Kotlin 工程，使用 `./gradlew` 构建。

## 当前项目要求

| 项目 | 当前项目配置 |
|---|---|
| Gradle wrapper | `9.1.0` |
| Android Gradle Plugin | `8.9.1` |
| Kotlin | `2.0.21` |
| Java 编译目标 | `17` |
| compileSdk / targetSdk / minSdk | `36` / `36` / `26` |
| 主要模块 | `app`、`core-*`、`feature-*`、`benchmark`、`test-robot` |

## 当前电脑环境

| 项目 | 当前状态 | 说明 |
|---|---|---|
| Android Studio | 已安装 | `/home/meng/Software/android-studio-2025.2.3.9/android-studio` |
| Android Studio JBR | 已安装 | `/home/meng/Software/android-studio-2025.2.3.9/android-studio/jbr/bin/java`，版本 `OpenJDK 21.0.8` |
| 系统 JDK | 已安装 | `/usr/bin/java`，版本 `OpenJDK 21.0.10`；`./gradlew --version` 当前使用 `/usr/lib/jvm/java-21-openjdk-amd64` |
| Android SDK | 已安装 | `/home/meng/Android/Sdk` |
| 环境变量 | 未配置 | 当前 shell 中 `ANDROID_HOME`、`ANDROID_SDK_ROOT`、`JAVA_HOME` 为空；直接运行 `./gradlew assembleDebug` 会失败 |
| SDK adb | 已安装 | `/home/meng/Android/Sdk/platform-tools/adb`，版本 `35.0.2-12147458` |
| PATH adb | 已安装但较旧 | `/usr/bin/adb`，版本 `34.0.4-debian`；建议优先使用 SDK adb |
| fastboot | 已安装 | `/home/meng/Android/Sdk/platform-tools/fastboot`，版本 `35.0.2-12147458` |
| Android platforms | 已安装 | `android-33`、`android-35`、`android-36` |
| build-tools | 已安装 | `30.0.3`、`34.0.0`、`35.0.0`、`35.0.1`、`36.0.0` |
| emulator | 已安装 | `/home/meng/Android/Sdk/emulator/emulator`，版本 `36.3.10.0` |
| AVD | 已安装 | `Medium_Phone_API_35` |
| system-images | 已安装 | `android-35/google_apis_playstore/x86_64` |
| Android cmdline-tools | 已安装 | `/home/meng/Android/Sdk/cmdline-tools/latest/bin`，`sdkmanager --version` 为 `20.0` |
| KVM | 设备节点存在 | `/dev/kvm` 存在；当前用户组未包含 `kvm`，模拟器加速异常时再配置 |
| git / curl / unzip | 已安装 | `git 2.43.0`、`curl 8.5.0`、`UnZip 6.00` |
| Python | 已安装 | `Python 3.12.3` |
| ffmpeg | 已安装 | `ffmpeg 6.1.1-3ubuntu5` |
| ImageMagick | 已安装 | `/usr/bin/convert`，版本 `6.9.12-98`；`magick` 命令未发现，Ubuntu 上使用 `convert` 即可 |

## 必须配置

当前仓库没有 `local.properties`，当前 shell 也没有 Android SDK 环境变量，所以直接运行 Gradle 会报：

```text
SDK location not found. Define a valid SDK location with an ANDROID_HOME environment variable or by setting the sdk.dir path in your project's local properties file
```

二选一即可。

方式 1：写入 shell 配置，例如 `~/.bashrc` 或 `~/.zshrc`：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export JAVA_HOME="/usr/lib/jvm/java-21-openjdk-amd64"
export PATH="$ANDROID_HOME/platform-tools:$ANDROID_HOME/emulator:$ANDROID_HOME/cmdline-tools/latest/bin:$JAVA_HOME/bin:$PATH"
```

写入后重新打开终端，或执行：

```bash
source ~/.bashrc
```

方式 2：只为当前项目创建本地配置文件：

```properties
sdk.dir=/home/meng/Android/Sdk
```

文件路径为：

```text
/home/meng/Documents/projects/dashcam/local.properties
```

`local.properties` 已在 `.gitignore` 中，不会提交到仓库。

## 需要安装或补齐的包

| 项目 | 是否必须 | 安装方式 |
|---|---|---|
| Android SDK Command-line Tools (latest) | 已安装 | `/home/meng/Android/Sdk/cmdline-tools/latest/bin/sdkmanager` 与 `avdmanager` 可用 |
| ImageMagick | 已安装 | `/usr/bin/convert` 可用；`magick` 命令未发现 |

安装 Command-line Tools 后应存在：

```bash
ls "$ANDROID_HOME/cmdline-tools/latest/bin/sdkmanager"
ls "$ANDROID_HOME/cmdline-tools/latest/bin/avdmanager"
```

如果模拟器加速失败，再把当前用户加入 `kvm` 组，完成后需要重新登录：

```bash
sudo usermod -aG kvm meng
```

## 环境检查命令

```bash
java -version
/home/meng/Android/Sdk/platform-tools/adb version
/home/meng/Android/Sdk/platform-tools/adb devices -l
/home/meng/Android/Sdk/emulator/emulator -list-avds
/home/meng/Android/Sdk/emulator/emulator -version
python3 --version
ffmpeg -version
```

配置好 `PATH` 后可简化为：

```bash
adb version
adb devices -l
emulator -list-avds
```

## 当前真机

当前连接的真实 Android 设备：

```text
4e348abc device usb:1-1 product:umi model:Mi_10 device:umi
```

设备信息：

| 项目 | 值 |
|---|---|
| 厂商 | Xiaomi |
| 型号 | Mi 10 |
| device | `umi` |
| Android | `13` |
| API | `33` |

真机测试前检查：

```bash
/home/meng/Android/Sdk/platform-tools/adb devices -l
```

期望设备状态为 `device`，不是 `unauthorized` 或空列表。

## 构建与测试命令

如果还没有配置 shell 环境变量，可以在命令前临时加：

```bash
ANDROID_HOME=/home/meng/Android/Sdk ANDROID_SDK_ROOT=/home/meng/Android/Sdk ./gradlew assembleDebug
```

常用检查：

```bash
./gradlew --version
./gradlew ktlintCheck
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

真机 instrumentation 测试：

```bash
./gradlew :app:connectedDebugAndroidTest
```

## 本次验证结果

在临时设置 `ANDROID_HOME=/home/meng/Android/Sdk` 与 `ANDROID_SDK_ROOT=/home/meng/Android/Sdk` 后，以下命令已通过：

```text
./gradlew --version
./gradlew ktlintCheck
./gradlew assembleDebug
./gradlew testDebugUnitTest
```

直接运行 `./gradlew assembleDebug` 未通过，原因是当前 shell 未设置 SDK 路径。

`./gradlew :app:connectedDebugAndroidTest` 已连接到 Mi 10，但未完成测试：

```text
INSTALL_FAILED_USER_RESTRICTED: Install canceled by user
Starting 0 tests on Mi 10 - 13
Finished 0 tests on Mi 10 - 13
```

这不是代码构建错误，而是真机阻止了 ADB 安装。请在手机开发者选项中允许 USB 安装/通过 USB 安装应用，并确认手机上的安装授权弹窗后重试。

## 真机必须覆盖的场景

模拟器适合普通 UI 与 repository 测试。以下能力仍然必须用真实设备覆盖：

- CameraX 录制
- 息屏录制
- Wi-Fi 热点 / LocalOnlyHotspot
- 双机远程查看
- 语音唤醒
- 功耗与发热
