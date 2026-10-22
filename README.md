# 壁纸桌面 · 手机主题美化

安卓桌面启动器测试版：整张壁纸做桌面，左侧苹果风格的透明磨砂菜单，软件收纳和壁纸设置都用卡片弹窗。动效规范参照 dongxiaosheji 动效仓库的 MOTION_GUIDE（只动 transform / opacity、可打断并继承速度、进出成对、每个按钮都有按下反馈）。

**下载最新版：** [wallpaper-launcher.apk](https://github.com/dhhd1426-ops/Theme-enhancement-/releases/latest/download/wallpaper-launcher.apk)

## 功能

| 场景 | 行为 |
| --- | --- |
| 打开 App | 不是默认桌面时，弹出系统确认框请求成为默认桌面（安卓 10+ 用 RoleManager，更早版本打开「默认桌面」设置） |
| 三条杠 | 点开或从屏幕左边缘右滑，拉出玻璃侧栏；三条杠变成 × |
| 软件收纳 | 一开始为空；「添加软件」读取手机里所有带图标的软件，可多选；收纳列表保存在本机 |
| 点收纳里的软件 | 直接打开该软件 |
| 卡片右上角箭头 | 卡片铺满屏幕，再点一次缩回 |
| 壁纸设置 › 退出桌面 | 确认后打开系统「默认桌面」设置，选回手机自带桌面 |
| 最近使用胶囊 | 桌面底部常驻玻璃胶囊，按最后使用时间列出最近 3 天用过的软件（最多 30 个）；胶囊宽度固定露 5 个，多了在胶囊里左右滑，松手吸附到整格 |
| 点胶囊里的软件 | 从图标位置放大成全屏打开（三星弹出窗口会叠加且位置不受控，所以不用）；用过的软件排到最前 |
| 胶囊右侧时钟 | 弹出「最近使用」卡片，按「刚刚 / 更早」分组列出全部 |
| 首次使用胶囊 | 显示「开启最近使用」，跳到系统「使用情况访问权限」页开启 |
| 系统多任务界面 | 壁纸桌面自己不出现在系统多任务列表里 |
| 返回键 | 不退出桌面，只收起最上面的卡片或菜单 |
| 卸载软件 | 回到桌面时自动从收纳里移除 |

## 结构

- `app/src/main/assets/index.html`：全部界面与动效（单文件，无依赖）
- `app/src/main/java/com/dongxiao/launcher/MainActivity.java`：全屏 WebView，加上 `window.Launcher` 桥（读取软件、打开软件、默认桌面）
- `.github/workflows/build-apk.yml`：推送到 `main` 自动构建并发布 Release

## 构建

```bash
gradle assembleDebug
# 输出 app/build/outputs/apk/debug/app-debug.apk
```

AGP 8.5.2 / Gradle 8.9 / JDK 17；最低安卓 8.0（API 26），目标 API 34。
