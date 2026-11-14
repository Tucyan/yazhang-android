# 芽账

芽账是本地优先的 Java 原生 Android 记账应用，提供手动记账、统计、攒钱目标、用户主动选图的 AI 识别、AI 助手和本地备份。核心账本功能无需网络；AI 功能需要用户自行配置服务商。

## 构建

使用 JDK 17、Android SDK 36 和项目自带的 Gradle Wrapper。在项目根目录创建未纳入版本控制的 `local.properties`，将 `sdk.dir` 指向本机 Android SDK，然后运行：

```text
gradlew.bat :app:testDebugUnitTest
gradlew.bat :app:assembleDebug
```

Debug APK 位于 `app/build/outputs/apk/debug/app-debug.apk`。使用说明见 [用户手册](docs/USER_MANUAL.md)，课程报告位于项目根目录。

## 隐私与测试素材

API Key 由用户在设备上配置，勿提交到仓库。为保护隐私，真实账单测试图片和本地演示录屏未发布；依赖 `app/src/androidTest/assets/test_bills/` 的真实 AI 仪器测试需要自行提供相应素材和有效服务商配置，不能在全新克隆中直接运行。常规 JVM 测试不需要这些素材。
