package com.example.placemate.core.utils

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class SynonymManagerTest {

    private val subject = SynonymManager()

    @Test
    fun `representative name normalizes known synonym`() {
        assertEquals("living room", subject.getRepresentativeName("Lounge"))
    }

    @Test
    fun `get synonyms includes original normalized term`() {
        val synonyms = subject.getSynonyms("Cabinet")

        assertTrue(synonyms.contains("cabinet"))
        assertTrue(synonyms.contains("closet"))
    }

    @Test
    fun `unknown term falls back to normalized input`() {
        assertEquals(listOf("tripod"), subject.getSynonyms(" Tripod "))
        assertEquals("tripod", subject.getRepresentativeName(" Tripod "))
    }
}
