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

    /**
     * The squiggle belongs on the offending unit, not on the `to` that merely reveals it.
     * Pointing at `to` invites deleting the wrong thing.
     */
    fun testUnitOnTheFirstBoundUnderlinesTheUnitItself() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        height 400px to 800px\n")

        val info = myFixture.doHighlighting()
            .firstOrNull { it.description?.startsWith("GL304") == true }
        assertNotNull("Expected a GL304 finding", info)

        val highlighted = myFixture.file.text.substring(info!!.startOffset, info.endOffset)
        assertEquals("The first bound's unit is the mistake", "px", highlighted)
    }

    /**
     * A *malformed* unit on the first bound is not claimed by the range parser at all, so it ends
     * up stranded between the bound and the `to`. Earlier this fell through to underlining the
     * `to`, which points at the symptom rather than the mistake.
     */
    fun testMalformedUnitOnTheFirstBoundUnderlinesThatUnit() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        width 10p to 15px\n")

        val info = myFixture.doHighlighting()
            .firstOrNull { it.description?.startsWith("GL304") == true }
        assertNotNull("Expected a GL304 finding", info)

        val highlighted = myFixture.file.text.substring(info!!.startOffset, info.endOffset)
        assertEquals("The malformed unit is the mistake, not the \"to\"", "p", highlighted)
    }

    fun testRemovingAMalformedFirstBoundUnitFixesIt() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        width 10<caret>p to 15px\n")

        val fix = myFixture.filterAvailableIntentions("Remove it from the first bound").firstOrNull()
        assertNotNull("Expected a fix, got ${myFixture.availableIntentions.map { it.text }}", fix)
        myFixture.launchAction(fix!!)

        assertEquals("= Main =\n    target:\n        width 10 to 15px\n", myFixture.file.text)
    }

    /** The already-working case must keep working. */
    fun testValidUnitOnTheFirstBoundStillUnderlinesTheUnit() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        width 10px to 15px\n")

        val info = myFixture.doHighlighting()
            .firstOrNull { it.description?.startsWith("GL304") == true }
        val highlighted = myFixture.file.text.substring(info!!.startOffset, info.endOffset)
        assertEquals("px", highlighted)
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

    // ---- what Galen actually permits ---------------------------------------

    /**
     * `SpecWithObjectAndRangeProcessor` defaults the range to `>= 0` when none is given, so these
     * four are complete without one. Only `width` and `height` read a range unconditionally.
     */
    fun testPositionalSpecsDoNotRequireARange() {
        assertClean("        left-of buttonName\n")
        assertClean("        right-of buttonName\n")
        assertClean("        above caption\n")
        assertClean("        below caption\n")
    }

    /**
     * `ExpectRange` returns early for a range carrying a comparison operator, so no unit is
     * needed there — while a bare exact range still throws without one.
     */
    fun testComparisonRangesNeedNoUnit() {
        assertClean("        width > 40\n")
        assertClean("        width < 40\n")
        assertClean("        width >= 40\n")
        assertClean("        height ~ 100\n")
    }

    fun testComparisonRangesWithUnitsAreAlsoFine() {
        assertClean("        width > 40 px\n")
        assertClean("        width ~ 95 % of other/width\n")
    }

    /**
     * A `to` range does need one: ExpectRange reads the second value then insists on the ending
     * word, throwing Expecting "px", got "top". A real error, not a shorthand Galen tolerates.
     */
    fun testToRangeWithoutAUnitIsStillReported() =
        assertReports("GL304", "        inside container 16 to 24px left right, 20 to 28 top\n")

    fun testBothSideGroupsWithUnitsAreAccepted() =
        assertClean("        inside container 16 to 24px left right, 20 to 28px top\n")

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
