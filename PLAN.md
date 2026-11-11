# GlassMail Android 完整实施方案

> 目标：制作一款原创、可公开发布的 Android 邮件客户端，以 iOS 26 Mail 的层次、动效和半透明玻璃观感为设计参考，但不复制 Apple 商标、图标、代码或专有素材。

## 1. 产品范围

### 1.1 邮件核心
- 多账户：Gmail、Outlook/Office 365、Yahoo、iCloud、自定义 IMAP/SMTP。
- 认证：密码/应用专用密码、手动 OAuth2/XOAUTH2 令牌；不在仓库内置第三方 OAuth 私钥。
- IMAP：邮箱发现、收件同步、文件夹、已读/未读、星标、归档、垃圾箱、删除、移动、垃圾邮件。
- SMTP：新邮件、回复、全部回复、转发、抄送/密送、附件、签名。
- 本地离线缓存、全文搜索、草稿、发件箱、失败重试、定时后台同步。
- 会话视图、统一收件箱、未读/星标/VIP/附件等智能邮箱。
- Primary/Transactions/Updates/Promotions 分类；分类为透明的本地规则，可手动纠正。
- 下拉刷新、滑动操作、批量选择、撤销发送窗口、定时发送入口。

### 1.2 界面
- 欢迎页、服务商选择、自动/手动账户配置、连接测试。
- 邮箱主页、大标题、分类胶囊、邮件列表、搜索与筛选。
- 侧边邮箱抽屉、账户切换、文件夹管理。
- 邮件详情/会话、HTML 正文安全渲染、附件区、回复操作栏。
- 撰写页、收件人编辑、附件选择、发送计划、草稿处理。
- 设置：账户、同步频率、滑动动作、外观、隐私、签名、关于。
- 手机/平板自适应、深色模式、动态字体、TalkBack 语义、减少动画兼容。

### 1.3 安全与隐私
- 凭据使用 Android Keystore + AES-GCM 加密，本地数据库不保存明文密码。
- HTML 邮件禁用 JavaScript、文件访问和跨域能力；默认阻止远程图片。
- TLS 默认开启；仅手动高级配置可改变端口/安全方式。
- 无广告、无遥测、无中转服务器；邮件直接在设备与用户的邮件服务器间传输。
- 日志不得记录密码、令牌、完整邮件正文。

## 2. 技术架构

- Kotlin、Jetpack Compose、Material 3 基础组件 + 自研 Glass 视觉组件。
- 单 Activity、Navigation Compose、MVVM、StateFlow、Coroutines。
- SQLiteOpenHelper 离线数据库，避免注解处理器导致的 CI/版本耦合。
- Android Keystore 凭据库。
- Android JavaMail（IMAP/SMTP/MIME）。
- WorkManager 后台同步与待发送任务，系统通知提示新邮件。
- 分层：`data/model`、`data/local`、`data/mail`、`data/security`、`sync`、`ui`。

## 3. 数据与同步

1. 账户连接测试成功后保存非敏感配置，密钥单独进入 Keystore。
2. 首次同步枚举服务端文件夹，每个文件夹抓取最近邮件。
3. 以 `account + folder + UID` 幂等更新本地数据库。
4. 前台按需同步；后台由 WorkManager 周期执行。
5. 用户操作先乐观更新 UI，再提交 IMAP；失败时恢复并提示。
6. SMTP 邮件进入本地发件箱，发送成功后清理；失败保留错误并重试。
7. 删除账户时同时清除邮件缓存、附件缓存和 Keystore 凭据。

## 4. 交付流程

1. 建立完整方案与范围（本文件）。
2. 初始化 Android 工程、原创 UI 设计系统和本地数据层。
3. 实现 IMAP/SMTP、同步、后台任务与通知。
4. 完成全部主要页面、交互、演示模式和中英文资源。
5. 添加单元测试、Lint、隐私说明、许可证、贡献说明。
6. 生成独立 PKCS12 发布签名；私钥只放 GitHub Actions Secrets。
7. 创建 GitHub 公开仓库并推送源码。
8. Actions 执行测试、Lint、签名 Release 构建、验签、SHA-256。
9. 创建 `v1.0.0` GitHub Release 并上传签名 APK 与校验文件。
10. 核验 Release 下载链接、包名、版本、签名证书和工作流状态。

## 5. 验收标准

- 干净环境可由 `./gradlew test lint assembleRelease` 构建。
- 未配置发布密钥时只能产出未签名 Release，绝不回退到 debug key。
- CI 发布的 APK 能通过 `apksigner verify --verbose --print-certs`。
- 无账户时能完成引导或进入演示模式；有账户时能完成基础 IMAP/SMTP 闭环。
- 关键状态均有加载、空数据、错误和离线反馈。
- 仓库中不存在密码、OAuth 私钥、签名私钥或用户邮件数据。

## 6. 边界说明

- Apple Intelligence、系统级“隐藏邮件地址”、Apple 私有推送和 Apple 账户内部 API 无法合法移植；本项目提供本地摘要/分类、标准 IMAP IDLE/轮询及服务商标准协议替代。
- Gmail/微软一键 OAuth 需要仓库所有者分别在 Google Cloud/Azure 注册客户端；首版提供应用专用密码与可配置 XOAUTH2，仓库不共享秘密。
- “视觉参考”不等于逐像素复制；项目保持原创命名、图标与资产，以便公开分发。