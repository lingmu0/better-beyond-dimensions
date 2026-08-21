# Better Beyond Dimensions

一个面向 Beyond Dimensions 的双版本附属模组：

- `forge/`：Minecraft 1.20.1 Forge 47.4.23
- `neoforge/`：Minecraft 1.21.1 NeoForge 21.1.233

## 功能

- 在所有 `AbstractContainerScreen` 左侧显示可搜索的维度网络侧边栏。
- 只有服务器确认玩家拥有可用 Beyond Dimensions 网络时才显示侧边栏。
- 左键从网络取出一组，右键取出一个；搜索支持物品显示名和注册名。
- “玩家移入”和“容器移入”分别控制对应方向的 Shift-click。
- “存入容器”把当前打开容器的物品存入网络；“存入背包”只处理玩家主背包，快捷栏不会被处理。
- 所有网络存取和 Shift-click 拦截都在服务端校验并执行。

## 构建

在对应目录运行：

```powershell
cd forge
.\gradlew.bat build

cd ..\neoforge
.\gradlew.bat build
```

最终文件位于各自的 `build/libs/better_beyond_dimensions-0.1.0.jar`。

两个 `libs/` 目录中包含开发期使用的 Beyond Dimensions 0.7.27 对应版本依赖；运行时仍需安装与 Minecraft/加载器匹配的 Beyond Dimensions。模组 ID 为 `better_beyond_dimensions`，依赖 ID 为 `beyonddimensions`。
