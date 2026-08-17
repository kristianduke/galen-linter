package com.galenlinter.js

import com.galenlinter.inspections.GalenJsInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenJsFallbackTest : BasePlatformTestCase() {

    // ---- tokenizer ---------------------------------------------------------

    private fun kinds(text: String): Map<String, GalenJsLexer.Kind> =
        GalenJsLexer.tokenize(text).associate { it.text to it.kind }

    fun testFallbackIsNeededWithoutAJavaScriptPlugin() {
        // The test IDE is Community, which bundles no JavaScript support.
        assertTrue("Expected the fallback to be active in Community", GalenJsLexer.isFallbackNeeded())
    }

    fun testRecognisesTheLexicalSurface() {
        val kinds = kinds("var x = count(\"item-*\") + 10; // note")
        assertEquals(GalenJsLexer.Kind.KEYWORD, kinds["var"])
        assertEquals(GalenJsLexer.Kind.GALEN_API, kinds["count"])
        assertEquals(GalenJsLexer.Kind.STRING, kinds["\"item-*\""])
        assertEquals(GalenJsLexer.Kind.NUMBER, kinds["10"])
        assertEquals(GalenJsLexer.Kind.COMMENT, kinds["// note"])
        assertEquals(GalenJsLexer.Kind.IDENTIFIER, kinds["x"])
    }

    fun testBlockCommentsAndTemplateStrings() {
        val kinds = kinds("/* block */ `tpl` 'single'")
        assertEquals(GalenJsLexer.Kind.COMMENT, kinds["/* block */"])
        assertEquals(GalenJsLexer.Kind.STRING, kinds["`tpl`"])
        assertEquals(GalenJsLexer.Kind.STRING, kinds["'single'"])
    }

    /** A brace inside a string must not be read as a bracket. */
    fun testBracketsInsideStringsAreNotCounted() {
        assertTrue(GalenJsLexer.problems("data[\"}\"].x").isEmpty())
        assertTrue(GalenJsLexer.problems("count(\"a-(\") > 0").isEmpty())
    }

    // ---- problems ----------------------------------------------------------

    private fun messages(text: String): List<String> =
        GalenJsLexer.problems(text).map { it.message }

    fun testUnterminatedString() =
        assertTrue(messages("count(\"unterminated)").any { it.contains("Unterminated") })

    fun testUnclosedBracket() =
        assertTrue(messages("count(\"a\"").any { it.contains("never closed") })

    fun testMismatchedBracket() =
        assertTrue(messages("data[0)").any { it.contains("does not match") })

    fun testWellFormedExpressionHasNoProblems() {
        assertTrue(messages("isVisible(\"banner\") && count(\"item-*\") > 0").isEmpty())
        assertTrue(messages("find(\"a\").left() - find(\"b\").right()").isEmpty())
    }

    /** `.name` is the one page-element member that is a property. */
    fun testNameCalledAsAFunction() =
        assertTrue(messages("find(\"x\").name()").any { it.contains("'name' is a property") })

    fun testNameUsedAsAPropertyIsFine() =
        assertTrue(messages("find(\"x\").name").isEmpty())

    fun testNearMissOfAGalenApiFunction() =
        assertTrue(messages("isVisble(\"banner\")").any { it.contains("Did you mean 'isVisible'") })

    /** A `@script` file may define anything, so an unrelated function must not be reported. */
    fun testUnrelatedFunctionCallsAreNotReported() {
        assertTrue(messages("myOwnHelper(\"x\")").isEmpty())
        assertTrue(messages("i18n('header.greeting')").isEmpty())
    }

    // ---- end to end --------------------------------------------------------

    private fun findings(text: String): List<String> {
        myFixture.enableInspections(GalenJsInspection())
        myFixture.configureByText("test.gspec", text)
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL7") }
    }

    fun testProblemInAnExpressionIsReported() {
        val found = findings("= Main =\n    a:\n        width ${'$'}{find(\"x\").name()} px\n")
        assertTrue("Expected GL701, got $found", found.any { it.startsWith("GL701") })
    }

    fun testUnbalancedBracketInAnExpressionIsReported() {
        val found = findings("= Main =\n    @if ${'$'}{isVisible(\"a\"}\n        b:\n            visible\n")
        assertTrue("Expected GL704, got $found", found.any { it.startsWith("GL704") })
    }

    fun testProblemInAScriptBlockIsReported() {
        val found = findings("@script\n    var n = find(\"x\").name();\n")
        assertTrue("Expected GL701 inside @script, got $found", found.any { it.startsWith("GL701") })
    }

    /**
     * A `@script` body is one program across lines, so a brace opened on one line and closed on
     * the next must not be reported as unbalanced.
     */
    fun testMultiLineScriptBlockIsNotReportedAsUnbalanced() {
        val found = findings(
            "@script\n    function pick(i) {\n        return i + 1;\n    }\n",
        )
        assertTrue("A multi-line function must not be flagged, got $found", found.isEmpty())
    }

    fun testValidGalenExpressionsAreClean() {
        val found = findings(
            "= Main =\n    @forEach [item-*] as i\n        ${'$'}{i}:\n" +
                "            width ${'$'}{find(\"a\").width() - 10} px\n",
        )
        assertTrue("Expected no findings, got $found", found.isEmpty())
    }
}
