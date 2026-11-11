# 澄邮 · GlassMail

一款原创 Android 邮件客户端，以清澈、分层、轻盈的移动邮件体验为目标。界面参考现代玻璃质感设计语言；项目不包含 Apple 的代码、商标或专有素材。

## 功能

- Gmail / Outlook / Yahoo / iCloud / QQ / 网易 / 自定义 IMAP + SMTP
- 多账户统一收件箱、文件夹、会话、分类、搜索
- 已读、星标、归档、移动、删除、回复、转发、附件
- 离线缓存、后台同步、新邮件通知、延迟发送
- Android Keystore 加密凭据；默认阻止 HTML 远程图片与脚本
- 日间/夜间模式、玻璃质感 Compose 界面、演示模式

## 构建

```bash
./gradlew testDebugUnitTest lintDebug assembleDebug
```

Release 签名使用 `app/glassmail-release.p12`（不提交）以及环境变量：

- `SIGNING_STORE_PASSWORD`
- `SIGNING_KEY_PASSWORD`
- `SIGNING_KEY_ALIAS`

GitHub Actions 从同名 Secrets 恢复密钥并发布签名 APK。

## 账户提示

多数服务商已禁止普通账户密码登录第三方客户端。请启用 IMAP/SMTP，并使用“应用专用密码/授权码”。XOAUTH2 可手动填写访问令牌；一键 OAuth 需要仓库维护者自行注册 Google/Microsoft 客户端，公开仓库不会内置客户端私钥。

## 隐私

无广告、无遥测、无代理服务器。详情见 [PRIVACY.md](PRIVACY.md)。

## 设计与实现计划

见 [PLAN.md](PLAN.md)。

## 许可证

Apache-2.0
