package com.torrentmovie.app.compat

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class LaunchCompatibilityManifestTest {
    @Test
    fun manifestDeclaresNetworkAndFormFactorCompatibility() {
        val text = manifestFile().readText()
        assertTrue(text.contains("android.permission.INTERNET"))
        assertTrue(text.contains("android.permission.ACCESS_NETWORK_STATE"))
        assertTrue(text.contains("android.permission.ACCESS_WIFI_STATE"))
        assertTrue(text.contains("android:resizeableActivity=\"true\""))
        assertTrue(text.contains("android:windowSoftInputMode=\"adjustResize\""))
        assertTrue(text.contains("smallestScreenSize"))
        assertTrue(text.contains("android:usesCleartextTraffic=\"true\""))
        assertTrue(text.contains("android.hardware.wifi"))
        assertTrue(text.contains("android:required=\"false\""))
        assertTrue(text.contains("android:smallScreens=\"true\""))
        assertTrue(text.contains("android:xlargeScreens=\"true\""))
    }

    @Test
    fun sdkRangeCoversPhoneAndFoldFromOreoThroughAndroid14() {
        val gradle = appBuildGradle().readText()
        assertTrue(gradle.contains("minSdk = 26"))
        assertTrue(gradle.contains("targetSdk = 34"))
        assertTrue(gradle.contains("compileSdk = 34"))
        assertFalse(
            "minSdk must stay at 26 so Android 8 phones remain installable",
            gradle.contains("minSdk = 33") || gradle.contains("minSdk = 34"),
        )
    }

    private fun manifestFile(): File = firstExisting(
        "src/main/AndroidManifest.xml",
        "app/src/main/AndroidManifest.xml",
        "../app/src/main/AndroidManifest.xml",
    )

    private fun appBuildGradle(): File = firstExisting(
        "build.gradle.kts",
        "app/build.gradle.kts",
        "../app/build.gradle.kts",
    )

    private fun firstExisting(vararg relative: String): File {
        val cwd = File(System.getProperty("user.dir")!!)
        return relative.map { File(cwd, it) }.first { it.isFile }
    }
}
