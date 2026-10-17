# 构建与验收

0.4.12 / 70 基线：仅 ExoPlayer（Media3 1.2.1）+ 官方 FFmpeg 音频扩展，统一 minSdk18，arm64-v8a / armeabi-v7a。compileSdk36、targetSdk33、Kotlin2.2.0、AGP8.9.2、Gradle8.11.1、JDK17 / Java11。

仅 server_id=19 /srv/davplayer 使用固定 Docker 镜像及既有 signing、Gradle/NDK/FFmpeg 缓存。同步前备份并替换 app/src，不上传凭据、签名密钥、缓存或 .codex。

```sh
./build-android-docker.sh testDebugUnitTest lintPreview assemblePreview
./verify-apk-docker.sh app/build/outputs/apk/preview/app-preview.apk
```

仅一套构建、一份 FFmpeg 模块；NDK r23c 23.2.8568313、FFmpeg6.0.1，ARMv7 API18 / ARM64 API21。首次 NDK 安装与重建说明见 media3-ffmpeg.md。

清理了系统播放核心、核心枚举和选择设置、多核心抽象/测试、重复现代版构建、HLS 依赖、旧播放诊断日志和 API 统计计数（现有可选有界采样默认关闭）。进度、选集、音轨、缓存与真实请求取消逻辑保留。缓存模型不再保存未使用的校验字段；维持磁盘头部读取，现有海报可直接复用。

测试覆盖登录与令牌重试、分页与特殊路径、认证隔离、连接复用、实际请求取消/排空、列表顺序、NFO、海报复用与原子缓存、限流冷却、音轨辅助、速率计量和系统栏兼容。静态与构建结果不替代手机上的解码、跳转、切集、前后台及 Android4.3 真机验收。

安装不清配置、缓存或观看记录，不修改 30 秒息屏设置；安装、操作手机和推送/发布分别需要明确授权。

## 0.4.12 验证范围

本轮处理详情缓存与目录顺序、页面返回复用与再次选集交接、完成重播、24 集分页、设置中断恢复、服务替换与缓存统计/手动清理，以及首帧和缓冲等待。构建未传 playbackDiagnostics，正式包采样默认关闭；API18 / Media3 1.2.1 / FFmpeg 及官方视频 HTTP 数据源保持不变。

指定服务器最终构建成功：70 项测试，0 失败/错误/跳过；Lint 0 Error/Fatal、9 Warning（8 条原有提示及新增缓存统计文案的 SetTextI18n）。v1/v2 签名及两 ABI FFmpeg 的 16KiB LOAD/RELRO 校验通过；PLAYBACK_DIAGNOSTICS=false。APK 4,970,607 bytes，SHA256 `bf02edf07134ff3b5ddd7df8c3a0fac6ac14f77206a38ee8175d94a0e5e97407`。尚未安装本轮包，设置中断、清缓存、服务替换、首帧超时及大量剧集的实际交互需手动验收；Android4.3 真机兼容尚未验证。上一诊断版手机播放成功不等于本轮功能已验收。

## 0.4.13 首页排版

版本 71，查看全部改为海报同尺寸卡片，共用图片与标题区尺寸、焦点背景；去掉首页分类标题、分类页以及刷新完成提示的作品统计数量，刷新过程仍显示进度。仅调整 UI，未新增测试；本地 XML 属性检查确认卡片高度、标题高度、留白和焦点属性一致。指定服务器 lintPreview、assemblePreview 与 verify-apk-docker.sh 全部通过；Lint 0 Error/Fatal、9 条既有 Warning。APK 4,973,552 bytes；SHA256 `2e577ef6e215d1cbc7557bddfedef3af7b3ff3ca80012fe3a117a1f34f003d6d`。尚未安装本轮包，未进行真机视觉验收。

## 0.4.14 横屏详情与声道入口

版本 72。横屏详情采用左侧 132×198dp 完整海报，右侧片名、资料、三行简介、播放及展开，下方选集；补齐 layout-w600dp-land，竖屏保持现状。播放器声道入口移到进度条右侧，进度行 48dp 保证触控尺寸，保留原先多音轨显示、单音轨/无音轨隐藏的条件。XML 检查确认横屏绑定字段完整、简介与海报同排、选集在下方，以及声道入口仅位于进度行；布局调整未新增单元测试。指定服务器 lintPreview、assemblePreview 及 APK 校验通过；Lint 0 Error/Fatal、13 Warning，其中新增 4 条为两套横屏布局中播放/展开按钮的 ButtonStyle 建议（保留应用的珊瑚橙主操作和焦点背景）。APK 4,975,095 bytes；SHA256 `83d385e4bdd320b1ba1577ead09cae81efe60de0e3afd935be624f024940cc96`。未安装、未做真机视觉验收。

## 0.4.15 电视式详情

版本 73。电影按可用高度放大 2:3 海报（最高 360dp），隐藏无剧集的选集区；剧集保持紧凑海报并保留下方选集。播放按钮限宽 240dp，并依次与简介、查看简介分行；宽屏横屏使用同一布局。竖屏保持现状。本地 XML 检查确认两种横屏资源的播放/展开位于垂直容器且无横向权重，指定服务器 lintPreview、assemblePreview 及 APK 校验通过；Lint 0 Error/Fatal、9 条既有 Warning。APK 4,975,657 bytes；SHA256 `e2d798724b5497132fa1c014d5ecd742732da5ad77ca9799165afb44c75c1a89`。本轮只调整布局与展示尺寸，不新增业务测试；未安装、未进行电视或手机视觉验收。

