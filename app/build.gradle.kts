plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.ksp)
}

android {
    namespace = providers.gradleProperty("project.app.packageName").get()
    compileSdk = providers.gradleProperty("project.android.compileSdk").get().toInt()

    defaultConfig {
        applicationId = providers.gradleProperty("project.app.packageName").get()
        minSdk = providers.gradleProperty("project.android.minSdk").get().toInt()
        targetSdk = providers.gradleProperty("project.android.targetSdk").get().toInt()
        versionName = providers.gradleProperty("project.app.versionName").get()
        versionCode = providers.gradleProperty("project.app.versionCode").get().toInt()
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
        ndk {
            abiFilters.clear();
            abiFilters.add("arm64-v8a");
        }
    }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        buildConfig = true
        viewBinding = true
    }
    // Load bundled native libraries directly from the read-only APK, aligned by modern AGP.
    packaging { jniLibs.useLegacyPackaging = false }
    lint { checkReleaseBuilds = false }
    // TODO Please visit https://highcapable.github.io/YukiHookAPI/en/api/special-features/host-inject
    // TODO 请参考 https://highcapable.github.io/YukiHookAPI/zh-cn/api/special-features/host-inject
    // androidResources.additionalParameters += listOf("--allow-reserved-package-id", "--package-id", "0x64")
}

kotlin {
    compilerOptions {
        jvmTarget.set(org.jetbrains.kotlin.gradle.dsl.JvmTarget.JVM_17)
        freeCompilerArgs.addAll(
            "-Xno-param-assertions",
            "-Xno-call-assertions",
            "-Xno-receiver-assertions"
        )
    }
}

dependencies {
    implementation(libs.material)
    implementation(libs.appcompat)
    implementation(libs.recyclerview)
    implementation(libs.core.ktx)
    implementation(libs.constraintlayout)
    compileOnly(libs.xposed)
    implementation(libs.yuki.api)
    // Preserve Yuki integration while fixing the old writable-Dex fallback.
    implementation(libs.free.reflection)
    ksp(libs.yuki.ksp)
    implementation(libs.ffmpeg)
    implementation(libs.glide)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.espresso.core)
}
