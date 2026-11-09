plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.google.services)
}

android {
    namespace = "com.namstd.androidskillhub"
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.namstd.androidskillhub"
        minSdk = 24
        targetSdk = 37
        // versionCode tăng mỗi bản phát hành (vd. 2.0.1 → 201). Remote Config tách cấu hình theo bản bằng điều kiện
        // "App build" trên Firebase dựa vào số này - app luôn đọc cùng các key, không tạo key theo version.
        // Xem docs/BASE_GUIDE.md.
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        buildConfigField("String", "ADJUST_APP_TOKEN", "\"{YourAppToken}\"")
        // Event token Adjust cho doanh thu mua trong app (gói Premium). Để trống = không gửi.
        buildConfigField("String", "ADJUST_IAP_EVENT_TOKEN", "\"\"")
    }

    buildTypes {
        release {
            optimization {
                enable = false
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    buildFeatures {
        viewBinding = true
        buildConfig = true
    }
}

dependencies {
    implementation(libs.androidx.appcompat)
    implementation(libs.androidx.core.ktx)
    implementation(libs.material)
    implementation(libs.androidx.activity.ktx)
    implementation(libs.androidx.fragment.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.lifecycle.viewmodel.ktx)
    implementation(libs.androidx.lifecycle.process)
    implementation(libs.androidx.recyclerview)
    implementation(libs.androidx.viewpager2)
    implementation(libs.kotlinx.coroutines.android)
    implementation(libs.google.mobile.ads.next.gen)
    implementation(libs.google.ump)
    implementation(libs.adjust.android)
    implementation(libs.androidx.installreferrer)
    implementation(libs.google.play.billing)
    implementation(libs.facebook.shimmer)
    implementation(platform(libs.firebase.bom))
    implementation(libs.firebase.config)
    testImplementation(libs.junit)
    // org.json thật cho unit test JVM (android.jar chỉ có stub) - PlacementConfig parse JSON.
    testImplementation(libs.org.json)
    testImplementation(libs.kotlinx.coroutines.test)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.test.core)
    androidTestImplementation(libs.androidx.test.runner)
    androidTestImplementation(libs.androidx.test.rules)
}
