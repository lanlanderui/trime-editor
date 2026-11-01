plugins {
    alias(libs.plugins.android.library)
}

android {
    namespace = "com.osfans.trime.editor.ui"
    compileSdk = 37

    defaultConfig {
        minSdk = 21
    }

    buildFeatures {
        viewBinding = true
    }
}

dependencies {
    api(project(":theme-editor"))
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.timber)
}
