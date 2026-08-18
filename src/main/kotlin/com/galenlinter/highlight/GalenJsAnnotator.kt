package com.galenlinter.highlight

import com.galenlinter.js.GalenJsLexer
import com.galenlinter.psi.GalenExpressionElement
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenRawJsLine
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement

/**
 * Colours embedded JavaScript when no JavaScript plugin is installed.
 *
 * IntelliJ IDEA Community bundles no JavaScript support, so injection cannot run there and a
 * `${...}` expression or `@script` body would otherwise be one undifferentiated span. This paints
 * the lexical surface — comments, strings, numbers, keywords and Galen's own API functions — which
 * is most of the readability benefit for a fraction of a language implementation.
 *
 * It stands down entirely when a real JavaScript plugin is present, so the two never fight.
 */
class GalenJsAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        if (element.containingFile !is GalenFile) return
        if (!GalenJsLexer.isFallbackNeeded()) return

        val (text, offset) = when (element) {
            is GalenExpressionElement -> {
                val range = element.expressionRange() ?: return
                range.substring(element.text) to (element.textRange.startOffset + range.startOffset)
            }

            is GalenRawJsLine -> {
                val range = element.contentRange() ?: return
                range.substring(element.text) to (element.textRange.startOffset + range.startOffset)
            }

            else -> return
        }

        for (token in GalenJsLexer.tokenize(text)) {
            val attributes = attributesFor(token.kind) ?: continue
            holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
                .range(TextRange(offset + token.range.startOffset, offset + token.range.endOffset))
                .textAttributes(attributes)
                .create()
        }
    }

    private fun attributesFor(kind: GalenJsLexer.Kind): TextAttributesKey? = when (kind) {
        GalenJsLexer.Kind.COMMENT -> GalenColors.JS_COMMENT
        GalenJsLexer.Kind.STRING -> GalenColors.JS_STRING
        GalenJsLexer.Kind.NUMBER -> GalenColors.JS_NUMBER
        GalenJsLexer.Kind.KEYWORD -> GalenColors.JS_KEYWORD
        GalenJsLexer.Kind.GALEN_API -> GalenColors.JS_GALEN_API
        GalenJsLexer.Kind.IDENTIFIER -> GalenColors.JS_IDENTIFIER
        GalenJsLexer.Kind.PUNCTUATION -> GalenColors.JS_PUNCTUATION
    }
}
