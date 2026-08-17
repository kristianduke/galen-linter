package com.galenlinter.lexer

import com.galenlinter.lang.GalenTypes
import com.intellij.lexer.Lexer
import com.intellij.psi.tree.IElementType
import com.intellij.testFramework.LexerTestCase

class GalenLexerTest : LexerTestCase() {

    override fun createLexer(): Lexer = GalenLexer()

    override fun getDirPath(): String = "src/test/testData/lexer"

    private fun tokens(text: String): List<Pair<IElementType, String>> {
        val lexer = GalenLexer()
        lexer.start(text)
        val result = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            result += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }
        return result
    }

    /**
     * The single most important lexer invariant: tokens must tile the input exactly. If this ever
     * breaks, IntelliJ throws obscure "all tokens must cover the file" assertions at runtime.
     */
    private fun assertRoundTrip(text: String) {
        val rebuilt = tokens(text).joinToString("") { it.second }
        assertEquals("Token stream must reproduce the source exactly", text, rebuilt)
    }

    fun testRoundTripOnRepresentativeSpec() {
        assertRoundTrip(
            """
            # a comment
            @objects
                header          #header
                menu_item-*     css     #menu li a
                logo @(0, 0, -50, 0)  id  logo

            = Main =
                header:
                    inside screen 0px top left
                    % width 100 % of screen/width
                    "note" height ~ 40px
                    text is "Hello #1, ${'$'}{name}!"
            """.trimIndent(),
        )
    }

    fun testRoundTripOnCrlfAndTabs() {
        assertRoundTrip("@objects\r\n\theader\t#header\r\n\r\n= S =\r\n\theader:\r\n\t\tvisible\r\n")
    }

    fun testHashIsCommentOnlyAtLineStart() {
        val commentLine = tokens("    # this is a comment")
        assertEquals(GalenTypes.LINE_INDENT, commentLine[0].first)
        assertEquals(GalenTypes.COMMENT, commentLine[1].first)
        assertEquals("# this is a comment", commentLine[1].second)

        // Mid-line '#' is an ordinary character: CSS ids and hex colours depend on it.
        val locatorLine = tokens("    header  #header ul li")
        assertFalse(
            "Mid-line '#' must not start a comment",
            locatorLine.any { it.first == GalenTypes.COMMENT },
        )
        assertTrue(locatorLine.any { it.first == GalenTypes.WORD && it.second == "#header" })
    }

    fun testExpressionUsesBalancedBraceScanning() {
        // A naive "scan to first '}'" would stop inside the object literal.
        val t = tokens("width ${'$'}{ {a:1}.a } px")
        val expression = t.single { it.first == GalenTypes.EXPRESSION }
        assertEquals("${'$'}{ {a:1}.a }", expression.second)
    }

    fun testExpressionWithBraceInsideStringLiteral() {
        val t = tokens("text is ${'$'}{ x[\"}\"] }")
        val expression = t.single { it.first == GalenTypes.EXPRESSION }
        assertEquals("${'$'}{ x[\"}\"] }", expression.second)
    }

    fun testHyphenContinuesWordButDoesNotStartOne() {
        val spec = tokens("left-of button 10px")
        assertEquals(GalenTypes.WORD, spec[0].first)
        assertEquals("left-of", spec[0].second)

        val negative = tokens("inside partly box -10px top")
        assertTrue(negative.any { it.first == GalenTypes.MINUS })
        assertTrue(negative.any { it.first == GalenTypes.NUMBER && it.second == "10" })
    }

    fun testGradientColourIsASingleWord() {
        val t = tokens("color-scheme ~20% #000-#555-#955")
        assertTrue(t.any { it.first == GalenTypes.WORD && it.second == "#000-#555-#955" })
    }

    fun testCorrectionAndRuleParamAreSingleTokens() {
        val correction = tokens("logo @(0, 0, -50, 0)  id  logo")
        assertTrue(correction.any { it.first == GalenTypes.CORRECTION && it.second == "@(0, 0, -50, 0)" })

        val ruleParam = tokens("@rule %{object} should be squared with %{size: [0-9]+} pixel size")
        val params = ruleParam.filter { it.first == GalenTypes.RULE_PARAM }
        assertEquals(2, params.size)
        assertEquals("%{size: [0-9]+}", params[1].second)
    }

    fun testUnterminatedSpansStopAtEndOfLine() {
        assertRoundTrip("text is \"unterminated\nheader:\n")
        assertRoundTrip("width ${'$'}{ unterminated\nheader:\n")
    }

    /** Restartability: lexing from a line boundary with the line-start state must agree. */
    fun testRestartAtLineBoundaryProducesSameTokens() {
        val text = "@objects\n    header  #header\n"
        val boundary = text.indexOf('\n') + 1

        val whole = tokens(text).filter { it.second.isNotEmpty() }

        val lexer = GalenLexer()
        lexer.start(text, boundary, text.length, GalenLexer.STATE_LINE_START)
        val partial = mutableListOf<Pair<IElementType, String>>()
        while (true) {
            val type = lexer.tokenType ?: break
            partial += type to text.substring(lexer.tokenStart, lexer.tokenEnd)
            lexer.advance()
        }

        assertEquals(whole.takeLast(partial.size), partial)
    }
}
