# 金价状态栏 · Android

积存金实时行情监控 **Android 版**：没有看板娘，价格直接常驻在系统状态栏（通知栏），
像网速指示器一样用「箭头 + 数字」表达涨跌：**红色 ↑ 表示赚了，绿色 ↓ 表示亏损**。
下拉通知栏即可在 **工银 / 民生 / 浙商** 之间切换数据源。

数据来源与 macOS / Windows 版完全一致（京东金融行情接口）。

## 与 macOS / Windows 版的差异

| 项目 | 说明 |
|------|------|
| 看板娘 | **已移除**，Android 版只做状态栏显示 |
| 状态栏样式 | `↑ 1049.59` 形式，红色上升 = 高于成本价，绿色下降 = 低于成本价 |
| 数据源切换 | 下拉通知栏，点击通知里的「浙商 / 民生 / 工银」胶囊；也可在 App 内切换 |
| 请求失败 | 保留上一次成功的价格并标记失败（桌面端会直接显示 `0.00`），避免移动网络抖动导致状态栏闪成 0.00 |
| 默认刷新频率 | **5 秒**（桌面端为 1 秒）；1 / 2 / 5 / 10 秒四档全部保留 |
| 关联行情 | 界面可见时随刷新周期更新；退到后台后降频到 60 秒一次，省电省流量 |

其余功能与桌面端一致：数据源切换、刷新频率、成本价（每个数据源独立）、
高低价提醒、启动时提示设置成本价、设置持久化。

## 功能

### 状态栏常驻价格

- 前台服务（`PriceMonitorService`）按设定间隔轮询行情，写入常驻通知
- 收起状态只有一行：箭头 + 价格 + 三个数据源胶囊
- 下拉展开：数据源全称、大号价格、涨跌额与涨跌幅、更新时间与刷新频率、数据源切换按钮
- 通知渠道使用 `IMPORTANCE_LOW`：静默更新，不会每次刷新都响铃或震动

### 红绿基准

- 设定了该数据源的成本价时以成本价为准：**高于成本价 = 红色 ↑，低于成本价 = 绿色 ↓**
- 未设置成本价时回退到当日涨跌方向
- 与桌面端使用同一套判定逻辑（`PriceTrend`）

### 数据源

| 数据源 | 简称 | 接口 |
|--------|------|------|
| 浙商积存金 | 浙商 | `api.jdjygold.com` … `goldCode=CZB-JCJ` |
| 工商积存金 | 工银 | `api.jdjygold.com` … `goldCode=ICBC-JCJ` |
| 民生积存金 | 民生 | `ms.jr.jd.com` … `getFirstRelatedProductInfo` |

浙商与工商共用京东金价行情结构，仅 `goldCode` 不同；民生为独立结构（字符串字段）。

### 价格提醒

- 高价提醒（金价 ≥ 设定值）与低价提醒（金价 ≤ 设定值）
- 触发时弹出高优先级通知 + 震动/提示音
- 每次穿越阈值只提醒一次，价格回到正常范围后自动复位
- 提醒在后台服务中判定，App 未打开也会生效

### 后台运行

- 前台服务 + `specialUse` 类型（无 Android 15 的 `dataSync` 六小时限制）
- 可选「开机自动启动」，重启手机后自动恢复监控
- 「电池优化白名单」引导：加入白名单后锁屏休眠期间也能持续刷新

## 系统要求

- Android 8.0（API 26）及以上
- 编译需要 JDK 17+：脚本会自动挑选可用的 JDK，找不到时下载标准 OpenJDK 17

> ⚠️ 不要用 GraalVM 的 JDK 构建：它的 `java.base` 依赖 `jdk.internal.vm.ci`，
> AGP 编译 Java 源码时执行的 `jlink` 会失败。`android-setup.sh` 已自动跳过 GraalVM。

## 构建

一条命令即可，脚本会自动准备 JDK / Gradle / Android SDK：

```bash
bash scripts/build-android.sh                     # release APK（默认，自签名可直接安装）
bash scripts/build-android.sh debug               # debug APK
bash scripts/build-android.sh release --install   # 构建并通过 adb 安装到已连接设备
```

产物输出到（版本号取自 `Android/version.properties`）：

```
dist/GoldPriceBar-Android-1.0.3.apk          # release
dist/GoldPriceBar-Android-1.0.3-debug.apk    # debug
```

首次运行会在工程目录下创建（均已加入 `.gitignore`）：

- `.tooling/` — Gradle 发行包、Android 命令行工具、OpenJDK 17
- `.android-sdk/` — Android SDK（platform-tools、platforms;android-35、build-tools;35.0.0）
- `.android-home/` — AGP 的 `.android` 目录（analytics、调试密钥库）
- `.gradle-home/` — Gradle 依赖缓存

不会写入 `~/Library/Android/sdk`、`~/.gradle` 或 `~/.android`。

### 版本号

`Android/version.properties` 是 Android 版本号的唯一来源：`app/build.gradle.kts` 把它写进 APK 清单，
构建脚本与 CI 用它决定产物文件名。发版时与下面两处一起改，保持三端同一版本线：

- `scripts/build-dmg.sh` 的 `VERSION`
- `Windows/Directory.Build.props` 的 `<Version>`（以及 `scripts/build-windows.ps1` 里的 ZIP 名）

