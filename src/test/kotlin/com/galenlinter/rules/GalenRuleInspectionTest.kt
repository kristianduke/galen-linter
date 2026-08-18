package com.galenlinter.rules

import com.galenlinter.inspections.GalenRuleInspection
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenRuleInspectionTest : BasePlatformTestCase() {

    private fun findings(text: String): List<String> {
        myFixture.enableInspections(GalenRuleInspection())
        myFixture.configureByText("test.gspec", text)
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL6") }
    }

    private fun assertReports(rule: String, text: String) {
        val found = findings(text)
        assertTrue("Expected $rule, got $found", found.any { it.startsWith(rule) })
    }

    private fun assertClean(text: String) {
        val found = findings(text)
        assertTrue("Expected no findings, got $found", found.isEmpty())
    }

    // ---- rule text matching ------------------------------------------------

    fun testRuleTextCompilesToAMatchingPattern() {
        val pattern = GalenRuleUtil.compile("%{name} should be squared")
        assertNotNull(pattern)
        assertTrue(pattern!!.matches("header-icon should be squared"))
        assertFalse(pattern.matches("header-icon should be round"))
    }

    fun testCustomParameterRegexIsHonoured() {
        val pattern = GalenRuleUtil.compile("%{object} should be squared with %{size: [0-9]+} pixel size")
        assertNotNull(pattern)
        assertTrue(pattern!!.matches("logo should be squared with 100 pixel size"))
        assertFalse("The custom regex accepts digits only", pattern.matches("logo should be squared with big pixel size"))
    }

    fun testRuleWithNoParametersMatchesExactly() {
        val pattern = GalenRuleUtil.compile("should be squared")
        assertTrue(pattern!!.matches("should be squared"))
        assertFalse(pattern.matches("should be squared twice"))
    }

    /** Literal parts must be escaped, or regex metacharacters in rule text would misbehave. */
    fun testLiteralTextIsEscaped() {
        val pattern = GalenRuleUtil.compile("%{name} matches (a.b)")
        assertTrue(pattern!!.matches("x matches (a.b)"))
        assertFalse(pattern.matches("x matches (axb)"))
    }

    // ---- GL602 unmatched ---------------------------------------------------

    fun testInvocationMatchingNoRuleIsReported() =
        assertReports(
            "GL602",
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n\n" +
                "= Main =\n    | header should be round\n",
        )

    fun testMatchingInvocationIsAccepted() =
        assertClean(
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n\n" +
                "= Main =\n    | header should be squared\n",
        )

    fun testObjectScopedRuleIsAccepted() =
        assertClean(
            "@rule should be squared\n    width 100% of ${'$'}{objectName}/height\n\n" +
                "= Main =\n    header-icon:\n        | should be squared\n",
        )

    /** An invocation assembled from an expression cannot be matched until run time. */
    fun testExpressionInvocationIsNotReported() =
        assertClean(
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n\n" +
                "= Main =\n    | ${'$'}{someName} should be squared\n",
        )

    fun testRuleFromAnImportedFileIsFound() {
        myFixture.addFileToProject(
            "rules.gspec",
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n",
        )
        myFixture.enableInspections(GalenRuleInspection())
        myFixture.configureByText("page.gspec", "@import rules.gspec\n\n= Main =\n    | header should be squared\n")
        val found = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL6") }
        assertTrue("An imported rule should be in scope, got $found", found.isEmpty())
    }

    /**
     * Without scanning JavaScript, every invocation of a JS-defined rule would be reported as
     * matching nothing — unusable in exactly the projects that use rules most.
     */
    fun testRuleDefinedInJavaScriptIsFound() {
        myFixture.addFileToProject(
            "my-rules.js",
            """rule("%{objectPattern} are equally distant from each other", function (o, p) {});""",
        )
        myFixture.enableInspections(GalenRuleInspection())
        myFixture.configureByText(
            "page.gspec",
            "@script my-rules.js\n\n= Menu =\n    | menu_item-* are equally distant from each other\n",
        )
        val found = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL6") }
        assertTrue("A JavaScript rule should be in scope, got $found", found.isEmpty())
    }

    // ---- GL601 ambiguous ---------------------------------------------------

    fun testInvocationMatchingTwoRulesIsReported() =
        assertReports(
            "GL601",
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n\n" +
                "@rule %{other} should be %{what}\n    ${'$'}{other}:\n        visible\n\n" +
                "= Main =\n    | header should be squared\n",
        )

    // ---- GL603 / GL604 rule bodies -----------------------------------------

    fun testRuleBodyOutsideARuleIsReported() =
        assertReports("GL603", "= Main =\n    @ruleBody\n")

    fun testRuleBodyInsideARuleIsAccepted() =
        assertClean(
            "@rule if %{objectName} is visible\n    @if ${'$'}{isVisible(objectName)}\n        @ruleBody\n\n" +
                "= Main =\n    | if banner is visible\n        banner:\n            visible\n",
        )

    fun testBlockUnderAnInvocationWhoseRuleIgnoresItIsReported() =
        assertReports(
            "GL604",
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        visible\n\n" +
                "= Main =\n    | header should be squared\n        extra:\n            visible\n",
        )

    // ---- GL606 / GL607 -----------------------------------------------------

    fun testInvalidParameterRegexIsReported() =
        assertReports(
            "GL606",
            "@rule %{name} sized %{size: [0-9} px\n    ${'$'}{name}:\n        visible\n",
        )

    fun testUndeclaredParameterInTheBodyIsReported() =
        assertReports(
            "GL607",
            "@rule %{name} should be squared\n    ${'$'}{nmae}:\n        visible\n",
        )

    fun testDeclaredParameterIsAccepted() =
        assertClean(
            "@rule %{name} should be squared\n    ${'$'}{name}:\n        width 100% of ${'$'}{name}/height\n\n" +
                "= Main =\n    | a should be squared\n",
        )

    /** `objectName` is supplied by Galen on an object-scoped rule, not declared. */
    fun testObjectNameIsAcceptedWithoutDeclaration() =
        assertClean(
            "@rule should be squared\n    width 100% of ${'$'}{objectName}/height\n\n" +
                "= Main =\n    a:\n        | should be squared\n",
        )
}
