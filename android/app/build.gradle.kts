import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
}

fun readMissysBuildProperty(name: String): String {
    val fromGradle = (project.findProperty(name) as String?)?.trim()
    if (!fromGradle.isNullOrBlank()) return fromGradle
    val localFile = rootProject.file("local.properties")
    if (localFile.isFile) {
        val props = Properties()
        localFile.inputStream().use { props.load(it) }
        val fromLocal = props.getProperty(name)?.trim()
        if (!fromLocal.isNullOrBlank()) return fromLocal
    }
    val envName = when (name) {
        "missysBundledSearchApiUrl" -> "MISSYS_BUNDLED_SEARCH_API_URL"
        "missysBundledTmdbApiKey" -> "MISSYS_BUNDLED_TMDB_API_KEY"
        else -> name.uppercase()
    }
    return System.getenv(envName)?.trim().orEmpty()
}

android {
    namespace = "com.torrentmovie.app"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.torrentmovie.app"
        minSdk = 26
        targetSdk = 34
        versionCode = 1
        versionName = "0.1.0"
        val bundledSearchApi = readMissysBuildProperty("missysBundledSearchApiUrl")
        val bundledTmdbApiKey = readMissysBuildProperty("missysBundledTmdbApiKey")
        buildConfigField("String", "BUNDLED_SEARCH_API_URL", "\"$bundledSearchApi\"")
        buildConfigField("String", "BUNDLED_TMDB_API_KEY", "\"$bundledTmdbApiKey\"")
    }

    buildFeatures {
        compose = true
        buildConfig = true
    }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    packaging { resources { excludes += "/META-INF/{AL2.0,LGPL2.1}" } }

    testOptions { unitTests.isIncludeAndroidResources = false }
}

dependencies {
    testImplementation(libs.junit)
    implementation(project(":core:data"))
    implementation(project(":core:network"))
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.navigation.compose)
    implementation(libs.androidx.lifecycle.viewmodel.compose)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(platform(libs.compose.bom))
    implementation(libs.compose.ui)
    implementation(libs.compose.material3)
    implementation("androidx.compose.material:material")
    implementation("androidx.compose.material:material-icons-extended")
    implementation(libs.coil.compose)
    implementation(libs.compose.ui.tooling.preview)
}
