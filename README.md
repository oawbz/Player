# OpenList 分支（0.4.3-landscape）

在设置中添加 OpenList 服务连接（填写服务首页地址和账号密码），测试并保存后添加文件夹作为分类。优先读取作品目录内 movie.nfo 和 poster.jpg；保留分季、自然集序、进度、音轨。首页进入时自动获取列表，首页与分类的可见海报优先读取缓存，缺失自动下载并缓存。首页无刷新按钮，分类和详情保留手动刷新用于更新已有资料。

参考 my-tv 的 OkHttp API 网络层及 Media3 播放方式，保留本项目 Media3 1.8.0 和 FFmpeg 音频扩展。使用 /api/auth/login、/api/fs/list、/api/fs/get 与 fs/get 返回的 raw_url 下载，移除 Ghosten 运行依赖。Token 仅驻留内存，401 重新登录一次，资料和视频下载不发送登录密码或 API token。

使用独立的数据文件，从空白配置开始，不读取或迁移旧版本数据。请添加 OpenList 连接；服务部署在子路径时填写包含子路径的首页地址。

构建仍仅在天翼云 server_id=19 /srv/davplayer，使用现有签名和 Docker 脚本。尚需用户手动验证真实播放。

播放核心仅在设置选择：ExoPlayer（默认，Media3 1.8.0 + FFmpeg 音频扩展）或系统 MediaPlayer。播放器不显示内核名称或切换入口；音轨偏好按核心分别保存。MediaPlayer 的格式和音轨支持取决于设备，不提供下载速率。不会因错误自动切换核心。

构建、验证和当前验收边界见 [docs/verification.md](docs/verification.md)，播放生命周期见 [docs/playback.md](docs/playback.md)，资料规则见 [docs/cache.md](docs/cache.md)。FFmpeg 源码与重建说明见 [docs/media3-ffmpeg.md](docs/media3-ffmpeg.md)。

项目只提交当前应用源码、测试、构建脚本、图标源文件、NFO 示例及许可声明；签名、APK、缓存、源码快照和本地诊断材料由 .gitignore 排除。

横屏页面与弹窗采用沉浸式全屏，详情海报和剧集卡片使用紧凑横屏布局。

浏览目录与海报/NFO 共用串行队列，同一页面加载内的相同目录请求合并。首页/分类在配置与记录未变化的 30 秒内返回复用已显示列表；不增加持久目录缓存。详情到播放复用两分钟内、同账号且包含当前剧集的一次性选集快照；恢复入口缺少快照时先读选集，再排空队列并开始视频。

自动海报保持内存/磁盘缓存优先，可见稳定 180ms 后读取缺失资料，离屏 250ms 后取消，无人订阅才取消合并请求。缺失/失败冷却 30 秒；429/503 根据 Retry-After 秒数（至少 30 秒、最多一天）暂停自动海报；不轮询重试，手动刷新绕过自动冷却。分类手动刷新复用本次作品列表。

API 和资料下载共用无默认认证的 OkHttp 连接池，移除强制 Connection: close；认证仅在 API 请求添加。取消和超时继续关闭实际请求，视频传输保持现有实现。播放交接时输出累计请求种类/状态/数量、连接次数、读取字节及耗时，不输出地址、签名或账号。读取字节并非链路流量，静态验证不代表降速问题已解决。

API 18 兼容包：`./build-android-docker.sh -Papi18=true testDebugUnitTest lintPreview assemblePreview`，校验 `./verify-apk-docker.sh app/build/outputs/apk/preview/app-preview.apk 18`。MediaPlayer / ExoPlayer（Media3 1.2.1）双核参照 my-tv；含单独编译的 FFmpeg 软件音频扩展。默认无参数构建仍为 minSdk23 / Media3 1.8.0 + FFmpeg。两者包名、签名和存储一致，覆盖安装保留现有数据。
