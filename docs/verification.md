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

## 0.4.4-request-budget / 62 请求收紧

- 指定服务器 Docker：testDebugUnitTest、lintPreview、assemblePreview、verify-apk-docker.sh 通过。55 项测试，0 失败/错误/跳过；Lint 0 Error/Fatal、80 Warning。
- 新增覆盖并发登录去重、单批目录去重、共享连接及认证隔离、真实请求取消、队列嵌套/串行、选集单次交接与过期、失败与限流冷却。
- 初次构建发现 MockWebServer setter 调用写法及统计 AtomicLong API 24 限制，已修复后重跑通过；未屏蔽 Lint。
- APK SHA256：f5a79219cdc8b14e623100fa2ae81d726915e07d2083c338509637d73e4ee32c。签名/ZIP/两 ABI FFmpeg 对齐校验通过。
- 保留前次横屏适配；不修改视频传输及核心选择。未提交、推送、安装或操作手机；是否改善首次加载后的降速仍待手动验收。

## 0.4.5-api18 / 63 + FFmpeg

- 参考 my-tv 的 MediaPlayer / Media3 ExoPlayer 1.2.1 双核，单独 `-Papi18=true` 构建，minSdk18；OpenList、缓存和播放进度继续使用现有实现。补齐多 Dex、旧版系统栏/图片统计、音频初始化与音轨 API 兼容。系统旧 API 不提供已选音轨查询，用户选轨成功后记录本次实例的选择，不伪造默认选中音轨。
- 用户明确选择保留 FFmpeg。新增官方 Media3 1.2.1 扩展源码与文件哈希；NDK r23c 23.2.8568313 安装于独立项目缓存，FFmpeg 6.0.1 音频模块独立构建，禁止未解析符号，静态 C++。ELF Android ident：armeabi-v7a API18、arm64-v8a API21。AAC/AC3/EAC3/DTS/FLAC/MP3/ALAC/Opus/Vorbis/MLP/TrueHD 解码器配置启用。
- 指定 server_id=19 Docker 完成 testDebugUnitTest、lintPreview、assemblePreview、verify-apk-docker.sh；56 项测试，0 失败/错误/跳过；API18 Lint 0 Error/Fatal、82 Warning。签名 v1/v2、ZIP 与两 ABI 16KiB LOAD/RELRO 对齐通过。
- API18 APK SHA256：07b15ed14a3dab92d1fe4fdbf73ab92ba242dff82676de425b5bb4b9c9186873。
- 现代版回归构建通过（Media3 1.8.0 + 现有 FFmpeg），56 项测试通过，Lint 0 Error/Fatal、88 Warning；APK SHA256：6811a077edfb8863ee1053d7c1b67a48a4e445192955eabfd55352a1cb28b649。
- 已修复兼容扫描发现的 Locale API21 调用、旧模块注解依赖缺失与重复类问题，未关闭 Lint。FFmpeg 官方源码未修改。
- 未提交、推送或安装，未操控设备；静态链接与 APK 验证不代替 Android 4.3 设备动态加载、实际解码、音轨/进度与遥控器验收。FFmpeg 仅扩充 ExoPlayer 音频，不能保证所有音视频格式或设备可播放。

## 0.4.6-api18 / 64 启动崩溃与 Lint 收敛

- 手机 2e9f51e3 的 crash buffer 记录：MainActivity 启动时，BaseActivity.onCreate 调用 SystemBars.apply，PhoneWindow.getInsetsController 在 DecorView 未创建时发生 NullPointerException。与播放核心、FFmpeg 和 API18 解码无关，启动尚未进入播放。
- 修正现代系统栏路径：先调用 window.decorView 创建 DecorView，再获取 InsetsController。新增模拟 PhoneWindow 的初始化顺序回归测试，覆盖竖/横屏请求及旧 API 系统栏分支；不吞异常或捕获后忽略。
- API18 构建、签名、ZIP、两 ABI 原生库对齐通过；58 项测试，0 失败/错误/跳过，Lint 0 Error/Fatal。警告 82 -> 8，未新增 suppression、baseline 或禁用规则。
- 修复：XML/动态文案资源、重复图标（保留原图并复用引用）、单色图标、TV banner 密度位置、RTL、横屏 baseline 测量、重复背景绘制、明确备份排除规则、可聚焦的播放控制切换区域。单色图标保留既有 SVG 造型，Android 8 AdaptiveIconDrawable 会忽略未知的 monochrome 子标签，上游依据：https://github.com/aosp-mirror/platform_frameworks_base/blob/android-8.0.0_r1/graphics/java/android/graphics/drawable/AdaptiveIconDrawable.java 。
- 保留 8 条：OldTargetApi 1；固定旧 Media3 更新提示 2；旧系统忽略 banner / usesCleartextTraffic 属性 2（现代 TV/HTTP 访问仍需）；固定横屏 1；无独立动作的顶部/底部触摸拦截栏 KeyboardInaccessibleWidget 2（不额外制造遥控器焦点停靠点）。
- API18 APK SHA256：9f3723439d080a2b6e1e9ecd11638014e1e206c2befa0ff69e3a9131d238e114；5,021,900 bytes。
- 用户明确授权后 adb -s 2e9f51e3 install -r 成功，已核对 versionCode64 / 0.4.6-api18，保留配置/缓存/历史，screen_off_timeout=30000。没有自动启动或操作应用；手机是否仍闪退及真实播放待用户手动验收。

