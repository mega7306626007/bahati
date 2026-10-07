package com.pesaflow.app.parsers

import com.pesaflow.app.data.models.CategoryRule
import com.pesaflow.app.data.parsers.MpesaParser
import org.junit.Assert.*
import org.junit.Test


class CategoryRuleTest {

    private fun rules(vararg pairs: Pair<String, String>): List<CategoryRule> =
        pairs.map { (kw, cat) -> CategoryRule(keyword = kw, category = cat) }

    @Test
    fun `keyword match is case-insensitive on text and merchant`() {
        MpesaParser.setCachedRules(rules("UNES BOOKSTORE" to "School"))
        assertEquals("School", MpesaParser.matchCategoryRule("Paid to unes bookstore.", "UNES"))
        assertEquals("School", MpesaParser.matchCategoryRule("Random text", "Unes Bookstore Ltd"))
    }

    @Test
    fun `no match returns null`() {
        MpesaParser.setCachedRules(rules("UNES BOOKSTORE" to "School"))
        assertNull(MpesaParser.matchCategoryRule("Paid to Java House.", "Java"))
    }

    @Test
    fun `disabled rules never match`() {
        MpesaParser.setCachedRules(
            listOf(CategoryRule(keyword = "UNES", category = "School", enabled = false))
        )
        assertNull(MpesaParser.matchCategoryRule("Paid to UNES.", "UNES"))
    }

    @Test
    fun `empty cache never matches`() {
        MpesaParser.setCachedRules(emptyList())
        assertNull(MpesaParser.matchCategoryRule("Paid to UNES.", "UNES"))
    }
}
