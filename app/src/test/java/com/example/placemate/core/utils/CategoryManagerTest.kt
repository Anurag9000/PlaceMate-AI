package com.example.placemate.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CategoryManagerTest {

    private val subject = CategoryManager()

    @Test
    fun `map label to category returns expected bucket`() {
        assertEquals("Tools", subject.mapLabelToCategory("Hammer drill"))
        assertEquals("Electronics", subject.mapLabelToCategory("Laptop stand"))
        assertEquals("Kitchen", subject.mapLabelToCategory("Pan"))
    }

    @Test
    fun `is label container recognizes storage surfaces`() {
        assertTrue(subject.isLabelContainer("Wardrobe"))
        assertTrue(subject.isLabelContainer("Desk"))
        assertFalse(subject.isLabelContainer("Notebook"))
    }
}
