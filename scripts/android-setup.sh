#!/bin/bash
#
# Android 构建环境准备（被 build-android.sh / test-android.sh source，不要直接执行）。
#
# 全部工具链都装在工程目录下的隐藏目录里，不污染 ~/.gradle 与 ~/Library/Android/sdk：
#   .tooling/       Gradle 发行包与 Android 包压缩包
#   .android-sdk/   Android SDK
#   .gradle-home/   Gradle 依赖缓存
#
# 支持 macOS 与 Linux（GitHub Actions 的 ubuntu runner 跑的是同一套脚本）。
#
# 默认走腾讯 / 阿里云镜像（国内实测 ~11MB/s，dl.google.com 只有 ~40KB/s），
# 可用环境变量覆盖：ANDROID_SDK_MIRROR、GRADLE_MIRROR、MAVEN_MIRROR。

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ANDROID_DIR="${PROJECT_DIR}/Android"
TOOLING_DIR="${PROJECT_DIR}/.tooling"
SDK_DIR="${PROJECT_DIR}/.android-sdk"
GRADLE_HOME_DIR="${PROJECT_DIR}/.gradle-home"
# AGP 默认会写 ~/.android（analytics.settings、debug.keystore）。把它也放到工程目录下，
# 这样整个构建过程只依赖工作区内的路径。
ANDROID_USER_HOME_DIR="${PROJECT_DIR}/.android-home"

GRADLE_VERSION="8.11.1"
COMPILE_SDK="35"
BUILD_TOOLS_VERSION="35.0.0"

GRADLE_MIRROR="${GRADLE_MIRROR:-https://mirrors.cloud.tencent.com/gradle}"
SDK_MIRROR="${ANDROID_SDK_MIRROR:-https://mirrors.cloud.tencent.com/AndroidSDK}"
SDK_FALLBACK_MIRROR="https://dl.google.com/android/repository"

CMDLINE_TOOLS_VERSION="13114758"
PLATFORM_TOOLS_VERSION="35.0.2"

# SDK 压缩包按宿主系统取名：本机（macOS）与 GitHub Actions（Linux）都要能直接解压使用，
# 拿错平台的 build-tools / platform-tools 会在构建时才炸。
case "$(uname -s)" in
    Darwin)
        CMDLINE_TOOLS_OS="mac"
        BUILD_TOOLS_OS="macosx"
        PLATFORM_TOOLS_OS="darwin"
        ;;
    Linux)
        CMDLINE_TOOLS_OS="linux"
        BUILD_TOOLS_OS="linux"
        PLATFORM_TOOLS_OS="linux"
        ;;
    *)
        echo "❌ 不支持的构建平台：$(uname -s)（只提供 macOS / Linux 的 SDK 包直链）" >&2
        exit 1
        ;;
esac

CMDLINE_TOOLS_ZIP="commandlinetools-${CMDLINE_TOOLS_OS}-${CMDLINE_TOOLS_VERSION}_latest.zip"
PLATFORM_ZIP="platform-${COMPILE_SDK}_r02.zip"
BUILD_TOOLS_ZIP="build-tools_r${COMPILE_SDK}_${BUILD_TOOLS_OS}.zip"
PLATFORM_TOOLS_ZIP="platform-tools_r${PLATFORM_TOOLS_VERSION}-${PLATFORM_TOOLS_OS}.zip"

# ── 基础工具 ────────────────────────────────────────────────────────────────
# AGP 编译 Java 源码时要跑 jlink 生成 JDK image，因此 JDK 必须带 jmods，
# 且不能是 GraalVM（GraalVM 的 java.base 依赖 jdk.internal.vm.ci，jlink 会失败）。
jdk_is_usable() {
    local home="$1"
    [ -n "${home}" ] || return 1
    [ -x "${home}/bin/java" ] || return 1
    [ -d "${home}/jmods" ] || return 1
    if "${home}/bin/java" -version 2>&1 | grep -qi graal; then
        return 1
    fi
    return 0
}

