package com.galenlinter.injection

import com.galenlinter.psi.GalenExpressionElement
import com.galenlinter.psi.GalenRawJsLine
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The host side of JavaScript injection.
 *
 * The injector itself only runs where a JavaScript plugin is installed, which Community is not, so
 * what is testable here is the part that must be right regardless: that the elements are injection
 * hosts at all, and that the ranges handed to the injector cover the JavaScript and nothing else.
 */
class GalenInjectionHostTest : BasePlatformTestCase() {

    private fun expressionHost(text: String): GalenExpressionElement {
        myFixture.configureByText("test.gspec", text)
        var found: GalenExpressionElement? = null
        fun walk(element: PsiElement) {
            if (element is GalenExpressionElement && found == null) found = element
            var child = element.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(myFixture.file)
        assertNotNull("No expression host found", found)
        return found!!
    }

    fun testExpressionTokenIsAnInjectionHost() {
        val host = expressionHost("= Main =\n    a:\n        width ${'$'}{gutter + 10} px\n")
        assertTrue("The expression should be a valid injection host", host.isValidHost)
    }

    /** The injected range must exclude the `${` and `}` delimiters, which are Galen, not JavaScript. */
    fun testExpressionRangeCoversOnlyTheJavaScript() {
        val host = expressionHost("= Main =\n    a:\n        width ${'$'}{gutter + 10} px\n")
        val range = host.expressionRange()
        assertNotNull(range)
        assertEquals("gutter + 10", range!!.substring(host.text))
    }

    fun testExpressionWithAwkwardJavaScriptStillYieldsTheRightRange() {
        val host = expressionHost("= Main =\n    a:\n        width ${'$'}{ data[\"}\"].x } px\n")
        assertEquals(" data[\"}\"].x ", host.expressionRange()!!.substring(host.text))
    }

    fun testEmptyExpressionIsNotInjected() {
        val host = expressionHost("= Main =\n    a:\n        width ${'$'}{} px\n")
        assertNull("An empty expression has nothing to inject", host.expressionRange())
    }

    // ---- @script blocks ----------------------------------------------------

    fun testScriptLinesAreInjectionHosts() {
        myFixture.configureByText(
            "test.gspec",
            "@script\n    data = [\"Home\"];\n    function pick(i) { return data[i]; }\n",
        )
        val lines = PsiTreeUtil.findChildrenOfType(myFixture.file, GalenRawJsLine::class.java).toList()
        assertEquals(2, lines.size)
        assertTrue("Script lines should be injection hosts", lines.all { it.isValidHost })
    }

    /** Indentation is Galen's block structure, not part of the JavaScript. */
    fun testScriptLineRangeExcludesIndentation() {
        myFixture.configureByText("test.gspec", "@script\n    data = [\"Home\"];\n")
        val line = PsiTreeUtil.findChildrenOfType(myFixture.file, GalenRawJsLine::class.java).first()
        assertEquals("data = [\"Home\"];", line.contentRange()!!.substring(line.text))
    }
}