### 镜像与开关

默认使用腾讯 / 阿里云镜像（本机实测约 10MB/s，官方源约 40KB/s）：

| 用途 | 默认 | 覆盖变量 |
|------|------|----------|
| Gradle 发行包 | `mirrors.cloud.tencent.com/gradle` | `GRADLE_MIRROR` |
| Android SDK | `mirrors.cloud.tencent.com/AndroidSDK` | `ANDROID_SDK_MIRROR` |
| Maven 依赖 | `maven.aliyun.com` | `GRADLE_CHINA_MIRRORS=false` 关闭，直接走 google()/mavenCentral() |

海外环境（GitHub Actions）设 `GRADLE_CHINA_MIRRORS=false` 即可；CI 里还额外用
`GRADLE_MIRROR=https://services.gradle.org/distributions` 从官方源取 Gradle。

脚本是幂等的：`JAVA_HOME` / `ANDROID_HOME` 已由 CI 的 `setup-java`、`setup-android` 提供、
且 SDK 组件齐全时，一个字节都不会重新下载（CI 只额外装一次 Gradle）。

### 用 Android Studio 打开

直接用 Android Studio 打开 `Android/` 目录即可。首次同步前在
`Android/local.properties` 里写入 `sdk.dir=<你的 Android SDK 路径>`。

`Android/gradle/wrapper/gradle-wrapper.properties` 里的 `distributionUrl` 指向 Gradle 官方源
（GitHub Actions 使用）。国内网络下如果卡住，可以换成：

```properties
distributionUrl=https\://mirrors.cloud.tencent.com/gradle/gradle-8.11.1-bin.zip
```

### 只跑单元测试

```bash
bash scripts/test-android.sh
```

纯 JVM 测试，不需要模拟器或真机，覆盖解析、红绿基准、状态栏文案、设置解析与状态仓库。

### 签名

`scripts/build-android.sh` 按下面的优先级选密钥：

| 场景 | 行为 |
|------|------|
| 设置了 `ANDROID_KEYSTORE_PATH`（CI 从 Secrets 解出） | 用指定的正式密钥 |
| 本地且 `Android/keystore/goldpricebar.jks` 存在 | 用工程内自签名密钥 |
| 本地且不存在 | 用 `keytool` 生成一把并复用，保证能覆盖安装 |
| CI（`CI=true`）且没有密钥 | **不生成**，回退到 debug 签名并告警 |

最后一种情况是为了避免每次 CI 都生成一把新密钥——那样打出来的包反而互相覆盖不了安装。
正式发布请在仓库 Secrets 里配置：

| Secret | 说明 |
|--------|------|
| `ANDROID_KEYSTORE_BASE64` | keystore 文件的 base64（`base64 -i release.jks \| pbcopy`） |
| `ANDROID_KEYSTORE_PASSWORD` | keystore 口令 |
| `ANDROID_KEY_ALIAS` | 别名 |
| `ANDROID_KEY_PASSWORD` | 别名口令 |

四个都不配也能出包（走 debug 签名），但用户无法覆盖安装历史版本。

## 安装

1. 把 `dist/GoldPriceBar-Android-1.0.3.apk` 传到手机并安装（需允许「安装未知来源应用」）
2. 首次打开时授予**通知权限**——状态栏价格依赖它；拒绝后状态栏不会显示任何内容
3. 建议点击「电池优化白名单」并允许，否则手机休眠后行情会暂停刷新

## 代码结构

```
Android/
├── settings.gradle.kts / build.gradle.kts / gradle.properties
├── gradle/libs.versions.toml                  # 版本目录
└── app/
    ├── build.gradle.kts
    └── src/
        ├── main/
        │   ├── AndroidManifest.xml
        │   ├── java/com/goldpricebar/monitor/
        │   │   ├── GoldPriceApp.kt            # Application：初始化设置与通知渠道
        │   │   ├── data/
        │   │   │   ├── GoldProvider.kt        # 浙商 / 民生 / 工银 与接口地址
        │   │   │   ├── Models.kt              # PriceInfo / QuoteRow / MarketData
        │   │   │   ├── PriceTrend.kt          # 红绿基准（成本价优先）
        │   │   │   ├── GoldPriceParser.kt     # 纯函数 JSON 解析
        │   │   │   ├── GoldPriceClient.kt     # HttpURLConnection 客户端
        │   │   │   └── PriceRepository.kt     # 服务 ↔ 界面的 StateFlow
        │   │   ├── settings/
        │   │   │   ├── SettingsStore.kt       # SharedPreferences 持久化
        │   │   │   └── PriceInput.kt          # 价格解析/格式化
        │   │   ├── service/
        │   │   │   ├── PriceMonitorService.kt # 前台服务与轮询
        │   │   │   └── NotificationFactory.kt # 状态栏通知 + 数据源切换胶囊
        │   │   ├── receiver/BootReceiver.kt   # 开机自启
        │   │   └── ui/
        │   │       ├── MainActivity.kt        # 主界面
        │   │       └── StatusBarPresentation.kt # 箭头/文案/配色
        │   └── res/                           # 布局、主题、通知 RemoteViews
        └── test/java/com/goldpricebar/monitor/ # JVM 单元测试
```
