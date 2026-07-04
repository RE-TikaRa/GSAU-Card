# 甘农卡

甘肃农业大学校园卡付款码的桌面组件 App。把付款码放到手机桌面，付款时瞄一眼组件或点开全屏，省去从微信层层跳转。

二维码在本地渲染（ZXing），后台只取页面里那段 32 位付款码内容，传输量极小，清晰度和尺寸随意控制。

## 功能

- **桌面组件**：外层圆角卡 + 顶部姓名条 + 大圆角二维码。点姓名条切换用户，点二维码打开全屏付款页。
- **全屏付款页**：打开即实时拉最新码，自动提亮屏幕，每 60 秒刷新。不依赖后台，付款那一刻的码一定新鲜。
- **多账号**：粘贴付款码链接添加多张卡，App 和组件都能切换。
- **保活两档**：
  - 省心档：WorkManager 定时刷新，无常驻通知。
  - 稳妥档：前台服务每分钟刷新，通知栏一条低优先级常驻通知。
- **国产 ROM 适配**：设置页一键跳转电池白名单和小米/OPPO/vivo 自启动管理。

## 架构

```
com.tika.paycard
├── data/          数据层
│   ├── Account            账号模型（openid + cardId + 缓存）
│   ├── AccountStore       多账号存储（SharedPreferences）
│   ├── LinkParser         从链接解析 openid/id
│   ├── PayCodeRepository  抓取页面并解析付款码内容
│   └── PayCodeManager     刷新协调：抓取 → 回填缓存 → 通知组件
├── qr/            QrGenerator：ZXing 渲染二维码
├── widget/        PayWidgetProvider：桌面组件
├── work/          后台
│   ├── RefreshWorker      WorkManager 周期刷新（15 分钟兜底）
│   ├── RefreshService     前台服务（60 秒密集刷新）
│   └── KeepAlive          保活档位 + 系统设置跳转
└── ui/            界面
    ├── MainActivity       主界面：当前卡 + 账号列表
    ├── PayActivity        全屏付款页
    ├── SettingsActivity   设置
    └── AccountAdapter     账号列表适配器
```

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

## 已知限制

- `openid` 凭证有效期未知，失效后需重新粘贴链接。详见 [PLAN_MISMATCHES.md](PLAN_MISMATCHES.md)。
- Android 桌面组件系统刷新周期最短 30 分钟，密集刷新依赖前台服务，可能被强省电策略影响。
