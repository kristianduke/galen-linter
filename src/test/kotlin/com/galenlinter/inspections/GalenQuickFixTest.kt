package com.galenlinter.inspections

import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenQuickFixTest : BasePlatformTestCase() {

    private fun applyFix(tool: LocalInspectionTool, fixName: String, text: String): String {
        myFixture.enableInspections(tool)
        myFixture.configureByText("test.gspec", text)
        val intention = myFixture.filterAvailableIntentions(fixName).firstOrNull()
        assertNotNull(
            "Fix '$fixName' was not offered. Available: " +
                myFixture.availableIntentions.map { it.text },
            intention,
        )
        myFixture.launchAction(intention!!)
        return myFixture.file.text
    }

    private fun offeredFixes(tool: LocalInspectionTool, text: String): List<String> {
        myFixture.enableInspections(tool)
        myFixture.configureByText("test.gspec", text)
        return myFixture.availableIntentions.map { it.text }
    }

    // ---- did you mean ------------------------------------------------------

    fun testUnknownSpecNameIsCorrected() {
        val result = applyFix(
            GalenInvalidSpecInspection(),
            "Change to 'width'",
            "= Main =\n    target:\n        wid<caret>ht 100px\n",
        )
        assertEquals("= Main =\n    target:\n        width 100px\n", result)
    }

    fun testMisspelledSideIsCorrected() {
        val result = applyFix(
            GalenInvalidSpecInspection(),
            "Change to 'left'",
            "= Main =\n    target:\n        near other 10px lef<caret>ft\n",
        )
        assertEquals("= Main =\n    target:\n        near other 10px left\n", result)
    }

    fun testUnknownTextOperationIsCorrected() {
        val result = applyFix(
            GalenSuspiciousSpecInspection(),
            "Change to 'lowercase'",
            "= Main =\n    target:\n        text lower<caret>cse is \"x\"\n",
        )
        assertEquals("= Main =\n    target:\n        text lowercase is \"x\"\n", result)
    }

    fun testUnknownRelativePropertyIsCorrected() {
        val result = applyFix(
            GalenSuspiciousSpecInspection(),
            "Change to 'height'",
            "= Main =\n    target:\n        width 100 % of other/heig<caret>th\n",
        )
        assertEquals("= Main =\n    target:\n        width 100 % of other/height\n", result)
    }

    /**
     * An edge from the wrong direction is genuinely ambiguous, so every edge legal for that
     * direction is offered rather than one guess.
     */
    fun testWrongAlignmentEdgeOffersEveryValidEdge() {
        val fixes = offeredFixes(
            GalenInvalidSpecInspection(),
            "= Main =\n    target:\n        aligned vertically to<caret>p other\n",
        )
        for (edge in listOf("all", "centered", "left", "right")) {
            assertTrue("Expected 'Change to \\'$edge\\'', got $fixes", fixes.contains("Change to '$edge'"))
        }
        assertFalse("'top' is the invalid value itself", fixes.contains("Change to 'top'"))
    }

    fun testAlignmentEdgeIsCorrected() {
        val result = applyFix(
            GalenInvalidSpecInspection(),
            "Change to 'left'",
            "= Main =\n    target:\n        aligned vertically to<caret>p other\n",
        )
        assertEquals("= Main =\n    target:\n        aligned vertically left other\n", result)
    }

    // ---- add the missing @import -------------------------------------------

    /** The most valuable fix: GL201 already knows which file declares the name. */
    fun testMissingImportIsAdded() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    shared_header   #header\n")
        val result = applyFix(
            GalenUnresolvedReferenceInspection(),
            "Add '@import shared.gspec'",
            "@objects\n    local  #local\n\n= Main =\n    local:\n        below shared_hea<caret>der 10px\n",
        )
        assertTrue("Expected the @import to be added, got:\n$result", result.contains("@import shared.gspec"))
        assertTrue("It should precede the objects block, got:\n$result",
            result.indexOf("@import shared.gspec") < result.indexOf("@objects"))
    }

    fun testMissingImportIsAddedAfterExistingImports() {
        myFixture.addFileToProject("first.gspec", "@objects\n    a  #a\n")
        myFixture.addFileToProject("second.gspec", "@objects\n    second_object  #b\n")
        val result = applyFix(
            GalenUnresolvedReferenceInspection(),
            "Add '@import second.gspec'",
            "@import first.gspec\n\n= Main =\n    a:\n        below second_ob<caret>ject 10px\n",
        )
        assertTrue(
            "New import should follow the existing one, got:\n$result",
            result.indexOf("@import first.gspec") < result.indexOf("@import second.gspec"),
        )
    }

    fun testUndeclaredObjectSuggestsASimilarName() {
        val result = applyFix(
            GalenUnresolvedReferenceInspection(),
            "Change to 'footer'",
            "@objects\n    header  #header\n    footer  #footer\n\n" +
                "= Main =\n    header:\n        below fot<caret>er 10px\n",
        )
        assertTrue("Expected the corrected name, got:\n$result", result.contains("below footer 10px"))
    }

    // ---- whitespace --------------------------------------------------------

    // These three report on a range of raw text rather than on an element, so the caret has to sit
    // inside that range for the intention to be offered at all.

    fun testTrailingWhitespaceIsRemoved() {
        val result = applyFix(
            GL003TrailingWhitespaceInspection(),
            "Remove trailing whitespace",
            "= Main = <caret>  \n    target:\n        visible\n",
        )
        assertEquals("= Main =\n    target:\n        visible\n", result)
    }

    fun testFinalNewlineIsAdded() {
        val result = applyFix(
            GL006MissingFinalNewlineInspection(),
            "Add a final newline",
            "= Main =\n    target:\n        visibl<caret>e",
        )
        assertEquals("= Main =\n    target:\n        visible\n", result)
    }

    fun testMixedIndentationIsNormalised() {
        val result = applyFix(
            GL001MixedIndentationInspection(),
            "Convert indentation to spaces",
            "= Main =\n <caret>\ttarget:\n        visible\n",
        )
        // A tab is 4 columns to Galen, so " \t" normalises to 5 spaces.
        assertEquals("= Main =\n     target:\n        visible\n", result)
    }
}
