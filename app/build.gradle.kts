plugins {
    alias(libs.plugins.android.application)
}

android {
    namespace = "com.osfans.trime.editor"
    compileSdk = 37
    buildToolsVersion = "36.0.0"

    defaultConfig {
        applicationId = "com.osfans.trime.editor"
        minSdk = 21
        targetSdk = 37
        versionCode = 1
        versionName = "0.1.0"

        multiDexEnabled = true
    }

    buildFeatures {
        buildConfig = true
        viewBinding = true
    }

    buildTypes {
        release {
            // 混淆留到 D8：现在打开会让堆栈难以阅读，且还没有需要保护的代码。
            isMinifyEnabled = false
        }
        debug {
            applicationIdSuffix = ".debug"
        }
    }

    testOptions {
        unitTests.all {
            it.useJUnitPlatform()
        }
    }
}

dependencies {
    implementation(project(":editor-ui"))
    implementation(libs.androidx.activity)
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.timber)

    testImplementation(libs.junit)
    testImplementation(libs.kotest.runner.junit5)
    testImplementation(libs.kotest.assertions.core)
}
