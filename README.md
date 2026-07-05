# 甘农卡

甘肃农业大学校园卡付款码的桌面组件 App。把付款码放到手机桌面，付款时瞄一眼组件或点开全屏，省去从微信层层跳转。

二维码在本地渲染（ZXing），后台只取页面里那段 32 位付款码内容，传输量极小，清晰度和尺寸随意控制。

## 功能

- **桌面组件**：外层圆角卡 + 顶部姓名条 + 大圆角二维码。点姓名条切换用户，点二维码打开全屏付款页。
- **全屏付款页**：打开即实时拉最新码，自动提亮屏幕，每 60 秒刷新。不依赖后台，付款那一刻的码一定新鲜。
- **多账号**：粘贴付款码链接添加多张卡，App 和组件都能切换。
- **深色模式**：支持浅色、深色、跟随系统三档，设置页手动切换，冷启动即生效。所有界面走主题变量，弹窗、列表、卡片、输入框同步适配。
- **统一视觉**：Tabler 描边图标（返回/添加/删除/编辑/设置/刷新等），应用内自定义 Dialog 替代系统原生弹窗，轻提示走 Material Snackbar 跟随主题，二级页统一顶栏一键返回主页。
- **保活两档**：
  - 省心档：WorkManager 定时刷新，无常驻通知。
  - 稳妥档：前台服务每分钟刷新，通知栏一条低优先级常驻通知。
- **国产 ROM 适配**：设置页一键跳转电池白名单和小米/OPPO/vivo 自启动管理。
- **关于软件**：设置页进关于页，含版本号、简介、联系方式（邮箱/GitHub/哔哩哔哩/微信公众号）与开源许可证、隐私政策、用户协议，以及检查更新（读 GitHub Releases，有新版拉起浏览器去下载页）。
- **首启引导**：第一次打开弹操作指引页，图文分步讲绑定付款码链接和添加桌面卡片，截图走反代远程加载，看完点开始使用进主界面。

## 架构

```
com.tika.paycard
├── data/          数据层
│   ├── Account            账号模型（openid + cardId + 缓存）
│   ├── AccountStore       多账号存储（SharedPreferences）
│   ├── LinkParser         从链接解析 openid/id
│   ├── PayCodeRepository  抓取页面并解析付款码内容
│   ├── PayCodeManager     刷新协调：抓取 → 回填缓存 → 通知组件
│   ├── UpdateChecker      读 GitHub Releases 比对版本，走反代
│   └── ImageLoader        引导页远程截图加载（OkHttp + 内存缓存）
├── qr/            QrGenerator：ZXing 渲染二维码
├── widget/        PayWidgetProvider：桌面组件
├── work/          后台
│   ├── RefreshWorker      WorkManager 周期刷新（15 分钟兜底）
│   ├── RefreshService     前台服务（60 秒密集刷新）
│   └── KeepAlive          保活档位 + 系统设置跳转
└── ui/            界面
    ├── MainActivity       主界面：当前卡 + 账号列表
    ├── PayActivity        全屏付款页
    ├── SettingsActivity   设置（含主题切换）
    ├── AboutActivity      关于软件：版本、简介、联系方式、许可证与政策
    ├── GuideActivity      首启操作引导：分步图文 + 远程截图
    ├── AccountAdapter     账号列表适配器
    ├── AppDialog          应用内统一弹窗（输入/确认/文本/轻提示）
    └── ThemeManager       主题档位持久化与应用

PayCardApp（Application）冷启动时应用已保存的主题档位。
```

界面统一走主题变量：`res/values/colors.xml` 定义语义色（surface / outline / text_* 等），`res/values-night/colors.xml` 提供深色版本，drawable 与布局一律引用 `@color` 而非字面量。二维码矩阵固定黑白，任意主题下都清晰可扫。

付款码内容 = 页面 `id="code"` 隐藏字段里的 32 位十六进制串，服务端每次请求都会轮换。App 把它渲染成二维码，商户扫到的内容与官方页面完全一致。

## 构建

需要 JDK 17 和 Android SDK（compileSdk 34，build-tools 35.0.0）。

```bash
# 首次构建前，在 local.properties 写入 SDK 路径
echo "sdk.dir=/path/to/android-sdk" > local.properties

# 编译 debug apk
./gradlew assembleDebug

# 跑解析层单元测试（付款码页面解析、链接解析）
./gradlew testDebugUnitTest
```

产物在 `app/build/outputs/apk/debug/app-debug.apk`。

依赖仓库默认走阿里云镜像（见 `settings.gradle.kts`），国内构建更稳。

## 使用

1. 安装 apk（debug 包需允许「未知来源」）。
2. 打开 App，点右上角 +，粘贴从微信付款码页面复制的链接。
3. 长按桌面添加「甘农卡付款码」组件。
4. 建议进设置页开启电池白名单和自启动，后台刷新才稳。

## 发布新版本

发版全自动，推一个 `v` 开头的 tag 即触发 `.github/workflows/release.yml` 打包发布，代码本身不用改版本号。

版本号从 tag 自动推导：

- `versionName` = tag 去掉前缀 `v`（`v1.1` → `1.1`），写在 Release 页和「关于软件」里。
- `versionCode` = Actions 的 run_number，每跑一次自动递增，无需手动维护。

发布步骤：

```bash
# 1. 确保要发布的代码已提交并推到 main
git push origin main

# 2. 打新 tag（版本号递增，别复用旧 tag）
git tag v1.1

# 3. 推 tag，触发 Actions 构建
git push origin v1.1
```

推上去后 Actions 会：签名打包 `assembleRelease` → 产物重命名成 `GSAU-Card-v1.1.apk` → 建对应 Release 并自动生成更新日志。构建进度和结果在仓库 Actions 页看。

几点约束：

- 签名走 CI 里的固定 keystore（`secrets.KEYSTORE_BASE64` 等），全程一致，用户才能覆盖安装、保留已添加的卡。这些 Secret 换了会导致签名变化，务必别动。
- tag 名单调递增（`v1.1`、`v1.2`…），已用过的不要复用。
- 引导页截图走 Cloudflare Worker 反代（`worker/gh-proxy.js`），改过 Worker 要重新部署才生效，否则新版引导页图片拉不到。

## 已知限制

- `openid` 凭证有效期未知，失效后需重新粘贴链接。详见 [PLAN_MISMATCHES.md](PLAN_MISMATCHES.md)。
- Android 桌面组件系统刷新周期最短 30 分钟，密集刷新依赖前台服务，可能被强省电策略影响。
