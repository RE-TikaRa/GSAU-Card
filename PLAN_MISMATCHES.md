# 设计空缺与决策记录

记录实现过程中发现的设计空缺,以及在缺少明确需求时自行采用的合理方案。供后续迭代复核。

## 1. openid 凭证长期有效

**结论**:付款码抓取依赖链接里的 `openid`,已确认该凭证长期有效,不会过期,无需续期或重新登录流程。

**抓取三态**:抓取失败区分三态——`Ok`（成功）、`Invalid`（页面通但取不到 code）、`Error`（网络错误）。`Invalid` 时提示用户重新粘贴链接。openid 既然不过期,`Invalid` 更可能源于页面结构变化而非凭证失效。

## 2. 保活档位与后台 Worker 的关系

**决策**:两档保活都保留 `RefreshWorker`（15 分钟周期，系统调度兜底）：

- 省心档（LITE）：仅 WorkManager。
- 稳妥档（STEADY）：WorkManager + 前台服务（60 秒密集刷新）。

**理由**:即使省心档，也需要一个不依赖前台服务的兜底刷新，否则组件长时间不更新会导致付款码过期。

**开机恢复**:`BootReceiver` 只调度 `RefreshWorker`，不拉起前台服务。`dataSync` 类型不在 `BOOT_COMPLETED` 的前台服务启动豁免名单内（Android 14），从开机广播直接启动会抛 `ForegroundServiceStartNotAllowedException`。稳妥档的前台服务留待用户下次打开 App 时，由 `MainActivity` 的 `KeepAlive.apply` 在前台合法拉起。付款码只有约 1 分钟有效期，重启后未打开 App 的空窗期用户不会付款；真要付款时打开 App 即恢复服务、进全屏页即强刷，功能无损。

**同理**:`RefreshService` 不再在 `onTaskRemoved` 里手动 `start` 自身（后台启动前台服务同样受限），改由 `START_STICKY` 让系统在资源允许时自行重建。

**待验证**:稳妥档用 `dataSync` 前台服务类型。Android 14 起 `dataSync` 有单日累计约 6 小时运行上限，长驻场景可能被系统回收。真机观察是否触顶；若触顶，再评估 `specialUse` 类型（需在应用商店说明用途）或退回纯 WorkManager 兜底。

## 3. 协程刷新的账号引用竞态

**现状**:`PayCodeManager.refresh(context, account)` 会就地修改传入的 `account` 对象（回填 code/余额）。UI 层在异步刷新未返回时若切换了当前账号，旧协程的回调仍持有旧 `account` 引用。

**当前风险评估**:实际影响有限——回调只更新 UI 文本和二维码图像，`store.update()` 按 openid+cardId 精确匹配落库，不会写错账号。但 UI 可能短暂显示旧账号刷新结果。

**当前方案**:UI 回调以 `store.current()` 为准重新取当前账号判断，避免展示错卡。后续若引入更复杂的并发场景，考虑给刷新请求打 token 做取消。

## 4. 二维码渲染尺寸

**决策**:`QrGenerator` 定义三档语义化尺寸常量（`SIZE_WIDGET=400` / `SIZE_CARD=600` / `SIZE_FULLSCREEN=700`），替代散落的魔法数字。二维码是矢量矩阵，尺寸只影响位图清晰度，不影响扫码内容。

## 5. 用户可见文案的外部化边界

**决策**:所有界面文案统一进 `res/values/strings.xml`，界面层（Activity/Adapter/Widget/Service/Manifest/布局）一律引用 `@string` 或 `getString`。设计占位（列表项姓名、组件姓名条等运行时覆盖的文本）改用 `tools:text`，不进包体。

**边界**:数据/模型层保留少量字面量,不接线 `R.string`：

- `Account.displayName()` 的 `"卡"` 前缀——纯数据模型,无 `Context`。
- `PayCodeRepository` 的 `"网络错误"`、`PayCodeManager` 的 `"无账号"`——数据层不持有 `Context`,仅作为兜底 `message` 冒泡给 UI,UI 已有自己的 `@string` 文案包裹。

**理由**:为取字符串给数据层回注 `Context` 属于分层倒退,得不偿失。这些字面量不面向最终展示（UI 分支各自用 `@string`），保留在原地更干净。

## 6. 深色模式配色取值

**决策**:未接入 Material You 动态取色，改用两套固定语义色板（`values/colors.xml` 与 `values-night/colors.xml`）。深色底选偏冷的深绿灰（`screen_bg=#101410`、`surface=#1C211C`），品牌绿在深色下略提亮（`#4CAF50`）以压制纯黑上的刺眼感。

**关键约束**:二维码衬托块 `qr_block` 在深色下反而取白色（`#FFFFFF`），二维码矩阵固定黑白（`QrGenerator` 默认黑前景白背景）。付款码的可扫性优先于视觉统一，深色主题不改二维码本身。

**待验证**:深色色板未经真机不同屏幕（LCD/OLED）实测，对比度是否达 WCAG AA 尚未用工具验证，仅按经验取值。

## 7. 桌面组件的主题归属

**现状**:`widget_paycard.xml` 复用 `bg_card`/`bg_name_bar`/`bg_qr_block`，这些 drawable 随系统夜间模式切换 `values-night`。

**决策**:桌面组件跟随**系统**夜间模式，不跟随 App 内的手动主题档位。App 内 `ThemeManager` 通过 `AppCompatDelegate` 只作用于 App 自身 Activity，RemoteViews 渲染的组件走系统资源配置，二者无法共享同一开关。

**理由**:桌面组件由 Launcher 进程按系统配置渲染，强行同步 App 内档位需额外的配置覆盖机制，收益低。跟随系统是符合平台惯例的合理默认。
