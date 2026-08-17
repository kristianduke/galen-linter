package com.galenlinter.highlight

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.folding.FoldingBuilderEx
import com.intellij.lang.folding.FoldingDescriptor
import com.intellij.openapi.editor.Document
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.tree.IElementType
import com.intellij.psi.util.PsiTreeUtil

/**
 * Code folding for Galen specs.
 *
 * Galen has no closing delimiters — a block is defined purely by the indentation of the lines
 * following its header — so a foldable region is "everything more-indented than this header line".
 * The PSI mirrors that: a line element *contains* its children, so the fold range runs from the
 * end of the header line to the end of the element, with the trailing newline trimmed off so the
 * collapsed placeholder does not swallow the following blank line.
 */
class GalenFoldingBuilder : FoldingBuilderEx() {

    override fun isCollapsedByDefault(node: ASTNode): Boolean = false

    override fun getPlaceholderText(node: ASTNode): String {
        val text = node.psi?.let { headerTextOf(it) } ?: return "..."
        return "$text ..."
    }

    override fun buildFoldRegions(root: PsiElement, document: Document, quick: Boolean): Array<FoldingDescriptor> {
        val descriptors = mutableListOf<FoldingDescriptor>()

        for (element in PsiTreeUtil.findChildrenOfAnyType(root, PsiElement::class.java)) {
            val type = element.node?.elementType ?: continue
            if (type !in FOLDABLE) continue
            val range = foldRangeOf(element) ?: continue
            descriptors += FoldingDescriptor(element.node, range)
        }

        return descriptors.toTypedArray()
    }

    /**
     * The region after the header line, or null when there is nothing to fold — a section with no
     * body, or an `@objects` entry with no nested children, must not offer a fold arrow.
     */
    private fun foldRangeOf(element: PsiElement): TextRange? {
        val elementRange = element.textRange
        val headerEnd = headerEndOffset(element) ?: return null

        var end = elementRange.endOffset
        val text = element.containingFile.text
        while (end > headerEnd && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--

        if (end <= headerEnd + 1) return null
        return TextRange(headerEnd, end)
    }

    /** Offset of the first newline inside the element: the end of its header line. */
    private fun headerEndOffset(element: PsiElement): Int? {
        val child = element.node.findChildByType(GalenTypes.EOL) ?: return null
        return child.startOffset
    }

    private fun headerTextOf(element: PsiElement): String {
        val headerEnd = headerEndOffset(element) ?: return "..."
        val start = element.textRange.startOffset
        return element.containingFile.text
            .substring(start, headerEnd)
            .trim()
            .ifEmpty { "..." }
    }

    private companion object {
        /**
         * Every construct that owns an indented body. `RULE_INVOCATION` is included because a rule
         * with a `@ruleBody` is invoked with a block underneath it; the null fold range filters out
         * the invocations that have none.
         */
        val FOLDABLE: Set<IElementType> = setOf(
            GalenTypes.SECTION,
            GalenTypes.OBJECTS_BLOCK,
            GalenTypes.GROUPS_BLOCK,
            GalenTypes.SET_BLOCK,
            GalenTypes.SCRIPT_BLOCK,
            GalenTypes.ON_STATEMENT,
            GalenTypes.IF_STATEMENT,
            GalenTypes.ELSEIF_STATEMENT,
            GalenTypes.ELSE_STATEMENT,
            GalenTypes.FOR_STATEMENT,
            GalenTypes.FOREACH_STATEMENT,
            GalenTypes.RULE_DEFINITION,
            GalenTypes.RULE_INVOCATION,
            GalenTypes.OBJECT_STATEMENT,
            GalenTypes.OBJECT_DEF,
        )
    }
}
