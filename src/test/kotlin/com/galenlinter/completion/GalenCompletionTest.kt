package com.galenlinter.completion

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenCompletionTest : BasePlatformTestCase() {

    /**
     * Note on fixtures: when exactly one item matches the prefix, `completeBasic` inserts it and
     * offers no list at all. Cases below therefore either use an empty prefix or arrange for more
     * than one candidate.
     */
    private fun completions(text: String): List<String> {
        myFixture.configureByText("test.gspec", text)
        myFixture.completeBasic()
        return myFixture.lookupElementStrings ?: emptyList()
    }

    private fun assertOffers(text: String, vararg expected: String) {
        val actual = completions(text)
        for (item in expected) {
            assertTrue("Expected '$item' to be offered, got $actual", actual.contains(item))
        }
    }

    private fun assertDoesNotOffer(text: String, vararg unexpected: String) {
        val actual = completions(text)
        for (item in unexpected) {
            assertFalse("'$item' should not be offered, got $actual", actual.contains(item))
        }
    }

    // ---- spec names --------------------------------------------------------

    fun testSpecNamesInsideAnObjectStatement() =
        assertOffers(
            "= Main =\n    header:\n        <caret>\n",
            "visible", "width", "inside", "aligned", "color-scheme",
        )

    fun testSpecNamePrefixFilters() {
        val offered = completions("= Main =\n    header:\n        w<caret>\n")
        assertTrue("Expected width, got $offered", offered.contains("width"))
        assertFalse("'visible' does not start with 'w', got $offered", offered.contains("visible"))
    }

    fun testStatementsAtTheStartOfASectionLine() =
        assertOffers("= Main =\n    @o<caret>\n", "@on", "@objects")

    fun testLocatorTypesInsideObjectsBlock() =
        assertOffers("@objects\n    header   <caret>\n", "id", "css", "xpath")

    // ---- direction-aware alignment -----------------------------------------

    /**
     * The case worth the whole context-aware design: `vertically` and `horizontally` accept
     * disjoint sets of edges, and offering the wrong one would be actively misleading.
     */
    fun testAlignedOffersOnlyEdgesValidForTheDirection() {
        assertOffers("= Main =\n    a:\n        aligned vertically <caret>\n", "left", "right", "centered", "all")
        assertDoesNotOffer("= Main =\n    a:\n        aligned vertically <caret>\n", "top", "bottom")

        assertOffers("= Main =\n    a:\n        aligned horizontally <caret>\n", "top", "bottom", "centered", "all")
        assertDoesNotOffer("= Main =\n    a:\n        aligned horizontally <caret>\n", "left", "right")
    }

    fun testAlignedOffersDirectionsFirst() =
        assertOffers("= Main =\n    a:\n        aligned <caret>\n", "horizontally", "vertically")

    // ---- object names ------------------------------------------------------

    fun testObjectNamesAfterAPositionalSpec() =
        assertOffers(
            "@objects\n    header  #header\n    footer  #footer\n\n= Main =\n    header:\n        below <caret>\n",
            "footer", "header", "screen", "viewport",
        )

    fun testObjectNamesComeFromImportedFilesToo() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    shared_header   #header\n")
        assertOffers(
            "@import shared.gspec\n\n= Main =\n    a:\n        below <caret>\n",
            "shared_header",
        )
    }

    /** An object from an unimported file would produce a spec that cannot run. */
    fun testObjectNamesFromUnimportedFilesAreNotOffered() {
        myFixture.addFileToProject("other.gspec", "@objects\n    lonely   #lonely\n")
        assertDoesNotOffer("= Main =\n    a:\n        below <caret>\n", "lonely")
    }

    fun testDottedNestedNamesAreOffered() =
        assertOffers(
            "@objects\n    panel  #panel\n        input  input\n\n= Main =\n    a:\n        below <caret>\n",
            "panel.input",
        )

    // ---- sides -------------------------------------------------------------

    fun testSidesAfterARange() =
        assertOffers(
            "= Main =\n    a:\n        near b 10px <caret>\n",
            "left", "right", "top", "bottom",
        )

    fun testSpecNamesAreNotOfferedWhereASideBelongs() =
        assertDoesNotOffer("= Main =\n    a:\n        near b 10px <caret>\n", "visible", "width")

    // ---- text and count ----------------------------------------------------

    fun testTextOffersMatchersAndOperations() =
        assertOffers(
            "= Main =\n    a:\n        text <caret>\n",
            "is", "contains", "matches", "lowercase", "singleline",
        )

    fun testMatchersAreNotOfferedTwice() =
        assertDoesNotOffer("= Main =\n    a:\n        text is \"x\" <caret>\n", "is", "matches")

    fun testCountOffersFilters() =
        assertOffers("= Main =\n    global:\n        count <caret>\n", "any", "visible", "absent")

    fun testImageOffersOptionsAndFilters() =
        assertOffers("= Main =\n    a:\n        image <caret>\n", "file", "error", "tolerance", "blur", "denoise")

    // ---- groups ------------------------------------------------------------

    fun testGroupNamesAfterAmpersand() =
        assertOffers(
            "@objects\n    a  #a\n\n@groups\n    skeleton   a\n    mainframe  a\n\n= Main =\n    &<caret>\n",
            "&skeleton", "&mainframe",
        )
}
