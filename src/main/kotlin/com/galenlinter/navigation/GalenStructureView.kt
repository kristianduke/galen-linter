package com.galenlinter.navigation

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenObjectDefinition
import com.intellij.ide.structureView.StructureViewBuilder
import com.intellij.ide.structureView.StructureViewModel
import com.intellij.ide.structureView.StructureViewModelBase
import com.intellij.ide.structureView.StructureViewTreeElement
import com.intellij.ide.structureView.TreeBasedStructureViewBuilder
import com.intellij.ide.util.treeView.smartTree.SortableTreeElement
import com.intellij.ide.util.treeView.smartTree.TreeElement
import com.intellij.lang.PsiStructureViewFactory
import com.intellij.navigation.ItemPresentation
import com.intellij.openapi.editor.Editor
import com.intellij.psi.NavigatablePsiElement
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType
import javax.swing.Icon

/**
 * The structure view: an outline of a spec file.
 *
 * Galen nests by indentation with no closing delimiters, so a long file gives no visual anchor for
 * where you are. The outline shows the same shape the parser sees.
 */
class GalenStructureViewFactory : PsiStructureViewFactory {
    override fun getStructureViewBuilder(psiFile: PsiFile): StructureViewBuilder? {
        if (psiFile !is GalenFile) return null
        return object : TreeBasedStructureViewBuilder() {
            override fun createStructureViewModel(editor: Editor?): StructureViewModel =
                GalenStructureViewModel(psiFile)
        }
    }
}

class GalenStructureViewModel(file: GalenFile) :
    StructureViewModelBase(file, GalenStructureViewElement(file)),
    StructureViewModel.ElementInfoProvider {

    override fun isAlwaysShowsPlus(element: StructureViewTreeElement): Boolean = false

    /** Spec lines are the leaves; everything above them can hold children. */
    override fun isAlwaysLeaf(element: StructureViewTreeElement): Boolean =
        element.value.let { it is PsiElement && it.node?.elementType == GalenTypes.SPEC_LINE }
}

class GalenStructureViewElement(private val element: PsiElement) :
    StructureViewTreeElement, SortableTreeElement {

    override fun getValue(): Any = element

    override fun navigate(requestFocus: Boolean) =
        (element as? NavigatablePsiElement)?.navigate(requestFocus) ?: Unit

    override fun canNavigate(): Boolean = (element as? NavigatablePsiElement)?.canNavigate() ?: false

    override fun canNavigateToSource(): Boolean =
        (element as? NavigatablePsiElement)?.canNavigateToSource() ?: false

    override fun getAlphaSortKey(): String = presentableTextOf(element) ?: ""

    override fun getPresentation(): ItemPresentation = object : ItemPresentation {
        override fun getPresentableText(): String? = presentableTextOf(element)
        override fun getLocationString(): String? =
            (element as? GalenObjectDefinition)?.locatorText()
        override fun getIcon(unused: Boolean): Icon? = null
    }

    override fun getChildren(): Array<TreeElement> {
        val children = mutableListOf<TreeElement>()
        var child = element.firstChild
        while (child != null) {
            if (child.node?.elementType in INTERESTING) {
                children += GalenStructureViewElement(child)
            }
            child = child.nextSibling
        }
        return children.toTypedArray()
    }

    private companion object {
        /** What is worth an outline row: structure, not every spec. */
        val INTERESTING: Set<IElementType> = setOf(
            GalenTypes.SECTION,
            GalenTypes.OBJECTS_BLOCK,
            GalenTypes.OBJECT_DEF,
            GalenTypes.GROUPS_BLOCK,
            GalenTypes.OBJECT_STATEMENT,
            GalenTypes.RULE_DEFINITION,
            GalenTypes.ON_STATEMENT,
            GalenTypes.IF_STATEMENT,
            GalenTypes.FOR_STATEMENT,
            GalenTypes.FOREACH_STATEMENT,
            GalenTypes.SCRIPT_BLOCK,
            GalenTypes.SET_BLOCK,
        )

        /** The element's own header line, which is what identifies it to a reader. */
        fun presentableTextOf(element: PsiElement): String? {
            if (element is PsiFile) return element.name
            if (element is GalenObjectDefinition) return element.name

            val end = element.node?.findChildByType(GalenTypes.EOL)?.startOffset
                ?: element.textRange.endOffset
            val start = element.textRange.startOffset
            if (end <= start) return element.text.trim().ifEmpty { null }
            return element.containingFile.text.substring(start, end).trim().ifEmpty { null }
        }
    }
}
