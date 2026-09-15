#!/bin/bash
#
# 运行 Android 版单元测试（纯 JVM，不需要模拟器或真机）。
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
# shellcheck source=scripts/android-setup.sh
source "${SCRIPT_DIR}/android-setup.sh"

android_setup

echo "🧪 运行单元测试…"
(cd "${ANDROID_DIR}" && "${GRADLE_BIN}" --console=plain :app:testDebugUnitTest)

echo ""
echo "✅ 测试通过"
echo "   报告：${ANDROID_DIR}/app/build/reports/tests/testDebugUnitTest/index.html"
