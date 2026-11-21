# 提词器 (Teleprompter)

一个极简的安卓原生提词器，纯 Android Views 手写界面，无第三方依赖，APK 只有 30 KB 左右。

## 功能

- **编辑页**：中间大输入框，正下方整条「开始播放」按钮；草稿自动保存；显示字数与「每行约 N 字」；「从光标处播放」从插入符位置开始。
- **保存文案**：右上角「保存」给当前正文起个名字存起来；输入框下方的「文案 N」打开列表，点一条直接载入编辑器，右边可删除。
- **在线更新**：设置里的「检查更新」读 GitHub Release 的最新版本，下载 APK 后交给系统安装器（首次需允许「安装未知应用」）。
- **两种滚动方式**
  - 整屏上滚：文字按屏幕宽度折行，匀速向上滚动，滚到末尾停住。
  - 单行横移：所有文字连成一条线（换行当空格），从左侧开始向左滚动。
- **播放页**：全屏（隐藏状态栏 / 导航栏）、屏幕常亮、按设置锁定横屏或竖屏。
- **控制条**：默认隐藏，点一下屏幕才从底部出现，播放中 4 秒无操作自动收起。
  - 从头播放、暂停 / 继续、速度滑杆（1–10 字/秒，实时生效）、退出
  - 进度条可拖动，跳到任意位置继续；上滚模式显示「第 n / N 行」，横移模式显示百分比
- **播放结束**：停在末尾并提示，点「从头」重新播放。
- **设置**
  - 滚动方式：整屏上滚 / 单行横移
  - 播放方向：竖屏 / 横屏
  - 字号：20%–100%（100% 时一行约 4 个字，最小可到一行 20 字），永远不超过屏幕
  - 字体：系统 / 黑体 / 宋体 / 等宽，可加粗
  - 背景：黑 / 白 / 米黄 / 深蓝 / 绿幕 / 深灰，文字颜色按亮度自动取黑或白
  - 镜像翻转：对着提词镜拍摄时用

## 构建

不使用 Gradle / Compose / aapt2，方便在 ARM 或没有完整 Android SDK 的环境里直接构建：

- JDK（javac + keytool）
- `aapt`（Debian/Ubuntu 的 `aapt` 包，AOSP aapt v1 即可，ARM 原生也能跑）
- `d8`（来自 R8 jar）
- `zipalign`、`apksigner`（Android build-tools 或 `apksigner` 包）
- `android.jar`（platform-34 及以上）

```bash
# 依赖路径在 build.sh 顶部修改
./build.sh        # 输出 提词器.apk（自签名，可直接安装）
```

脚本流程：`javac --release 8` → `d8 --min-api 24` → `aapt package` → `aapt add classes.dex` → `zipalign -f 4` → `apksigner sign`。

## 源码结构

| 文件 | 作用 |
| --- | --- |
| `src/com/dsh/tp/MainActivity.java` | 编辑页与设置底部弹层 |
| `src/com/dsh/tp/PromptActivity.java` | 全屏播放页、浮动控制条 |
| `src/com/dsh/tp/PromptView.java` | 自绘文字排版与两套滚动逻辑 |
| `src/com/dsh/tp/Settings.java` | SharedPreferences 持久化 |
| `src/com/dsh/tp/Ui.java` | dp/sp、圆角/涟漪背景、亮度取反色 |
| `src/com/dsh/tp/Crash.java` | 崩溃堆栈落盘与错误页（排障用） |

- minSdk 24，targetSdk 34
- 包名 `com.dsh.tp`
