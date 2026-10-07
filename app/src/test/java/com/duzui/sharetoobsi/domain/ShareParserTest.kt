package com.duzui.sharetoobsi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareParserTest {

    /** A real Kindle share, verbatim. */
    private val kindleShare = """
        我在 平野啓一郎 所著的《本心 (Japanese Edition)》中讀到以下這段引述時，就想到您：
        「僕にはまだ、お母さんが必要なんだよ。」
        開始免費閱讀這本書：https://read.amazon.co.jp/kp/kshare?asin=B092J53NPG&ref_=kar_wh_ca
    """.trimIndent()

    @Test
    fun `reads the author and title out of a kindle preamble`() {
        val hint = ShareParser.extract(kindleShare)
        assertEquals("本心", hint.title)
        assertEquals("平野啓一郎", hint.author)
    }

    @Test
    fun `strips the edition marker so the note that already exists is reused`() {
        assertEquals("本心", ShareParser.cleanTitle("本心 (Japanese Edition)"))
        assertEquals("Meditations", ShareParser.cleanTitle("Meditations (Kindle Edition)"))
    }

    @Test
    fun `leaves a normal title alone`() {
        assertEquals(
            "A Philosophy of Loneliness",
            ShareParser.cleanTitle("A Philosophy of Loneliness"),
        )
    }

    @Test
    fun `does not invent a book out of a plain highlight`() {
        assertNull(ShareParser.extract("僕にはまだ、お母さんが必要なんだよ。").title)
    }

    @Test
    fun `ignores a book title mentioned inside the quote itself`() {
        assertNull(ShareParser.extract("「我讀過《反建築論》，印象很深。」").title)
    }
}
