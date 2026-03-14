package com.example.placemate.core.input

import com.example.placemate.core.utils.SynonymManager
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class StubInputInterpreterTest {

    private val subject = StubInputInterpreter(SynonymManager())

    @Test
    fun `interprets add command with location path`() = runTest {
        val result = subject.interpret(UserInput.Text("Add hammer to garage shelf"))

        assertTrue(result is InterpretedIntent.AddItem)
        result as InterpretedIntent.AddItem
        assertEquals("hammer", result.name)
        assertEquals(listOf("garage shelf"), result.locationPath)
    }

    @Test
    fun `interprets taken command`() = runTest {
        val result = subject.interpret(UserInput.Speech("mark drill as taken"))

        assertTrue(result is InterpretedIntent.MarkTaken)
        result as InterpretedIntent.MarkTaken
        assertEquals("drill", result.itemName)
    }

    @Test
    fun `falls back to normalized search`() = runTest {
        val result = subject.interpret(UserInput.Text("Lounge"))

        assertTrue(result is InterpretedIntent.Search)
        result as InterpretedIntent.Search
        assertEquals("living room", result.query)
    }
}
