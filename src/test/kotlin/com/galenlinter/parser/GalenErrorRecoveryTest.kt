package com.galenlinter.parser

import com.galenlinter.lang.GalenParserDefinition
import com.galenlinter.lang.GalenTypes
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.PsiFile
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase

/**
 * Negative cases.
 *
 * Every one of these asserts two things: that the expected diagnostic is produced, and that the
 * parser keeps going afterwards. A linter that gives up on the first bad line is useless in an
 * editor, where the file is mid-edit and therefore broken most of the time.
 */
class GalenErrorRecoveryTest : ParsingTestCase("", "gspec", GalenParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    private fun parse(text: String): PsiFile = createPsiFile("recovery", text)

    private fun errors(file: PsiFile): List<PsiErrorElement> =
        PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java).toList()

    private fun assertParsesRestOfFile(file: PsiFile) {
        var found = 0
        fun walk(element: PsiElement) {
            if (element.node?.elementType == GalenTypes.SPEC_NAME) found++
            element.children.forEach(::walk)
        }
        walk(file)
        assertTrue(
            "Parser must recover and still build spec elements after the bad line",
            found > 0,
        )
    }

    fun testUnknownStatementIsReportedAndRecovered() {
        val file = parse(
            """
            @nonsense something
            = Main =
                header:
                    visible
            """.trimIndent(),
        )
        val messages = errors(file).map { it.errorDescription }
        assertTrue("Expected GL101, got $messages", messages.any { it.contains("GL101") })
        assertParsesRestOfFile(file)
    }

    fun testObjectStatementWithoutColon() {
        val file = parse(
            """
            = Main =
                header
                    visible
                footer:
                    visible
            """.trimIndent(),
        )
        // 'header' with no colon parses as a spec line, and 'visible' below it still parses.
        assertParsesRestOfFile(file)
    }

    fun testUnclosedSectionHeader() {
        val file = parse(
            """
            = Main
                header:
                    visible
            """.trimIndent(),
        )
        val messages = errors(file).map { it.errorDescription }
        assertTrue("Expected GL107, got $messages", messages.any { it.contains("GL107") })
        assertParsesRestOfFile(file)
    }

    fun testInconsistentIndentationIsReported() {
        // Galen itself throws SyntaxException("Inconsistent indentation") for this shape:
        // siblings must agree exactly, not merely exceed the parent.
        val file = parse("= Main =\n    header:\n        visible\n      footer:\n        visible\n")
        val messages = errors(file).map { it.errorDescription }
        assertTrue("Expected GL104, got $messages", messages.any { it.contains("GL104") })
        assertParsesRestOfFile(file)
    }

    fun testDanglingElseIsReported() {
        val file = parse(
            """
            = Main =
                @else
                    header:
                        visible
            """.trimIndent(),
        )
        val messages = errors(file).map { it.errorDescription }
        assertTrue("Expected GL103, got $messages", messages.any { it.contains("GL103") })
        assertParsesRestOfFile(file)
    }

    fun testElseAfterIfIsAccepted() {
        val file = parse(
            """
            = Main =
                @if ${'$'}{isVisible("a")}
                    a:
                        visible
                @else
                    b:
                        visible
            """.trimIndent(),
        )
        assertEquals("Well-formed if/else must not produce errors", emptyList<String>(), errors(file).map { it.errorDescription })
    }

    fun testUnterminatedStringDoesNotSwallowTheRestOfTheFile() {
        val file = parse(
            """
            = Main =
                header:
                    text is "unterminated
                footer:
                    visible
            """.trimIndent(),
        )
        assertParsesRestOfFile(file)
    }

    fun testEmptyFile() {
        val file = parse("")
        assertEquals(emptyList<String>(), errors(file).map { it.errorDescription })
    }

    fun testOnlyCommentsAndBlankLines() {
        val file = parse("# one\n\n   # two\n\n")
        assertEquals(emptyList<String>(), errors(file).map { it.errorDescription })
    }
}
