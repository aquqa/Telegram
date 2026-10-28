# 手动构建 APK

工作流：`Build APK (ARM64)`，仅由 `workflow_dispatch` 手动触发。

1. 打开仓库 **Actions → Build APK (ARM64) → Run workflow**。
   工作流需存在于默认分支；默认分支为 `custom` 时无需合并到 `master`。
2. 选择要构建的分支和 `build_type`：
   - `debug`：构建 Debug APK，默认选项，使用仓库示例签名，无需 Secrets。
   - `release`：构建 Release APK，使用你配置的签名 Secrets。
   - `both`：分别启动 Debug 和 Release 构建，产物独立上传。
3. 构建成功后，从运行的 **Artifacts** 下载
   `whisper-debug-arm64-运行编号` 或 `whisper-release-arm64-运行编号`。
   包含 APK、应用信息、公开签名证书指纹、清单信息和 SHA-256 校验文件，保留 14 天。

## Release 签名

在 **Settings → Secrets and variables → Actions → Repository secrets** 添加：

| Secret | 内容 |
| --- | --- |
| `RELEASE_KEYSTORE_BASE64` | keystore 文件的 Base64 编码，可包含换行 |
| `RELEASE_STORE_PASSWORD` | keystore 密码 |
| `RELEASE_KEY_ALIAS` | 签名密钥别名 |
| `RELEASE_KEY_PASSWORD` | 签名密钥密码 |

Release 构建会检查四项是否齐全，在 runner 临时目录还原 keystore，覆盖当前
应用模块的 Release 签名配置，并核对 APK 的证书与该 keystore 是否一致。
完成或失败后都会清理临时签名文件。私钥和密码不会上传为构建产物。
Debug 构建不读取这些 Secrets，继续使用仓库示例密钥。

使用自己的密钥签名后，Release 通常不能直接覆盖示例密钥签名的 Debug 应用；
两者包名相同，切换时需要卸载旧版本（卸载会清除本地数据）。

## 构建配置

使用 JDK 17、Gradle Wrapper 8.13、Android SDK 36、NDK 27.2.12479018
和 CMake 3.22.1。子模块自动初始化，使用仓库已有的预编译静态库，
无需执行 `prebuild/build_all.sh`。Gradle 依赖缓存供后续运行使用。

两种构建都使用 ARM64 和当前分支的包名、`whisper` 名称及自定义图标。
通过 Gradle DSL 选择 ABI，不使用 IDE 注入参数。校验 Release 不可调试、
不带 `testOnly` 标记，并启用项目原有的代码混淆、优化和资源清理配置。
当前两种构建都跳过应用模块的原 Telegram Firebase 包名校验；新包名的推送未配置。

本机 Debug 构建（配置好 JDK 17 和 Android SDK 后）：

```bash
BUILD_TYPE=debug bash ./gradlew :TMessagesProj_App:assembleAfatDebug \
  --init-script .github/scripts/build-apk.init.gradle \
  --no-daemon --max-workers=4 \
  '-Dorg.gradle.jvmargs=-Xmx4g -XX:MaxMetaspaceSize=1g -Dfile.encoding=UTF-8'
```

本机 Release 构建时，设置 `BUILD_TYPE=release`、`RELEASE_KEYSTORE_PATH`
（keystore 的绝对路径），以及三个签名环境变量，然后使用相同 init 脚本执行
`:TMessagesProj_App:assembleAfatRelease`。密码通过环境变量提供，无需写入源码。

APK 通常位于 `TMessagesProj_App/build/outputs/apk/afat/构建类型/app.apk`；
工作流也兼容 `TMessagesProj_App/build/intermediates/apk/afat/构建类型/app.apk` 路径。
