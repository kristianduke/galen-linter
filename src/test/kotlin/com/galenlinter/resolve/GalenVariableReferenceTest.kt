package com.galenlinter.resolve

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenVariableReferenceTest : BasePlatformTestCase() {

    private fun resolvedTextAtCaret(): String? =
        myFixture.getReferenceAtCaretPosition()?.resolve()?.text?.trim()

    fun testResolvesSetVariable() {
        myFixture.configureByText(
            "test.gspec",
            """
            @set
                gutter    10 to 20px

            = Main =
                header:
                    inside screen ${'$'}{gut<caret>ter} top left
            """.trimIndent(),
        )
        val resolved = resolvedTextAtCaret()
        assertNotNull("Expected the @set entry to resolve", resolved)
        assertTrue("Got: $resolved", resolved!!.startsWith("gutter"))
    }

    fun testResolvesForEachBinding() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                menu_item-*     #menu li a

            = Main =
                @forEach [menu_item-*] as item, next as nextItem
                    ${'$'}{it<caret>em}:
                        left-of ${'$'}{nextItem} 10px
            """.trimIndent(),
        )
        val resolved = resolvedTextAtCaret()
        assertNotNull("Expected the loop binding to resolve", resolved)
        assertTrue("Got: $resolved", resolved!!.startsWith("@forEach"))
    }

    fun testResolvesNextBinding() {
        myFixture.configureByText(
            "test.gspec",
            """
            = Main =
                @forEach [menu_item-*] as item, next as nextItem
                    ${'$'}{item}:
                        left-of ${'$'}{nextIt<caret>em} 10px
            """.trimIndent(),
        )
        assertNotNull("Expected 'next as' binding to resolve", resolvedTextAtCaret())
    }

    fun testResolvesForLoopIndex() {
        myFixture.configureByText(
            "test.gspec",
            """
            = Main =
                @for [1 - 9] as index
                    menu_item-${'$'}{index}:
                        width ${'$'}{ind<caret>ex} px
            """.trimIndent(),
        )
        assertNotNull("Expected the @for binding to resolve", resolvedTextAtCaret())
    }

    fun testResolvesRuleParameter() {
        myFixture.configureByText(
            "test.gspec",
            """
            @rule %{name} should be squared
                ${'$'}{na<caret>me}:
                    width 100% of ${'$'}{name}/height
            """.trimIndent(),
        )
        val resolved = resolvedTextAtCaret()
        assertNotNull("Expected the rule parameter to resolve", resolved)
        assertTrue("Got: $resolved", resolved!!.contains("name"))
    }

    /** A loop variable must not leak outside the loop that declares it. */
    fun testLoopBindingIsNotVisibleOutsideTheLoop() {
        myFixture.configureByText(
            "test.gspec",
            """
            = Main =
                @forEach [menu_item-*] as item
                    ${'$'}{item}:
                        visible

                header:
                    width ${'$'}{it<caret>em} px
            """.trimIndent(),
        )
        assertNull("A loop binding must not resolve outside its loop", resolvedTextAtCaret())
    }

    /** Galen's own JS API is not a variable declaration, and must simply not resolve. */
    fun testGalenApiCallsDoNotResolveAndDoNotCrash() {
        myFixture.configureByText(
            "test.gspec",
            """
            = Main =
                header:
                    width ${'$'}{coun<caret>t("menu_item-*")} px
            """.trimIndent(),
        )
        assertNull(resolvedTextAtCaret())
    }

    fun testIdentifierRangeExtraction() {
        assertEquals("gutter", rangeText("\${gutter}"))
        assertEquals("item", rangeText("\${ item.name }"))
        assertEquals("data", rangeText("\${data[i-1]}"))
        // Not an identifier start, so there is nothing to reference.
        assertNull(GalenVariableUtil.identifierRangeIn("\${1 + 2}"))
        assertNull(GalenVariableUtil.identifierRangeIn("\${}"))
    }

    private fun rangeText(text: String): String? =
        GalenVariableUtil.identifierRangeIn(text)?.substring(text)
}
