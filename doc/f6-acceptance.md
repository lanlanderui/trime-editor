# F6 收敛验收报告（方向 B · 舞台）

> 日期：2026-11-07 21:0x
> 设备：平板 `J7JBXCLVPFTSQKFA`（2120×3000，density 2.5），皮肤 `m3-expressive`
> 产物：`dist/同文编辑器-0.4.0.apk`（5,853,803 字节，versionCode 4，release 签名）

## 1. 已完成

### 1.1 对比度抽测（WCAG 2.1，从色板 token 直接算）

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

### 1.2 打包

- `./gradlew distApk` → `dist/同文编辑器-0.4.0.apk`
- versionCode 3 → 4，versionName 0.3.0 → 0.4.0
- 签名与 0.3.0 同一份 `keystore/trime-editor.jks`（可覆盖安装）

## 2. 待补（设备在 20:58 掉线，`adb devices` 为空）

| 项 | 说明 | 状态 |
|---|---|---|
| 亮/暗 × 9 页全量截图 | 只拿到 `f6_light_workbench.png`、`f6_light_preview.png` | 2 / 18 |
| G-05 21–24dp 小字平板实测 | 最小字 8.5sp（键名）≈ 21px，需截图量实际字高 | 待测 |
| 性能（冷启动 / 页面切换） | `am start -W` 读 TotalTime，脚本已就绪（`.tmp/f6_perf.py`） | 待测 |
| `proof` / `m3-tonal` 两套皮肤回归 | 各拍工作台 / 配色 / 键盘列表，确认卡片化在旧皮肤下不崩 | 待测 |
| 手机（411dp）图 | B1/B2/B3/F5 的手机图一直欠着（没有手机设备） | 待补 |

## 3. 复跑命令

```bash
# 设备连上之后（脚本都在 .tmp/，已 gitignore）
export PATH=/d/AndroidSdk/platform-tools:$PATH MSYS_NO_PATHCONV=1
python .tmp/f6_shots.py        # 9 页 × 亮/暗，文件名 f6_<mode>_<page>.png
python .tmp/f6_perf.py         # 冷启动 3 轮取中位 + 五页热启动
python .tmp/f6_contrast.py     # 对比度表（无需设备）

# proof / tonal 回归：外观页切皮肤后重拍三页
adb shell am start -n com.osfans.trime.editor.debug/com.osfans.trime.editor.ui.AppearanceActivity
```

## 4. 判定

「舞台」皮肤本身：对比度全绿、`0.4.0` 已打包；三屏（B1/B2/B3）与五页（F5）
在平板上逐屏看过，卡片 / 胶囊 / 大标题的语言一致。

**尚未形成完整结论**：全量截图、G-05、性能、旧皮肤回归四项依赖设备，
设备掉线后未完成——在补齐之前，本报告只能作为「部分验收」。
