package com.duzui.sharetoobsi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class CleanupTest {

    /** No markdown, no line joining: isolates the stage under test. */
    private val plain = CleanupOptions(wrapQuote = false, unwrapLines = false)

    /** Line joining on, markdown off. */
    private val unwrapping = CleanupOptions(wrapQuote = false)

    @Test
    fun `normalises line endings, nbsp and zero width characters`() {
        val nbsp = 0x00A0.toChar()
        val zeroWidth = 0x200B.toChar()
        val input = "a${nbsp}b\r\nc${zeroWidth}d"
        assertEquals("a b\ncd", Cleanup.clean(input, SourceProfiles.GENERIC, plain))
    }

    @Test
    fun `drops lines that are nothing but a url`() {
        val input = "真正的句子\nhttps://a.co/xyz\n另一句"
        assertEquals("真正的句子\n另一句", Cleanup.clean(input, SourceProfiles.GENERIC, plain))
    }

    @Test
    fun `keeps an inline url when the profile has no rule for it`() {
        val input = "引用内容 https://example.com/x"
        assertEquals(input, Cleanup.clean(input, SourceProfiles.GENERIC, plain))
    }

    @Test
    fun `kindle removes the store link and the attribution line`() {
        val input = "Two points in the Stoic system deserve special mention. http://a.co/xyz\n" +
            "—— from 《Meditations》 by Marcus Aurelius"
        assertEquals(
            "Two points in the Stoic system deserve special mention.",
            Cleanup.clean(input, SourceProfiles.KINDLE, plain),
        )
    }

    @Test
    fun `a highlight that merely mentions amazon is not treated as boilerplate`() {
        val input = "Amazon began as an online bookstore and grew from there."
        assertEquals(input, Cleanup.clean(input, SourceProfiles.KINDLE, plain))
    }

    @Test
    fun `joins hard wrapped cjk lines without a space`() {
        assertEquals(
            "这是被截断的第一行接着第二行",
            Cleanup.clean("这是被截断的第一行\n接着第二行", SourceProfiles.GENERIC, unwrapping),
        )
    }

    @Test
    fun `joins hard wrapped latin lines with a space`() {
        assertEquals(
            "The signature was good for more than that",
            Cleanup.clean("The signature\nwas good for more than that", SourceProfiles.GENERIC, unwrapping),
        )
    }

    @Test
    fun `rejoins a word broken across lines`() {
        assertEquals(
            "the architecture of happiness",
            Cleanup.clean("the architec-\nture of happiness", SourceProfiles.GENERIC, unwrapping),
        )
    }

    @Test
    fun `does not join across a finished sentence`() {
        assertEquals(
            "第一句。\n第二句",
            Cleanup.clean("第一句。\n第二句", SourceProfiles.GENERIC, unwrapping),
        )
    }

    @Test
    fun `leaves list items alone`() {
        val input = "- 第一条\n- 第二条"
        assertEquals(input, Cleanup.clean(input, SourceProfiles.GENERIC, unwrapping))
    }

    @Test
    fun `collapses runs of blank lines`() {
        assertEquals("a\n\nb", Cleanup.clean("a\n\n\n\n\nb", SourceProfiles.GENERIC, plain))
    }

    @Test
    fun `wraps the result in a blockquote`() {
        assertEquals(
            "> a\n> b",
            Cleanup.clean("a\nb", SourceProfiles.GENERIC, CleanupOptions(unwrapLines = false)),
        )
    }

    @Test
    fun `can wrap the text as a highlight inside the quote`() {
        val options = CleanupOptions(wrapHighlight = true, unwrapLines = false)
        assertEquals("> ==a==", Cleanup.clean("a", SourceProfiles.GENERIC, options))
    }

    @Test
    fun `leaves no dangling quote marker when trailing boilerplate is removed`() {
        assertEquals("> 真正的句子", Cleanup.clean("真正的句子\nhttps://a.co/xyz", SourceProfiles.GENERIC))
    }

    @Test
    fun `honours a user supplied line rule`() {
        val input = "正文\n广告：点击购买"
        assertEquals(
            "正文",
            Cleanup.clean(
                input,
                SourceProfiles.GENERIC,
                plain,
                extraLineRules = listOf(Regex("""^广告：.*$""")),
            ),
        )
    }
}
