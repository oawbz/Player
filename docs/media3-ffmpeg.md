# FFmpeg 音频扩展

唯一扩展模块位于 third_party/media3-ffmpeg，使用官方 Media3 1.2.1 未修改的 Java/JNI 源码；来源、逐文件校验和见模块 README 与 UPSTREAM.sha256。ExoPlayer 优先设备解码，需要时使用软件音频解码；不提供软件视频解码。

在 server_id=19 的 /srv/davplayer 使用固定 Docker 镜像构建。首次运行 setup-api18-ndk-docker.sh，将官方 NDK r23c 23.2.8568313 安装到项目 cache/legacy-sdk。build-android-docker.sh 自动调用 build-ffmpeg-docker.sh，构建 FFmpeg 6.0.1：armeabi-v7a API18 / arm64-v8a API21、静态 C++、16KiB LOAD/RELRO 对齐。

FFmpeg 源码：https://ffmpeg.org/releases/ffmpeg-6.0.1.tar.xz

SHA256：9b16b8731d78e596b4be0d720428ca42df642bb2d78342881ff7f5bc29fc9623

只启用脚本列出的音频解码器与重采样，禁用网络、程序、视频解码、GPL/nonfree 和外部依赖。FFmpeg 静态链接入 JNI 共享库；分发时提供对应源码、构建脚本与 LGPL 所需的替换/重链接材料。保留 Apache、LGPL 及已有来源许可；原文见 app/src/main/assets/licenses 和 docs/licenses。

verify-apk-docker.sh 检查 SDK、签名、ELF 与 ZIP 对齐。源码、许可和构建脚本纳入 Git；生成的 JNI 库、下载与构建缓存不纳入 Git。静态检查不替代 Android 4.3 设备上的动态加载、解码与长时间播放验证。
