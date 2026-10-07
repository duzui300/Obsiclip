package com.duzui.sharetoobsi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

/** Exercises the whole pure path: shared text in, the exact string handed to Obsidian out. */
class PipelineTest {

    @Test
    fun `a kindle highlight becomes a vault ready quote`() {
        val shared = """
            Two points in the Stoic system deserve special mention. One is a careful
            distinction between things which are in our power and things which are not.
            https://a.co/xyz
            —— from 《Meditations》 by Marcus Aurelius
        """.trimIndent()

        val cleaned = Cleanup.clean(shared, SourceProfiles.KINDLE)

        val rendered = Template.render(
            Defaults.TEMPLATE,
            TemplateValues(
                text = cleaned,
                title = "Meditations",
                author = "Marcus Aurelius",
                tags = Defaults.TAGS,
                date = "2026-10-08",
            ),
        )

        assertEquals(
            "> Two points in the Stoic system deserve special mention. One is a careful " +
                "distinction between things which are in our power and things which are not." +
                "\n\n> — Marcus Aurelius《Meditations》 #reading",
            rendered,
        )
    }

    @Test
    fun `a real kindle share reduces to just the quote`() {
        val shared = """
            我在 平野啓一郎 所著的《本心 (Japanese Edition)》中讀到以下這段引述時，就想到您：
            「僕にはまだ、お母さんが必要なんだよ。」
            開始免費閱讀這本書：https://read.amazon.co.jp/kp/kshare?asin=B092J53NPG&ref_=kar_wh_ca
        """.trimIndent()

        val cleaned = Cleanup.clean(shared, SourceProfiles.KINDLE)

        // The book identity comes from the target the user picked, not from the text.
        val rendered = Template.render(
            Defaults.TEMPLATE,
            TemplateValues(
                text = cleaned,
                title = "本心",
                author = "平野啓一郎",
                tags = Defaults.TAGS,
            ),
        )

        assertEquals(
            "> 「僕にはまだ、お母さんが必要なんだよ。」\n\n> — 平野啓一郎《本心》 #reading",
            rendered,
        )
    }

    @Test
    fun `the rendered quote lands under the heading the vault expects`() {
        val rendered = Template.render(
            Defaults.TEMPLATE,
            TemplateValues(text = Cleanup.clean("划线内容", SourceProfiles.GENERIC), title = "反建築論"),
        )

        val uri = ObsidianUri.build(
            WriteRequest(
                vault = Defaults.VAULT,
                filePath = Defaults.BOOK_PATH_TEMPLATE.replace("{title}", "反建築論"),
                heading = Defaults.HEADING,
                content = rendered,
                mode = WriteMode.ADVANCED,
            )
        )

        assertEquals("obsidian://adv-uri", uri.substringBefore('?'))
        assertEquals(
            ObsidianUri.encode("30-Reading/Book/反建築論.md"),
            uri.substringAfter("filepath=").substringBefore('&'),
        )
        assertEquals("Quotes%20worth%20keeping", uri.substringAfter("heading=").substringBefore('&'))
    }
}
