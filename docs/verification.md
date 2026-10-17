# 构建与验收

当前版本 0.4.9 / 67：仅 ExoPlayer（Media3 1.2.1）+ 官方 FFmpeg 音频扩展，统一 minSdk18，arm64-v8a / armeabi-v7a。compileSdk36、targetSdk33、Kotlin2.2.0、AGP8.9.2、Gradle8.11.1、JDK17 / Java11。

仅 server_id=19 /srv/davplayer 使用固定 Docker 镜像及既有 signing、Gradle/NDK/FFmpeg 缓存。同步前备份并替换 app/src，不上传凭据、签名密钥、缓存或 .codex。

```sh
./build-android-docker.sh testDebugUnitTest lintPreview assemblePreview
./verify-apk-docker.sh app/build/outputs/apk/preview/app-preview.apk
```

仅一套构建、一份 FFmpeg 模块；NDK r23c 23.2.8568313、FFmpeg6.0.1，ARMv7 API18 / ARM64 API21。首次 NDK 安装与重建说明见 media3-ffmpeg.md。

清理了系统播放核心、核心枚举和选择设置、多核心抽象/测试、重复现代版构建、HLS 依赖、播放诊断日志和 API 统计计数。进度、选集、音轨、缓存与真实请求取消逻辑保留。缓存模型不再保存未使用的校验字段；维持磁盘头部读取，现有海报可直接复用。

测试覆盖登录与令牌重试、分页与特殊路径、认证隔离、连接复用、实际请求取消/排空、列表顺序、NFO、海报复用与原子缓存、限流冷却、音轨辅助、速率计量和系统栏兼容。静态与构建结果不替代手机上的解码、跳转、切集、前后台及 Android4.3 真机验收。

安装不清配置、缓存或观看记录，不修改 30 秒息屏设置；安装、操作手机和推送/发布分别需要明确授权。

## 本轮结果

- 指定 Docker 的 testDebugUnitTest、lintPreview、assemblePreview、verify-apk-docker.sh 全部通过。57 项测试，0 失败/错误/跳过；Lint 0 Error/Fatal、8 条既有 Warning、0 未使用资源。
- APK DEX 检查：没有 SystemPlayer、PlaybackEngine、PlaybackPlayer、PlaybackTrace 和 HLS 模块；原生库仅两 ABI 的 libffmpegJNI.so。v1/v2 签名、ZIP、16KiB LOAD/RELRO 对齐通过。
- APK 4,958,098 bytes；SHA256 `c53db336bb6488b33ceef3448e021a8edf17178921d41a40883c4d15478ab74c`。
- 新增缓存头部兼容回归，确认删除无用模型字段不会导致现有海报失效；取消测试继续验证实际请求退出，不依赖已删除的诊断计数器。
- 尚未安装或进行本轮真机播放验收；未操作手机、未清手机数据、未推送。