android_download_jdk() {
    local arch url_suffix
    case "$(uname -m)" in
        arm64|aarch64) url_suffix="aarch64" ;;
        *) url_suffix="x64" ;;
    esac

    echo "⬇️  未找到可用的 JDK，下载 OpenJDK 17 到 ${TOOLING_DIR}/jdk-17 …"
    mkdir -p "${TOOLING_DIR}"
    local archive="${TOOLING_DIR}/openjdk-17.tar.gz"
    download_to "${archive}" \
        "https://mirrors.huaweicloud.com/openjdk/17/openjdk-17_macos-${url_suffix}_bin.tar.gz" \
        "https://download.java.net/java/GA/jdk17.0.2/dfd4a8d0985749f896bed50d7138ee7f/8/GPL/openjdk-17.0.2_macos-${url_suffix}_bin.tar.gz"

    local tmp="${TOOLING_DIR}/.jdk-extract"
    rm -rf "${tmp}"
    mkdir -p "${tmp}"
    tar -xzf "${archive}" -C "${tmp}"
    rm -rf "${TOOLING_DIR}/jdk-17"
    mv "${tmp}"/*/Contents/Home "${TOOLING_DIR}/jdk-17"
    rm -rf "${tmp}" "${archive}"
}

android_setup_java() {
    local home=""

    # 1) 用户显式指定的 JAVA_HOME
    if jdk_is_usable "${JAVA_HOME:-}"; then
        home="${JAVA_HOME}"
    # 2) 上次已下载到工程目录的 JDK
    elif jdk_is_usable "${TOOLING_DIR}/jdk-17"; then
        home="${TOOLING_DIR}/jdk-17"
    # 3) 系统里已有的标准 JDK 17 / 21
    else
        local version candidate
        for version in 17 21 22; do
            candidate="$(/usr/libexec/java_home -v "${version}" 2>/dev/null || true)"
            if jdk_is_usable "${candidate}"; then
                home="${candidate}"
                break
            fi
        done
    fi

    # 4) 都没有就下载（只实现了 macOS 的自动下载，其它平台请自己提供 JAVA_HOME，
    #    否则会装成 macOS 的 JDK）
    if [ -z "${home}" ]; then
        if [ "$(uname -s)" != "Darwin" ]; then
            echo "❌ 未找到可用的 JDK（需要带 jmods 的 JDK 17+），请设置 JAVA_HOME 后重试。" >&2
            exit 1
        fi
        android_download_jdk
        home="${TOOLING_DIR}/jdk-17"
    fi

    if ! jdk_is_usable "${home}"; then
        echo "❌ 无法准备可用的 JDK（需要带 jmods 的标准 OpenJDK 17+）。" >&2
        exit 1
    fi

    export JAVA_HOME="${home}"
    export PATH="${JAVA_HOME}/bin:${PATH}"
}

# download_to <目标文件> <url...>：按顺序尝试，第一个成功的即采纳。
download_to() {
    local dest="$1"
    shift
    local url
    for url in "$@"; do
        echo "⬇️  ${url}"
        if curl -fSL --retry 3 -C - --connect-timeout 20 -o "${dest}" "${url}"; then
            return 0
        fi
        echo "⚠️  下载失败，尝试下一个源…" >&2
    done
    echo "❌ 所有下载源都失败：${dest}" >&2
    return 1
}

# ── Gradle ──────────────────────────────────────────────────────────────────
android_setup_gradle() {
    # CI 可以用 ANDROID_GRADLE_BIN 指定已有的 gradle，避免重复下载。
    if [ -n "${ANDROID_GRADLE_BIN:-}" ] && [ -x "${ANDROID_GRADLE_BIN}" ]; then
        GRADLE_BIN="${ANDROID_GRADLE_BIN}"
        return 0
    fi
    GRADLE_BIN="${TOOLING_DIR}/gradle-${GRADLE_VERSION}/bin/gradle"
    if [ -x "${GRADLE_BIN}" ]; then
        return 0
    fi
    mkdir -p "${TOOLING_DIR}"
    download_to "${TOOLING_DIR}/gradle-${GRADLE_VERSION}-bin.zip" \
        "${GRADLE_MIRROR}/gradle-${GRADLE_VERSION}-bin.zip" \
        "https://services.gradle.org/distributions/gradle-${GRADLE_VERSION}-bin.zip"
    (cd "${TOOLING_DIR}" && unzip -q -o "gradle-${GRADLE_VERSION}-bin.zip")
    echo "✅ Gradle ${GRADLE_VERSION} 就绪"
}

# ── Android SDK ─────────────────────────────────────────────────────────────
# install_sdk_zip <压缩包名> <目标父目录> <目标目录名>
install_sdk_zip() {
    local zip_name="$1" dest_parent="$2" dest_name="$3"
    local zip_path="${TOOLING_DIR}/${zip_name}"
    local tmp_dir="${TOOLING_DIR}/.extract"

    download_to "${zip_path}" "${SDK_MIRROR}/${zip_name}" "${SDK_FALLBACK_MIRROR}/${zip_name}"

    rm -rf "${tmp_dir}"
    mkdir -p "${tmp_dir}"
    unzip -q -o "${zip_path}" -d "${tmp_dir}"

    # 压缩包内顶层目录名各不相同（platform-tools/、android-35/、android-15/…），
    # 这里直接探测后重命名，避免依赖硬编码。
    local root
    root="$(cd "${tmp_dir}" && ls -1 | head -1)"
    if [ -z "${root}" ]; then
        echo "❌ 解压后目录为空：${zip_name}" >&2
        exit 1
    fi

    mkdir -p "${ANDROID_HOME}/${dest_parent}"
    rm -rf "${ANDROID_HOME}/${dest_parent}/${dest_name}"
    mv "${tmp_dir}/${root}" "${ANDROID_HOME}/${dest_parent}/${dest_name}"
    rm -rf "${tmp_dir}"
}

# 幂等：只看 ANDROID_HOME（可能是工程内的 .android-sdk，也可能是 CI 预装的 SDK），
# 组件齐全就一个字节都不下载。
android_setup_sdk() {
    local need_platform=false need_build_tools=false need_platform_tools=false
    [ -d "${ANDROID_HOME}/platforms/android-${COMPILE_SDK}" ] || need_platform=true
    [ -d "${ANDROID_HOME}/build-tools/${BUILD_TOOLS_VERSION}" ] || need_build_tools=true
    [ -x "${ANDROID_HOME}/platform-tools/adb" ] || need_platform_tools=true

    if [ "${need_platform}" = true ] || [ "${need_build_tools}" = true ] || [ "${need_platform_tools}" = true ]; then
        mkdir -p "${ANDROID_HOME}"
        echo "⬇️  安装 Android SDK 组件到 ${ANDROID_HOME}…"

        # 命令行工具：本脚本用不到 sdkmanager（包直接解压），但 AGP 与手动排障都需要它。
        if [ ! -x "${ANDROID_HOME}/cmdline-tools/latest/bin/sdkmanager" ]; then
            download_to "${TOOLING_DIR}/${CMDLINE_TOOLS_ZIP}" \
                "${SDK_MIRROR}/${CMDLINE_TOOLS_ZIP}" "${SDK_FALLBACK_MIRROR}/${CMDLINE_TOOLS_ZIP}"
            rm -rf "${ANDROID_HOME}/cmdline-tools/latest"
            mkdir -p "${ANDROID_HOME}/cmdline-tools"
            (cd "${ANDROID_HOME}/cmdline-tools" && unzip -q -o "${TOOLING_DIR}/${CMDLINE_TOOLS_ZIP}")
            mv "${ANDROID_HOME}/cmdline-tools/cmdline-tools" "${ANDROID_HOME}/cmdline-tools/latest"
        fi

        [ "${need_platform}" = true ] && install_sdk_zip "${PLATFORM_ZIP}" "platforms" "android-${COMPILE_SDK}"
        [ "${need_build_tools}" = true ] && install_sdk_zip "${BUILD_TOOLS_ZIP}" "build-tools" "${BUILD_TOOLS_VERSION}"
        [ "${need_platform_tools}" = true ] && install_sdk_zip "${PLATFORM_TOOLS_ZIP}" "." "platform-tools"

        echo "✅ SDK 组件就绪"
    fi

    # AGP 在缺少已接受许可时会拒绝构建。
    mkdir -p "${ANDROID_HOME}/licenses"
    cat > "${ANDROID_HOME}/licenses/android-sdk-license" <<'EOF'
8933bad161af4178b1185d1a37fbf41ea5269c55
d56f5187479451eabf01fb78af6dfcb131a6481e
24333f8a63b6825ea9c5514f83c2829b004d1fee
EOF
    cat > "${ANDROID_HOME}/licenses/android-sdk-preview-license" <<'EOF'
84831b9409646a918e30573bab4c9c91346d8abd
EOF
}

android_setup() {
    android_setup_java
    mkdir -p "${TOOLING_DIR}" "${GRADLE_HOME_DIR}" "${ANDROID_USER_HOME_DIR}"

    export GRADLE_USER_HOME="${GRADLE_USER_HOME:-${GRADLE_HOME_DIR}}"
    export ANDROID_HOME="${ANDROID_HOME:-${SDK_DIR}}"
    export ANDROID_SDK_ROOT="${ANDROID_HOME}"
    export ANDROID_USER_HOME="${ANDROID_USER_HOME:-${ANDROID_USER_HOME_DIR}}"

    android_setup_gradle
    android_setup_sdk

    echo "sdk.dir=${ANDROID_HOME}" > "${ANDROID_DIR}/local.properties"
    echo "☕️  JDK $("${JAVA_HOME}/bin/java" -version 2>&1 | head -1 | sed 's/.*"\(.*\)".*/\1/')"
    echo "📦 Gradle ${GRADLE_VERSION}  ·  SDK ${ANDROID_HOME}"
}
