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
        val lowerText = text.lowercase()
        return when {
            lowerText.startsWith("add ") -> {
                val raw = text.substring(4).trim()
                // Pattern: "add [Item] in [Location] in [Sub-Location]..."
                // Simple split by " in " or " at "
                val delimiters = arrayOf(" in ", " at ")
                var currentText = raw
                val path = mutableListOf<String>()
                
                // Heuristic: Last parts separated by " in " are usually locations
                val parts = raw.split(" in ", " at ")
                if (parts.size > 1) {
                    val itemName = parts[0].trim()
                    val locationPath = parts.drop(1).map { it.trim() }
                    InterpretedIntent.AddItem(name = itemName, locationPath = locationPath)
                } else {
                    InterpretedIntent.AddItem(name = raw)
                }
            }
            lowerText.contains("put ") || lowerText.contains("move ") -> {
                 val parts = lowerText.split(" in ", " at ")
                 if (parts.size > 1) {
                     val itemName = parts[0].replace("put", "").replace("move", "").trim()
                     val path = parts.drop(1).map { it.trim() }
                     InterpretedIntent.AssignLocation(itemName = itemName, locationPath = path)
                 } else InterpretedIntent.Unknown
            }
            lowerText.contains("taken") || lowerText.contains("borrow") -> {
                // Simple heuristic: "Mark [item] as taken"
                val itemName = text.replace("mark", "")
                    .replace("as taken", "")
                    .replace("borrowed", "")
                    .trim()
                InterpretedIntent.MarkTaken(itemName = itemName)
            }
            lowerText.contains("returned") -> {
                val itemName = text.replace("returned", "").trim()
                InterpretedIntent.MarkReturned(itemName = itemName)
            }
            else -> {
                val normalizedQuery = synonymManager.getRepresentativeName(text)
                InterpretedIntent.Search(query = normalizedQuery)
            }
        }
    }
}
