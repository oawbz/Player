# 云影 · Android WebDAV 播放器

轻量原生 Android 播放器，最低 Android 4.3 / API 18，同时面向触屏和电视遥控器。首页上方显示继续播放，下面按分类展示海报；分类进入完整海报网格。

## 首次使用

1. 在设置中添加 WebDAV 连接，填写最终服务地址，明确选择“匿名访问”或“用户名密码”，测试成功后保存。匿名访问不发送 Authorization，也不保留账号密码；用户名密码模式使用 HTTP Basic，用户名必填，密码按服务要求可为空。旧连接自动按原有账号密码迁移模式。
2. 添加分类，选择连接，在远程目录选择器里逐层进入目录，然后选择当前目录并填写分类名称。
3. 分类目录下一层的每个文件夹作为一部作品，文件夹名称即作品名称。
4. 点击海报进入作品详情，选择视频或季/集播放。首页的继续播放直接恢复对应视频。

支持多个 WebDAV 连接。编辑连接时如果根地址改变，保存为新连接，保留旧连接和分类，避免把旧分类误指向另一台服务器。更换分类目录可在设置中操作。删除分类不删除服务器文件，也不清除播放记录。

## 目录约定

```text
电影/                       # 分类目录
└── 示例影片/                # 作品名称
    ├── poster.jpg           # 封面
    ├── description.txt      # UTF-8 简介，保留换行，可有 BOM
    └── 示例影片.mkv
电视剧/
└── 示例剧集/
    ├── cover.png
    ├── description.txt
    ├── 第1季/
    │   ├── 第01集.mp4
    │   └── 第02集.mp4
    └── 第2季/
        └── 第01集.mp4
```

- 封面优先级：`poster` → `cover` → `folder` → 按名称排序的第一张图片。支持 JPG/JPEG/PNG/WebP；没有封面时使用占位。
- 简介仅读取作品根目录的 `description.txt`，UTF-8，最多 64 KiB。文件可选，读取失败不会阻止播放；按纯文本显示，不执行 HTML 或链接。
- 作品根目录只有一个视频即单影片；多个视频按自然文件名排序，例如第 2 集在第 10 集前面。
- 作品的直接子目录中有视频时，该目录作为一季；无视频的图片或字幕目录不作为季。不向更深层递归。
- 根目录视频和分季共存时，根目录视频显示为“未分季”。最多读取 100 个直接子目录。
- 按扩展名识别候选视频不代表设备一定能解码。具体格式、音轨、封装兼容性取决于选中的核心和设备。

示例简介位于 [examples/media-library/电影/示例影片/description.txt](examples/media-library/电影/示例影片/description.txt)。示例中没有视频、真实账号或远程配置。

## 播放与记录

默认系统 MediaPlayer，可在设置或播放器里手动切换 ExoPlayer，并保存偏好。切换时释放旧实例并恢复进度，不自动换核。播放结束停在当前视频，不自动播放下一集。

支持暂停、进度条跳转和前后 10 秒。所有按钮支持遥控器焦点及触屏；硬件媒体键支持播放暂停、快退快进。退出、进入后台及 Surface 销毁时保存进度并释放实例；恢复前台后重建，恢复此前的播放/暂停意图。旧实例回调通过代次和实例隔离。

播放中每 5 秒保存记录，暂停和退出补存。最多保留最近 100 部作品，每部作品保留最近观看的视频。记录关联连接和视频路径；文件移动/删除后提示错误，不自动清空记录。

## 请求、缓存与凭据边界

目录使用 `PROPFIND Depth: 1`，命名空间与成功 `propstat` 解析；XML 禁用 DTD/实体，响应最多 2 MiB / 5000 条 / 32 层。路径使用 HttpUrl 编解码，拒绝跨源、超出连接根目录、签名查询参数和编码分隔符。账号密码单独填写，不嵌入 URL。

目录、简介、封面和 ExoPlayer 的 OkHttp 请求使用一致的认证；应用控制的 HTTP 请求拒绝重定向，要求配置最终地址。系统 MediaPlayer 使用公开 API 传入媒体 URL 与认证头，由系统网络栈负责实际传输、Range 和重定向处理；该行为必须在实际服务/旧设备上验证。

