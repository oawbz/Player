# 构建与验收

仅在天翼云 SPanel server_id=19 的 /srv/davplayer 构建；不使用本地 Android SDK/NDK。同步源码前备份并完整替换 app/src，保留原有 signing 和 cache。不得上传凭据、私钥、应用资料缓存或 .codex。

```sh
./build-android-docker.sh testDebugUnitTest lintPreview assemblePreview
./verify-apk-docker.sh app/build/outputs/apk/preview/app-preview.apk
```

工具链：minSdk 23、compileSdk 36、targetSdk 33、Kotlin 2.2.0、AGP 8.9.2、Gradle 8.11.1、JDK 17 / Java 11。固定镜像与派生步骤见 docker/ 及构建脚本。签名目录缺失时构建停止，不生成替代密钥。

自动检查覆盖 OpenList 登录/令牌重试/分页/特殊字符路径/取消，列表过滤与排序、NFO 解析、资料缓存与下载边界、播放前排空、音轨提示以及受控 MediaPlayer 生命周期。预览 APK 不可调试；签名、SDK、ELF 和 ZIP 对齐分别校验。

当前用户已反馈 0.4.0 的手动播放没有卡顿；0.4.1 已安装但自动海报滚动效果尚未记录验收结果；0.4.2 双核实现构建通过，真实 MediaPlayer/ExoPlayer 切换、解码、进度恢复和连续切集仍需用户手动测试。不能由这次无卡顿反推旧版卡顿的唯一原因。

安装、清数据、操作手机、推送或发布分别取得明确授权。设备操作使用 adb -s 核对序列号。安装不自动启动、不修改系统息屏设置；默认不清配置、缓存和观看记录。日志只记录数字、请求种类与状态，不输出地址、签名或认证。

## 2026-10-17 源码收敛验证

- server_id=19 指定 Docker 的 testDebugUnitTest、lintPreview、assemblePreview 和 verify-apk-docker.sh 均成功。
- 43 项测试，0 失败、0 错误；删除旧批量线程池的专用测试，保留取消 Future 后仍等待实际退出的队列测试。
- 完整 lint：0 Error/Fatal、0 未使用资源问题，74 Warning（固定 SDK/依赖、中文文案、RTL、图标和界面布局等），未通过关闭 lint 掩盖问题。
- APK 内仅有两种 ABI 的 libffmpegJNI.so，LOAD/RELRO 均为 16 KiB 对齐；v1/v2 签名和 ZIP 对齐通过。
- APK SHA256：ee6d03532ab6236c828c1d30b9fe3a55b668c80373123f427f4c7ae113b14d40。
- 未安装、未操作手机、未清除应用配置/缓存/观看记录；真实双核播放验收仍待手动完成。
