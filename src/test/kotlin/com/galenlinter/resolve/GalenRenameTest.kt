package com.galenlinter.resolve

import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.openapi.command.WriteCommandAction
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.util.IncorrectOperationException

class GalenRenameTest : BasePlatformTestCase() {

    fun testRenamingAnObjectUpdatesItsUsages() {
        myFixture.configureByText(
            "test.gspec",
            """
            @objects
                hea<caret>der      #header
                menu        ul.menu

            = Main =
                menu:
                    below header 10px
                    aligned horizontally all header
            """.trimIndent(),
        )

        myFixture.renameElementAtCaret("site_header")

        myFixture.checkResult(
            """
            @objects
                site_header      #header
                menu        ul.menu

            = Main =
                menu:
                    below site_header 10px
                    aligned horizontally all site_header
            """.trimIndent(),
        )
    }

    fun testRenamingUpdatesTheObjectStatementHeader() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    hea<caret>der   #header\n\n= Main =\n    header:\n        visible\n",
        )
        myFixture.renameElementAtCaret("top_bar")
        myFixture.checkResult("@objects\n    top_bar   #header\n\n= Main =\n    top_bar:\n        visible\n")
    }

    fun testRenamingUpdatesUsagesInImportingFiles() {
        myFixture.addFileToProject(
            "page.gspec",
            "@import shared.gspec\n\n= Main =\n    shared_header:\n        visible\n",
        )
        myFixture.configureByText("shared.gspec", "@objects\n    shared_hea<caret>der   #header\n")

        myFixture.renameElementAtCaret("banner")

        // Read through PSI, not the VFS: the refactoring lives in the document until it is saved,
        // so contentsToByteArray() would show the pre-rename text.
        val page = myFixture.findFileInTempDir("page.gspec")
        val text = PsiManager.getInstance(project).findFile(page)!!.text
        assertTrue("Usage in the importing file should be updated, got:\n$text", text.contains("banner:"))
        assertFalse("Old name should be gone, got:\n$text", text.contains("shared_header"))
    }

    /**
     * Renaming a wildcard family is refused rather than approximated.
     *
     * `menu_item-*` names a family whose members are `menu_item-1`, `menu_item-2` and so on.
     * Rewriting the declaration cannot rewrite those, and renaming only the declaration would
     * leave every usage pointing at nothing — a silent break, which is worse than a refusal.
     */
    fun testRenamingAWildcardFamilyIsRefused() {
        myFixture.configureByText("test.gspec", "@objects\n    menu_item-*   #menu li a\n")
        val definition = PsiTreeUtil.findChildOfType(myFixture.file, GalenObjectDefinition::class.java)
        assertNotNull(definition)
        assertTrue("Should be recognised as a pattern", definition!!.isPattern)

        try {
            definition.setName("menu_entry")
            fail("Renaming a wildcard family should have been refused")
        } catch (expected: IncorrectOperationException) {
            assertTrue(
                "The refusal should explain why, got: ${expected.message}",
                expected.message!!.contains("wildcard"),
            )
        }
    }

    fun testExactNameIsNotTreatedAsAPattern() {
        myFixture.configureByText("test.gspec", "@objects\n    header   #header\n")
        val definition = PsiTreeUtil.findChildOfType(myFixture.file, GalenObjectDefinition::class.java)!!
        assertFalse(definition.isPattern)

        WriteCommandAction.runWriteCommandAction(project) {
            definition.setName("renamed")
        }
        assertEquals("renamed", definition.name)
    }

    /**
     * Regression: renaming to a dashed name was refused as "not a valid identifier", because with
     * no NamesValidator registered the platform applies Java identifier rules. Galen object names
     * routinely contain dashes.
     */
    fun testRenamingToADashedNameIsAllowed() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    hea<caret>der   #header\n\n= Main =\n    header:\n        visible\n",
        )
        myFixture.renameElementAtCaret("hero-header")
        myFixture.checkResult(
            "@objects\n    hero-header   #header\n\n= Main =\n    hero-header:\n        visible\n",
        )
    }

    fun testRenamingFromADashedNameIsAllowed() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    hero-hea<caret>der   #header\n\n= Main =\n    menu:\n        below hero-header 10px\n",
        )
        myFixture.renameElementAtCaret("site-banner")
        myFixture.checkResult(
            "@objects\n    site-banner   #header\n\n= Main =\n    menu:\n        below site-banner 10px\n",
        )
    }

    fun testQualifiedNameOfNestedObject() {
        myFixture.configureByText(
            "test.gspec",
            "@objects\n    panel   #panel\n        input   input\n",
        )
        val names = PsiTreeUtil.findChildrenOfType(myFixture.file, GalenObjectDefinition::class.java)
            .map { it.qualifiedName }
        assertTrue("Expected nested dotted name, got $names", names.contains("panel.input"))
        assertTrue(names.contains("panel"))
    }
}
