package com.duzui.sharetoobsi.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class TemplateTest {

    private val sourceLine = "{text}\n\n> — {author}《{title}》{year} {tags}"

    @Test
    fun `substitutes what it has and leaves no debris for what it lacks`() {
        val values = TemplateValues(text = "> 引用", title = "反建築論", tags = "#reading")
        assertEquals(
            "> 引用\n\n> — 《反建築論》 #reading",
            Template.render(sourceLine, values),
        )
    }

    @Test
    fun `drops a lone attribution dash when only tags are left`() {
        // The inbox case: no book named, so the dash would introduce nothing.
        // `{text}` already carries its own `>` prefixes from the cleanup stage.
        val rendered = Template.render(
            "{text}\n\n> — {author}《{title}》{year} {tags}",
            TemplateValues(text = "> 順手記一句", tags = "#reading"),
        )
        assertEquals("> 順手記一句\n\n> #reading", rendered)
    }

    @Test
    fun `keeps the dash when a name follows it`() {
        val rendered = Template.render(
            "{text}\n\n> — {author}《{title}》{year} {tags}",
            TemplateValues(text = "> x", author = "平野啓一郎", title = "本心", tags = "#reading"),
        )
        assertEquals("> x\n\n> — 平野啓一郎《本心》 #reading", rendered)
    }

    @Test
    fun `drops the title brackets along with an empty title`() {
        val rendered = Template.render(
            "> — {author}《{title}》{year}",
            TemplateValues(text = "x", author = "平野啓一郎"),
        )
        assertEquals("> — 平野啓一郎", rendered)
    }

    @Test
    fun `drops the attribution line when there is nothing left on it`() {
        assertEquals("> 引用", Template.render(sourceLine, TemplateValues(text = "> 引用")))
    }

    @Test
    fun `accepts double braces`() {
        assertEquals(
            "反建築論",
            Template.render("{{title}}", TemplateValues(text = "", title = "反建築論")),
        )
    }

    @Test
    fun `keeps text that spans several lines`() {
        assertEquals(
            "> 一\n> 二\n\n> — 《T》",
            Template.render(sourceLine, TemplateValues(text = "> 一\n> 二", title = "T")),
        )
    }

    @Test
    fun `never leaves a markdown hard break from a vanished value`() {
        // Two trailing spaces would be a hard line break; the tag now sits on its own.
        val rendered = Template.render("> {text}\n> {tags}", TemplateValues(text = "引用", tags = ""))
        assertEquals("> 引用", rendered)
    }

    @Test
    fun `leaves an unknown placeholder untouched`() {
        assertEquals(
            "{nope}",
            Template.render("{nope}", TemplateValues(text = "x")),
        )
    }
}
