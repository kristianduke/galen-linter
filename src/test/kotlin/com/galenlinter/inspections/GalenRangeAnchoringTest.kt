package com.galenlinter.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

/**
 * Where GL304 puts the squiggle when a unit ends up on the first bound of a range.
 *
 * The rule is positional rather than a list of known typos: whatever sits between the first bound
 * and the `to` is the mistake, whether or not it is a recognisable unit. This table exists to prove
 * that generality — every row is a different shape, and none of them is special-cased.
 */
class GalenRangeAnchoringTest : BasePlatformTestCase() {

    /** The text GL304 underlines for [spec], or null when nothing is reported. */
    private fun anchorFor(spec: String): String? {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        $spec\n")
        val info = myFixture.doHighlighting()
            .firstOrNull { it.description?.startsWith("GL304") == true } ?: return null
        return myFixture.file.text.substring(info.startOffset, info.endOffset)
    }

    private fun assertAnchor(expected: String, spec: String) =
        assertEquals("Wrong anchor for: $spec", expected, anchorFor(spec))

    /** A real unit in the wrong position, which the range parser does claim. */
    fun testMisplacedValidUnits() {
        assertAnchor("px", "width 10px to 15px")
        assertAnchor("px", "height 400px to 800px")
        assertAnchor("%", "width 10 % to 15px")
    }

    /** A malformed unit, which the range parser does not claim at all. */
    fun testMalformedUnitsOfAnyShape() {
        assertAnchor("p", "width 10p to 15px")
        assertAnchor("pxx", "width 10pxx to 15px")
        assertAnchor("pixels", "width 10pixels to 15px")
        assertAnchor("em", "width 10em to 15px")
    }

    /** The object-and-range specs put the range in the same flat position. */
    fun testPositionalSpecs() {
        assertAnchor("px", "above caption 10px to 20px")
        assertAnchor("px", "below caption 10px to 20px")
        assertAnchor("px", "left-of button 10px to 20px")
        assertAnchor("px", "right-of button 10px to 20px")
    }

    /**
     * The side-group specs are the awkward case: their parser absorbs the stray `to` into a side,
     * so a search restricted to the top level would miss them and a different rule would fire with
     * the wrong explanation.
     */
    fun testSideGroupSpecs() {
        assertAnchor("px", "inside container 10px to 20px left")
        assertAnchor("px", "near other 10px to 20px left")
        assertAnchor("p", "inside container 10p to 20px left")
        assertAnchor("px", "on top left edge other 10px to 20px left")
    }

    /** A stray `to` must not be reported as a bad side keyword. */
    fun testStrayToIsNotReportedAsASide() {
        myFixture.enableInspections(GalenInvalidSpecInspection())
        myFixture.configureByText("t.gspec", "= Main =\n    target:\n        inside container 10px to 20px left\n")
        val found = myFixture.doHighlighting().mapNotNull { it.description }
        assertTrue("'to' should not be reported as a side, got $found", found.none { it.startsWith("GL319") })
    }

    fun testWellFormedRangesAreNotReported() {
        assertNull(anchorFor("width 10 to 15px"))
        assertNull(anchorFor("width > 40"))
        assertNull(anchorFor("inside container 16 to 24px left right, 20 to 28px top"))
        assertNull(anchorFor("width 100 % of other/width"))
    }
}
