package com.galenlinter.highlight

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.annotation.AnnotationHolder
import com.intellij.lang.annotation.Annotator
import com.intellij.lang.annotation.HighlightSeverity
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

/**
 * Context-sensitive colouring, applied on top of [GalenSyntaxHighlighter].
 *
 * Almost every keyword in a Galen spec is a bare word — `left`, `is`, `visible`, `width`, `px`,
 * `horizontally` — and several are reused in unrelated roles (`contains` is both a spec and a text
 * matcher; `all`, `top` and `left` are variously sides, alignment edges and corners). The lexer
 * cannot tell them apart, so every keyword colour is decided here, from the position the parser
 * assigned the word in the grammar.
 *
 * Colouring only what the parser recognised has a useful side effect: a misspelled keyword simply
 * fails to light up, which is often noticed before the inspection warning is read.
 */
class GalenAnnotator : Annotator {

    override fun annotate(element: PsiElement, holder: AnnotationHolder) {
        val attributes = attributesFor(element) ?: return
        holder.newSilentAnnotation(HighlightSeverity.INFORMATION)
            .range(element)
            .textAttributes(attributes)
            .create()
    }

    private fun attributesFor(element: PsiElement): TextAttributesKey? =
        when (element.node.elementType) {
            GalenTypes.SPEC_NAME ->
                if (element.text in GalenTypes.SPEC_NAMES) GalenColors.SPEC_NAME
                else GalenColors.UNKNOWN_SPEC

            // A special object needs no declaration, so highlighting it distinctly makes an
            // undeclared ordinary name stand out by contrast.
            GalenTypes.OBJECT_NAME_REF ->
                if (element.text in GalenTypes.SPECIAL_OBJECTS) GalenColors.SPECIAL_OBJECT
                else GalenColors.OBJECT_REF

            GalenTypes.OBJECT_NAME -> GalenColors.OBJECT_REF
            GalenTypes.GROUP_REF -> GalenColors.GROUP_REF
            GalenTypes.LOCATOR -> GalenColors.LOCATOR
            GalenTypes.LOCATOR_TYPE -> GalenColors.LOCATOR_TYPE
            GalenTypes.SECTION_TITLE -> GalenColors.SECTION
            GalenTypes.FILE_PATH_REF -> GalenColors.FILE_PATH

            // Only colour a side that really is one; a typo stays uncoloured on purpose.
            GalenTypes.SIDE ->
                if (element.text in GalenTypes.SIDES) GalenColors.SIDE else null

            GalenTypes.MATCHER ->
                if (element.text in GalenTypes.MATCHERS || element.text == "is") GalenColors.MATCHER
                else null

            GalenTypes.TEXT_OPERATION ->
                if (element.text in GalenTypes.TEXT_OPERATIONS) GalenColors.TEXT_OPERATION else null

            GalenTypes.ALIGN_DIRECTION ->
                if (element.text in GalenTypes.ALIGN_DIRECTIONS) GalenColors.ALIGN_KEYWORD else null

            GalenTypes.ALIGN_EDGE ->
                if (element.text in GalenTypes.ALIGN_EDGES) GalenColors.ALIGN_KEYWORD else null

            GalenTypes.CENTERED_DIRECTION ->
                if (element.text in GalenTypes.CENTERED_DIRECTIONS) GalenColors.ALIGN_KEYWORD else null

            GalenTypes.CENTERED_RELATION ->
                if (element.text in GalenTypes.CENTERED_RELATIONS) GalenColors.ALIGN_KEYWORD else null

            GalenTypes.COUNT_FILTER ->
                if (element.text in GalenTypes.COUNT_FILTERS) GalenColors.MODIFIER else null

            GalenTypes.MODIFIER -> GalenColors.MODIFIER
            GalenTypes.UNIT, GalenTypes.RANGE_KEYWORD -> GalenColors.UNIT

            GalenTypes.PROPERTY_NAME ->
                if (element.text in GalenTypes.RELATIVE_PROPERTIES) GalenColors.PROPERTY_NAME else null

            GalenTypes.IMAGE_OPTION ->
                if (element.text in GalenTypes.IMAGE_OPTIONS) GalenColors.IMAGE_OPTION else null

            GalenTypes.IMAGE_FILTER ->
                if (element.text in GalenTypes.IMAGE_FILTERS) GalenColors.IMAGE_OPTION else null

            GalenTypes.COLOR_VALUE -> GalenColors.COLOR_VALUE
            GalenTypes.CSS_PROPERTY -> GalenColors.PROPERTY_NAME

            else -> null
        }
}
