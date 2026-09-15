# GoldPriceBar

macOS 状态栏与 Windows 任务栏黄金积存金实时价格监控应用。

积存金数据来源于京东金融。https://gold-price-pro.pf.jd.com/

## 功能

### 实时金价显示

- 启动后在状态栏显示当前黄金价格，默认展示 `浙商积存金 xx.xx`
- 接口取值失败时显示 `0.00`

### 数据源切换

- 菜单支持切换 `浙商积存金` / `民生积存金` / `工商积存金`
- **单击桌面看板娘**即可依次切换：`浙商 → 民生 → 工银 → 浙商`，状态栏、看板娘牌子与行情详情同步更新，并以气泡提示当前数据源
- 选择结果会持久化，重启后恢复上次的数据源

### 成本价与红绿基准

- 可为每个数据源**分别**设置买入成本价（浙商 / 民生 / 工银 各一个）
- 设定了成本价后，红绿以成本价为准：**高于成本价显示红色，低于成本价显示绿色**，与成本价持平显示中性色
- 未设置成本价时沿用原有语义（相对昨收上涨显示红色、下跌显示绿色）
- 该红绿基准同时作用于状态栏、看板娘牌子与 Windows 任务栏价格条，并决定看板娘的表情（跌破成本价显示难过）
- 启动时会弹出成本价设置窗口（预填上次的值，可直接跳过）；也可随时通过菜单「成本价 → 设置成本价…」修改
- 菜单「成本价 → 启动时提示设置成本价」可关闭每次启动的提示

### 积存金数据源接口

| 数据源 | 简称 | 接口 |
|--------|------|------|
| 浙商积存金 | 浙商 | `https://api.jdjygold.com/gw2/generic/produTools/h5/m/getGoldPrice?goldCode=CZB-JCJ` |
| 工商积存金 | 工银 | `https://api.jdjygold.com/gw2/generic/produTools/h5/m/getGoldPrice?goldCode=ICBC-JCJ` |
| 民生积存金 | 民生 | `https://ms.jr.jd.com/gw2/generic/CreatorSer/newh5/m/getFirstRelatedProductInfo?reqData=%7B%22circleId%22%3A%2213245%22%2C%22invokeSource%22%3A5%2C%22productId%22%3A%2221001001000001%22%7D` |

- 浙商与工商同为京东金价行情接口，仅 `goldCode` 不同，响应结构一致（`lastPrice` / `raise` / `raisePercent`），共用同一套解析逻辑
- 民生接口为独立结构，返回字符串型字段（`minimumPriceValue` / `rateValue` / `dayFluctuateNum`）
- 涨跌幅按截断（非四舍五入）保留两位小数；接口失败时金价显示 `0.00`

### 刷新频率设置

- 支持设置刷新间隔：`1 秒`、`2 秒`、`5 秒`、`10 秒`
- 当前选中频率会打勾标记

### 价格提醒

- 支持设置 **高价提醒**（金价 ≥ 设定值时提醒）
- 支持设置 **低价提醒**（金价 ≤ 设定值时提醒）
- 触发时弹出悬浮 Toast 通知窗口 + 系统提示音
- 每次穿越阈值只通知一次，价格回到正常范围后自动复位
- 支持修改、清除单个提醒或一键清除所有提醒

### 设置持久化

- macOS 使用 `UserDefaults`，Windows 使用 `%LocalAppData%/GoldPriceBar/settings.json`
- 数据源、刷新频率、高低价提醒、各数据源成本价及浮动人物状态都会持久化
- 退出重启后自动恢复上次设置

### Windows 常驻价格条

- Windows 通知区域保留应用图标，并在主任务栏通知区域旁始终显示原生紧凑价格条
- 价格条显示数据源、金价及涨跌幅，悬停或左键打开行情详情，右键打开完整设置菜单
- 价格条整体按成本价红绿基准着色，与状态栏、看板娘保持一致
- 按住价格条左键可跨屏拖动；靠近屏幕边缘时自动吸附，并始终限制在任务栏以外的可见工作区内
- 拖动位置仅在本次运行期间保留，重启后恢复到主任务栏附近；显示器或 DPI 变化时会自动修正当前位置

### 桌面浮动人物

