# To Claude 开发约定

安卓小工具：打开 Claude 前检查网络，确认后拉起 `com.anthropic.claude`。界面文字和文档用中文。从 EasyClash 的「打开 Claude」功能独立出来，不依赖 EasyClash。

- 发布只在云端：改 `app/build.gradle.kts` 版本号推 `main`，CI（`.github/workflows/build.yml`）签名、核对证书指纹后自动发 Release（`ToClaude-v<版本>.apk` 和固定名 `ToClaude.apk`）。
- 签名与 EasyClash 同一把固定密钥，证书 SHA-256 `ada95c194e88ce73bf7bea727b02a8231496fa96ec87935e50c3860321b5a861`，不能换。
- 出口查询 ipwho.is 优先、ip-api.com 兜底。**IP 库的「所在地」不可靠**（实测过一个日本节点被 ip-api.com 报成香港），所以另做了延迟实测（`NetCheck.nearest`，经当前网络测亚马逊云各地 DynamoDB 的 ping 地址，首个请求建连接不计，再测两次取最小）。
- 图标是原创的（箭头穿进圆环），不要用 Claude 的标志。
- 不收集、不上传任何信息。
