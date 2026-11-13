package com.chantingscheduler.app.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VersionComparatorTest {

    @Test
    fun `equal versions are not newer`() {
        assertFalse(VersionComparator.isNewer("v1.3", "1.3"))
        assertFalse(VersionComparator.isNewer("1.3", "1.3"))
    }

    @Test
    fun `newer and older versions`() {
        assertTrue(VersionComparator.isNewer("v1.4", "1.3"))
        assertFalse(VersionComparator.isNewer("v1.2", "1.3"))
    }

    @Test
    fun `numeric part comparison, not string`() {
        assertTrue(VersionComparator.isNewer("v1.10", "1.9"))
        assertFalse(VersionComparator.isNewer("v1.9", "1.10"))
    }

    @Test
    fun `different lengths`() {
        assertTrue(VersionComparator.isNewer("v1.3", "1.2.1"))
        assertFalse(VersionComparator.isNewer("v1.2.1", "1.3"))
        // Trailing zero parts are equal.
        assertFalse(VersionComparator.isNewer("v1.3.0", "1.3"))
    }

    @Test
    fun `v1_4_2 update chain`() {
        // Users on 1.4 or a future 1.4.1 must both see v1.4.2 as newer.
        assertTrue(VersionComparator.isNewer("v1.4.2", "1.4"))
        assertTrue(VersionComparator.isNewer("v1.4.2", "1.4.1"))
        assertFalse(VersionComparator.isNewer("v1.4.2", "1.4.2"))
        assertFalse(VersionComparator.isNewer("v1.4", "1.4.2"))
    }

    @Test
    fun `v prefix handled`() {
        assertTrue(VersionComparator.isNewer("v2.0", "1.9"))
    }

    @Test
    fun `malformed tags count as not newer`() {
        assertFalse(VersionComparator.isNewer("", "1.0"))
        assertFalse(VersionComparator.isNewer("v", "1.0"))
        assertFalse(VersionComparator.isNewer("v1..2", "1.0"))
        assertFalse(VersionComparator.isNewer("v1.beta", "1.0"))
        assertFalse(VersionComparator.isNewer("latest", "1.0"))
    }
}
