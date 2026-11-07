# F6 收敛验收报告（方向 B · 舞台）

> 日期：2026-11-07 21:2x
> 设备：平板 `J7JBXCLVPFTSQKFA`（物理 2120×3000，实测横屏 3000×2120，
> 400dpi / density 2.5），皮肤 `m3-expressive`
> 产物：`dist/同文编辑器-0.4.0.apk`（5,853,803 字节，versionCode 4，release 签名）

## 1. 对比度抽测（WCAG 2.1，从色板 token 直接算）

舞台 · 亮色（正文阈值 4.5:1，标签 3:1）：

| 组合 | 比值 | 判定 |
|---|---|---|
| ink on paper | 16.35 : 1 | OK |
| ink_2 on paper | 10.64 : 1 | OK |
| muted on paper | 8.88 : 1 | OK |
| ink on card | 14.65 : 1 | OK |
| muted on card | 7.96 : 1 | OK |
| on_primary on primary | 6.43 : 1 | OK |
| on_pcontainer on pcontainer | 13.24 : 1 | OK |
| primary on paper（分组标签） | 6.13 : 1 | OK（≥3） |
| rule_strong on card（1dp 描边） | 1.45 : 1 | OK（≥1.2，非文字） |

舞台 · 暗色：正文最低 7.70（on_primary on primary），muted on card 9.64，
primary on paper 10.90，全部达标。

层叠（不回归抽测）：亮 6.44–16.23、暗 7.71–14.35，全部达标。
校样（不回归抽测）：`proof_muted on proof_sheet` 亮 3.75 / 暗 4.08，
**低于 4.5**——这是改造前就有的值，本次未动校样色板（旧皮肤的价值是「回到
从前」），记为历史遗留，不在本次范围。

## 2. 全量截图（18 / 18）

`f6_light_*.png` / `f6_dark_*.png`，十页：工作台、预览、样式、配色、布局、
键盘编辑、按键、工具条、候选窗、检查。设备本次为**横屏**（3000×2120），
两栏目录栏常驻，正好覆盖平板的主用方向；竖屏三屏图在 B1/B2/B3 阶段已有。
（键盘编辑页是复核清单时补进来的：`f6_{light,dark}_kbdedit.png`。）

## 3. G-05 小字实测（平板）

400dpi 下 1sp = 2.5px。用 `uiautomator dump` 量 TextView 实际高度（含行距）：

| 元素 | 字号 | bounds 高 |
|---|---|---|
| 大标题「配色」 | 30sp | 88 px |
| 副标题 | 11sp | 38 px |
| 大块 key + hex（两行） | 10sp | 63 px |
| 右值 hex | 9.5sp | 33 px |
| dock 标签 | 9sp | 32 px |
| **行键名（最小）** | **8.5sp** | **29 px**（字高约 21 px） |

`.tmp/f6_g05_rows.png` 放大 2 倍：8.5sp 键名笔画清楚、不与 12sp 名称行粘连，
在 3000px 宽的横屏上仍可逐字辨认。**G-05 通过**，小字没有发散。

## 4. 性能

`am start -W`（工作台冷启动，force-stop 后 3 轮）：406 / 320 / 299 ms，
**中位 320 ms**。

页面热切换（工作台 → 目标页，WARM）：配色 77、布局 48、工具条 83、
候选窗 38、检查 52 ms，全部 < 100 ms。

## 5. `proof` / `m3-tonal` 不回归

`f6_skin_{proof,tonal}_{workbench,colors,layout}.png` 共 6 张：
- 校样：卡片退化成「纸色底 + 1dp 细描边 + 直角」，段控与 dock 走旧主色深绿，
  页面结构完整、无崩溃、无遮挡（见 `f6_skin_proof_colors_s.png`）。
- 层叠：16dp 圆角 M3 卡片、12dp 搜索胶囊，缩略图与文字对齐正常
  （见 `f6_skin_tonal_layout_s.png`）。
- 三套皮肤都验完已切回 `m3-expressive`。

## 6. 打包

- `./gradlew distApk` → `dist/同文编辑器-0.4.0.apk`
- versionCode 3 → 4，versionName 0.3.0 → 0.4.0
- 签名与 0.3.0 同一份 `keystore/trime-editor.jks`（可覆盖安装）

