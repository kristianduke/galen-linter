package com.galenlinter.resolve

import com.galenlinter.inspections.GalenUnresolvedReferenceInspection
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

/** Focused on wildcard families, using the exact names from the report. */
class GalenWildcardTest : BasePlatformTestCase() {

    fun testPatternMatchingDirectly() {
        assertTrue(GalenObjectResolver.matchesPattern("row-value-*", "row-value-1"))
        assertTrue(GalenObjectResolver.matchesPattern("row-value-*", "row-value-abc"))
        assertTrue(GalenObjectResolver.matchesPattern("menu_item-*", "menu_item-3"))
        assertFalse(GalenObjectResolver.matchesPattern("row-value-*", "other-1"))
    }

    fun testDeclarationIsRecognisedAsAPattern() {
        myFixture.configureByText("test.gspec", "@objects\n    row-value-*   css   .row .value\n")
        val definition = PsiTreeUtil.findChildOfType(myFixture.file, GalenObjectDefinition::class.java)
        assertNotNull(definition)
        assertEquals("row-value-*", definition!!.qualifiedName)
        assertTrue("Should be recognised as a wildcard family", definition.isPattern)
    }

    fun testConcreteNameResolvesToTheFamily() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    row-value-*   css   .row .value\n\n" +
                "= Main =\n    header:\n        below row-val<caret>ue-1 10px\n",
        )
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertNotNull("row-value-1 should resolve to row-value-*", target)
        assertEquals("row-value-*", (target as GalenObjectDefinition).qualifiedName)
    }

    fun testConcreteNameAsAnObjectStatementHeaderResolves() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    row-value-*   css   .row .value\n\n= Main =\n    row-val<caret>ue-1:\n        visible\n",
        )
        assertNotNull(myFixture.getReferenceAtCaretPosition()?.resolve())
    }

    private fun findings(text: String): List<String> {
        myFixture.enableInspections(GalenUnresolvedReferenceInspection())
        myFixture.configureByText("test.gspec", text)
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL") }
    }

    fun testConcreteNameIsNotReportedUnresolved() {
        val found = findings(
            "@objects\n    row-value-*   css   .row .value\n\n" +
                "= Main =\n    row-value-1:\n        visible\n        below row-value-2 10px\n",
        )
        assertTrue("A name matching the family must not be reported, got $found", found.isEmpty())
    }

    /** The declaration line itself must not be reported against its own family. */
    fun testFamilyDeclarationIsNotReported() {
        val found = findings(
            "@objects\n    row-value-*   css   .row .value\n\n= Main =\n    row-value-*:\n        visible\n",
        )
        assertTrue("The family itself must resolve, got $found", found.isEmpty())
    }

    fun testWildcardInTheMiddleOfANameWorks() {
        assertTrue(GalenObjectResolver.matchesPattern("row-*-value", "row-3-value"))
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    row-*-value   css   .v\n\n= Main =\n    a:\n        below row-3-va<caret>lue 10px\n",
        )
        assertNotNull(myFixture.getReferenceAtCaretPosition()?.resolve())
    }

    fun testNestedFamilyResolvesByDottedName() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    table   #table\n        row-value-*   .value\n\n" +
                "= Main =\n    a:\n        below table.row-val<caret>ue-1 10px\n",
        )
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertNotNull("Nested family should resolve", target)
        assertEquals("table.row-value-*", (target as GalenObjectDefinition).qualifiedName)
    }
}
