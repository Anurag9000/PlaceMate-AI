package com.example.placemate.core.input

import android.net.Uri

sealed class UserInput {
    data class Text(val value: String) : UserInput()
    data class Speech(val transcript: String) : UserInput()
    data class Image(val uri: Uri) : UserInput()
}

sealed class InterpretedIntent {
    data class AddItem(
        val name: String? = null,
        val category: String? = null,
        val description: String? = null,
        val imageUri: Uri? = null,
        val locationPath: List<String>? = null
    ) : InterpretedIntent()

    data class AssignLocation(
        val itemName: String,
        val locationPath: List<String>
    ) : InterpretedIntent()

    data class MarkTaken(
        val itemName: String,
        val borrower: String? = null,
        val dueDate: Long? = null
    ) : InterpretedIntent()

    data class MarkReturned(
        val itemName: String
    ) : InterpretedIntent()

    data class Search(
        val query: String
    ) : InterpretedIntent()

    object Unknown : InterpretedIntent()
}

interface InputInterpreter {
    suspend fun interpret(input: UserInput): InterpretedIntent
}
