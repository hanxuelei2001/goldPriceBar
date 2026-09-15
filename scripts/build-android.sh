#!/bin/bash
#
# 构建 Android 版「金价状态栏」APK。
#
#   bash scripts/build-android.sh                  # release APK（默认，可直接安装）
#   bash scripts/build-android.sh debug            # debug APK
#   bash scripts/build-android.sh release --install  # 构建并通过 adb 安装到已连接设备
#
# 工具链都在工程目录下的隐藏目录中（.tooling / .android-sdk / .gradle-home），
# 不会污染 ~/.gradle 或 ~/Library/Android/sdk。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/android-setup.sh
source "${SCRIPT_DIR}/android-setup.sh"

VARIANT="${1:-release}"
INSTALL_FLAG="${2:-}"

case "${VARIANT}" in
    release|debug) ;;
    *) echo "❌ 未知的构建类型：${VARIANT}（可选 release / debug）" >&2; exit 1 ;;
esac

android_setup

# ── 签名密钥 ────────────────────────────────────────────────────────────────
# 1) ANDROID_KEYSTORE_PATH：CI 从 Secrets 解出的正式密钥
# 2) 本地且工程内有 keystore/goldpricebar.jks：直接用（目录已 gitignore）
# 3) 本地且没有：生成一把自签名密钥，之后每次构建复用，保证能覆盖安装
# 4) CI 且没有：不生成，交给 Gradle 回退到 debug 签名并告警
#    （每次 CI 运行新生成的密钥都不同，那样打出来的包反而无法覆盖安装）
if [ -n "${ANDROID_KEYSTORE_PATH:-}" ]; then
    if [ ! -f "${ANDROID_KEYSTORE_PATH}" ]; then
        echo "❌ ANDROID_KEYSTORE_PATH 指向的密钥不存在：${ANDROID_KEYSTORE_PATH}" >&2
        exit 1
    fi
    echo "🔐 使用外部签名密钥：${ANDROID_KEYSTORE_PATH}"
else
    KEYSTORE="${ANDROID_DIR}/keystore/goldpricebar.jks"
    if [ -f "${KEYSTORE}" ]; then
        echo "🔐 使用工程内密钥：${KEYSTORE}"
    elif [ -n "${CI:-}" ] && [ "${CI}" != "false" ]; then
        echo "⚠️  CI 环境未提供 ANDROID_KEYSTORE_PATH，release 包将回退为 debug 签名" >&2
        echo "    如需发布可覆盖安装的正式包，请配置 ANDROID_KEYSTORE_BASE64 等 Secrets" >&2
    else
        echo "🔑 生成自签名密钥（仅用于本地安装）…"
        mkdir -p "$(dirname "${KEYSTORE}")"
        "${JAVA_HOME}/bin/keytool" -genkeypair \
            -keystore "${KEYSTORE}" \
            -alias goldpricebar \
            -keyalg RSA -keysize 2048 -validity 10950 \
            -storepass goldpricebar -keypass goldpricebar \
            -dname "CN=GoldPriceBar, OU=Personal, O=GoldPriceBar, L=Beijing, ST=Beijing, C=CN"
        echo "✅ 密钥已生成"
    fi
fi

# ── 版本号 ──────────────────────────────────────────────────────────────────
# 与 app/build.gradle.kts 读同一份 version.properties，保证产物文件名与包内版本一致。
VERSION="$(sed -n 's/^versionName=//p' "${ANDROID_DIR}/version.properties" | tr -d '[:space:]')"
if [ -z "${VERSION}" ]; then
    echo "❌ 无法从 ${ANDROID_DIR}/version.properties 读取 versionName" >&2
    exit 1
fi

# ── 构建 ────────────────────────────────────────────────────────────────────
TASK=":app:assemble$(tr '[:lower:]' '[:upper:]' <<< "${VARIANT:0:1}")${VARIANT:1}"
echo "🔨 构建 ${TASK}（v${VERSION}）…"
(cd "${ANDROID_DIR}" && "${GRADLE_BIN}" --console=plain "${TASK}")

APK_PATH="${ANDROID_DIR}/app/build/outputs/apk/${VARIANT}/app-${VARIANT}.apk"
if [ ! -f "${APK_PATH}" ]; then
    echo "❌ 未找到产物：${APK_PATH}" >&2
    exit 1
fi

DIST_DIR="${PROJECT_DIR}/dist"
mkdir -p "${DIST_DIR}"
if [ "${VARIANT}" = "release" ]; then
    APK_NAME="GoldPriceBar-Android-${VERSION}.apk"
else
    APK_NAME="GoldPriceBar-Android-${VERSION}-debug.apk"
fi
cp "${APK_PATH}" "${DIST_DIR}/${APK_NAME}"

echo ""
echo "🎉 构建完成：${DIST_DIR}/${APK_NAME}"
echo "   大小：$(du -h "${DIST_DIR}/${APK_NAME}" | cut -f1)"
echo ""

# release 走到 debug 签名时明确提示，避免发布出去的包无法覆盖安装。
SIGNER="$("${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}/apksigner" verify --print-certs "${DIST_DIR}/${APK_NAME}" 2>/dev/null \
    | sed -n 's/^Signer #1 certificate DN: //p' || true)"
if [ -n "${SIGNER}" ]; then
    echo "🔏 签名者：${SIGNER}"
    case "${SIGNER}" in
        *Debug*|*debug*) echo "⚠️  当前使用 debug 签名，正式发布请配置 ANDROID_KEYSTORE_PATH" >&2 ;;
    esac
    echo ""
fi

if [ "${INSTALL_FLAG}" = "--install" ]; then
    ADB="${ANDROID_HOME}/platform-tools/adb"
    if [ -x "${ADB}" ]; then
        echo "📲 安装到已连接的设备…"
        "${ADB}" install -r "${DIST_DIR}/${APK_NAME}"
    else
        echo "⚠️  未找到 adb，跳过安装" >&2
    fi
fi
