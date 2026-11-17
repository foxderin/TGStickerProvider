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
        // YukiHook 1.2's existing FreeReflection dependency is published through JitPack.
        maven("https://jitpack.io") {
            content { includeGroup("com.github.tiann") }
        }
        maven("https://api.xposed.info/") {
            content { includeGroup("de.robv.android.xposed") }
        }
    }
}
rootProject.name = "TGStickerProvider"
include(":app")