## 7. 手机 411dp（`wm size` 模拟，非真机）

没有手机设备，用平板模拟：**`wm size 1028x1920` + density 400 不变**
（1028 ÷ 2.5 = **411.2dp** 宽），并 `accelerometer_rotation=0`、
`user_rotation=0` 强制竖屏。`sw411dp < 600dp` 时 `ui_two_pane=false`，
单栏布局与真手机一致。验完脚本 `finally` 里 `wm size reset` 并还原旋转设置。

`f6_phone_{light,dark}_*.png` 共 **20 张**（十页 × 亮暗），总览见
`f6_phone_overview_{light,dark}.png`。逐页看过：

- 十页全部单栏，标题 / 卡片 / dock 都在，无裁切无重叠。
- B2 段控 4 项在 411dp 下标签完整；三项 dock 不挤。
- B3 六项 dock（加键 / 删键 / 左移 / 右移 / 撤销 / 重做）全部可读，
  键盘通栏、检查器卡完整。

**发现并修掉一处缺陷**：预览页 `menu/preview.xml` 里「编辑样式 / 编辑配色」
两项 `showAsAction=ifRoom`，在 411dp 下把标题挤成「templa...」。页面内本来
就有「配色方案 / 键盘布局」等跳转卡片，这两项是重复入口，改成 `never`
收进 ⋯ 后标题完整（`.tmp/f6_phone_light_preview.png`）。改完重跑了全量门禁
并**重打了 0.4.0 release 包**。

**已知偏差（不改）**：B2 在 411dp 下预览板（键盘约 300dp 高）占首屏约 45%，
表单首屏只剩三行 + dock，需滚动；设计稿 B2 手机框没画预览板。保留预览板是
「改色立刻看到键盘变」的前提（也保 D2 像素闸门），见偏差表 B2-i。

**真机仍待验**：模拟能覆盖布局与资源配置，但覆盖不了真机的状态栏高度、
手势导航条与字体缩放；接到 411dp 真手机后仍需补一轮。

## 8. 复跑命令

```bash
export PATH=/d/AndroidSdk/platform-tools:$PATH MSYS_NO_PATHCONV=1
python .tmp/f6_shots.py        # 9 页 × 亮/暗（带 pull 大小校验）
python .tmp/f6_kbdedit.py      # 键盘编辑页：平板 + 手机宽度
python .tmp/f6_phone.py        # 手机宽度 10 页 × 亮暗（带 finally 恢复）
python .tmp/f6_preview_fix.py  # 预览页修复后重拍
python .tmp/f6_perf.py         # 冷启动 3 轮取中位 + 五页热启动
python .tmp/f6_contrast.py     # 对比度表（无需设备）
python .tmp/f6_smalltext.py    # G-05 小字 bounds + 放大图
python .tmp/f6_skins.py        # proof / tonal 回归，结束自动切回 expressive
```

## 9. 判定

F6 六项（全量截图、对比度、G-05、性能、旧皮肤不回归、0.4.0 打包）
在平板横屏上全部完成且达标；舞台皮肤亮/暗对比度全绿，三屏与五页语言一致。
手机 411dp 已用 `wm size` 模拟补齐十页 × 亮暗并修掉预览页标题截断。

**唯一未完成项**：真手机 411dp 截图——没有手机设备，模拟覆盖布局但不覆盖
状态栏 / 手势条 / 字体缩放；接到手机后需补一轮，不阻塞当前版本。

**过程记录**：设备在 20:58 掉线过一次（`adb devices` 为空），当时批量截图
脚本没校验 `adb pull` 返回，20 张全打印 `ok` 却只落了 2 张；重连后给
`f6_shots.py` 加了 >100 KB 的文件大小校验，18/18 才真正核实。

## 10. 验收后的用户反馈修复（21:5x）

用户看真机截图后提了两条，都是「入口藏起来 / 点不动」，一并修掉：

**① 预览页统计卡不能点、编辑入口全在 ⋯ 里**
`ThemePreviewActivity.showInfo()` 原来用四行 `pref_proof_value` 只显示数字，
八个编辑动作全在 `menu/preview.xml` 的溢出菜单。改法：

