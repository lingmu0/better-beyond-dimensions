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
- “存入容器”默认快捷键为 `F`，“存入背包”默认未绑定；只有侧边栏显示且当前界面没有任何文本输入框聚焦时生效。
- 玩家 Shift 转移和容器 Shift 转移选项会保存到客户端 `config/better_beyond_dimensions-settings.json`，打开新界面时自动同步到服务端。
- 所有网络存取和 Shift-click 拦截都在服务端校验并执行。

## 侧边栏显示事件

侧边栏创建完成、显示前会触发客户端 `SidebarDisplayEvent`。事件可以读取当前 `Screen`，只禁用某个按钮的功能、覆盖按钮文字/悬浮提示，或完全禁用当前界面的侧边栏。事件只影响当前界面实例，不会修改服务端菜单。

例如，在工作台界面中禁用“存容器”，并把鼠标悬浮提示改为“已禁用”：

```java
private static void onSidebarDisplay(SidebarDisplayEvent event)
{
    if (event.getScreen() instanceof CraftingScreen)
    {
        event.disableButton(SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER);
        event.setButtonTooltip(
                SidebarDisplayEvent.ButtonId.DEPOSIT_CONTAINER,
                Component.literal("已禁用")
        );
    }
}
```

Forge 在客户端初始化时注册：

```java
MinecraftForge.EVENT_BUS.addListener(YourClientClass::onSidebarDisplay);
```

NeoForge 使用：

```java
NeoForge.EVENT_BUS.addListener(YourClientClass::onSidebarDisplay);
```

还可以调用 `event.setButtonMessage(...)` 修改按钮显示文本，或调用 `event.disableSidebar()` 完全关闭当前界面的侧边栏。

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
