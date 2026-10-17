# FFmpeg 音频扩展

ExoPlayer 使用 Media3 1.8.0 的官方 FFmpeg 音频扩展，优先设备解码器，仅在需要时补充软件音频解码，不负责视频解码或网络传输。MediaPlayer 不使用此扩展。

源码位于 third_party/media3-ffmpeg，来源为 AndroidX Media 1.8.0 的 libraries/decoder_ffmpeg。保留 Apache-2.0 许可、NOTICE 及上游文件头；仅补充独立模块所需的 UnstableApi 注解。

build-ffmpeg-docker.sh 在 server_id=19 的 /srv/davplayer 使用 davplayer-android:36-temurin17.0.20.1-1 镜像、NDK 26.2.11394342、API 23 构建 arm64-v8a / armeabi-v7a。build-android-docker.sh 会先调用该脚本；生成的 JNI 库、Gradle 缓存和下载缓存不纳入 Git。

FFmpeg 6.0.1 源码包：https://ffmpeg.org/releases/ffmpeg-6.0.1.tar.xz

SHA256：9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623

仅启用脚本列出的音频解码和重采样，禁用网络、程序、视频、GPL/nonfree 和外部依赖。FFmpeg 静态链接入 JNI 共享库；分发时需提供对应源码、构建脚本及 LGPL 所需的替换/重链接材料。许可原文见 app/src/main/assets/licenses/FFmpeg-LGPL-2.1.txt；Apache 原文与 NOTICE 位于同目录。

JNI 链接使用 max-page-size/common-page-size=16384；verify-native-alignment.py 检查 APK 内每个 .so 的 LOAD/RELRO，verify-apk-docker.sh 另查 ZIP 对齐。当前 APK 不再包含 Ghosten libapi.so，已删除为其保留的 pageSizeCompat 开关。静态对齐检查不代替不同页大小设备的安装与运行验收。

## API18 专用扩展

用户要求 API18 也保留软件音频解码。使用单独的 `third_party/media3-ffmpeg-api18` 模块，官方 Media3 1.2.1 未修改源码（来源、逐文件 SHA256 见模块 README 与 UPSTREAM.sha256）。仅 `-Papi18=true` 选择它，现代模块保持 Media3 1.8.0。

在指定远程 Docker 中先运行 `./setup-api18-ndk-docker.sh`，安装官方 NDK r23c（23.2.8568313）到项目 cache/legacy-sdk，不替换镜像内 NDK26。然后 `./build-android-docker.sh -Papi18=true testDebugUnitTest lintPreview assemblePreview` 自动执行 `build-ffmpeg-api18-docker.sh`。FFmpeg 6.0.1 源码与校验和同现代构建；独立缓存和完成标记避免产物混用。

armeabi-v7a 使用 API18，arm64-v8a 使用该 ABI 的最低 API21。同样只启用音频解码器、静态 C++、LGPL 配置与 16 KiB LOAD/RELRO 对齐。该扩展供 ExoPlayer 使用，系统 MediaPlayer 不加载它；不是所有音频/视频格式的支持保证。需在真实 API18 设备验证动态加载、声音、跳转及长时间播放。
