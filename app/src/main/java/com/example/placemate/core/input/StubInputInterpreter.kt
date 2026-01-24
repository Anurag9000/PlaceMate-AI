package com.example.placemate.core.input

import javax.inject.Inject
import javax.inject.Singleton

import com.example.placemate.core.utils.SynonymManager

@Singleton
class StubInputInterpreter @Inject constructor(
    private val synonymManager: SynonymManager
) : InputInterpreter {
    override suspend fun interpret(input: UserInput): InterpretedIntent {
        return when (input) {
            is UserInput.Text -> interpretText(input.value)
            is UserInput.Speech -> interpretText(input.transcript)
            is UserInput.Image -> InterpretedIntent.AddItem(imageUri = input.uri)
        }
    }

    private fun interpretText(text: String): InterpretedIntent {
        val lowerText = text.trim().lowercase()
        
        // Regex Patterns for better robustness
        // Matches: "add [item] to [location]", "put [item] in [location]"
        val addPattern = Regex("""^(?:add|put|place|store)\s+(.+?)\s+(?:in|at|to|on)\s+(.+)$""")
        // Matches: "add [item]" (simple)
        val addSimplePattern = Regex("""^(?:add|create|new)\s+(.+)$""")
        
        return when {
            // Priority 1: Location Assignment (Add/Move to Location)
            addPattern.matches(lowerText) -> {
                val match = addPattern.find(lowerText) ?: return InterpretedIntent.Unknown
                val (itemName, locationRaw) = match.destructured
                val locationPath = locationRaw.split(Regex(" (?:in|at|on) ")).map { it.trim() }
                InterpretedIntent.AddItem(name = itemName.trim(), locationPath = locationPath)
            }
            
            // Priority 2: Simple Add
            addSimplePattern.matches(lowerText) -> {
                val match = addSimplePattern.find(lowerText) ?: return InterpretedIntent.Unknown
                val itemName = match.groupValues[1].trim()
                InterpretedIntent.AddItem(name = itemName)
            }

            // Priority 3: Status Updates
            lowerText.contains("taken") || lowerText.contains("borrow") -> {
                 val itemName = text.replace(Regex("""\b(mark|as|taken|borrowed|is)\b""", RegexOption.IGNORE_CASE), "").trim()
                 InterpretedIntent.MarkTaken(itemName = itemName)
            }
            
            lowerText.contains("returned") || lowerText.contains("back") -> {
                val itemName = text.replace(Regex("""\b(mark|as|returned|brought|back|is)\b""", RegexOption.IGNORE_CASE), "").trim()
                InterpretedIntent.MarkReturned(itemName = itemName)
            }

            // Priority 4: Search (Fallback)
            else -> {
                val normalizedQuery = synonymManager.getRepresentativeName(text)
                InterpretedIntent.Search(query = normalizedQuery)
            }
        }
    }
}
