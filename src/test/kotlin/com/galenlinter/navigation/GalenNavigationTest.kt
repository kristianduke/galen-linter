package com.galenlinter.navigation

import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.psi.PsiElement
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import java.awt.Color

class GalenNavigationTest : BasePlatformTestCase() {

    // ---- structure view ----------------------------------------------------

    private fun outline(text: String): List<String> {
        val file = myFixture.configureByText("test.gspec", text) as GalenFile
        val root = GalenStructureViewElement(file)
        val rows = mutableListOf<String>()
        fun walk(element: TreeElement, depth: Int) {
            val label = (element as StructureViewTreeElement).presentation.presentableText ?: return
            rows += "  ".repeat(depth) + label
            element.children.forEach { walk(it, depth + 1) }
        }
        root.children.forEach { walk(it, 0) }
        return rows
    }

    fun testStructureViewShowsTheBlockShape() {
        val rows = outline(
            """
            @objects
                header    #header
                menu      ul.menu

            = Main section =
                header:
                    visible
            """.trimIndent(),
        )
        assertTrue("Expected the @objects block, got $rows", rows.any { it.trim() == "@objects" })
        assertTrue("Expected object entries, got $rows", rows.any { it.trim() == "header" })
        assertTrue("Expected the section, got $rows", rows.any { it.trim() == "= Main section =" })
        assertTrue("Expected the object statement, got $rows", rows.any { it.trim() == "header:" })
    }

    fun testStructureViewNestsSections() {
        val rows = outline(
            "= Outer =\n    = Inner =\n        a:\n            visible\n",
        )
        val outer = rows.indexOfFirst { it.trim() == "= Outer =" }
        val inner = rows.indexOfFirst { it.trim() == "= Inner =" }
        assertTrue("Both sections should appear, got $rows", outer >= 0 && inner > outer)
        assertTrue("Inner should be nested deeper, got $rows", rows[inner].startsWith("  "))
    }

    /** Individual specs would swamp the outline; only structure earns a row. */
    fun testSpecLinesAreNotInTheOutline() {
        val rows = outline("= Main =\n    a:\n        visible\n        width 100px\n")
        assertFalse("Spec lines should not appear, got $rows", rows.any { it.contains("width 100px") })
    }

    // ---- breadcrumbs -------------------------------------------------------

    fun testBreadcrumbsDescribeTheEnclosingBlocks() {
        myFixture.configureByText(
            "test.gspec",
            "= Main section =\n    hero-header:\n        wid<caret>th 100px\n",
        )
        val provider = GalenBreadcrumbsProvider()
        val crumbs = mutableListOf<String>()
        var element: PsiElement? = myFixture.file.findElementAt(myFixture.caretOffset)
        while (element != null) {
            if (provider.acceptElement(element)) crumbs += provider.getElementInfo(element)
            element = element.parent
        }
        crumbs.reverse()
        assertEquals(listOf("= Main section =", "hero-header:", "width 100px"), crumbs)
    }

    // ---- colour swatches ---------------------------------------------------

    private fun colourOf(text: String, target: String): Color? {
        myFixture.configureByText("test.gspec", text)
        val provider = GalenColorProvider()
        var result: Color? = null
        fun walk(element: PsiElement) {
            if (element.text == target && result == null) {
                provider.getColorFrom(element)?.let { result = it }
            }
            var child = element.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        walk(myFixture.file)
        return result
    }

    fun testHexColoursGetASwatch() {
        val body = "= Main =\n    a:\n        color-scheme 30% #f845b7\n"
        assertEquals(Color(0xf8, 0x45, 0xb7), colourOf(body, "#f845b7"))
    }

    fun testShortHexIsExpanded() =
        assertEquals(Color(0x00, 0x55, 0xaa), colourOf("= Main =\n    a:\n        color-scheme 30% #05a\n", "#05a"))

    fun testNamedColoursGetASwatch() =
        assertEquals(Color.WHITE, colourOf("= Main =\n    a:\n        color-scheme 80% white\n", "white"))

    /** A gradient is several colours in one token, with no single value to show or set. */
    fun testGradientsGetNoSwatch() =
        assertNull(colourOf("= Main =\n    a:\n        color-scheme 20% #000-#555-#955\n", "#000-#555-#955"))

    // ---- go to symbol ------------------------------------------------------

    fun testGoToSymbolFindsObjectsAcrossFiles() {
        myFixture.addFileToProject("other.gspec", "@objects\n    remote_object   #remote\n")
        myFixture.configureByText("test.gspec", "@objects\n    local_object   #local\n")

        val contributor = GalenGotoSymbolContributor()
        val names = mutableListOf<String>()
        contributor.processNames({ names += it; true }, com.intellij.psi.search.GlobalSearchScope.allScope(project), null)

        assertTrue("Expected the local object, got $names", names.contains("local_object"))
        assertTrue("Expected the object from another file, got $names", names.contains("remote_object"))
        assertFalse("The wildcard sentinel is not a name", names.any { it.isBlank() })
    }

    fun testGoToSymbolResolvesToTheDeclaration() {
        myFixture.addFileToProject("other.gspec", "@objects\n    remote_object   css   #remote\n")
        myFixture.configureByText("test.gspec", "@objects\n    a   #a\n")

        val found = mutableListOf<GalenObjectDefinition>()
        GalenGotoSymbolContributor().processElementsWithName(
            "remote_object",
            { item -> (item as? GalenObjectDefinition)?.let { found += it }; true },
            com.intellij.util.indexing.FindSymbolParameters.simple(project, false),
        )
        assertEquals(1, found.size)
        assertEquals("remote_object", found.first().qualifiedName)
    }

    // ---- image preview -----------------------------------------------------

    fun testHoveringAnImageSampleShowsThePicture() {
        myFixture.addFileToProject("imgs/logo.png", "not really a png")
        myFixture.configureByText(
            "test.gspec",
            "= Main =\n    logo:\n        image file imgs/lo<caret>go.png, error 4%\n",
        )
        val provider = com.galenlinter.documentation.GalenDocumentationProvider()
        val context = myFixture.file.findElementAt(myFixture.caretOffset)
        val target = provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, context, myFixture.caretOffset,
        )
        assertNotNull("The path should resolve to the image file", target)

        val doc = provider.generateDoc(target, context)
        assertNotNull("Expected documentation for the image", doc)
        assertTrue("Expected an <img> tag, got:\n$doc", doc!!.contains("<img src="))
        assertTrue("Expected it to point at the sample, got:\n$doc", doc.contains("logo.png"))
    }

    fun testHoveringANonImageFileShowsNothingSpecial() {
        myFixture.addFileToProject("shared.gspec", "@objects\n    a   #a\n")
        myFixture.configureByText("page.gspec", "@import shar<caret>ed.gspec\n")
        val provider = com.galenlinter.documentation.GalenDocumentationProvider()
        val context = myFixture.file.findElementAt(myFixture.caretOffset)
        val target = provider.getCustomDocumentationElement(
            myFixture.editor, myFixture.file, context, myFixture.caretOffset,
        )
        // Resolves to the spec file, which has no image preview to offer.
        assertNull(provider.generateDoc(target, context))
    }
}
