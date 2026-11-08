# 同文编辑器 · Android 工程

> 产品介绍与截图见[仓库根目录的 README](../README.md)。
> 本文件是 Android 工程的技术文档，下面的命令都在当前目录（`android/`）下执行。

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


## 外观

0.3.0 起界面是「一套 token + 皮肤 overlay」；**0.4.0 起只保留两套皮肤**。皮肤不含任何行为，换皮肤只换资源：

| 皮肤 | id | 说明 |
|---|---|---|
| 层叠 | `m3-tonal` | **默认**。Material You 的取色与色调，卡片分组，圆角 16 / 12 / 20 |
| 舞台 | `m3-expressive` | 可选。暖陶土色板、大字号、色块头、悬浮底栏 |

选哪一套存在 `EditorPrefs.skinId`，在「外观」页切换。切换用 `theme.applyStyle()` 而不是
`setTheme()`——三个快捷入口在清单里挂了半透明主题，`setTheme` 会把它们换成白屏；
切完当前页 `recreate()`，返回栈里的页面在 `onResume` 自检后重建。
旧版本存下的 `proof` 不再是合法皮肤 id，会由 `Skin.of` 回退到默认的层叠。

三件事是单一来源：

- **token**：`editor-ui/src/main/res/values/attrs.xml` 声明 `ui_*` 属性；三张 overlay 在
  `app/src/main/res/values/skins.xml` 给值，色板在 `ui_palette_*.xml`（`values-night` 各一份）。
  尺寸（gutter、色块边长、hex 列宽）仍是 `editor-ui/.../values/ui_dimens.xml`，平板在
  `values-sw600dp` 覆盖。
- **页面**：布局与自绘 View 只写 `?attr/ui_*`，不写颜色字面量；代码侧唯一出口是
  `UiTokens.color / dimen / card / field / swatch`。
- **结构差异**：只允许 `SkinCaps` 的三个开关（`stageHeader` / `floatingDock` / `segmented`），
  由基类 `EditorActivity.applyChrome()` 统一施加（它认工具栏和 `ui_dock` 标签），页面里不出现
  「这是哪套皮肤」的判断。

平板（≥600dp）仍是「目录栏 + 内容」两栏，判定只此一处（`ui_two_pane`）。目录栏的章是九个页面、
节取当前页的真实分组（`PreferenceOutline` 从偏好树里读，不另存一份清单），点节滚到那一行，点章换页。

## 与输入法的版本配对

编辑器和输入法共用主题文件格式，格式跟的是**输入法的版本**，不是编辑器的：

| 编辑器 | 配套输入法 | 说明 |
|---|---|---|
| 0.4.x | 原版同文输入法（`com.osfans.trime`） | 方向 B「舞台」皮肤；包名改为 `com.trime.editor` |
| 0.3.x | 原版同文输入法（`com.osfans.trime`） | 只改界面，主题格式同 0.2.x |
| 0.2.x | 原版同文输入法（`com.osfans.trime`） | 主题格式 `config_version: 3.0` |
| 0.1.x | 同文输入法03 `appearance-03-my2`（`com.osfans.trime03`） | 已不再推进，仅保留兼容 |

「回到输入法」认识这四个包（原版 / 原版调试 / 03 / 03 调试），**原版优先**：装了原版就打开原版，只装了 03 的设备仍能打开 03。选包走系统的已启用输入法列表，系统默认输入法优先于列表顺序。

输入法侧的复核（`.revision` + 内容哈希）从 `com.osfans.trime03` 的 D5 版本开始才有；更早的输入法不会自动重载，改完需要切换一下键盘或重启输入法。编辑器写出的 `.revision` 只是给输入法看的一行计数，删除无害。

## 输入法按键快捷入口

编辑器包名是 **`com.trime.editor`**（调试包 `com.trime.editor.debug`）。在输入法主题里给按键写 `command: run`，`option` 用 `包名/.类名`：

