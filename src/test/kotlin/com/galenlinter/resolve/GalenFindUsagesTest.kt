package com.galenlinter.resolve

import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenFindUsagesTest : BasePlatformTestCase() {

    private fun declaration(): GalenObjectDefinition =
        PsiTreeUtil.findChildrenOfType(myFixture.file, GalenObjectDefinition::class.java).first()

    private fun usageTexts(): List<String> =
        myFixture.findUsages(declaration()).mapNotNull { it.element?.text }

    fun testExactNameUsagesAreFound() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    header   #header\n\n= Main =\n    header:\n        visible\n    menu:\n        below header 10px\n",
        )
        val usages = usageTexts()
        assertEquals("Expected both usages of 'header', got $usages", 2, usages.size)
    }

    /**
     * A wildcard family is used through its concrete members — `row-value-1`, `row-value-2` — never
     * by its literal name, so a text-based search for `row-value-*` finds nothing.
     */
    fun testWildcardFamilyUsagesAreFound() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    row-value-*   css   .row .value\n\n" +
                "= Main =\n    row-value-1:\n        visible\n" +
                "    menu:\n        below row-value-2 10px\n",
        )
        val usages = usageTexts()
        assertTrue(
            "Expected the concrete members to be found as usages of the family, got $usages",
            usages.size >= 2,
        )
    }
}
