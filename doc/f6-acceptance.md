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
