# OpenList 分支（0.4.2-dual-player）

在设置中添加 OpenList 服务连接（填写服务首页地址和账号密码），测试并保存后添加文件夹作为分类。优先读取作品目录内 movie.nfo 和 poster.jpg；保留分季、自然集序、进度、音轨。首页进入时自动获取列表，首页与分类的可见海报优先读取缓存，缺失自动下载并缓存。首页无刷新按钮，分类和详情保留手动刷新用于更新已有资料。

参考 my-tv 的 OkHttp API 网络层及 Media3 播放方式，保留本项目 Media3 1.8.0 和 FFmpeg 音频扩展。使用 /api/auth/login、/api/fs/list、/api/fs/get 与 fs/get 返回的 raw_url 下载，移除 Ghosten 运行依赖。Token 仅驻留内存，401 重新登录一次，资料和视频下载不发送登录密码或 API token。

使用独立的数据文件，从空白配置开始，不读取或迁移旧版本数据。请添加 OpenList 连接；服务部署在子路径时填写包含子路径的首页地址。

构建仍仅在天翼云 server_id=19 /srv/davplayer，使用现有签名和 Docker 脚本。尚需用户手动验证真实播放。

播放核心可在设置和播放页切换：ExoPlayer（默认，Media3 1.8.0 + FFmpeg 音频扩展）或系统 MediaPlayer。切换释放旧核心后接续进度，保留播放/暂停意图；音轨偏好按核心分别保存。MediaPlayer 的格式和音轨支持取决于设备，不提供下载速率。不会因错误自动切换核心。

构建、验证和当前验收边界见 [docs/verification.md](docs/verification.md)，播放生命周期见 [docs/playback.md](docs/playback.md)，资料规则见 [docs/cache.md](docs/cache.md)。FFmpeg 源码与重建说明见 [docs/media3-ffmpeg.md](docs/media3-ffmpeg.md)。

项目只提交当前应用源码、测试、构建脚本、图标源文件、NFO 示例及许可声明；签名、APK、缓存、源码快照和本地诊断材料由 .gitignore 排除。
