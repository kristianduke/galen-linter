package com.galenlinter.resolve

import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenReferenceTest : BasePlatformTestCase() {

    /** Resolves the reference under `<caret>` and returns the declaration's qualified name. */
    private fun resolvedNameAtCaret(): String? {
        val reference = myFixture.getReferenceAtCaretPosition()
        assertNotNull("No reference under the caret", reference)
        val target = reference!!.resolve() ?: return null
        return (target as? GalenObjectDefinition)?.qualifiedName
            ?: target.text
    }

    // ---- in-file resolution ------------------------------------------------

    fun testResolvesSimpleObjectName() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                header      #header
                menu        ul.menu

            = Main =
                menu:
                    inside hea<caret>der 0px top
            """.trimIndent(),
        )
        assertEquals("header", resolvedNameAtCaret())
    }

    fun testResolvesDottedNestedName() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                search_panel    #search-bar
                    input       input[type='text']

            = Main =
                other:
                    near search_panel.in<caret>put 5px left
            """.trimIndent(),
        )
        assertEquals("search_panel.input", resolvedNameAtCaret())
    }

    /** A concrete name must resolve to the wildcard family that produces it. */
    fun testResolvesConcreteNameToWildcardFamily() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                menu_item-*     #menu li a

            = Main =
                header:
                    near menu_i<caret>tem-3 10px left
            """.trimIndent(),
        )
        assertEquals("menu_item-*", resolvedNameAtCaret())
    }

    fun testResolvesNumericWildcard() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                item-#      li

            = Main =
                header:
                    near ite<caret>m-7 10px left
            """.trimIndent(),
        )
        assertEquals("item-#", resolvedNameAtCaret())
    }

    /** `#` matches digits only, so a non-numeric suffix must not resolve to it. */
    fun testNumericWildcardDoesNotMatchLetters() {
        assertFalse(GalenObjectResolver.matchesPattern("item-#", "item-home"))
        assertTrue(GalenObjectResolver.matchesPattern("item-#", "item-12"))
        assertTrue(GalenObjectResolver.matchesPattern("menu_item-*", "menu_item-home"))
    }

    fun testSpecialObjectsResolveAsBuiltins() {
        myFixture.configureByText("test.gspec", "= Main =\n    header:\n        inside scre<caret>en 0px top\n")
        // A builtin has no declaration to jump to, but must not be reported unresolved either.
        val resolution = GalenObjectResolver.resolve("screen", myFixture.file)
        assertEquals(Resolution.Builtin, resolution)
    }

    fun testExpressionNamesAreDynamic() {
        myFixture.configureByText("test.gspec", "= Main =\n    header:\n        visible\n")
        assertEquals(Resolution.Dynamic, GalenObjectResolver.resolve("\${item}", myFixture.file))
        assertEquals(Resolution.Dynamic, GalenObjectResolver.resolve("menu_item-\${index}", myFixture.file))
    }

    fun testUnknownNameIsNotFound() {
        myFixture.configureByText("test.gspec", "@objects\n    header  #header\n")
        val resolution = GalenObjectResolver.resolve("footer", myFixture.file)
        assertTrue("Expected NotFound, got $resolution", resolution is Resolution.NotFound)
    }

    // ---- cross-file resolution through @import -----------------------------

    fun testResolvesObjectFromImportedFile() {
        myFixture.addFileToProject(
            "header.gspec",
            "@objects\n    site_header     #header\n",
        )
        myFixture.configureByText(
            "page.gspec",
            """
            @import header.gspec

            = Main =
                content:
                    below site_hea<caret>der 10px
            """.trimIndent(),
        )
        assertEquals("site_header", resolvedNameAtCaret())
    }

    /** Imports chain: a -> b -> c must still resolve c's objects. */
    fun testResolvesThroughTransitiveImports() {
        myFixture.addFileToProject("base.gspec", "@objects\n    deep_object    #deep\n")
        myFixture.addFileToProject("middle.gspec", "@import base.gspec\n")
        myFixture.configureByText(
            "page.gspec",
            "@import middle.gspec\n\n= Main =\n    content:\n        below deep_ob<caret>ject 10px\n",
        )
        assertEquals("deep_object", resolvedNameAtCaret())
    }

    fun testImportPathsResolveRelativeToTheImportingFile() {
        myFixture.addFileToProject("shared/header.gspec", "@objects\n    nested_header   #header\n")
        myFixture.configureByText(
            "page.gspec",
            "@import shared/header.gspec\n\n= Main =\n    content:\n        below nested_hea<caret>der 10px\n",
        )
        assertEquals("nested_header", resolvedNameAtCaret())
    }

    /** A cycle must terminate rather than recurse forever. */
    fun testCircularImportsTerminate() {
        myFixture.addFileToProject("a.gspec", "@import b.gspec\n@objects\n    from_a  #a\n")
        myFixture.addFileToProject("b.gspec", "@import a.gspec\n@objects\n    from_b  #b\n")
        myFixture.configureByText(
            "page.gspec",
            "@import a.gspec\n\n= Main =\n    x:\n        below from<caret>_b 10px\n",
        )
        assertEquals("from_b", resolvedNameAtCaret())
    }

    /** An object declared in an unimported file is not in scope, but the hint should name it. */
    fun testNotImportedFileIsReportedAsDeclaredElsewhere() {
        myFixture.addFileToProject("other.gspec", "@objects\n    lonely_object   #lonely\n")
        myFixture.configureByText("page.gspec", "= Main =\n    x:\n        visible\n")

        val resolution = GalenObjectResolver.resolve("lonely_object", myFixture.file)
        assertTrue("Expected NotFound, got $resolution", resolution is Resolution.NotFound)
        assertEquals("other.gspec", (resolution as Resolution.NotFound).declaredElsewhere)
    }

    // ---- file path references ---------------------------------------------

    fun testImportPathIsClickable() {
        myFixture.addFileToProject("header.gspec", "@objects\n    a   #a\n")
        myFixture.configureByText("page.gspec", "@import head<caret>er.gspec\n")
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertNotNull("The @import path should resolve to the file", target)
        assertEquals("header.gspec", (target as? com.intellij.psi.PsiFile)?.name)
    }

    fun testComponentPathIsClickable() {
        myFixture.addFileToProject("profile.gspec", "@objects\n    a   #a\n")
        myFixture.configureByText(
            "page.gspec",
            "= Main =\n    card:\n        component prof<caret>ile.gspec\n",
        )
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertEquals("profile.gspec", (target as? com.intellij.psi.PsiFile)?.name)
    }

    // ---- groups ------------------------------------------------------------

    fun testGroupReferenceResolves() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                header      #header
                footer      #footer

            @groups
                skeleton    header, footer

            = Main =
                &skel<caret>eton:
                    visible
            """.trimIndent(),
        )
        val target = myFixture.getReferenceAtCaretPosition()?.resolve()
        assertNotNull("Group reference should resolve to its @groups line", target)
        assertTrue(target!!.text.contains("skeleton"))
    }

    fun testGroupReferenceWithBracketedDeclarationResolves() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                header      #header

            @groups
                (skeleton, mainframe)   header

            = Main =
                &mainfr<caret>ame:
                    visible
            """.trimIndent(),
        )
        assertNotNull(myFixture.getReferenceAtCaretPosition()?.resolve())
    }
}