- 四张统计卡各绑跳转：配色方案 / 明暗配对 → 配色页，键盘布局 → 布局列表，
  自定义动作 → 动作列表；右值加「›」提示可点。
- 菜单里没被统计卡覆盖的项，新增「快捷编辑」分组铺成卡片：编辑样式、
  候选窗与预编辑、编辑工具条、静态检查；「回到输入法」仅在该输入法**已启用**
  时出现（本机装了但未启用，与菜单里的可见性判断一致）。
- 诊断条整卡也可点（原来只有右侧「逐条查看」小字能点），改用
  `bg_card_ripple`；`showFailure()` 时清空入口，避免拿旧 themeId 开页面。
- 卡片统一 16dp 边距（原来外层 20dp + 卡自身 16dp = 36dp，看着像报表）。
- ⋯ 菜单保留作备用入口，主路径已全部在页面上。

**② 首页英雄区点不进编辑**
`MainActivity` 只给下面列表的 `ThemeCardAdapter` 绑了 `onOpen`，英雄区
（`heroBlock`）**从来没有点击监听**，而页面提示却写着「点击主题进入编辑」。
改法：`present()` 保存 `currentHero`，`heroBlock.setOnClickListener` 走同一个
`openTheme()`；列表为空 / 无权限 / 未选目录时清掉它。
另外那句提示还承诺「长按可重命名、复制、在文件管理器中打开」——**这套功能
从未实现**（全仓库搜不到任何长按或文件写操作），改成实际行为的说明，不再
给假承诺。

**真机验证**：点英雄区 → `ThemePreviewActivity`；八张卡逐张点过，分别跳
配色 / 布局 / 动作 / 样式 / 候选窗 / 工具条 / 诊断；点诊断条中间 → 诊断页。
全量门禁重跑全绿，`0.4.0` 重打。

## 11. 交互一致性审计与后续优化（22:2x–23:0x）

上面两个 bug 都是「画了入口的样子、代码里没有入口」，所以按同一思路把
十页整体审了一遍：

**审计（无新增死入口）**
- 静态：扫所有布局里 `android:clickable="true"` 且带 id 的 9 个控件，逐个
  对照 Kotlin 里的监听绑定，全部有绑定。
- 动态：十页逐页 `uiautomator dump`，找带 `› / → / 打开 / 编辑 / 查看`
  等入口暗示却没有可点击祖先的文字；命中的全是**说明文字 / 分组标题 / 页面
  标题 / 摘要**，不是入口。

**做了两处一致性优化**
- **方案 chips 换成舞台胶囊语言**：`ThemePreviewActivity.chipBackground`
  原来是「4dp 圆角 + 实心墨 + 描边」（旧 Proof 风格），跟配色页段控、
  诊断页筛选的「全圆角 + 主色实心」不是一套。现在圆角走
  `ui_radius_button`、选中 `ui_primary` + `ui_on_primary`、未选 `ui_card` +
  1dp `ui_rule`，padding 加到 13/7。`ToolBarActivity` 的工具条示意条
  （画的是主题按钮，颜色语义不同）只把 4dp 圆角换成同一个 token，不动配色。
- **⋯ 菜单不再重复一套入口**：`menu/preview.xml` 删掉 7 个编辑项
  （页面卡片已全覆盖），只留「回到输入法」+ `OutlineMenu` 动态加的「目录」；
  `onPrepareOptionsMenu` / `onOptionsItemSelected` 里的对应 `R.id.*` 引用
  一并清掉（item 删了 id 就不生成，留着会编译失败）。

**验证**：全量门禁全绿；静态核对 `preview.xml` 只剩 1 个 item、
`R.id.action_edit_*` 在 Kotlin 中出现 0 次、`ui_radius_button` /
`ui_primary` / `ui_on_primary` 引用到位；`0.4.0` 重打（5,856,585 字节）。

**真机截图待补**：这一轮设备进入锁屏 + 短休眠，`adb` 唤醒后 `screencap`
连续抓到黑屏（锁屏界面 / 灭屏），无法拍 chips 与示意条的改后图。改动都是
纯样式与菜单精简、编译与门禁全过，风险低；平板解锁后补两张图即可。
设备 `screen_off_timeout` 试改过一次，已还原为 300000。
