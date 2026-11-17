# Android / Material 3 升级记录

## 范围

- 将实际布局、按钮、开关、卡片、输入框、工具栏及对话框迁移为 Material 3 风格，而不是只换主题名称。
- 浅色/深色语义配色；Android 12+ 使用系统动态取色，旧系统保留静态配色。
- 新目标 API 的 edge-to-edge：统一处理系统栏、刘海及输入法 inset，避免内容被遮挡。
- 使用 Activity Result API 获取存储访问授权，保留 Android 9/10 的旧存储权限路径。拒绝权限后不再反复强制跳转设置。
- 进度对话框支持主线程更新/关闭，并随 Activity 销毁清理；主界面 ViewBinding 不再依赖 Hook 反射取得布局。

## 构建与依赖

依赖统一写入 `gradle/libs.versions.toml`，移除旧 SweetDependency/SweetProperty 插件、重复声明及未使用的自定义开关/DrawableToolbox。版本均固定，没有 `+` 或动态最新版本。使用受当前集成约束的稳定组合，而不是把所有库一律升到最新主版本。

- JDK 17；Gradle 8.13（官方 SHA-256 校验）；AGP 8.13.2。
- Kotlin 2.2.21；KSP 2.2.21-2.0.5。
- compileSdk/targetSdk 36；minSdk 28 不变；应用 ID、版本号、arm64 ABI 不变。
- AndroidX Test ext:junit 1.3.0、Espresso 3.7.0；JUnit 4.13.2。
- Material 1.14.0、Core 1.17.0、AppCompat 1.7.1、RecyclerView 1.4.0、ConstraintLayout 2.2.1。

Glide 升至 5.0.7；本项目只使用基础加载、错误回退和暂停/恢复接口，仍需设备验证预览显示。5.0.9 的 AAR 实测要求 compileSdk 37，因此没有强行忽略兼容性检查，而采用官方 AAR 元数据与 API 36 兼容的 5.0.7。

没有在本批次中迁移 Xposed API 82、YukiHookAPI 1.2.0 或 FFmpegKit min 6.0.1。Yuki 新版涉及 Hook/反射 API 与产物变更，需要独立迁移和宿主设备验证；更换 FFmpeg 包也涉及编解码能力与体积。Yuki 1.2.0 的 KSP 生成代码在 Kotlin 2.2 下可编译，但含不可见 API 引用/未来 Kotlin 兼容性警告，不能无限升级 Kotlin 而不处理。保留 Yuki 既有 FreeReflection 的 JitPack 源，并限制为该依赖组。

## 验证边界

构建、JVM 测试、lint、ELF/ZIP 对齐检查均不能替代 Android 实机验证。发布前应在最低支持版本和 Android 15/16 上检查：

- 浅/深色、动态取色、窄屏、横屏、大字体和输入法；初始说明页与管理页的滚动。
- 权限允许/拒绝/设置页返回，搜索、刷新、单包/全部同步、删除/清理确认。
- 切后台或旋转时的同步/进度反馈；真实 Telegram/LSPosed Hook、FFmpeg 转换与透明输出。
- 16KB 页设备的 native 库加载。ELF/ZIP 对齐通过仅证明布局条件，不证明所有运行时行为。

## 本次验证结果

```sh
./gradlew :app:assembleDebug :app:testDebugUnitTest :app:assembleDebugAndroidTest :app:lintDebug :app:assembleRelease --no-daemon
```

最终组合构建成功（139 tasks）。现有 JVM 示例测试 1/1 通过；instrumentation APK 仅完成编译，没有设备执行。Lint 无错误、46 条警告（包括未使用资源、可更新依赖、存储 API 等），没有把警告整体屏蔽，也不宣称代码无告警。

最终 debug/release APK 均通过 `zipalign -c -P 16 -v 4`；debug APK 内 9 个 arm64 原生库的 ELF LOAD 对齐均至少 16KB。release APK 未签名，不是可直接安装的正式发布包。未做截图/真机视觉验收、Telegram Hook 或真实贴纸转换回归。

## 官方参考

- [AGP 8.13 兼容性](https://developer.android.com/build/releases/agp-8-13-0-release-notes)
- [Material Components Android 发布](https://github.com/material-components/material-components-android/releases)
- [KSP 发布](https://github.com/google/ksp/releases)
- [Android edge-to-edge](https://developer.android.com/develop/ui/views/layout/edge-to-edge)
- [16KB 页支持](https://developer.android.com/guide/practices/page-sizes)
