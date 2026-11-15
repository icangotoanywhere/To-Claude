# To Claude

打开 Claude 前先看一眼网络的小工具（安卓）。不依赖任何代理软件，不收集任何信息。

> **已经在用 EasyClash 的，不用装 To Claude。** EasyClash「高级」页的「Claude 专用」已经包含这里的全部功能（桌面「Claude」图标、打开前的网络检查），还能在网络上真正挡住 Claude、确认后才放行。两个都装会弹两次卡片。To Claude 是给用别的代理软件的朋友准备的。

点桌面上的 To Claude 图标，弹出一张卡片：

- **网络**：有没有开 VPN / 代理；
- **出口 IP、IP 库所在地、运营商**：来自 ipwho.is（查不到时用 ip-api.com）；
- **实测最近**：经当前网络测到亚马逊云东京、首尔、香港、新加坡、美国、法兰克福、伦敦机房的往返时间，离哪个最近，节点多半就在那附近。IP 库常按登记信息填写，和实测不一致时以实测为准；
- **Claude 服务器**：连不连得上、多快。

右上角「设置」里可以打开「Claude 打开时先检查网络」（要在系统「无障碍」里手动开启）：不管从哪里打开 Claude、或从后台切回来，都先弹出这张卡片盖在 Claude 上面，点「打开」继续，点「取消」回桌面；可选多久内不重复检查、网络变了就重新检查。

发现问题会直接提醒（没开代理、出口在 Claude 不提供服务的地区、连不上 Claude 的服务器）。下方「取消」「打开」，点「打开」拉起手机上的 Claude App（没装就跳应用商店）。右上角「使用提示」里可以「在桌面创建 Claude 图标」（图标取手机上 Claude App 自己的图标，名字叫「Claude」，点它先检查网络再打开 Claude，角上有系统加的 To Claude 小标），也说明了怎么把原生 Claude 图标从桌面拿掉，以及让检查结果准确的注意事项。

## 下载

[最新版 ToClaude.apk](https://github.com/icangotoanywhere/To-Claude/releases/latest/download/ToClaude.apk)（安卓 8.0 及以上）

## 开发

- Kotlin + Jetpack Compose，单个界面（`MainActivity.kt`），检查逻辑在 `NetCheck.kt`。
- **只在云端发布**：改 `app/build.gradle.kts` 的 `versionCode`（+1）和 `versionName`，可选写 `release-notes/v<版本>.md`，推到 `main`。CI 编译、用固定密钥签名并核对证书指纹，版本号没发布过就自动发布 Release。
- 签名和 EasyClash 是同一把固定密钥（仓库 Secrets：`ANDROID_KEYSTORE_B64`、`ANDROID_KEYSTORE_PASSWORD`、`ANDROID_KEY_ALIAS`、`ANDROID_KEY_PASSWORD`；证书 SHA-256 `ada95c19…b5a861`）。换了密钥用户就无法覆盖升级。密钥文件绝不能进仓库。
