package com.galenlinter.inspections

import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenReferenceInspectionTest : BasePlatformTestCase() {

    private fun findings(fileName: String, text: String): List<String> {
        myFixture.enableInspections(
            GalenUnresolvedReferenceInspection(),
            GalenMissingFileInspection(),
        )
        myFixture.configureByText(fileName, text)
        return myFixture.doHighlighting().mapNotNull { it.description }.filter { it.startsWith("GL") }
    }

    private fun assertReports(rule: String, text: String) {
        val found = findings("test.gspec", text)
        assertTrue("Expected $rule, got $found", found.any { it.startsWith(rule) })
    }

    private fun assertClean(text: String) {
        val found = findings("test.gspec", text)
        assertTrue("Expected no findings, got $found", found.isEmpty())
    }

    // ---- GL201 -------------------------------------------------------------

    fun testUndeclaredObjectIsReported() =
        assertReports(
            "GL201",
            "@objects\n    header  #header\n\n= Main =\n    header:\n        below foter 10px\n",
        )

    fun testDeclaredObjectIsAccepted() =
        assertClean(
            "@objects\n    header  #header\n    footer  #footer\n\n= Main =\n    header:\n        below footer 10px\n",
        )

    fun testWildcardFamilySatisfiesConcreteName() =
        assertClean(
            "@objects\n    menu_item-*  #menu li a\n\n= Main =\n    menu_item-1:\n        near menu_item-2 10px left\n",
        )

    fun testSpecialObjectsAreNeverReported() =
        assertClean(
            "@objects\n    header  #header\n\n= Main =\n    header:\n        inside screen 0px top\n" +
                "        centered vertically inside viewport\n    global:\n        count any header is 1\n",
        )

    /** The single most important negative case for this inspection. */
    fun testExpressionNamesAreNeverReported() =
        assertClean(
            "@objects\n    menu_item-*  #menu li a\n\n= Main =\n" +
                "    @forEach [menu_item-*] as item, next as nextItem\n" +
                "        ${'$'}{item}:\n            left-of ${'$'}{nextItem} 10px\n",
        )

    fun testObjectStatementHeaderIsAlsoChecked() =
        assertReports("GL201", "@objects\n    header  #header\n\n= Main =\n    fotter:\n        visible\n")

    /** The hint should point at the missing import rather than just saying "unknown". */
    fun testUnimportedDeclarationIsNamedInTheMessage() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    shared_header   #header\n")
        val found = findings(
            "page.gspec",
            "@objects\n    local  #local\n\n= Main =\n    local:\n        below shared_header 10px\n",
        )
        assertTrue("Expected GL201, got $found", found.any { it.startsWith("GL201") })
        assertTrue(
            "Message should name the file declaring it, got $found",
            found.any { it.contains("shared.gspec") },
        )
    }

    fun testImportedObjectIsAccepted() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    shared_header   #header\n")
        val found = findings(
            "page.gspec",
            "@import shared.gspec\n\n@objects\n    local  #local\n\n= Main =\n    local:\n" +
                "        below shared_header 10px\n",
        )
        assertTrue("Expected no GL201, got $found", found.none { it.startsWith("GL201") })
    }

    // ---- GL202 -------------------------------------------------------------

    fun testUnknownGroupIsReported() =
        assertReports(
            "GL202",
            "@objects\n    header  #header\n\n@groups\n    skeleton   header\n\n= Main =\n    &skeltn:\n        visible\n",
        )

    fun testKnownGroupIsAccepted() =
        assertClean(
            "@objects\n    header  #header\n\n@groups\n    skeleton   header\n\n= Main =\n    &skeleton:\n        visible\n",
        )

    // ---- GL501 -------------------------------------------------------------

    fun testMissingImportFileIsReported() =
        assertReports("GL501", "@import nope.gspec\n")

    fun testExistingImportFileIsAccepted() {
        myFixture.addFileToProject("real.gspec", "@objects\n    a  #a\n")
        val found = findings("page.gspec", "@import real.gspec\n")
        assertTrue("Expected no GL501, got $found", found.none { it.startsWith("GL501") })
    }

    /** `@lib` names a library inside the Galen jar, so it must never be reported as missing. */
    fun testLibIsNeverReportedAsMissing() {
        val found = findings("page.gspec", "@lib galen-extras\n")
        assertTrue("@lib must not be reported, got $found", found.none { it.startsWith("GL501") })
    }

    fun testMissingImageFileIsReported() =
        assertReports(
            "GL501",
            "@objects\n    logo  #logo\n\n= Main =\n    logo:\n        image file imgs/missing.png, error 4%\n",
        )
}
