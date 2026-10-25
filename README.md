<!--
SPDX-FileCopyrightText: 2015 - 2024 Rime community

SPDX-License-Identifier: GPL-3.0-or-later
-->

# Unexpect Keyboard

**安卓端 Rime 输入法框架 Trime 的个人定制分支** · 包名 `com.unexpect.keyboard`

[![License: GPL v3](https://img.shields.io/badge/License-GPL%20v3-blue.svg)](https://www.gnu.org/licenses/gpl-3.0)
[![GitHub release](https://img.shields.io/github/v/release/limnop/trime?label=release)](https://github.com/limnop/trime/releases)
[![Last commit](https://img.shields.io/github/last-commit/limnop/trime/develop)](https://github.com/limnop/trime/commits/develop)

> ⚠️ **这不是官方 Trime。**
> 上游项目是 [osfans/trime](https://github.com/osfans/trime)，本仓库是个人使用的定制分支，只对自己负责，不保证通用性。
> 官方文档、讨论与反馈请去上游：[Wiki](https://github.com/osfans/trime/wiki) · [Issues](https://github.com/osfans/trime/issues)

---

## 改了什么

相对上游 `develop`（快照版本 v3.3.13）的差异，全部集中在一个提交里，共 6 个文件 / +2089 行：

| 项目 | 上游 | 本分支 |
|---|---|---|
| 包名 | `com.osfans.trime` | `com.unexpect.keyboard`（**可与官方版共存**） |
| 键盘形态 | 只能停靠在屏幕底部 | 支持**悬浮键盘**：可拖动、可缩放 |
| 横屏 | 拉伸铺满 | 自动以悬浮形态呼出，竖屏自动回落到停靠 |
| 主题 | 官方默认主题 | 内置 `unexpect` 主题（1824 行，含自制键位与配色） |
| 词库 | 上游 `luna_pinyin` | `unexpect-max` 版词库 |
| ABI | 多架构 | 仅 `arm64-v8a`（只剩 50MB，去掉的架构没人用） |

改动文件清单：

```
app/build.gradle.kts                             applicationId / ABI
app/src/main/assets/shared/unexpect.trime.yaml   自制主题（新增，1824 行）
app/src/main/java/.../ime/core/InputView.kt      悬浮键盘主体（+230 行）
app/src/main/java/.../ime/core/TrimeInputMethodService.kt         setFloating 入口（+25）
app/src/main/java/.../ime/keyboard/CommonKeyboardActionListener.kt  float_keyboard 动作（+11）
app/src/main/jni/CMakeLists.txt                   glog 的 unwind 构建修复
```

## 下载与安装

| 版本 | 下载 |
|---|---|
| v3.3.13（arm64-v8a） | [Trime-Unexpect-Max-arm64-v8a.apk](https://github.com/limnop/trime/releases/download/unexpect-max-v3.3.13/Trime-Unexpect-Max-arm64-v8a.apk) |

校验值（v3.3.13）：

```
大小：52151390 字节 (49.7 MiB)
MD5 ：675859e30a88ba3e30f873e4a2dd2289
签名：CN=Unexpect Keyboard
证书 SHA-256：e4f2d6fa8cd9cee5dbddbbe6e9a2fb9b0b232f3bf58feafe63b3320309e71b4c
```

安装步骤：

1. 直接装 APK。包名与官方版不同，**不需要卸载官方 Trime**，两者可以共存。
2. 系统设置 → 语言与输入法 → 启用 **Trime**，并设为默认输入法。
3. 首次启动会拉起部署向导，按提示走完（存储位置选「应用专属存储空间」最省事），Rime 数据落在
   `/sdcard/Android/data/com.unexpect.keyboard/files/rime/`。
4. 部署完成后若主题不是 `Unexpect Keyboard`：在输入法设置里选中它，或重启一次输入法。

用 adb 装的话：

```sh
adb install -r Trime-Unexpect-Max-arm64-v8a.apk
adb shell ime enable com.unexpect.keyboard/com.osfans.trime.ime.core.TrimeInputMethodService
adb shell ime set    com.unexpect.keyboard/com.osfans.trime.ime.core.TrimeInputMethodService
```

> 别 force-stop 正在使用的默认输入法，系统会临时回落到 `com.android.inputmethod.latin`，默认输入法还得重新设一遍。

## 悬浮键盘怎么用

**Shift 键上滑**浮起，**Shift 键下滑**停靠。键位上标了 `▲` / `▼` 提示。

浮起之后：

- **拖动**：键盘左上角的拖动手柄，拖到屏幕任意位置（会自动夹在屏幕内，不会被推出边界）。
- **缩放**：右下角手柄斜向拖，缩放范围 `0.3x ~ 1.0x`，浮起默认 `0.85x`。
- **横屏自动浮起**：转到横屏时键盘自动进入悬浮形态，转回竖屏自动停靠。
- 浮起时键盘上方至少保留 `72dp` 给拼音预编辑行，不会顶掉编码显示。

配置层面（`unexpect.trime.yaml`）：

```yaml
preset_keys:
  float_on:  {label: 浮动, send: FUNCTION, command: float_keyboard, option: "on"}
  float_off: {label: 停靠, send: FUNCTION, command: float_keyboard, option: "off"}

# 绑定在 Shift 键上
- {click: Shift_L, swipe_up: float_on, swipe_down: float_off, label_symbol: '▲', hint: '▼', ...}
```

也就是说，换任何主题只要把上面两行 preset 和 `swipe_up` / `swipe_down` 抄过去，悬浮功能照样能用，不依赖 `unexpect` 主题本身。

代码入口（想改行为看这几处）：

| 位置 | 作用 |
|---|---|
| `InputView.kt:491` `setFloating(enabled)` | 开关悬浮、切缩放、算位移、夹边界 |
| `InputView.kt` `handleFloatingDrag` / `handleFloatingResize` | 拖动与缩放手势 |
| `InputView.kt` `applyOrientationFloatingDefault` | 横屏默认浮起 / 竖屏回落 |
| `TrimeInputMethodService.kt:510` `setFloating` | 转发给 InputView |
| `CommonKeyboardActionListener.kt:197` | 解析 `float_keyboard` 动作的 `on` / `off` 参数 |

## 主题

`app/src/main/assets/shared/unexpect.trime.yaml`，`name: Unexpect Keyboard`，由上游默认主题（`trime.yaml`）改写，主要动了：

- 键高 `44`、键字号 `22`、横屏左右留白 `40`（防按键被拉伸变形）
- 自制配色与候选栏参数（候选字号 22 / 候选区高 28）
- 悬浮相关 preset 与 Shift 键位绑定

改主题不必重新编译整个工程：把 yaml 放进 `rime/` 目录（或用同文堂式设置界面导入），重启输入法即可生效。

## 自行构建

环境（本机实测通过的组合）：

| 组件 | 版本 |
|---|---|
| JDK | 21.0.13（上游要求 17+，21 实测无问题） |
| Gradle | 9.8.1（用 wrapper 即可） |
| Android SDK | compileSdk 37 / targetSdk 37 / minSdk 21 |
| NDK | 29.0.14206865 |
| CMake | 3.22.1 |

```sh
# 1. 拉代码 + 子模块（librime 全家桶，10 个）
git clone https://github.com/limnop/trime.git
cd trime
git submodule update --init --recursive --filter=blob:none

# 2. debug（免签名，包名会带 .debug 后缀）
./gradlew assembleDebug -PbuildABI=arm64-v8a

# 3. release（需要签名，产物才是 com.unexpect.keyboard）
cat > keystore.properties <<EOF
storePassword=你的store密码
keyPassword=你的key密码
keyAlias=你的alias
storeFile=/绝对路径/xxx.keystore
EOF
./gradlew assembleRelease -PndkVersion=29.0.14206865 -PcmakeVersion=3.22.1 -PbuildABI=arm64-v8a
```

`-PndkVersion` / `-PcmakeVersion` / `-PbuildABI` 用来覆盖本机实际安装的版本并只编单一 ABI——不传的话 Gradle 会按它自己记录的版本找 NDK，版本不对就直接报错。

### 踩过的坑

1. **Boost 解压路径**：`Boost.cmake` 里 `file(ARCHIVE_EXTRACT ...)` 用的是裸相对路径，会按 cmake 进程的 CWD（也就是 app 模块根目录）解析，报 `<app>/boost-1.92.0.tar.xz` 找不到。改成 `${CMAKE_SOURCE_DIR}/boost-x.tar.xz` 这种绝对路径即可。
2. **NDK r29 + glog 的 unwind**：sysroot 里没有 aarch64 的 libunwind，`find_package(Unwind)` 会退到宿主机的 x86_64 版本，链接阶段报 `incompatible with aarch64linux`。解法是把顶层 CMakeLists 的 `option(WITH_UNWIND ...)` 换成 `set(WITH_UNWIND none CACHE STRING "unwind driver" FORCE)`。
   > 上游已合并等价修复（`c0df6d07 fix(build): disable glog unwind support`），以后跟上游走这条本地补丁可以丢掉。
3. **依赖下载失败**：`repo.maven.apache.org` 在国内可能握手失败（`mergeReleaseNativeLibs` 拉 AAR 阶段最明显），给 `settings.gradle.kts` 的 `dependencyResolutionManagement` 加阿里云镜像即可。
4. **120MB 词库不在仓库里**：`luna_pinyin.dict.yaml` 超过 GitHub 100MB 单文件限制，仓库中保留的是指向上游词典的软链接。要复现完整词库（`version: "unexpect-max"`）得自己把真实文件放进去。

## 与上游同步

```sh
git remote add upstream https://github.com/osfans/trime.git   # 只需一次
git fetch upstream
git switch develop && git merge upstream/develop
```

本分支是「一个提交叠在 `develop` 上」的结构，所以 merge 上游一般不会打架；真正容易冲突的只有 `app/build.gradle.kts` 里 applicationId 那几行。

发布 tag 采用 `unexpect-max-vX.Y.Z` 命名，与上游的 `vX.Y.Z` 区分，避免以后跟上游 tag 撞名。

## 已知限制

- **只有 arm64-v8a**。armeabi-v7a / x86_64 没编过，32 位老设备装不上。
- **词库定制不在源码里**，只体现在已发布的 APK 中（原因见上一节第 4 条）。
- **自签名**。升级必须用同一把 keystore 签出的包，否则系统拒绝覆盖安装；换签名 = 卸载重装 = 配置全丢。
- 只在 **Redmi K30 Pro（lmi）/ LineageOS 23.2（Android 16）** 上真机验证过，其它 ROM 未测。
- 悬浮手势依赖主题里的 `swipe_up` / `swipe_down` 绑定，用第三方主题时若 Shift 键位被覆盖，悬浮入口就没了。

## 致谢

- 上游作者：[osfans](https://github.com/osfans) 及[所有贡献者](https://github.com/osfans/trime/graphs/contributors)
- 上游 [Wiki 编辑](https://github.com/osfans/trime/wiki)、翻译者、键盘设计者
- 项目基石：[RIME](http://rime.im) · [OpenCC](https://github.com/BYVoid/OpenCC) · [Android Traditional Chinese IME](https://code.google.com/p/android-traditional-chinese-ime/)

本项目的一切成果都建立在这些工作之上，定制部分只是个人口味。

## 第三方库

- [Boost C++ Libraries](https://www.boost.org/) (Boost Software License)
- [darts-clone](https://github.com/s-yata/darts-clone) (New BSD License)
- [LevelDB](https://github.com/google/leveldb) (New BSD License)
- [libiconv](https://www.gnu.org/software/libiconv/) (LGPL License)
- [marisa-trie](https://github.com/s-yata/marisa-trie) (BSD License)
- [glog](https://github.com/google/glog) (New BSD License)
- [OpenCC](https://github.com/BYVoid/OpenCC) (Apache License 2.0)
- [RIME](https://rime.im) (BSD License)
- [snappy](https://github.com/google/snappy) (BSD License)
- [utfcpp](https://github.com/nemtrif/utfcpp) (Boost Software License)
- [yaml-cpp](https://github.com/jbeder/yaml-cpp) (MIT License)
- [Android Traditional Chinese IME](https://code.google.com/p/android-traditional-chinese-ime/) (Apache License 2.0)

## License

[GPL-3.0-or-later](LICENSE)，与上游一致。