## 0.4.16 内联简介入口

版本 74。横屏简介取消固定三/四行限制，布局后按海报底部的实际剩余高度与字体行高自适应可见行数（最多 20 行），不拉伸短简介。全尺寸详情移除独立展开按钮，InlinePlotView 根据实际字体、宽度及最大行数判断截断，只在截断时于最后一行预留“…【查看简介】”；与正文同字号/字体，入口使用强调色。完整简介不显示入口，截断文本支持触摸和遥控器打开原始全文，关闭后恢复焦点。缓存更新、尺寸与字号变化会重新排版，长 NFO 使用有界预览，保留 API18 StaticLayout 路径。新增文本空间预留、emoji 边界、末尾换行测试；指定服务器 testDebugUnitTest、lintPreview、assemblePreview 与 APK 校验通过；73 项测试，0 失败/错误/跳过，Lint 0 Error/Fatal、9 条既有 Warning。编译产物检查确认未引入 LineBreaker 运行时类引用，保留 API18 兼容分支。APK 4,978,030 bytes；SHA256 `597de6aa0f37cea1cdda919efbd8ffb6e98ecfff2af9dbf84663ab15f8495f47`。未安装、未进行真机字体或遥控器验收。

## 0.4.17 简介底部校正

版本 75。根据用户截图修正简介超过海报底部的问题：海报和简介转换到同一祖先坐标系计算空间，使用 TextView 实际 Layout 的逐行底部校正，而非仅依赖样本文字估算。每次测量只单向减少行数，空间变化时重新计算，避免反复增减；继续保留末行内联入口。指定服务器 testDebugUnitTest、lintPreview、assemblePreview 与 APK 校验通过；76 项测试，0 失败/错误/跳过，Lint 0 Error/Fatal、9 条既有 Warning。APK 4,978,173 bytes；SHA256 `1c1c34f11a701e9c5bc3108b55030f0f5073f010a89bdb4aa9f07db17b9d30d1`。尚未安装或对同一电视/字体实测，不把编译通过当成截图问题已验收。

## 0.4.18 剧集继续播放按钮

版本 76。横屏剧集按钮从固定 240dp 改为最小 320dp，并按文字宽度扩展至右侧可用空间，完整容纳集数与播放时间；电影保持 240dp，竖屏不变。指定服务器 lintPreview、assemblePreview 与 APK 校验通过；Lint 无 Error/Fatal，报告 10 条 Warning（含同步归档附带的 macOS 图标元数据重复提示；附带元数据已移除）。本次仅调整按钮尺寸，不新增业务测试。APK 4,977,999 bytes；SHA256 `86a24eb57fb44b9aa8fc9960284308c5351de41806d4ff23223cbed7e7f1db81`。未安装、未进行真机视觉验收。

## 0.4.19 分季资料切换

版本 77。季度切换除剧集列表外，同时切换该季 movie.nfo/poster.jpg；目录列表中缺少某项文件则分别回退作品根目录对应文件。复用详情已有分季目录读取，不增加扫描或预取其他季。保留缓存优先和手动刷新，换季取消旧任务并以代次隔离结果；季资料仅影响展示，不替换播放目录交接。新增分季路径、单项回退、同名目录排除、扫描次数与播放交接回归。指定服务器 testDebugUnitTest、lintPreview、assemblePreview 和 APK 校验通过；77 项测试，0 失败/错误/跳过，Lint 0 Error/Fatal、9 条既有 Warning。APK 4,978,605 bytes；SHA256 `71b50324abc43323f70e25c8882234facac567b0badcc2f9ea69f8b9f2fbb753`。未安装、未进行真机多季切换验收。

## 0.4.20 丰富资料详情

版本 78。NFO 新增原名、导演、编剧、国家地区、制作公司、演员角色、标语、分级；展示已有首映日期。空值整项隐藏，原名与片名重复时省略；无简介不显示标题或占位文字。新增资料位于下方，不压缩海报旁摘要，宽横屏双列、其他屏幕单列，演员和标语整行。用户明确要求清空旧资料缓存：启用 v2 资源目录，后台删除 v1 海报/NFO，旧 NFO 编码不兼容，不清理配置、分类或观看记录。指定服务器 testDebugUnitTest、lintPreview、assemblePreview 与 APK 校验通过；81 项测试，0 失败/错误/跳过，Lint 0 Error/Fatal、9 条既有 Warning。验证四种详情布局均包含可隐藏的资料区及简介标题。APK 4,984,925 bytes；SHA256 `e5ac652a3ee3b4e06bfe6ae71b1252da9c4a3fb8c99dc93d5f6a95d4808eba7f`。未安装或进行真机视觉验收，手机旧缓存尚未清理（新版首次启动执行）。

0.4.20 后续设备验收：已覆盖安装至 PJZ110（2e9f51e3），版本 78 / 0.4.20，安装返回 Success，息屏 30000ms。用户反馈详情展示无明显问题、当前可用；仍报告视频偶发读取低速，原因未确认。暂停的 0.4.21 连接池实验不纳入主分支。
