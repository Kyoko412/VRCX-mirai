# Android 手机端构建与安装

手机端位于同一仓库的 `android/`，包名为 `com.kyoko412.vrcxcompanion`。它通过同一 Wi-Fi 读取已配对电脑上的 VRCX 记录；电脑 VRCX 必须保持运行并启用「手机访问」。手机端不会复制电脑数据库，也不会在手机上保存历史列表。

## 下载测试包

打开本仓库的 **Actions → Android companion → 最新成功运行 → Artifacts**，下载 `vrcx-mirai-companion-debug`，解压后得到 `app-debug.apk`。这是调试签名包，用于测试。不同电脑或 CI 运行生成的调试签名可能不同，覆盖安装失败时先卸载旧的调试包；卸载会清除手机上的配对令牌，需要重新配对。

也可以在本地使用 JDK 21 和 Android SDK Platform 37 构建：

```powershell
cd android
.\gradlew.bat testDebugUnitTest assembleDebug assembleDebugAndroidTest
```

产物在 `android/app/build/outputs/apk/debug/app-debug.apk`。`assembleDebugAndroidTest` 只编译设备测试包；要运行屏幕与权限测试，还需要连接可用的 Android 设备并执行 `./gradlew connectedDebugAndroidTest`。

用 USB 调试安装：

```powershell
adb install -r android/app/build/outputs/apk/debug/app-debug.apk
```

也可将 APK 传到手机并点击安装。Android 可能要求允许从该文件管理器安装应用。手机与电脑连接同一 Wi-Fi，在电脑 VRCX「设置 → 集成 → 手机访问」中选择 Windows **专用网络**地址、启用服务并生成二维码。用手机扫码，随后在电脑端批准这台手机。若电脑防火墙阻止访问，只对专用网络开放 TCP 34682；不要对公用网络开放。

## 发布签名

正式安装包必须由仓库所有者用**长期保管的同一密钥库**签名，才能正常覆盖升级。密钥库及密码不得加入 Git。`android/app/build.gradle.kts` 读取以下环境变量：

| 环境变量                         | 含义                 |
| -------------------------------- | -------------------- |
| `VRCX_ANDROID_KEYSTORE`          | 本机密钥库的绝对路径 |
| `VRCX_ANDROID_KEYSTORE_PASSWORD` | 密钥库密码           |
| `VRCX_ANDROID_KEY_ALIAS`         | 密钥别名             |
| `VRCX_ANDROID_KEY_PASSWORD`      | 密钥密码             |

提供这四项后运行 `./gradlew assembleRelease`，再用 Android SDK 的 `apksigner verify --verbose --print-certs android/app/build/outputs/apk/release/app-release.apk` 检查签名和证书指纹，并记录指纹供下一版核对。若在 GitHub Actions 构建，仓库所有者需保存 `VRCX_ANDROID_KEYSTORE_BASE64`（密钥库文件的 Base64 内容）以及其余三个密码/别名 Secrets，然后手动运行 **Android companion** 工作流并勾选 `build_signed_release`。它只上传供审核的签名 APK，不会自动创建 GitHub Release。正式发布需先完成真机验收并由仓库所有者确认。

## 数据与兼容性

第一次配对需要相机权限；也可粘贴二维码内容。Android 17 及以上还会请求局域网权限。手机只显示电脑本机已记录的数据，简介变化时间是本机观察到的时间，地图离开时间若未观察到会明确标注。游戏日志仅返回已确认属于当前账号的记录。电脑关闭服务、切换账号或撤销手机权限时，手机会清除当前列表并提示重新连接或配对。接口版本为 `v1`，不兼容时会提示更新应用。
