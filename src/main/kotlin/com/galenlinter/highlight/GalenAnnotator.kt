package com.galenlinter.highlight

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

/**
 * Context-sensitive colouring, applied on top of [GalenSyntaxHighlighter].
 *
 * The lexer cannot tell a spec name from a locator fragment from an object reference — they are
 * all bare words — so anything that depends on position in the grammar is coloured from the PSI
 * tree here.
 */
class GalenAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val attributes = when (element.node.elementType) {
            GalenTypes.SPEC_NAME ->
                if (element.text in GalenTypes.SPEC_NAMES) GalenColors.SPEC_NAME
                else GalenColors.UNKNOWN_SPEC

            GalenTypes.LOCATOR -> GalenColors.LOCATOR
            GalenTypes.OBJECT_REF -> GalenColors.OBJECT_REF
            GalenTypes.SECTION_TITLE -> GalenColors.SECTION
            else -> return
        }

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attributes)
            .create()
    }
}
