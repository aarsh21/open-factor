plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
    id("org.jetbrains.kotlin.plugin.compose")
}
val referenceDirectory = providers.gradleProperty("openfactorReferenceDir")
    .map { file(it) }.getOrElse(file("src/main/assets/reference"))
val legacyReference = providers.gradleProperty("includePrivateReference").orNull
val requestedMediaMode = providers.gradleProperty("openfactorMediaMode").orNull
    ?: legacyReference?.let { if (it.toBooleanStrict()) "full" else "source" }
    ?: "auto"
require(requestedMediaMode in listOf("auto", "source", "static", "full")) {
    "openfactorMediaMode must be auto, source, static, or full"
}
val mediaMode = if (requestedMediaMode == "auto") {
    if (referenceDirectory.resolve("catalog.json").isFile) "static" else "source"
} else requestedMediaMode
require(legacyReference != "false" || mediaMode == "source") {
    "includePrivateReference=false cannot be combined with a reference media mode"
}
val preparedReference = layout.buildDirectory.dir("generated/referenceMediaPack")
val mediaPackScript = rootProject.file("../tooling/prepare_media_pack.py")
val referenceMedia = tasks.register<Exec>("prepareReferenceMediaPack") {
    inputs.file(mediaPackScript)
    inputs.property("mode", mediaMode)
    if (mediaMode != "source") inputs.files(fileTree(referenceDirectory) {
        if (mediaMode != "full") exclude("videos/**")
    })
    outputs.dir(preparedReference)
    commandLine("python3", mediaPackScript.absolutePath,
        "--source", referenceDirectory.absolutePath,
        "--output", preparedReference.get().asFile.absolutePath,
        "--mode", mediaMode)
}
val bundledAssets = tasks.register<Sync>("prepareBundledAssets") {
    dependsOn(referenceMedia)
    from("src/main/assets") { exclude("reference/**") }
    from(preparedReference)
    into(layout.buildDirectory.dir("generated/bundledAssets"))
}
android {
    namespace = "org.openfactor.app"
    compileSdk = 36
    defaultConfig {
        applicationId = "org.openfactor.app"
        minSdk = 26
        targetSdk = 35
        versionCode = 7
        versionName = "0.3.0"
        val mediaBaseUrl = providers.gradleProperty("exerciseMediaBaseUrl").getOrElse("https://factor-media.zabimaru.me/v1/")
        buildConfigField("String", "EXERCISE_MEDIA_BASE_URL", "\"${mediaBaseUrl.replace("\\", "\\\\").replace("\"", "\\\"")}\"")
        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }
    buildFeatures { compose = true; buildConfig = true }
    // Local builds include the extracted static pack; source mode explicitly excludes it.
    sourceSets.getByName("main").assets.setSrcDirs(listOf(layout.buildDirectory.dir("generated/bundledAssets")))
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
    buildTypes {
        release {
            isMinifyEnabled = true
            isShrinkResources = true
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"))
        }
    }
    packaging { resources.excludes += "/META-INF/{AL2.0,LGPL2.1}" }
    providers.gradleProperty("openfactorBodyReferenceDir").orNull?.let {
        require(mediaMode != "source") { "External reference assets are excluded from source-only builds" }
        sourceSets.getByName("main").assets.srcDir(it)
    }
}
tasks.named("preBuild").configure { dependsOn(bundledAssets) }
dependencies {
    implementation(platform("androidx.compose:compose-bom:2025.04.01"))
    implementation("androidx.activity:activity-compose:1.10.1")
    implementation("androidx.compose.material3:material3")
    implementation("androidx.compose.material:material-icons-extended:1.7.8")
    implementation("androidx.compose.ui:ui-tooling-preview")
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.8.7")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.7")
    implementation("androidx.health.connect:connect-client:1.1.0")
    implementation("io.coil-kt.coil3:coil-compose:3.1.0")
    implementation("io.coil-kt.coil3:coil-svg:3.1.0")
    implementation("androidx.media3:media3-exoplayer:1.9.4")
    implementation("androidx.media3:media3-ui:1.9.4")
    implementation("androidx.media3:media3-datasource:1.9.4")
    implementation("androidx.media3:media3-database:1.9.4")
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.json:json:20240303")
    androidTestImplementation("androidx.test.ext:junit:1.3.0")
    androidTestImplementation("androidx.test:runner:1.7.0")
    androidTestImplementation(platform("androidx.compose:compose-bom:2025.04.01"))
    androidTestImplementation("androidx.compose.ui:ui-test-junit4")
    debugImplementation("androidx.compose.ui:ui-test-manifest")
    debugImplementation("androidx.compose.ui:ui-tooling")
}
