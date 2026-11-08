# 语伴（Talk）

一个「打电话式」的 Android 语音对话应用。接通后像打电话一样跟 AI 聊天：你说话，它听懂并开口回答，全程语音，屏幕上显示对话字幕。

特色是开放了 **MCP 服务**，本机上的 AI 客户端（RikkaHub / Operit 等）可以主动打电话给你。

---

## 目录结构

```
AndroidManifest.xml        清单：权限、Activity、前台服务
build.sh                   构建脚本（aapt2 → javac → d8 → 打包 → 签名）
pack.py                    资源 APK + dex 合成最终 APK，并做 resources.arsc 对齐
res/values/strings.xml     应用名
src/com/dsh/talk/
  MainActivity.java        全部 UI 与业务逻辑（单文件，体量较大）
  McpServer.java           极简 MCP 服务端（Streamable HTTP / JSON-RPC 2.0）
  Store.java               SQLite：会话、消息、文件夹
  VoiceService.java        常驻前台服务，保活 + 通知栏状态
```

## 构建

项目**不用 Gradle**，是手写脚本走 aapt2 / javac / d8 / apksigner 全流程。

```bash
./build.sh
```

工具链会自动探测，顺序是：项目目录下的 `toolchain/` → `$HOME/toolchain` → `/sdcard/toolchain`。
也可以直接用环境变量逐个指定：

| 变量 | 含义 |
|---|---|
| `TC` | 工具链根目录 |
| `AAPT2` | aapt2 可执行文件 |
| `JAVAC` | javac 可执行文件 |
| `BT` | build-tools 目录（需含 `lib/d8.jar`、`lib/apksigner.jar`） |
| `AJ` | android.jar |

机器上已经装好 Android SDK 的话，这样跑：

```sh
export ANDROID_HOME=$HOME/Android/Sdk
export BT=$ANDROID_HOME/build-tools/36.0.0
export AJ=$ANDROID_HOME/platforms/android-36/android.jar
export JAVAC=/usr/lib/jvm/java-17-openjdk/bin/javac
export AAPT2=$BT/aapt2
./build.sh
```

缺哪一样，脚本会直接说缺什么、该怎么设，不会一头雾水地 fail。

脚本会在没有 `debug.keystore` 时自动生成一个。**正式分发请换成自己的固定密钥并妥善保管**——密钥一换，老用户就得卸载重装。

最低支持 Android 10（API 29），targetSdk 36。

## 配置

应用**不内置任何 API Key**，首次使用需要在设置里填：

| 项目 | 用途 | 默认 |
|---|---|---|
| 对话模型 | 决定怎么回答 | DeepSeek（`api.deepseek.com`） |
| 识别模型 | 语音转文字 | 阿里云 DashScope `qwen3-asr-flash` |
| 语音模型 | 文字转语音 | ElevenLabs，需填音色 ID |

接口是 OpenAI 兼容格式，地址和模型名都可以换成别的服务商。

## MCP 服务

应用启动后在本机 `127.0.0.1:8765/mcp` 提供 MCP 服务，**只监听回环地址**，外部网络访问不到。

提供的工具：

| 工具 | 作用 |
|---|---|
| `start_call` | 打电话给用户，手机响铃等接听 |
| `listen` | 等用户说话并返回识别文字 |
| `say` | 把文本朗读出来 |
| `state` | 查询当前通话状态 |
| `end_call` | 挂断 |

连接后客户端会自动读到 `instructions` 里写给 AI 的通话规则（可在设置里改）。

MCP 本身是单向的：服务端只能发起呼叫给用户，不存在「用户主动拨给 AI」这条链路。要让 AI 主动打过来，客户端定时调 `start_call` 就行。

## 已知问题

见 [Issues](https://github.com/jsubs-shb/talk/issues)。目前挂着三项：来电界面底部透明未生效、英文词被识别成中文音译、通话中音量键偶发无响应。

## 权限说明

| 权限 | 用途 |
|---|---|
| `RECORD_AUDIO` | 通话收音 |
| `INTERNET` | 调用 AI 接口 |
| `SYSTEM_ALERT_WINDOW` | 来电弹窗 |
| `PACKAGE_USAGE_STATS` | 自动识别来源 App（可选） |
| `FOREGROUND_SERVICE(+SPECIAL_USE)` | 保活，保证 MCP 随时可用 |
| `POST_NOTIFICATIONS` | 通知栏显示运行状态 |

不声明安装应用、短信、通讯录、修改系统设置、开机自启等权限。

## 未上架说明

本项目未在应用商店上架，安装时国产 ROM（ColorOS / HyperOS 等）会弹通用的「未知来源应用风险」提示。那是针对未上架应用的模板文案，不是实际检出问题。

## 许可

MIT License，见 [LICENSE](LICENSE)。
