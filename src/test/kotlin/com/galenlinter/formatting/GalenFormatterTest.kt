package com.galenlinter.formatting

import com.galenlinter.psi.GalenFile
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiErrorElement
import com.intellij.psi.codeStyle.CodeStyleManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.io.File

class GalenFormatterTest : BasePlatformTestCase() {

    private fun reformat(text: String): String {
        myFixture.configureByText("test.gspec", text)
        WriteCommandAction.runWriteCommandAction(project) {
            CodeStyleManager.getInstance(project).reformat(myFixture.file)
        }
        return myFixture.file.text
    }

    /**
     * A signature of the parse tree: every element type paired with its depth, in document order.
     *
     * Indentation *is* the syntax in Galen, so a formatter that gets a level wrong does not
     * misalign the file, it changes what the file means. This captures the structure precisely
     * enough that any such change fails the test.
     */
    private fun structure(text: String): List<String> {
        val file = myFixture.configureByText("structure.gspec", text)
        val signature = mutableListOf<String>()
        fun walk(element: PsiElement, depth: Int) {
            val type = element.node?.elementType ?: return
            // Whitespace and the indent token are exactly what formatting is allowed to change.
            val name = type.toString()
            if (name != "WHITE_SPACE" && !name.endsWith("LINE_INDENT")) {
                signature += "$depth:$name"
            }
            var child = element.firstChild
            while (child != null) {
                walk(child, depth + 1)
                child = child.nextSibling
            }
        }
        walk(file, 0)
        return signature
    }

    private fun assertStructurePreserved(original: String) {
        val before = structure(original)
        val formatted = reformat(original)
        val after = structure(formatted)
        assertEquals(
            "Reformatting changed the parse structure, which changes what the spec means.\n" +
                "--- before ---\n$original\n--- after ---\n$formatted",
            before,
            after,
        )
    }

    // ---- the guarantee -----------------------------------------------------

    /** Every fixture, and every example in the reference documentation. */
    fun testReformattingNeverChangesStructure() {
        val samples = mutableListOf<String>()

        File("src/test/testData/parsing").listFiles { f -> f.extension == "gspec" }
            ?.forEach { samples += it.readText() }

        val doc = File("docs/galen-spec-reference.md")
        if (doc.exists()) {
            samples += Regex("^```galen\\r?\\n(.*?)^```", setOf(RegexOption.DOT_MATCHES_ALL, RegexOption.MULTILINE))
                .findAll(doc.readText())
                .map { it.groupValues[1] }
        }

        assertTrue("Expected a corpus to check against", samples.size > 40)
        samples.forEach { assertStructurePreserved(it) }
    }

    fun testReformattingNeverIntroducesParseErrors() {
        val formatted = reformat(
            "= Main =\n  header:\n      visible\n        width 100px\n",
        )
        myFixture.configureByText("check.gspec", formatted)
        val errors = PsiTreeUtil.findChildrenOfType(myFixture.file, PsiErrorElement::class.java)
        assertTrue("Formatted output must still parse, got ${errors.map { it.errorDescription }}", errors.isEmpty())
    }

    // ---- what it actually does ---------------------------------------------

    fun testIndentationIsNormalisedToFourSpaces() {
        val formatted = reformat("= Main =\n  header:\n      visible\n")
        assertEquals("= Main =\n    header:\n        visible\n", formatted)
    }

    fun testDeepNestingIsNormalised() {
        val formatted = reformat(
            "= Outer =\n = Inner =\n   a:\n     visible\n",
        )
        assertEquals("= Outer =\n    = Inner =\n        a:\n            visible\n", formatted)
    }

    fun testTabIndentationBecomesSpaces() {
        val formatted = reformat("= Main =\n\theader:\n\t\tvisible\n")
        assertEquals("= Main =\n    header:\n        visible\n", formatted)
    }

    fun testAlreadyFormattedFileIsUnchanged() {
        val text = "= Main =\n    header:\n        visible\n        width 100px\n"
        assertEquals(text, reformat(text))
    }

    fun testObjectLocatorsAreColumnAligned() {
        val formatted = reformat(
            "@objects\n    header #header\n    menu_item-* #menu li a\n    a #a\n",
        )

        // Every locator should start at the same column: two past the longest name.
        val locatorColumns = formatted.lines()
            .filter { it.contains('#') && !it.trimStart().startsWith('#') }
            .map { it.indexOf('#') }
        assertEquals("Locators should share one column, got $locatorColumns", 1, locatorColumns.distinct().size)

        val longest = "menu_item-*".length
        assertEquals(4 + longest + 2, locatorColumns.first())
    }

    /** A @script body is JavaScript; its indentation is the author's to arrange. */
    fun testScriptBodyIndentationIsLeftAlone() {
        val text = "@script\n    function pick(i) {\n            return i;\n    }\n"
        assertEquals(text, reformat(text))
    }

    fun testCommentsAndBlankLinesSurvive() {
        val formatted = reformat("# a note\n= Main =\n\n  header:\n        visible\n")
        assertTrue("The comment should survive, got:\n$formatted", formatted.contains("# a note"))
        assertTrue("The blank line should survive, got:\n$formatted", formatted.contains("\n\n"))
    }

    fun testExpressionsAndRulesAreNotDisturbed() {
        val formatted = reformat(
            "@rule %{name} should be squared\n  ${'$'}{name}:\n      width 100% of ${'$'}{name}/height\n",
        )
        assertTrue(formatted.contains("%{name} should be squared"))
        assertTrue(formatted.contains("width 100% of \${name}/height"))
    }
}