- 现代默认构建也完成 testDebugUnitTest / lintPreview / assemblePreview 和 APK 校验，58 项测试通过；现代 APK SHA256：9369a5c4211a376e85ced847d94672f4900ca976a0ba28de8bd980f4c5a3317f。未安装现代包，手机安装的是上述 API18 修复包。

## 0.4.7-api18 / 65 详情海报复用

- 详情海报成功下载后保留校验阶段产生的 480px 缩略图，原子写盘后发布到列表共用的 12 MiB LRU，不再解码后立即回收/清空。详情只读磁盘缓存也回填列表缩略图。列表绑定直接使用共享 Bitmap；详情缓存完整时不再提交资料刷新。分类批量刷新复用相同发布路径；手动刷新失败保留原有成功缓存。
- 新增跨层回归：详情下载后删除磁盘条目再访问列表，仍直接返回同一缩略图、无新解码/HTTP；详情磁盘命中后同样直接复用；手动刷新替换以及 HTTP503 保留旧图。使用真实资源 HTTP 与磁盘缓存、受控 Bitmap 解码与内存存储；不代表手机 UI 实测。首轮测试抓到首次 put 返回 null 后误移除缓存，改为显式分支后重跑全绿。
- 指定 server_id=19 Docker testDebugUnitTest / lintPreview / assemblePreview / verify-apk-docker.sh 通过。61 项测试，0 失败/错误/跳过；API18 Lint 0 Error/Fatal、8 Warning（既有保留项）。签名、ZIP、两 ABI 16KiB LOAD/RELRO 对齐通过。
- API18 APK：5,022,302 bytes，SHA256 6d73f2c70f9a5ed0465c272aaecadfa08ec88a48e3753f66de8d86e7bc220d54。未安装、未操作手机、未提交或推送。进程重启和内存 LRU 淘汰后仍从磁盘恢复；不承诺无限内存常驻。

- 现代默认构建 lintPreview / assemblePreview / APK 校验也通过；Lint 0 Error/Fatal、14 Warning（原有保留项），本次未重复跑相同单测。现代 APK SHA256：bbed914e102f5f8c874be54b02e7128bf47acdfae41cf0acc44c33862da482f3。

## 0.4.8-api18 / 66 跳转进度与异常结束

- 用户授权 ADB 点击对照：MediaPlayer 拖动后曾出现 `MediaHTTPConnection: unexpected end of stream`、NuCachedSource2 -1010 和 NuPlayer EOS，界面错误显示播放结束/00:00；同文件 ExoPlayer 能显示正常缓冲，用户确认工作正常。不能据此将流中断归因于某一服务器或网络参数。
- 修复应用状态：MediaPlayer seek 串行合并最新目标、核对实际回调位置、30 秒超时、释放隔离迟到结果；提前 EOF 报告可手动重试的中断，保留目标/最后观察到的进度，真正完成后不再被 native 0 覆盖历史。补齐遥控器/无障碍 SeekBar 用户事件。
- 按用户最新要求：MediaPlayer 保留加载转圈，不显示“缓冲中”文字、缓冲秒数或虚假速率；ExoPlayer 的显示与 HTTP 实现保持不变。
- API18 指定 Docker testDebugUnitTest / lintPreview / assemblePreview / verify-apk-docker.sh 全部通过。69 项测试（新增 8 项），0 失败/错误/跳过；Lint 0 Error/Fatal、8 Warning。回归覆盖连续跳转、原生回调仍在旧位置、播放中/跳转中提前 EOF、正常末尾、超时、释放后的截止回调及转圈不会被旧缓冲结束事件关闭。
- API18 APK：5,023,143 bytes；SHA256 `1a162273a8976fd63b858b0439b0d7c09a26b86d55dcb38415787829ad6ccd7e`。v1/v2 签名、ZIP、两 ABI 16KiB LOAD/RELRO 校验通过，本地回传哈希一致。
- 源码和自动测试修复不等于底层 HTTP 中断已消失；新包尚未安装/实播验收。本轮未新增手机操作、未清数据、未提交/推送。保留当前 ExoPlayer 设置。
- 现代默认版同样完成 69 项测试、lintPreview、assemblePreview 和 APK 校验；0 失败/错误/跳过，Lint 0 Error/Fatal、14 条既有 Warning。现代 APK 6,743,174 bytes，SHA256 `9a0e28c6b31894d7c5df8f76026b817074bfa634593dc7f7f633b9b5ea1340a6`，保存在服务器 openlist-upload/davplayer-0.4.8-modern.apk；手机待装包仍为 API18 版。