- 默认在桌面右下角显示 `240×240` 的可拖动透明人物，可在“浮动窗口”菜单选择 `220/240/260` 三档尺寸
- 金价跌破成本价（未设置成本价时按当日下跌）显示悲伤姿态，其余情况显示开心姿态
- 人物牌子分两行：上方为数据源简称（`浙商` / `民生` / `工银`），下方为对应数据源的价格；整块牌子按成本价红绿基准着色，并会根据数字长度自动缩放
- 人物会持续进行较明显但柔和的呼吸、上下漂浮和轻微摇摆，每 10–15 秒自动切换动作并停留 3 秒；点击时会从当前情绪的 8 个动作中随机选择
- 开心动作包含摆手、眨眼、庆祝、爱心、困倦、鼓掌、跳舞和点赞；难过动作包含撅嘴、躲藏、跺脚、流泪、转身、叹气、捂脸和发抖
- 人物会在点击、快速涨跌、入睡和唤醒时显示中文气泡，并每 45–90 秒随机说一句与当前心情匹配的话；气泡复用单一窗口，不持续创建额外视图
- 单击人物会切换到下一个数据源并提示当前数据源，双击会根据行情表演庆祝或生气；3 秒内连续点击到第 3 次会逐渐不耐烦，第 4 次躲到价格牌后，随后进入 8 秒彩蛋冷却
- 相邻两次有效报价变化达到 `1.00 元` 时会立即切换到庆祝或沮丧动作，冷却时间为 45 秒；切换数据源后会清空本次运行中的比较基线
- 快速拖动松手后人物会按末段速度产生最多 120pt 的短距离惯性，并通过落地、晃动和回弹稳定下来；所有落点都会限制在当前屏幕可见范围内
- 将人物拖到当前屏幕左侧或右侧边缘后，会吸附为“价格牌 + 偷看脑袋”的紧凑模式；向屏幕内拖动超过 48pt 即恢复完整人物
- 贴边人物每 4.5–5.5 秒轻量眨眼一次，点击与连击会通过连续眨眼和弹性探头反馈且不会自动展开；贴边方向、屏幕和纵向位置会在重启后恢复
- 5 分钟无人操作后人物会进入困倦姿态并显示 `Zzz…`，休眠期间每 5 秒轻量检查一次用户活动，操作恢复后自动醒来；屏幕休眠、会话锁定或低电量模式下会暂停不必要的动画
- 完整人物运行时图片为 512×512，贴边眨眼帧为 512×320，并使用 10MB 上限的按需缓存控制常驻内存
- 显示状态、人物尺寸和拖动位置会在重启后恢复

## 系统要求

- macOS：macOS 13.0 (Ventura) 及以上、Swift 6.2+
- Windows：Windows 10 22H2 / Windows 11 x64；便携包已自包含 .NET 10，无需单独安装运行时

## 代码结构

```
goldPriceBar/
├── Package.swift                              # Swift Package 工程定义
├── AppIcon.icns                               # 应用图标
├── Sources/goldPriceBar/
│   ├── goldPriceBar.swift                     # 应用主实现
│   ├── GoldPriceTrend.swift                   # 红绿基准（成本价优先，回退涨跌）
│   ├── CostPriceDialog.swift                  # 成本价设置窗口与持久化
│   ├── FloatingCharacter.swift                # 浮动人物窗口与交互
│   └── Resources/FloatingCharacter/           # 人物姿态图片
├── Artwork/FloatingCharacterSources/          # 浮动人物高清源素材
├── Artwork/FloatingCharacterActionSources/    # 新增动作的绿幕源图与透明母版
├── Artwork/FloatingCharacterDockedSources/    # 贴边人物生成源图与透明母版
├── Tests/goldPriceBarTests/                   # 浮动人物逻辑测试
├── Windows/
│   ├── GoldPriceBar.Core/                     # 跨 UI 的接口、解析、设置与行为策略
│   ├── GoldPriceBar.Windows/                  # .NET 10 WPF Windows 客户端
│   └── GoldPriceBar.Core.Tests/               # Windows 核心逻辑测试
├── scripts/
│   ├── build-dmg.sh                           # DMG 打包脚本
│   └── build-windows.ps1                      # Windows 便携包脚本
└── dist/                                      # 打包产出目录
    ├── GoldPriceBar.app                       # macOS 应用包
    └── GoldPriceBar-1.0.2.dmg                 # DMG 安装包
```

## macOS 运行方式

### 方式一：Xcode 运行

1. 使用 Xcode 打开项目目录中的 `Package.swift`
2. 选择 `goldPriceBar` 可执行目标
3. 直接运行

### 方式二：命令行运行

```bash
swift build
.build/debug/goldPriceBar
```

## 打包发布

运行打包脚本，自动完成 release 编译 → 创建 .app 包 → 生成 DMG：

```bash
bash scripts/build-dmg.sh
```

产出文件位于 `dist/` 目录：

- `GoldPriceBar.app` — macOS 应用包
- `GoldPriceBar-1.0.2.dmg` — DMG 安装包（含 Applications 快捷方式，可拖拽安装）

## Windows 开发与发布

在 Windows 10/11 安装 .NET 10 SDK 后运行：

```powershell
dotnet run --project Windows/GoldPriceBar.Windows/GoldPriceBar.Windows.csproj
```

执行测试并生成无需安装的 x64 便携 ZIP：

```powershell
./scripts/build-windows.ps1
```

产出文件为 `dist/GoldPriceBar-Windows-x64-1.0.3.zip`。GitHub Actions 中的 `Windows Build` 工作流也会自动测试并上传该产物。

## 技术实现

| 模块 | 技术方案 |
|------|----------|
| macOS UI | AppKit（NSStatusBar + NSMenu） |
| Windows UI | .NET 10 WPF + NotifyIcon |
| 网络请求 | URLSession / HttpClient + async/await |
| 数据解析 | JSONDecoder / System.Text.Json |
| 通知提醒 | 双端自定义悬浮 Toast 窗口 |
| 数据持久化 | UserDefaults / JSON 原子写入 |
| 并发安全 | Swift Strict Concurrency / WPF Dispatcher |
