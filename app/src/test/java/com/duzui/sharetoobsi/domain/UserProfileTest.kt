package com.duzui.sharetoobsi.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class UserProfileTest {

    @Test
    fun `separates rules that compile from ones that do not`() {
        val result = compileRules(
            """
            ^广告：.*$
            [unclosed
            查看详情
            """.trimIndent()
        )

        assertEquals(2, result.patterns.size)
        assertEquals(listOf("[unclosed"), result.invalid)
    }

    @Test
    fun `ignores blank lines and surrounding spaces`() {
        val result = compileRules("\n   ^a$  \n\n   \n")

        assertEquals(1, result.patterns.size)
        assertTrue(result.patterns.first().matches("a"))
        assertTrue(result.invalid.isEmpty())
    }

    @Test
    fun `a rule that matches nothing still compiles`() {
        // Worth pinning: an over-narrow rule looks identical to a working one, which is
        // why the editor has to report unparseable patterns instead of dropping them.
        assertEquals(1, compileRules("""^绝不可能出现的行$""").patterns.size)
    }

    @Test
    fun `builds a profile with a namespaced id so it cannot clash with a built-in`() {
        val profile = userProfile(
            rowId = 7,
            name = "我的规则",
            lineRules = "^广告：.*$",
            inlineRules = """https?://\S+""",
            template = "> {text}",
        )

        assertEquals("user:7", profile.id)
        assertEquals("我的规则", profile.label)
        assertEquals(1, profile.linePatterns.size)
        assertEquals(1, profile.inlinePatterns.size)
        assertEquals("> {text}", profile.template)
    }

    @Test
    fun `falls back to a placeholder label when the name is blank`() {
        assertEquals("未命名", userProfile(1, "", "", "", "").label)
    }
}
