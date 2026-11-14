# 芽账 · 本机开发环境与避坑指南

最后核对：2026-11-10

## 一、本机工具链

| 组件 | 路径 / 版本 | 注意 |
|---|---|---|
| 操作系统 | Windows 10.0.26200 x64 | 使用 Git Bash 执行命令 |
| JDK | Microsoft OpenJDK 17.0.17 | `JAVA_HOME` 已配置 |
| Android Studio | 2025.2.2（`AI-252.27397.103.2522`） | 当前最高兼容 AGP 8.13.1 |
| Android SDK | `C:\Users\ALmerb\AppData\Local\Android\Sdk` | `ANDROID_HOME` 未全局配置 |
| Build Tools | 34.0.0 – 37.0.0 | 项目实际使用 36 |
| Platforms | android-35 / 36 / 37 | `compileSdk = 36` |
| ADB | `Sdk\platform-tools\adb.exe`，1.0.41 | 不在 PATH，必须用绝对路径 |
| Gradle | 仅 Wrapper，9.4.1 | 无全局 Gradle CLI |
| AGP | 8.13.1 | 禁止升级 9.x |
| 模拟器 | `emulator-5554`，Pixel_9a，Android 17 / API 37，x86_64 | `connectedDebugAndroidTest` 需要它在线 |

`local.properties` 已存在且被 `.gitignore` 忽略，内容为：

```properties
sdk.dir=C\:/Users/ALmerb/AppData/Local/Android/Sdk
```

## 二、构建配置

- Java 17，`sourceCompatibility/targetCompatibility = 17`
- `compileSdk 36`、`targetSdk 36`、`minSdk 26`
- `applicationId = com.example.yazhang`
- Room 2.8.4，使用 `annotationProcessor`
- Maven 仓库使用本机已有镜像配置
- Gradle 发行包来自华为云镜像
- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`

## 三、常用命令

```bash
./gradlew.bat :app:testDebugUnitTest
./gradlew.bat :app:lintDebug
./gradlew.bat :app:assembleDebug
./gradlew.bat :app:connectedDebugAndroidTest
```

ADB 示例：

```bash
"/c/Users/ALmerb/AppData/Local/Android/Sdk/platform-tools/adb.exe" devices
"/c/Users/ALmerb/AppData/Local/Android/Sdk/platform-tools/adb.exe" -s emulator-5554 install -r app/build/outputs/apk/debug/app-debug.apk
```

## 四、本机容易踩的坑

1. **每个项目都必须有 `local.properties`**
   - 缺少时 Gradle 找不到 SDK。
   - 该文件已被 `.gitignore` 忽略，不要提交本机绝对路径。

2. **AGP 不能升级到 9.x**
   - 当前 Android Studio 2025.2.2 最高支持 AGP 8.13.1。
   - 不要跟随 Gradle/AGP 的“可升级”提示。

3. **ADB 不在 PATH**
   - 直接执行 `adb` 会失败。
   - 使用完整路径 `C:\Users\ALmerb\AppData\Local\Android\Sdk\platform-tools\adb.exe`。
   - 模拟器在线时也可能因 Gradle 仪器测试占用而暂时无响应，先等测试结束。
   - 若 Gradle 报 `Unknown API Level` 或 `Connection refused`，通常表示模拟器进程已退出：
     ```bash
     "/c/Users/ALmerb/AppData/Local/Android/Sdk/emulator/emulator.exe" -avd Pixel_9a -no-boot-anim -no-snapshot-load
     "/c/Users/ALmerb/AppData/Local/Android/Sdk/platform-tools/adb.exe" wait-for-device
     "/c/Users/ALmerb/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell getprop sys.boot_completed
     "/c/Users/ALmerb/AppData/Local/Android/Sdk/platform-tools/adb.exe" shell getprop ro.build.version.sdk
     ```
     等到 `sys.boot_completed=1`、API 为 37 后再重跑测试。

4. **不要同时运行多个 Gradle 构建**
   - 尤其 `connectedDebugAndroidTest` 会占用模拟器和 Gradle 进程。
   - 构建输出可能被缓冲，需等待任务结束再判断结果。

5. **PowerShell 5.1 与 UTF-8**
   - 无 BOM 的 UTF-8 脚本可能乱码或吞行。
   - 本项目统一在 Git Bash 中执行；自定义 `.ps1` 必须保存为 UTF-8 with BOM。

6. **Android Keystore 只能在设备/模拟器测试**
   - `SecureSettings` 依赖 AndroidKeyStore，JVM 单元测试不能覆盖。
   - 加密相关验证放在 `app/src/androidTest/`，需要 `connectedDebugAndroidTest`。

7. **Room 禁止主线程访问**
   - 所有 DAO 调用必须通过 `UiTask` 或测试中的 `allowMainThreadQueries()`。
   - 生产代码不要在主线程直接查询。

8. **日期相关测试依赖系统日期**
   - `EntryValidator` 允许日期最多到明天；部分测试使用 `LocalDate.now()`。
   - 模拟器/主机日期异常会导致测试结果异常；当前环境日期为 2026-11-10。

9. **真实 AI 与真实支付不能随意执行**
   - API Key 只能运行时输入或由用户在设备上配置，禁止写入源码、文档、日志或测试。
   - 真实模型调用、真实 TokenDance 下单、支付宝拉起和付款都必须得到用户明确同意。
   - 自动化支付测试只使用 `FakeConnection` / 内存伪实现。

10. **Lint 警告不阻塞但需知悉**
    - `MissingApplicationIcon`：尚未配置应用图标。
    - `OldTargetApi`：本机 SDK 有更新版本，当前仍按计划使用 36。
    - 依赖版本提示与 `SetTextI18n`：当前为预期状态，不改变功能。

11. **Windows 路径与引号**
    - 路径包含空格时必须加双引号。
    - Git Bash 中访问 `C:\...` 使用 `/c/...` 或完整 Windows 路径并正确转义。

12. **不要提交敏感文件**
    - `.gitignore` 已排除 `local.properties`、`.env*`、`keystore.properties`、`*.jks`、`*.keystore`、`*.key`、`*.pem` 等。
    - 提交前运行 `git status` 核对，不要使用未经检查的 `git add -A`。

## 五、当前验证状态

- `testDebugUnitTest`：通过
- `lintDebug`：通过（0 errors，10 warnings）
- `assembleDebug`：通过
- `connectedDebugAndroidTest`：通过，Pixel_9a（API 37）上 14 个测试全部完成；首次失败因模拟器退出，重启后通过
- 真实 AI 调用、真实 TokenDance 充值/支付：未执行

## 六、相关文档

- [AGENTS.md](../AGENTS.md)：约束、命令与索引
- [完成记录](COMPLETED_WORK.md)
- [当前状态](PROJECT_STATUS.md)
