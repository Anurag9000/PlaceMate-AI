package com.example.placemate.core.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class SynonymManager @Inject constructor() {

    private val synonymGroups = listOf(
        setOf("kitchen", "kitchenette", "cookery", "pantry"),
        setOf("living room", "lounge", "parlor", "sitting room"),
        setOf("bedroom", "dormitory", "bedchamber"),
        setOf("bathroom", "washroom", "restroom", "lavatory"),
        setOf("garage", "carport", "workshop"),
        setOf("couch", "sofa", "settee"),
        setOf("tool", "gadget", "instrument", "implement"),
        setOf("box", "container", "bin", "crate", "carton"),
        setOf("shelf", "rack", "ledge"),
        setOf("closet", "wardrobe", "cupboard", "cabinet")
    )

    /**
     * Returns a list of all synonyms for a given word, including the word itself.
     */
    fun getSynonyms(word: String): List<String> {
        val normalizedWord = word.lowercase().trim()
        val group = synonymGroups.find { group -> group.contains(normalizedWord) }
        return group?.toList() ?: listOf(normalizedWord)
    }

    /**
     * Returns a broad "Representative" name for a group (e.g., "Kitchen" for "Kitchenette").
     */
    fun getRepresentativeName(word: String): String {
        val normalizedWord = word.lowercase().trim()
        val group = synonymGroups.find { group -> group.contains(normalizedWord) }
        return group?.first() ?: normalizedWord
    }
}
