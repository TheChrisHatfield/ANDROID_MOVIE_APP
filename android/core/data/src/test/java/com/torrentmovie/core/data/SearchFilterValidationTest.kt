package com.torrentmovie.core.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SearchFilterValidationTest {
    @Test
    fun rejectsUnitlessMaxSize() {
        assertFalse(SearchFilterValidation.isValidMaxSize("500"))
    }

    @Test
    fun acceptsSizedValueWithUnit() {
        assertTrue(SearchFilterValidation.isValidMaxSize("4 GB"))
        assertTrue(SearchFilterValidation.isValidMaxSize("1.5 GiB"))
    }
}
