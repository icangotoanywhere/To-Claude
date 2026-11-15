# To Claude 开发约定

安卓小工具：打开 Claude 前检查网络，确认后拉起 `com.anthropic.claude`。界面文字和文档用中文。从 EasyClash 的「打开 Claude」功能独立出来，不依赖 EasyClash。

- 发布只在云端：改 `app/build.gradle.kts` 版本号推 `main`，CI（`.github/workflows/build.yml`）签名、核对证书指纹后自动发 Release（`ToClaude-v<版本>.apk` 和固定名 `ToClaude.apk`）。
- 签名与 EasyClash 同一把固定密钥，证书 SHA-256 `ada95c194e88ce73bf7bea727b02a8231496fa96ec87935e50c3860321b5a861`，不能换。
- 出口查询 ipwho.is 优先、ip-api.com 兜底。**IP 库的「所在地」不可靠**（实测过一个日本节点被 ip-api.com 报成香港），所以另做了延迟实测（`NetCheck.nearest`，经当前网络测亚马逊云各地 DynamoDB 的 ping 地址，首个请求建连接不计，再测两次取最小）。
- 本 App 的图标是原创的（箭头穿进圆环），**不要画或打包 Claude 的标志，App 名也不要叫 Claude**（维护者同意的合规做法）。想要「看起来是 Claude」的入口，用 `ClaudeShortcut`：桌面快捷方式的图标在运行时取手机上已安装的 Claude App 自己的图标，名字「Claude」，点它先进检查页；不要自己加角标（桌面会自动叠 To Claude 的小标）。没装 Claude 时不创建。
- 「Claude 打开时先检查网络」（v1.0.2）：无障碍服务 `GateService`，只收 `typeWindowStateChanged`、`canRetrieveWindowContent=false`，只看包名；要收所有 App 的事件才能分辨「从别处切进 Claude」和「Claude 内部换页」（下拉通知栏 systemui、输入法不算离开）。触发后带 `EXTRA_INTERCEPT` 启动 `MainActivity`：「打开」= 关掉卡片露出 Claude，「取消」/返回键 = 回桌面。`GatePrefs`：点「打开」时记时间和网络指纹，间隔（0/5/10/30 分钟，默认 10，「每次」也留 15 秒防连续触发）内且网络没变就不再弹。只能「盖住」不能「拦住」：卡片弹出前 Claude 已在前台。服务运行时还监听 Claude 升级，刷新桌面「Claude」图标。
- 不收集、不上传任何信息。
