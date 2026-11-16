plugins { id("com.android.application"); id("org.jetbrains.kotlin.android") }
android {
    namespace = "me.sunnah"
    compileSdk = 34
    defaultConfig { applicationId = "me.sunnah"; minSdk = 29; targetSdk = 34; versionCode = 6; versionName = "1.5" }
    compileOptions { sourceCompatibility = JavaVersion.VERSION_17; targetCompatibility = JavaVersion.VERSION_17 }
    kotlinOptions { jvmTarget = "17" }
}

dependencies {
    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.webkit:webkit:1.11.0")
    implementation("androidx.media3:media3-exoplayer:1.4.1")
    implementation("androidx.media3:media3-session:1.4.1")
}
