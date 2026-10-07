package com.duzui.sharetoobsi.domain

import java.net.URLDecoder
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ObsidianUriTest {

    @Test
    fun `escapes the path separator`() {
        assertEquals("30-Reading%2FBook%2Fnote.md", ObsidianUri.encode("30-Reading/Book/note.md"))
    }

    @Test
    fun `escapes a space as percent twenty and not as a plus`() {
        assertEquals("Quotes%20worth%20keeping", ObsidianUri.encode("Quotes worth keeping"))
    }

    @Test
    fun `round trips non ascii text`() {
        val original = "反建築論"
        val encoded = ObsidianUri.encode(original)
        assertTrue(encoded.startsWith("%"))
        assertEquals(original, URLDecoder.decode(encoded, "UTF-8"))
    }

    @Test
    fun `advanced uri targets the heading and opens the note`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "random",
                filePath = "30-Reading/Book/反建築論.md",
                heading = "Quotes worth keeping",
                content = "> 引用",
                mode = WriteMode.ADVANCED,
            )
        )
        assertTrue(uri.startsWith("obsidian://adv-uri?"))
        assertTrue(uri.contains("vault=random"))
        assertTrue(uri.contains("filepath=30-Reading%2FBook%2F"))
        assertTrue(uri.contains("mode=append"))
        assertTrue(uri.contains("heading=Quotes%20worth%20keeping"))
        assertTrue(uri.contains("separator=%0A%0A"))
        // A write lands the user on the note it just made.
        assertFalse(uri.contains("openmode=silent"))
    }

    @Test
    fun `official uri always appends because without it the write is a no op`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "random",
                filePath = "a/b.md",
                content = "x",
                mode = WriteMode.OFFICIAL,
            )
        )
        assertTrue(uri.startsWith("obsidian://new?"))
        assertTrue(uri.contains("append=true"))
        assertTrue(uri.contains("file=a%2Fb.md"))
        // Opening the note is the point, so nothing asks Obsidian to stay quiet.
        assertFalse(uri.contains("silent=true"))
        assertFalse(uri.contains("filepath="))
    }

    @Test
    fun `silence is still available when asked for explicitly`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "v",
                filePath = "n.md",
                content = "x",
                mode = WriteMode.ADVANCED,
                silent = true,
            )
        )
        assertTrue(uri.contains("openmode=silent"))
    }

    @Test
    fun `official uri cannot target a heading`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "random",
                filePath = "a/b.md",
                heading = "Quotes worth keeping",
                content = "x",
                mode = WriteMode.OFFICIAL,
            )
        )
        assertFalse(uri.contains("heading="))
    }

    @Test
    fun `clipboard variant carries no payload in the uri`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "random",
                filePath = "a/b.md",
                content = "很大一段正文",
                mode = WriteMode.ADVANCED,
                useClipboard = true,
            )
        )
        assertTrue(uri.contains("clipboard=true"))
        assertFalse(uri.contains("data="))
    }

    @Test
    fun `omits a blank heading`() {
        val uri = ObsidianUri.build(
            WriteRequest(
                vault = "random",
                filePath = "a/b.md",
                heading = "   ",
                content = "x",
                mode = WriteMode.ADVANCED,
            )
        )
        assertFalse(uri.contains("heading="))
    }
}
