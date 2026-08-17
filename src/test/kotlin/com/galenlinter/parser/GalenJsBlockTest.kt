package com.galenlinter.parser

import com.galenlinter.highlight.GalenColorMapping
import com.galenlinter.lang.GalenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * How `${...}` JavaScript and `@script` blocks are handled.
 *
 * Galen embeds JavaScript in two shapes — inline `${...}` expressions and indented `@script`
 * bodies — and both have to survive the lexer intact even though neither is Galen syntax.
 */
class GalenJsBlockTest : BasePlatformTestCase() {

    private fun parse(text: String): PsiElement {
        myFixture.configureByText("test.gspec", text)
        return myFixture.file
    }

    private fun errors(element: PsiElement): List<String> =
        PsiTreeUtil.findChildrenOfType(element, PsiErrorElement::class.java).map { it.errorDescription }

    /**
     * Walks via firstChild/nextSibling rather than [PsiElement.children], which returns only
     * composite children — an expression is a leaf token and would be invisible.
     */
    private fun expressions(element: PsiElement): List<String> {
        val found = mutableListOf<String>()
        fun walk(current: PsiElement) {
            if (current.node?.elementType == GalenTypes.EXPRESSION) found += current.text
            var child = current.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(element)
        return found
    }

    /** The exact shape from the report. */
    fun testConditionalBlockWithJsCondition() {
        val file = parse(
            """
            @objects
                elementName     #element

            = Main =
                @if ${'$'}{isVisible("elementName")}
                    elementName:
                        height 10 to 20px
                @else
                    @die "element is missing"
            """.trimIndent(),
        )

        assertEquals("The block must parse cleanly", emptyList<String>(), errors(file))

        // The whole call, including its quoted argument, is one token.
        assertEquals(listOf("""${'$'}{isVisible("elementName")}"""), expressions(file))
    }

    /** Braces and quotes inside the JavaScript must not terminate the expression early. */
    fun testAwkwardJavaScriptSurvivesLexing() {
        val file = parse(
            """
            = Main =
                @if ${'$'}{ count("item-*") > 0 && data["}"] }
                    a:
                        visible
            """.trimIndent(),
        )
        assertEquals(emptyList<String>(), errors(file))
        assertEquals(
            listOf("""${'$'}{ count("item-*") > 0 && data["}"] }"""),
            expressions(file),
        )
    }

    fun testScriptBlockBodyIsNotParsedAsGalen() {
        val file = parse(
            """
            @script
                data = ["Home", "About"];
                function pick(i) { return data[i - 1]; }

            = Main =
                a:
                    visible
            """.trimIndent(),
        )
        assertEquals("A @script body must never produce Galen syntax errors", emptyList<String>(), errors(file))

        val rawLines = PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.RAW_JS_LINE }
        assertEquals("Both JavaScript lines should be kept raw", 2, rawLines.size)
    }

    fun testExpressionInsideAStringLiteralIsPartOfTheString() {
        val file = parse("= Main =\n    a:\n        text is \"${'$'}{expected} today\"\n")
        assertEquals(emptyList<String>(), errors(file))
        // Galen substitutes into the quoted text, so the expression is not a separate token here.
        assertTrue("Expected no standalone expression token", expressions(file).isEmpty())
    }

    /**
     * Current limitation, asserted so a change is deliberate: the whole `${...}` span gets a single
     * colour. IntelliJ IDEA Community bundles no JavaScript support, so the JS inside cannot be
     * injected and syntax-highlighted as JavaScript.
     */
    fun testExpressionIsColouredAsOneSpan() {
        parse("= Main =\n    a:\n        width ${'$'}{gutter + 10} px\n")
        val expression = PsiTreeUtil.findChildrenOfAnyType(myFixture.file, PsiElement::class.java)
            .first { it.node?.elementType == GalenTypes.EXPRESSION }
        // Coloured by the lexer-level highlighter, not by the annotator.
        assertNull(GalenColorMapping.attributesFor(expression))
    }
}
