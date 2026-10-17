# FFmpeg 音频扩展

ExoPlayer 使用 Media3 1.8.0 的官方 FFmpeg 音频扩展，优先设备解码器，仅在需要时补充软件音频解码，不负责视频解码或网络传输。MediaPlayer 不使用此扩展。

源码位于 third_party/media3-ffmpeg，来源为 AndroidX Media 1.8.0 的 libraries/decoder_ffmpeg。保留 Apache-2.0 许可、NOTICE 及上游文件头；仅补充独立模块所需的 UnstableApi 注解。

build-ffmpeg-docker.sh 在 server_id=19 的 /srv/davplayer 使用 davplayer-android:36-temurin17.0.20.1-1 镜像、NDK 26.2.11394342、API 23 构建 arm64-v8a / armeabi-v7a。build-android-docker.sh 会先调用该脚本；生成的 JNI 库、Gradle 缓存和下载缓存不纳入 Git。

FFmpeg 6.0.1 源码包：https://ffmpeg.org/releases/ffmpeg-6.0.1.tar.xz

SHA256：9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623

仅启用脚本列出的音频解码和重采样，禁用网络、程序、视频、GPL/nonfree 和外部依赖。FFmpeg 静态链接入 JNI 共享库；分发时需提供对应源码、构建脚本及 LGPL 所需的替换/重链接材料。许可原文见 app/src/main/assets/licenses/FFmpeg-LGPL-2.1.txt；Apache 原文与 NOTICE 位于同目录。

JNI 链接使用 max-page-size/common-page-size=16384；verify-native-alignment.py 检查 APK 内每个 .so 的 LOAD/RELRO，verify-apk-docker.sh 另查 ZIP 对齐。当前 APK 不再包含 Ghosten libapi.so，已删除为其保留的 pageSizeCompat 开关。静态对齐检查不代替不同页大小设备的安装与运行验收。
