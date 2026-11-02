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
    // api 而不是 implementation：StyleEditorFragment 是 PreferenceFragmentCompat，
    // 宿主（:app）要把它挂进 FragmentTransaction，就必然要在自己的编译类路径上看得见
    // 这个基类，否则 replace() 会报「实参是 StyleEditorFragment 但期望 Fragment」。
    api(libs.androidx.preference)
    implementation(libs.androidx.recyclerview)
    implementation(libs.flexbox)
    implementation(libs.splitties.views.dsl)
    implementation(libs.splitties.views.dsl.constraintlayout)
    implementation(libs.splitties.views.dsl.recyclerview)
    implementation(libs.splitties.views.recyclerview)
    implementation(libs.timber)
}
