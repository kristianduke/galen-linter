package com.galenlinter.formatting

import com.galenlinter.lang.GalenTypes
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.tree.IElementType

/**
 * Computes the edits that normalise a spec's indentation.
 *
 * ### Why not IntelliJ's formatter
 * The platform formatter rewrites *whitespace* between blocks, and in Galen the indentation is not
 * whitespace: it is a real token the parser has to see in order to build the block structure at
 * all. So the indent is recomputed here from the parse tree instead.
 *
 * ### Why this is safe
 * The depth of each line comes from the tree the parser already built, and every line at depth *n*
 * is given exactly *n* steps. Nesting is therefore reproduced rather than reinterpreted, which is
 * what makes reformatting structure-preserving — asserted directly by
 * `GalenFormatterTest.testReformattingNeverChangesStructure` over every fixture and every example in
 * the reference documentation.
 */
object GalenReindenter {

    data class Edit(val range: TextRange, val text: String)

    /**
     * Lines that own an indented body, and therefore establish a level. This mirrors the parser's
     * own notion of a line, so depth here and depth there cannot disagree.
     */
    private val LINE_ELEMENTS: Set<IElementType> = setOf(
        GalenTypes.SECTION, GalenTypes.OBJECTS_BLOCK, GalenTypes.OBJECT_DEF,
        GalenTypes.GROUPS_BLOCK, GalenTypes.GROUP_DEF, GalenTypes.SET_BLOCK,
        GalenTypes.SET_ENTRY, GalenTypes.SCRIPT_BLOCK, GalenTypes.IMPORT_STATEMENT,
        GalenTypes.LIB_STATEMENT, GalenTypes.RULE_DEFINITION, GalenTypes.RULE_BODY_STATEMENT,
        GalenTypes.RULE_INVOCATION, GalenTypes.ON_STATEMENT, GalenTypes.IF_STATEMENT,
        GalenTypes.ELSEIF_STATEMENT, GalenTypes.ELSE_STATEMENT, GalenTypes.FOR_STATEMENT,
        GalenTypes.FOREACH_STATEMENT, GalenTypes.DIE_STATEMENT, GalenTypes.OBJECT_STATEMENT,
        GalenTypes.SPEC_LINE, GalenTypes.UNKNOWN_STATEMENT,
    )

    fun computeEdits(file: PsiFile, indentSize: Int): List<Edit> {
        val edits = mutableListOf<Edit>()
        visit(file, 0, false, indentSize, edits)
        alignObjectColumns(file, edits)
        // Applied back to front so earlier offsets stay valid.
        return edits.sortedByDescending { it.range.startOffset }
    }

    private fun visit(
        element: PsiElement,
        depth: Int,
        insideScript: Boolean,
        indentSize: Int,
        edits: MutableList<Edit>,
    ) {
        var child = element.firstChild
        while (child != null) {
            val type = child.node?.elementType
            if (type in LINE_ELEMENTS || type == GalenTypes.RAW_JS_LINE) {
                // A @script body is JavaScript, not Galen. Its indentation is the author's to
                // arrange; Galen only requires it to be deeper than the @script line itself.
                if (!insideScript) reindent(child, depth, indentSize, edits)

                visit(
                    child,
                    depth + 1,
                    insideScript || type == GalenTypes.SCRIPT_BLOCK,
                    indentSize,
                    edits,
                )
            } else {
                visit(child, depth, insideScript, indentSize, edits)
            }
            child = child.nextSibling
        }
    }

    private fun reindent(line: PsiElement, depth: Int, indentSize: Int, edits: MutableList<Edit>) {
        val first = line.node?.firstChildNode ?: return
        val wanted = " ".repeat(depth * indentSize)

        if (first.elementType == GalenTypes.LINE_INDENT) {
            if (first.text != wanted) edits += Edit(first.textRange, wanted)
        } else if (wanted.isNotEmpty()) {
            // A nested line always carries an indent token, since without one it would not have
            // been nested; nothing to do when it does not.
            edits += Edit(TextRange(line.textRange.startOffset, line.textRange.startOffset), wanted)
        }
    }

    /**
     * Lines up the locator column within each `@objects` block, which is how these are written by
     * hand and what makes a block of declarations readable.
     *
     * Only the whitespace *inside* a line is touched, so this cannot affect block structure.
     */
    private fun alignObjectColumns(file: PsiFile, edits: MutableList<Edit>) {
        for (block in descendants(file)) {
            if (block.node?.elementType != GalenTypes.OBJECTS_BLOCK) continue

            // Siblings only: a nested group of definitions aligns within itself.
            val siblings = block.children.filter { it.node?.elementType == GalenTypes.OBJECT_DEF }
            alignGroup(siblings, edits)
        }
    }

    private fun alignGroup(definitions: List<PsiElement>, edits: MutableList<Edit>) {
        if (definitions.size < 2) return

        val names = definitions.mapNotNull { definition ->
            val name = definition.node?.findChildByType(GalenTypes.OBJECT_NAME) ?: return@mapNotNull null
            definition to name
        }
        if (names.size < 2) return

        val widest = names.maxOf { (_, name) -> name.textLength }

        for ((definition, name) in names) {
            val gap = name.treeNext ?: continue
            if (gap.elementType != com.intellij.psi.TokenType.WHITE_SPACE) continue
            // Two spaces past the longest name, so the columns clear it.
            val wanted = " ".repeat(widest - name.textLength + 2)
            if (gap.text != wanted) edits += Edit(gap.textRange, wanted)
        }
    }

    private fun descendants(element: PsiElement): Sequence<PsiElement> = sequence {
        var child = element.firstChild
        while (child != null) {
            yield(child)
            yieldAll(descendants(child))
            child = child.nextSibling
        }
    }
}
