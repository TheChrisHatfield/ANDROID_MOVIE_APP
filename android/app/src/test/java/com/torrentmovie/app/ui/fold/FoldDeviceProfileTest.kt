package com.torrentmovie.app.ui.fold

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FoldDeviceProfileTest {
    @Test
    fun detectsGalaxyZFold5Model() {
        assertTrue(
            FoldDeviceProfile.isSamsungGalaxyZFoldModel(
                manufacturer = "samsung",
                model = "SM-F946U1",
                device = "q2q",
            ),
        )
    }

    @Test
    fun rejectsGalaxyZFlip() {
        assertFalse(
            FoldDeviceProfile.isSamsungGalaxyZFoldModel(
                manufacturer = "samsung",
                model = "SM-F731U",
                device = "b5q",
            ),
        )
    }

    @Test
    fun rejectsNonFoldSamsung() {
        assertFalse(
            FoldDeviceProfile.isSamsungGalaxyZFoldModel(
                manufacturer = "samsung",
                model = "SM-S918U",
                device = "dm3q",
            ),
        )
    }

    @Test
    fun rejectsOtherManufacturers() {
        assertFalse(
            FoldDeviceProfile.isSamsungGalaxyZFoldModel(
                manufacturer = "Google",
                model = "Pixel 8",
                device = "shiba",
            ),
        )
    }
}
