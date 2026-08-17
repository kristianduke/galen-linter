package com.galenlinter.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Validation tests for GL3xx.
 *
 * Each case is written as a whole spec file so the object statement context is real; the rule id is
 * asserted rather than the exact wording, so messages can be reworded without breaking tests.
 */
class GalenSpecValidationTest : BasePlatformTestCase() {

    private fun findings(body: String): List<String> {
        myFixture.enableInspections(
            GalenInvalidSpecInspection(),
            GalenSuspiciousSpecInspection(),
        )
        myFixture.configureByText("test.gspec", "= Main =\n    target:\n$body")
        return myFixture.doHighlighting().mapNotNull { it.description }
    }

    private fun assertReports(rule: String, body: String) {
        val found = findings(body)
        assertTrue("Expected $rule, got $found", found.any { it.startsWith(rule) })
    }

    private fun assertClean(body: String) {
        val found = findings(body).filter { it.matches(Regex("^GL\\d{3}.*")) }
        assertTrue("Expected no GL findings, got $found", found.isEmpty())
    }

    // ---- errors: Galen itself rejects these --------------------------------

    fun testUnknownSpecNameIsReportedWithSuggestion() {
        val found = findings("        widht 100px\n")
        assertTrue("Expected GL301, got $found", found.any { it.startsWith("GL301") })
        assertTrue("Expected a 'width' suggestion, got $found", found.any { it.contains("'width'") })
    }

    fun testAlignedRejectsEdgeFromTheWrongDirection() =
        assertReports("GL302", "        aligned horizontally left other\n")

    fun testAlignedAcceptsMatchingDirectionAndEdge() =
        assertClean("        aligned horizontally all other\n")

    fun testAlignedVerticallyAcceptsLeft() =
        assertClean("        aligned vertically left other\n")

    /**
     * The official Galen documentation contains `aligned horizontally screen`, which omits the
     * required edge. Galen rejects it, so this pins the doc bug rather than reproducing it.
     */
    fun testAlignedWithoutAnEdgeIsReported() =
        assertReports("GL302", "        aligned horizontally screen\n")

    fun testAlignedRejectsUnknownDirection() =
        assertReports("GL320", "        aligned sideways all other\n")

    /** Also from the official docs: `near user-pic 10px` has no side, which Galen rejects. */
    fun testNearWithoutASideIsReported() =
        assertReports("GL318", "        near other 10px\n")

    fun testNearWithASideIsAccepted() =
        assertClean("        near other 10px left\n")

    fun testInvalidSideIsReportedWithSuggestion() {
        val found = findings("        near other 10px lefft\n")
        assertTrue("Expected GL319, got $found", found.any { it.startsWith("GL319") })
        assertTrue("Expected a 'left' suggestion, got $found", found.any { it.contains("'left'") })
    }

    /** `inside` differs from `near`: its sides are genuinely optional. */
    fun testInsideWithoutSidesIsAccepted() =
        assertClean("        inside other\n")

    fun testCountRangeMustNotCarryPixels() =
        assertReports("GL309", "        count any item-* is 4 px\n")

    fun testCountWithPlainRangeIsAccepted() =
        assertClean("        count any item-* is 4 to 5\n")

    fun testOnRequiresTheEdgeKeyword() =
        assertReports("GL322", "        on top left other 10px left\n")

    fun testOnWithEdgeIsAccepted() =
        assertClean("        on top left edge other 10px left\n")

    fun testCornerCannotCombineOppositeSides() =
        assertReports("GL323", "        on top bottom edge other 10px left\n")

    fun testAbsentContradictsAPositionalSpec() =
        assertReports("GL303", "        absent\n        width 100px\n")

    fun testAbsentAloneIsAccepted() =
        assertClean("        absent\n")

    // ---- warnings: Galen tolerates these ----------------------------------

    fun testUnknownTextOperationIsReportedWithSuggestion() {
        val found = findings("        text lowercse is \"hello\"\n")
        assertTrue("Expected GL306, got $found", found.any { it.startsWith("GL306") })
        assertTrue("Expected a 'lowercase' suggestion, got $found", found.any { it.contains("'lowercase'") })
    }

    fun testKnownTextOperationIsAccepted() =
        assertClean("        text lowercase is \"hello\"\n")

    fun testCssDoesNotSupportCaseFolding() =
        assertReports("GL306", "        css font-size lowercase is \"18px\"\n")

    fun testInvalidJavaRegexIsReported() =
        assertReports("GL305", "        text matches \"[unclosed\"\n")

    fun testValidRegexIsAccepted() =
        assertClean("        text matches \"Welcome .* today\"\n")

    fun testUnknownRelativePropertyIsReported() =
        assertReports("GL315", "        width 100 % of other/heigth\n")

    fun testKnownRelativePropertyIsAccepted() =
        assertClean("        width 100 % of other/width\n")

    fun testImageWithoutASampleIsReported() =
        assertReports("GL312", "        image error 4%\n")

    fun testDenoiseMustBeAMapFilter() =
        assertReports("GL311", "        image file a.png, filter denoise 5\n")

    fun testDenoiseAsMapFilterIsAccepted() =
        assertClean("        image file a.png, map-filter denoise 5\n")

    fun testContrastOutOfRangeIsReported() =
        assertReports("GL310", "        image file a.png, filter contrast 300\n")

    fun testContrastInRangeIsAccepted() =
        assertClean("        image file a.png, filter contrast 200\n")

    fun testDuplicateSpecIsReported() =
        assertReports("GL316", "        width 100px\n        width 100px\n")

    // ---- dynamic values must never be reported ----------------------------

    /**
     * The single most important negative case. A spec whose arguments come from `${...}` cannot be
     * checked, and guessing would make the linter unusable on any spec that uses variables.
     */
    fun testExpressionArgumentsAreNeverReported() {
        assertClean("        width ${'$'}{gutter}\n")
        assertClean("        near ${'$'}{nextItem} 10px left\n")
        assertClean("        aligned ${'$'}{dir} ${'$'}{edge} other\n")
        assertClean("        text is \"${'$'}{expected}\"\n")
    }

    fun testDecimalRangesAreAccepted() {
        // ExpectRange keeps precision, so decimals are legal and must not be flagged.
        assertClean("        width 10.5 px\n")
    }
}
