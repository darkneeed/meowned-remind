package com.meowned.remind.network

import org.junit.Assert.*
import org.junit.Test

class TelegramSenderTest {

    @Test
    fun `chunkMessage returns single item when text is short`() {
        val shortText = "Short SMS text"
        val chunks = TelegramSender.chunkMessage(shortText, 4000)
        assertEquals(1, chunks.size)
        assertEquals(shortText, chunks[0])
    }

    @Test
    fun `chunkMessage splits long text into numbered chunks`() {
        val longText = "A".repeat(5000)
        val chunks = TelegramSender.chunkMessage(longText, 4000)
        assertEquals(2, chunks.size)
        assertTrue(chunks[0].startsWith("[1/2]\n"))
        assertTrue(chunks[1].startsWith("[2/2]\n"))
        assertTrue("Chunk 1 length must be within limit", chunks[0].length <= 4000)
        assertTrue("Chunk 2 length must be within limit", chunks[1].length <= 4000)
    }

    @Test
    fun `chunkMessage preserves entire message content across chunks`() {
        val original = "0123456789".repeat(500) // 5000 chars
        val chunks = TelegramSender.chunkMessage(original, 2000)
        val reconstructed = chunks.joinToString("") { chunk ->
            chunk.substringAfter("\n")
        }
        assertEquals(original, reconstructed)
    }

    @Test
    fun `chunkMessage never splits surrogate pairs`() {
        // Construct string with emoji at boundary: 10 chars + emoji (2 chars)
        // Header "[1/2]\n" is 6 chars. With maxLen = 17, available = 11 chars.
        // If content has 10 'a's followed by emoji '🎉' (\uD83C\uDF89),
        // boundary at 11 would split the emoji. It must not split!
        val text = "a".repeat(10) + "🎉" + "b".repeat(10)
        val chunks = TelegramSender.chunkMessage(text, 17)
        for (chunk in chunks) {
            val content = chunk.substringAfter("\n")
            // Check that no chunk ends with a high surrogate or starts with a low surrogate
            if (content.isNotEmpty()) {
                assertFalse("Chunk must not end with high surrogate", Character.isHighSurrogate(content.last()))
                assertFalse("Chunk must not start with low surrogate", Character.isLowSurrogate(content.first()))
            }
            assertTrue("Every chunk must be within maxLen", chunk.length <= 17)
        }
        val reconstructed = chunks.joinToString("") { it.substringAfter("\n") }
        assertEquals(text, reconstructed)
    }

    @Test
    fun `chunkMessage strictly respects maxLen for small limits and large texts`() {
        val longText = "x".repeat(300)
        val chunks = TelegramSender.chunkMessage(longText, 25)
        assertTrue(chunks.size > 1)
        for ((index, chunk) in chunks.withIndex()) {
            assertTrue("Chunk $index length ${chunk.length} must be <= 25", chunk.length <= 25)
        }
        val reconstructed = chunks.joinToString("") { it.substringAfter("\n") }
        assertEquals(longText, reconstructed)
    }

    @Test
    fun `isEntityParseError correctly recognizes Telegram parse errors`() {
        assertTrue(TelegramSender.isEntityParseError("Bad Request: can't parse entities: Unclosed tag"))
        assertTrue(TelegramSender.isEntityParseError("Bad Request: failed to parse entities"))
        assertTrue(TelegramSender.isEntityParseError("Can't find end tag of entity"))
        assertFalse(TelegramSender.isEntityParseError("Bad Request: chat not found"))
        assertFalse(TelegramSender.isEntityParseError("Unauthorized: invalid token"))
    }
}
