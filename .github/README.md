# 手动构建 Debug APK

工作流：`Build Debug APK (ARM64)`，仅由 `workflow_dispatch` 手动触发。

1. 将工作流合并到仓库默认分支后，打开 GitHub 的 **Actions** 页面。
2. 选择 **Build Debug APK (ARM64)**，点击 **Run workflow**。
3. 选择要构建的分支（例如 `custom`），再次点击 **Run workflow**。
4. 构建成功后，从该次运行的 **Artifacts** 下载 `whisper-debug-arm64-运行编号`。
   压缩包包含 `whisper-debug-arm64.apk`、APK 信息和 SHA-256 校验文件，保留 14 天。

构建使用 JDK 17、Gradle Wrapper 8.13、Android SDK 36、NDK 27.2.12479018
和 CMake 3.22.1。源码子模块会自动初始化；使用仓库已有的预编译静态库，
不需要执行 `prebuild/build_all.sh`。首次构建较慢，Gradle 依赖会缓存供后续运行使用。

产物是使用仓库示例密钥签名的 ARM64 Debug APK，包含当前分支的包名、
`whisper` 显示名称和自定义图标。无需配置 GitHub Secrets。
当前跳过应用模块的原 Telegram Firebase 包名校验；新包名的 Firebase 推送未配置。

也可在配置好 Android SDK 和 JDK 17 的本机使用相同设置：

```bash
bash ./gradlew :TMessagesProj_App:assembleAfatDebug \
  --init-script .github/scripts/debug-build.init.gradle \
  -Pandroid.injected.build.abi=arm64-v8a \
  --no-daemon --max-workers=4 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8'
```

APK 通常位于 `TMessagesProj_App/build/intermediates/apk/afat/debug/app.apk`；
工作流同时兼容 `TMessagesProj_App/build/outputs/apk/afat/debug/app.apk` 路径。