```yaml
DynamicColor:
  command: run
  label: "更新配色"
  option: "com.trime.editor/.QuickApplyActivity"    # 按壁纸重算明暗两套配色
SaveDynamicColor:
  command: run
  label: "收藏配色"
  option: "com.trime.editor/.QuickSaveActivity"     # 把当前配色存进收藏
```

- 原版同文输入法用 `ComponentName.unflattenFromString` 展开 `包名/.类名`，
  所以要写**完整包名**。装的是调试包就把两段里的 `com.trime.editor` 换成
  `com.trime.editor.debug`；两个包能共存，配置指向哪份就由哪份执行。
- 入口没有界面，做完只留一句 toast。`option` 带不了 extra，主题文件按
  「显式 `theme_id` → 上次编辑的主题 → 标准 rime 目录第一份」依次找。
- **首次使用**：Android 11+ 要给编辑器开「所有文件访问」。没授权时按一下会
  直接跳到系统授权页，开完再按一次即可；标准 `/storage/emulated/0/rime`
  目录里有主题时会自动认出来，不必先打开编辑器选目录。

## 主题管理（工作台）

底部 dock 四项按设计稿：**新建**（从随包模板复制一份，只改 `name:` 一行）、
**切换**（列出目录里其余主题）、**导入**（SAF 选 `.trime.yaml`）、**输入法**。
**长按主题**（英雄区或列表行）可重命名、复制、在文件管理器中打开；
**长按 dock** 打开「目录 / 刷新 / 外观 / 使用帮助」等次级入口；
英雄区右上角另有「◑ 外观」胶囊，点一下直接切界面皮肤（两套皮肤下都可见）。

所有文件级动作集中在 `workbench/ThemeActions`：文件名固定 `<名字>.trime.yaml`，
改名 / 复制只做字节级搬运 + 一次 `name:` 行改写，注释、缩进、CRLF 原样保留，
目标已存在时自动顺延或拒绝，**不覆盖**任何已有主题。

### 内置方案

工作台「其余主题」上方有一张**内置方案**卡片，点「保存到本地」把随包的
**全量参数模版**复制一份到当前主题目录，随后直接打开编辑。模版源文件在
`theme-core/src/main/assets/templates/template.trime.yaml`（LF、922 行、39 KB，
收录当前版本会读取的全部参数），app 通过 `assets.srcDirs` 直接打包这份目录，
**不复制第二份**，改一处两边同步。另存时只改第一处 `name:` 行的值、连行内
注释一起保留，其余字节不动；「新建」按钮用的是同一份模版。

模板由用户手工维护，别用脚本覆盖；`theme-core` 的 `BuiltinTemplateTest` 会
检查它能被编辑器自己的 YAML 解析器读开、顶层段齐全、色键表完整。

模板里的 `DynamicColor` / `SaveDynamicColor` 两个动作指向
`com.trime.editor/.QuickApplyActivity` 与 `/.QuickSaveActivity`（正式包）。
装的是调试包时把包名换成 `com.trime.editor.debug`；模板内已写了这行注释。

键盘编辑页舞台右上角的「▤ 完整 / ⌨ 仅键盘」可切掉候选栏与底部留白的示意带子；
键盘本身的宽度与绘制不变，预览仍然与输入法同宽。

## 构建

```bash
export JAVA_HOME=/path/to/jdk21      # AGP 8.x / Kotlin 2.x 需要 JDK 21
./gradlew assembleDebug              # 产物 app/build/outputs/apk/debug/
```

发布包：

```bash
./gradlew distApk                    # 产物 dist/同文编辑器-<版本>.apk
```

发布包的 applicationId 是 `com.trime.editor`，调试包多一个 `.debug` 后缀，
两份可以同时安装。**0.4.0 起不再沿用上游的 `com.osfans.trime.editor`**：
`osfans` 是原版同文输入法作者的标识，编辑器是独立作品；从上游抽出来的库
代码仍保留 `com.osfans.trime.data.theme` / `com.osfans.trime.ime` 的归属。
换包名等于换应用，从旧包名升级需要先卸载旧包。

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