所有网络和 XML 解析在工作线程执行；页面重载或销毁取消请求，丢弃过期结果。首页每分类预览最多 12 部，封面只对可见项加载，完整分类使用原生 GridView 复用视图。目录内存缓存最多 128 项、有效 60 秒；封面文件最多 4 MiB，缩略图内存预算 8 MiB、磁盘预算 32 MiB、有效 24 小时。刷新清除列表与封面缓存。视频不写入磁盘缓存。

账号密码保存在应用私有 SharedPreferences，禁止系统备份；设置页和凭据对话框禁止截图。此存储不抵抗 root、设备控制或已解锁设备上的应用调试。HTTP 会明文传输账号/视频；HTTPS 不关闭证书和主机名校验。旧 Android 的 TLS 与证书链兼容性需实机验证。

## 构建

只在天翼云（SPanel server_id=19）的独立目录 `/srv/davplayer` 构建，不使用本机 Android SDK/NDK，不复用 my-tv 目录。

固定组合：AGP 8.3.2 / Gradle 8.4 / Kotlin 1.9.22 / JDK 17，compileSdk 34、targetSdk 33、minSdk 18、Java 字节码目标 8。Gradle 分发包固定 SHA256。基础 SDK 镜像固定为：

```text
ghcr.io/cirruslabs/android-sdk@sha256:8f3fb92fc5c21ee8b9cfe560947ecd0ded39c1b0df7d20d937b8217c8c57c49b
```

基础镜像实际包含 JDK 21，项目通过 `docker/Dockerfile` 加入固定的 Eclipse Temurin 17.0.20.1+1，下载包 SHA256 固定为 `3808d1d15e3ec6bd5b84057fb5d84c33d8a1536a258146bcea2e603fc726e08e`。首轮构建自动生成项目专用镜像 `davplayer-android:34-temurin17.0.20.1-1`，不改变其他项目镜像或服务。来源为 [Temurin 官方发布](https://github.com/adoptium/temurin17-binaries/releases/tag/jdk-17.0.20.1%2B1)。

Media3 固定 1.2.1，仅 ExoPlayer 文件播放；项目自行实现小型文件数据源，使用严格固定的 OkHttp 3.12.13。官方 media3-datasource-okhttp 1.2.1 发布 AAR 的 minSdk 为 21，因此不引入该模块。数据源传递认证，验证 Range 206 与 Content-Range 起点，拒绝跳转时返回完整 200 文件。API 18 运行时 API 与完整依赖图仍须核验，不能靠版本号或 overrideLibrary 绕过最低要求。MultiDexApplication 已初始化，debug 启用 v1/v2 签名。

源码发布到公共仓库 [oawbz/Player](https://github.com/oawbz/Player)，本地提交并推送后，天翼云使用匿名 HTTPS 拉取，无需在编译机器放置 GitHub 私钥。首次创建空目录时：

```sh
git clone https://github.com/oawbz/Player.git /srv/davplayer
cd /srv/davplayer
```

签名密钥同时保存在本地 `signing/debug.keystore`（目录权限 700、文件权限 600，Git 忽略）和远程 signing 目录；使用同一密钥继续签名，禁止替换已存在且不同的密钥。

首次构建前须通过安全文件传输恢复本地签名到远程 signing 目录；缺失时脚本停止，避免生成新密钥导致已有应用无法覆盖升级。

后续在远程目录执行 `git pull --ff-only origin main`。仓库排除 `.codex`、签名密钥、缓存、APK 和本机环境配置；保留远程 `signing/debug.keystore` 与 `cache/gradle`。然后：

```sh
./build-android-docker.sh testDebugUnitTest lintDebug assembleDebug
./verify-apk-docker.sh
```

使用项目独立 `/srv/davplayer/cache/gradle` 缓存，使用镜像内 SDK，构建容器使用项目范围的 host 网络。脚本验证 JDK 17 与 SDK 34；若镜像环境不匹配应停止核查，不随意升级工具链。连接凭据不属于仓库内容。

工具链参考：[AGP 8.3 兼容要求](https://developer.android.com/build/releases/agp-8-3-0-release-notes)、[Media3 1.2.1 固定常量](https://github.com/androidx/media/blob/1.2.1/constants.gradle)。官方 Media3 声明自身最低 SDK 16，应用最低 SDK 仍设为 18，完整传递依赖与设备运行必须另验。

## 验证状态

见 [docs/verification.md](docs/verification.md)。构建成功、安装成功、目录加载成功和实际播放成功应分别记录；没有实机证据不能称为完整验收。
