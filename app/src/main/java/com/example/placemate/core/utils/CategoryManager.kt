package com.example.placemate.core.utils

import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class CategoryManager @Inject constructor() {

    fun mapLabelToCategory(label: String): String {
        val lower = label.lowercase()
        return when {
            lower.contains("tool") || lower.contains("hammer") || lower.contains("screw") || lower.contains("wrench") -> "Tools"
            lower.contains("book") || lower.contains("paper") || lower.contains("magazine") || lower.contains("newspaper") -> "Media"
            lower.contains("electronics") || lower.contains("phone") || lower.contains("laptop") || lower.contains("computer") || lower.contains("tablet") -> "Electronics"
            lower.contains("furniture") || lower.contains("chair") || lower.contains("table") || lower.contains("desk") || lower.contains("sofa") || lower.contains("bed") -> "Furniture"
            lower.contains("kitchen") || lower.contains("cook") || lower.contains("food") || lower.contains("appliance") || lower.contains("pot") || lower.contains("pan") -> "Kitchen"
            lower.contains("toy") || lower.contains("game") || lower.contains("puzzle") -> "Leisure"
            lower.contains("clothing") || lower.contains("wear") || lower.contains("shoe") || lower.contains("shirt") || lower.contains("pant") -> "Apparel"
            else -> "Decor & Misc"
        }
    }

    fun isLabelContainer(label: String): Boolean {
        val lower = label.lowercase()
        return lower.contains("shelf") || 
               lower.contains("bookcase") ||
               lower.contains("cupboard") || 
               lower.contains("wardrobe") || 
               lower.contains("almirah") ||
               lower.contains("rack") ||
               lower.contains("drawer") ||
               lower.contains("fridge") || 
               lower.contains("refrigerator") || 
               lower.contains("table") || 
               lower.contains("desk") ||
               lower.contains("box") ||
               lower.contains("cabinet") ||
               lower.contains("storage") ||
               lower.contains("bin") ||
               lower.contains("basket")
    }
}
