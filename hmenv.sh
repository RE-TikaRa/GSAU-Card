# 鸿蒙命令行构建环境 —— 仅在当前 shell 会话生效,不写全局 PATH。
# 用法: source hmenv.sh 之后即可用 hvigorw / ohpm / hdc。
# 不把 DevEco 自带 node(18) 加进全局,避免和 nvm 的 node 打架;
# 这里用 HVIGOR_NODE_HOME / DEVECO_SDK_HOME 显式指给工具链。

DEVECO="/c/Program Files/Huawei/DevEco Studio"

export DEVECO_SDK_HOME="$DEVECO/sdk"
export HVIGOR_NODE_HOME="$DEVECO/tools/node"

# 顺序: DevEco node 排在最前,保证 hvigorw 用 18;ohpm、hvigor、hdc 工具随后。
export PATH="$DEVECO/tools/node:$DEVECO/tools/ohpm/bin:$DEVECO/tools/hvigor/bin:$DEVECO/sdk/default/openharmony/toolchains:$PATH"

echo "鸿蒙构建环境已载入(仅当前会话):"
echo "  node   -> $(command -v node)  $(node --version)"
echo "  ohpm   -> $(command -v ohpm)"
echo "  hvigorw-> $(command -v hvigorw)"
echo "  hdc    -> $(command -v hdc)"
