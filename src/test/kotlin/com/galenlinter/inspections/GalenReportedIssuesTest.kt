package com.galenlinter.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * The specific spec mistakes reported from real use, each pinned so it cannot regress.
 *
 * All four parse cleanly — that is precisely the problem. Galen either rejects them at run time or,
 * worse, quietly reads them as something else.
 */
class GalenReportedIssuesTest : BasePlatformTestCase() {

    private fun findings(body: String): List<String> {
        myFixture.enableInspections(
            GalenInvalidSpecInspection(),
            GalenSuspiciousSpecInspection(),
            GalenUnterminatedStringInspection(),
        )
        myFixture.configureByText("test.gspec", "= Main =\n    target:\n$body")
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL") }
    }

    private fun assertReports(rule: String, body: String) {
        val found = findings(body)
        assertTrue("Expected $rule, got $found", found.any { it.startsWith(rule) })
    }

    private fun assertClean(body: String) {
        val found = findings(body)
        assertTrue("Expected no findings, got $found", found.isEmpty())
    }

    // ---- "height is 400px to 800px" ----------------------------------------

    fun testMatcherWhereARangeBelongs() {
        val found = findings("        height is 400px to 800px\n")
        assertTrue("Expected GL304, got $found", found.any { it.startsWith("GL304") })
        assertTrue("The message should show the correct form, got $found", found.any { it.contains("400 to 800px") })
    }

    fun testCorrectRangeFormIsAccepted() = assertClean("        height 400 to 800px\n")

    // ---- "height 400px to 800px" -------------------------------------------

    /** A unit on the first bound ends the range early and strands the rest of the line. */
    fun testUnitOnTheFirstBoundIsReported() {
        val found = findings("        height 400px to 800px\n")
        assertTrue("Expected GL304, got $found", found.any { it.startsWith("GL304") })
        assertTrue("Should explain which bound carries the unit, got $found", found.any { it.contains("last bound") })
    }

    // ---- "width 154 to 164p" -----------------------------------------------

    fun testMistypedUnitIsReported() {
        val found = findings("        width 154 to 164p\n")
        assertTrue("Expected GL304, got $found", found.any { it.startsWith("GL304") })
        assertTrue("Should name the valid units, got $found", found.any { it.contains("px") })
    }

    fun testRangeWithNoUnitAtAllIsReported() =
        assertReports("GL304", "        width 154 to 164\n")

    fun testPercentIsAValidUnit() = assertClean("        width 100 % of other/width\n")

    fun testMissingRangeEntirelyIsReported() = assertReports("GL304", "        width\n")

    /** `count` ranges legitimately have no unit, so they must not be caught by this. */
    fun testCountRangeIsNotReported() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    global:\n        count any item-* is 4 to 5\n")
        val found = myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL304") }
        assertTrue("A count range has no unit by design, got $found", found.isEmpty())
    }

    // ---- 'visible matches 10px' --------------------------------------------

    fun testArgumentsGivenToASpecThatTakesNone() {
        val found = findings("        visible matches 10px\n")
        assertTrue("Expected GL324, got $found", found.any { it.startsWith("GL324") })
        assertTrue("Should say the arguments are ignored, got $found", found.any { it.contains("ignored") })
    }

    fun testAbsentWithArgumentsIsReported() = assertReports("GL324", "        absent 10px\n")

    fun testBareVisibleIsAccepted() = assertClean("        visible\n")

    // ---- unterminated strings ----------------------------------------------

    fun testUnterminatedStringIsReported() =
        assertReports("GL106", "        text is \"comprehensive\n")

    fun testTerminatedStringIsAccepted() = assertClean("        text is \"comprehensive\"\n")

    fun testEscapedQuoteInsideAStringIsNotMistakenForTheEnd() =
        assertReports("GL106", "        text is \"he said \\\"hello\n")

    fun testStringContainingAnEscapedQuoteIsAccepted() =
        assertClean("        text is \"he said \\\"hello\\\"\"\n")

    // ---- expressions still exempt ------------------------------------------

    fun testExpressionSuppliedRangesAreNotReported() {
        assertClean("        width ${'$'}{gutter}\n")
        assertClean("        height ${'$'}{min} to ${'$'}{max}\n")
    }
}
