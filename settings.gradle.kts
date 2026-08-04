pluginManagement {
    repositories {
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        google()
        mavenCentral()
        // Google's RenderScript Intrinsics Replacement Toolkit
        // (github.com/android/renderscript-intrinsics-replacement-toolkit)
        // isn't published to Maven Central — only resolvable via JitPack,
        // built on-demand from a pinned commit SHA (see app/build.gradle.kts).
        maven { url = uri("https://jitpack.io") }
    }
}

rootProject.name = "peartv-launcher"
include(":app")
