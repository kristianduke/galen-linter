package com.galenlinter.parser

import com.galenlinter.lang.GalenParserDefinition
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.ParsingTestCase
import java.io.File

/**
 * Conformance corpus: every Galen example in `docs/galen-spec-reference.md` was copied verbatim
 * from the official spec language guide, so all of them are known-valid Galen. None may produce a
 * parse error.
 *
 * Harvesting the corpus straight out of the reference document keeps the parser and the
 * documentation from drifting apart: adding an example to the doc automatically extends the tests.
 */
class GalenDocConformanceTest : ParsingTestCase("", "gspec", GalenParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun skipSpaces(): Boolean = false

    override fun includeRanges(): Boolean = false

    fun testEveryDocumentedExampleParsesWithoutErrors() {
        val doc = File("docs/galen-spec-reference.md")
        assertTrue("Reference doc not found at ${doc.absolutePath}", doc.exists())

        val blocks = FENCE.findAll(doc.readText()).map { it.groupValues[1] }.toList()
        assertTrue("Expected to harvest Galen examples from the reference doc", blocks.size > 40)

        val failures = mutableListOf<String>()

        blocks.forEachIndexed { index, source ->
            val file = createPsiFile("doc_example_$index", source)
            val errors = PsiTreeUtil.findChildrenOfType(file, PsiErrorElement::class.java)
            if (errors.isNotEmpty()) {
                failures += buildString {
                    appendLine("--- example #$index ---")
                    appendLine(source.trimEnd())
                    errors.forEach { appendLine("  ERROR at ${offsetDescription(it)}: ${it.errorDescription}") }
                }
            }
        }

        if (failures.isNotEmpty()) {
            fail("${failures.size} of ${blocks.size} documented examples failed to parse:\n\n" + failures.joinToString("\n"))
        }
    }

    private fun offsetDescription(element: PsiElement): String = element.textRange.toString()

    private companion object {
        val FENCE = Regex("^```galen\\r?\\n(.*?)^```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
    }
}
