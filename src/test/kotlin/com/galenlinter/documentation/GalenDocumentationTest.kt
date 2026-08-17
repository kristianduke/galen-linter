package com.galenlinter.documentation

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.documentation.DocumentationProvider
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenDocumentationTest : BasePlatformTestCase() {

    private val provider: DocumentationProvider = GalenDocumentationProvider()

    /** Mirrors what the platform does on hover: find the target at the caret, then render it. */
    private fun docAtCaret(text: String): String? {
        myFixture.configureByText("test.gspec", text)
        val context = myFixture.file.findElementAt(myFixture.caretOffset)
        val target = (provider as GalenDocumentationProvider).getCustomDocumentationElement(
            myFixture.editor,
            myFixture.file,
            context,
            myFixture.caretOffset,
        )
        assertNotNull("No documentation target at the caret", target)
        return provider.generateDoc(target, context)
    }

    private fun assertDocContains(text: String, vararg expected: String) {
        val doc = docAtCaret(text)
        assertNotNull("No documentation generated", doc)
        for (fragment in expected) {
            assertTrue("Expected the doc to mention '$fragment', got:\n$doc", doc!!.contains(fragment))
        }
    }

    // ---- specs -------------------------------------------------------------

    /** The example from the request. */
    fun testHoveringHeightExplainsItAndItsValues() =
        assertDocContains(
            "= Main =\n    a:\n        heig<caret>ht 10 to 20px\n",
            "height",
            "Checks the rendered height",
            "50 to 200px",   // from the shared range grammar
            "Syntax",
            "Values",
        )

    fun testHoveringWidthMentionsRelativeForm() =
        assertDocContains(
            "= Main =\n    a:\n        wid<caret>th 100px\n",
            "Checks the rendered width",
            "of main/width",
        )

    /** Documentation records what Galen's own docs get wrong. */
    fun testNearDocumentsThatSidesAreRequired() =
        assertDocContains(
            "= Main =\n    a:\n        ne<caret>ar b 10px left\n",
            "At least one side is <b>required</b>",
        )

    fun testAlignedDocumentsTheValidEdgePairs() =
        assertDocContains(
            "= Main =\n    a:\n        alig<caret>ned horizontally all b\n",
            "<code>horizontally</code> accepts",
            "<code>vertically</code> accepts",
        )

    fun testCountDocumentsThatTheRangeHasNoUnit() =
        assertDocContains(
            "= Main =\n    global:\n        cou<caret>nt any item-* is 4\n",
            "no unit",
        )

    fun testAbsentHasDocumentation() =
        assertDocContains("= Main =\n    a:\n        abse<caret>nt\n", "missing from the page")

    fun testEverySpecNameHasDocumentation() {
        val missing = GalenTypes.SPEC_NAMES.filter { GalenDocs.spec(it) == null }
        assertTrue("Specs without documentation: $missing", missing.isEmpty())
    }

    fun testEveryStatementHasDocumentation() {
        val missing = GalenTypes.STATEMENT_KEYWORDS.filter { GalenDocs.statement(it) == null }
        assertTrue("Statements without documentation: $missing", missing.isEmpty())
    }

    // ---- statements and keywords -------------------------------------------

    fun testHoveringAStatement() =
        assertDocContains(
            "@impo<caret>rt header.gspec\n",
            "objects and specs",
            "relative to the importing file",
        )

    fun testHoveringForEachExplainsTheExtraBindings() =
        assertDocContains(
            "= Main =\n    @forE<caret>ach [item-*] as i, next as n\n        a:\n            visible\n",
            "next",
            "1-based",
        )

    fun testHoveringASideKeyword() =
        assertDocContains("= Main =\n    a:\n        near b 10px le<caret>ft\n", "A side")

    fun testHoveringAMatcher() =
        assertDocContains("= Main =\n    a:\n        text mat<caret>ches \"x\"\n", "Java")

    fun testHoveringASpecialObject() =
        assertDocContains(
            "= Main =\n    a:\n        inside scr<caret>een 0px top\n",
            "whole page area",
        )

    fun testHoveringALocatorType() =
        assertDocContains("@objects\n    a   cs<caret>s   .a\n", "CSS selector")

    // ---- objects -----------------------------------------------------------

    /** Hovering a reference should answer "what is this and where does it come from". */
    fun testHoveringAnObjectShowsItsLocatorAndFile() =
        assertDocContains(
            "@objects\n    hero-header   css   #hero\n\n= Main =\n    a:\n        below hero-hea<caret>der 10px\n",
            "hero-header",
            "Locator",
            "#hero",
            "Declared in",
        )

    fun testHoveringAWildcardFamilyExplainsIt() =
        assertDocContains(
            "@objects\n    menu_item-*   #menu li a\n\n= Main =\n    a:\n        below menu_ite<caret>m-3 10px\n",
            "object family",
            "menu_item-1",
        )

    fun testHoveringAnObjectAcrossAnImport() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    shared_header   css   #header\n")
        assertDocContains(
            "@import shared.gspec\n\n= Main =\n    a:\n        below shared_hea<caret>der 10px\n",
            "shared_header",
            "shared.gspec",
        )
    }

    fun testQuickNavigateInfoForAnObject() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    header   css   #header\n\n= Main =\n    a:\n        below hea<caret>der 10px\n",
        )
        val context = myFixture.file.findElementAt(myFixture.caretOffset)
        val target: PsiElement? = (provider as GalenDocumentationProvider)
            .getCustomDocumentationElement(myFixture.editor, myFixture.file, context, myFixture.caretOffset)
        val info = provider.getQuickNavigateInfo(target, context)
        assertNotNull(info)
        assertTrue("Expected locator in the summary, got: $info", info!!.contains("#header"))
    }
}
