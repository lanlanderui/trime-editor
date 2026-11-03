# 同文编辑器

给 [同文输入法（trime）](https://github.com/osfans/trime) 主题用的独立编辑器。改主题文件里的样式、配色、布局、动作、工具条和候选窗，改动直接写回同一份 `trime.yaml`。

编辑器与输入法是**两个应用、一份文件**：编辑器写盘，输入法下次露面前复核文件，变了就重载。没有后台服务，没有同步，也不需要 root。

## 现在能做什么

| 页面 | 改什么 |
|---|---|
| 工作台 | 选主题目录、列出目录里的主题、缩略配色、目录改名 |
| 预览 | 用输入法的排版引擎画键盘；切换配色方案；静态检查的入口 |
| 样式 | `style:` 段的标量（键高、字号、圆角、间距…） |
| 配色 | `preset_color_schemes` 的 54 个色键、方案增删改、Material You 生成 |
| 布局 | `preset_keyboards` 的键：拖动调宽、增删键、六个动作、撤销 |
| 动作 | `preset_keys` 的 240 条动作：label / send / select / command / states |
| 工具条 | `tool_bar` 的按钮顺序（右起）、间距、二态、主按钮、锚点块 |
| 候选窗 | `window:` / `preedit:` 两段标量，含「本主题没写这一段」的提醒 |
| 静态检查 | 主题里的未知键、失效引用，点一条跳到对应页面 |

写盘一律**只改目标行**：注释、缩进、字段顺序、行尾（CRLF/LF）都按原样保留。这一条有单测兜着——真主题 4234 行 / 50 个布局 / 2465 条键行，读出来再写回去必须逐字节相同。

## 与输入法的版本配对

编辑器和输入法共用主题文件格式，格式跟的是**输入法的版本**，不是编辑器的：

| 编辑器 | 配套输入法 | 说明 |
|---|---|---|
| 0.1.x | 同文输入法03 `appearance-03-my2` 及以上（`com.osfans.trime03`） | 主题格式 `config_version: 3.0` |

输入法侧的复核（`.revision` + 内容哈希）从 `com.osfans.trime03` 的 D5 版本开始才有；更早的输入法不会自动重载，改完需要切换一下键盘或重启输入法。编辑器写出的 `.revision` 只是给输入法看的一行计数，删除无害。

## 构建

```bash
export JAVA_HOME=/path/to/jdk21      # AGP 8.x / Kotlin 2.x 需要 JDK 21
./gradlew assembleDebug              # 产物 app/build/outputs/apk/debug/
```

发布包：

```bash
./gradlew distApk                    # 产物 dist/同文编辑器-<版本>.apk
```

### 发布签名

`keystore.properties` 与 `keystore/` **不进仓库**。换机器要自己生成一份，否则新包无法覆盖安装旧包——签名不同的 APK 在 Android 眼里是另一个应用：

```bash
keytool -genkeypair -v -keystore keystore/trime-editor.jks -alias trime-editor \
  -keyalg RSA -keysize 2048 -validity 10000
```

然后写 `keystore.properties`：

```properties
storeFile=keystore/trime-editor.jks
storePassword=…
keyAlias=trime-editor
keyPassword=…
```

文件不存在时 `assembleRelease` 会退回 debug 签名并打印一行警告——不是可以发布的包，但能让干净检出也跑通。

## 工程结构

5 个模块：`app`（界面与活动）、`editor-ui`（可复用页面与控件）、`theme-runtime`（需要 Android 的主题运行时：加载、颜色、输入法渲染类）、`theme-core`（纯 JVM：解析、读写、模型）、`theme-editor`（预留）。

主题的读写全在 `theme-core`，且都以「行」为单位做文本级修改，不重新序列化 YAML。

## 测试

```bash
./gradlew :theme-core:test    # 解析/写盘/目录/闸门（335 例，纯 JVM）
./gradlew :app:testDebugUnitTest   # 诊断跳转映射、崩溃日志文本（20 例，纯 JVM）
./gradlew lintDebug
```

`theme-core/src/test/resources/tongwenfeng.trime.yaml` 是真实主题的整份夹具（202,005 字节 / CRLF），`.gitattributes` 里用 `-text` 固定住行尾——它一旦被归一成 LF，「写盘不换行尾」那条断言就变成了同义反复。

`app` 的单测只覆盖不碰 Android 的那部分：`DiagnosticsRouter` 把「finding 的路径」映成「该开哪个页」，`CrashLog` 的 `format`/`shouldRotate` 决定报告长什么样、什么时候整份丢弃。需要 `Context`、`filesDir`、真实主题的验证都在设备上做。
