pluginManagement {
    repositories {
        maven { url = java.net.URI.create("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI.create("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}
dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven { url = java.net.URI.create("https://repo.huaweicloud.com/repository/maven/") }
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/public") }
        maven { url = java.net.URI.create("https://maven.aliyun.com/repository/google") }
        maven { url = java.net.URI.create("https://mirrors.cloud.tencent.com/nexus/repository/maven-public/") }
        google()
        mavenCentral()
        maven { url = java.net.URI.create("https://api.xposed.info/") }
    }
}

rootProject.name = "FakeGPS-next"
include(":app")