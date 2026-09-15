// 国内镜像优先：dl.google.com / repo1.maven.org 在国内实测只有 ~40KB/s，
// 阿里云镜像 ~9MB/s。海外环境（例如 GitHub Actions）可设 GRADLE_CHINA_MIRRORS=false
// 关掉镜像，直接走 google() / mavenCentral()。
//
// 这里不用顶层 val：pluginManagement 必须是 settings 脚本里的第一个语句块，
// 因此两个块各自读一次环境变量。
pluginManagement {
    repositories {
        if (System.getenv("GRADLE_CHINA_MIRRORS")?.equals("false", ignoreCase = true) != true) {
            maven("https://maven.aliyun.com/repository/gradle-plugin")
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (System.getenv("GRADLE_CHINA_MIRRORS")?.equals("false", ignoreCase = true) != true) {
            maven("https://maven.aliyun.com/repository/google")
            maven("https://maven.aliyun.com/repository/public")
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "GoldPriceBar"
include(":app")
