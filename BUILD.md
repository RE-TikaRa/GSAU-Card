# 命令行构建说明

这个工程走命令行构建，不依赖 DevEco Studio 的图形界面。

## 为什么要单独一份环境脚本

DevEco Studio 自带一套 node 18（`tools/node`），hvigor 只认这个版本。
系统里另装了 nvm 管理的 node 22，两者同时在 PATH 里会打架，
之前「加入 PATH 失败」就是这么来的。

所以这里不往全局 PATH 写任何东西。用 `hmenv.sh` 在当前 shell 会话临时挂上工具链，
关掉终端就还原，nvm 的 node 22 照常用。

## 用法

每次构建前，先在工程根目录载入环境：

```bash
source hmenv.sh
```

载入后会打印 node / ohpm / hvigorw / hdc 的实际路径，
其中 node 必须显示 18.x（DevEco 那份），显示 22 就说明 nvm 抢了前面。

## 常用命令

装依赖（从 https://ohpm.openharmony.cn 拉）：

```bash
ohpm install
```

构建 HAP：

```bash
hvigorw assembleHap --mode module -p product=default --no-daemon
```

`--no-daemon` 避免后台常驻进程，命令行下更省心。
产物在 `entry/build/default/outputs/default/` 下的 `.hap`。

真机调试（需先开发者模式 + USB 连接）：

```bash
hdc list targets        # 看设备是否连上
hdc install <hap 路径>  # 装到真机
```

## 工具链路径

都在 `C:\Program Files\Huawei\DevEco Studio` 下：

- node 18：`tools/node`
- ohpm：`tools/ohpm/bin`
- hvigor：`tools/hvigor/bin`
- hdc：`sdk/default/openharmony/toolchains`
- SDK：`sdk`（脚本里给到 `DEVECO_SDK_HOME`）

DevEco 装到别处的话，改 `hmenv.sh` 开头的 `DEVECO` 变量就行。
