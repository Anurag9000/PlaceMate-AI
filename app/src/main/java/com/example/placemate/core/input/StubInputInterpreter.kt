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
                InterpretedIntent.AddItem(name = text.substring(4).trim())
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
