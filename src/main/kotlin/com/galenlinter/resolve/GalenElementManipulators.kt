package com.galenlinter.resolve

import com.galenlinter.psi.GalenFilePathRef
import com.galenlinter.psi.GalenGroupRef
import com.galenlinter.psi.GalenObjectNameRef
import com.intellij.openapi.util.TextRange
import com.intellij.psi.AbstractElementManipulator
import com.intellij.psi.PsiElement
import com.intellij.psi.impl.source.tree.LeafElement
import com.intellij.util.IncorrectOperationException

/**
 * Teaches the platform how to write new text into a reference element.
 *
 * `PsiReferenceBase.handleElementRename` delegates to a manipulator; without one registered, any
 * rename that touches a usage fails outright with "No ElementManipulator instance registered".
 *
 * The change is applied to the single leaf containing the requested range rather than by rebuilding
 * the element. That matters for the composite cases: `&skeleton` is `&` plus a word, and
 * `imgs/logo.png` is several tokens, so only one leaf should be rewritten and the rest left alone.
 */
abstract class GalenTextManipulator<T : PsiElement> : AbstractElementManipulator<T>() {

    override fun handleContentChange(element: T, range: TextRange, newContent: String): T {
        val elementStart = element.textRange.startOffset
        var child = element.node.firstChildNode

        while (child != null) {
            val childRange = TextRange(
                child.textRange.startOffset - elementStart,
                child.textRange.endOffset - elementStart,
            )
            if (childRange.contains(range) && child is LeafElement) {
                val within = range.shiftLeft(childRange.startOffset)
                val old = child.text
                child.replaceWithText(
                    old.substring(0, within.startOffset) + newContent + old.substring(within.endOffset),
                )
                return element
            }
            child = child.treeNext
        }

        throw IncorrectOperationException(
            "Cannot rewrite '${element.text}': the range $range does not fall inside a single token.",
        )
    }
}

class GalenObjectNameRefManipulator : GalenTextManipulator<GalenObjectNameRef>()

class GalenGroupRefManipulator : GalenTextManipulator<GalenGroupRef>()

class GalenFilePathRefManipulator : GalenTextManipulator<GalenFilePathRef>()
