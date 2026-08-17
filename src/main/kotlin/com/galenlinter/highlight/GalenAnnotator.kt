package com.galenlinter.highlight

import com.galenlinter.psi.GalenFile
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.psi.PsiElement

/**
 * Context-sensitive colouring, applied on top of [GalenSyntaxHighlighter].
 *
 * Almost every keyword in a Galen spec is a bare word — `left`, `is`, `visible`, `width`, `px`,
 * `horizontally` — and several are reused in unrelated roles (`contains` is both a spec and a text
 * matcher; `all`, `top` and `left` are variously sides, alignment edges and corners). The lexer
 * cannot tell them apart, so every keyword colour is decided from the position the parser assigned
 * the word in the grammar. The mapping itself lives in [GalenColorMapping] so it can be tested.
 */
class GalenAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is GalenFile) return
        val attributes = GalenColorMapping.attributesFor(element) ?: return

        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attributes)
            .create()
    }
}
