plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
android {
    namespace = "org.openfactor.app"
    compileSdk = 35
    defaultConfig {
        applicationId = "org.openfactor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 1
        versionName = "0.1.0-dev"
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes { release { isMinifyEnabled = false } }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
}
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-svg:3.1.0")
    implementation("io.coil-kt.coil3:coil-network-okhttp:3.1.0")
    testImplementation("junit:junit:4.13.2")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    androidTestImplementation("androidx.test.ext:junit:1.2.1")
    androidTestImplementation("androidx.test:runner:1.6.2")
    debugImplementation("androidx.compose.ui:ui-tooling")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
}
