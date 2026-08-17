package com.galenlinter.highlight

import com.galenlinter.lang.GalenTypes
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.psi.PsiElement

/**
 * Which colour, if any, a parsed element should get.
 *
 * Split out of [GalenAnnotator] so the mapping can be asserted directly in tests. Colouring is a
 * feature that fails silently — an element can be parsed and annotated correctly and still render
 * as plain text if its key falls back to `IDENTIFIER` — so it is worth testing rather than eyeballing.
 */
object GalenColorMapping {

    fun attributesFor(element: PsiElement): TextAttributesKey? =
        when (element.node?.elementType) {
            GalenTypes.SPEC_NAME ->
                if (element.text in GalenTypes.SPEC_NAMES) GalenColors.SPEC_NAME
                else GalenColors.UNKNOWN_SPEC

            GalenTypes.OBJECT_NAME_REF -> when {
                // `objectName:` opening a block of specs, rather than a name used as an argument.
                isStatementHeader(element) -> GalenColors.OBJECT_HEADER
                element.text in GalenTypes.SPECIAL_OBJECTS -> GalenColors.SPECIAL_OBJECT
                else -> GalenColors.OBJECT_REF
            }

            GalenTypes.OBJECT_NAME -> GalenColors.OBJECT_REF
            GalenTypes.GROUP_REF ->
                if (isStatementHeader(element)) GalenColors.OBJECT_HEADER else GalenColors.GROUP_REF

            GalenTypes.LOCATOR -> GalenColors.LOCATOR
            GalenTypes.LOCATOR_TYPE -> GalenColors.LOCATOR_TYPE
            GalenTypes.SECTION_TITLE -> GalenColors.SECTION
            GalenTypes.FILE_PATH_REF -> GalenColors.FILE_PATH

            // Only colour a keyword that really is one; a typo stays uncoloured on purpose, which
            // is often noticed before the inspection warning is read.
            GalenTypes.SIDE -> ifKnown(element, GalenTypes.SIDES, GalenColors.SIDE)
            GalenTypes.MATCHER -> ifKnown(element, GalenTypes.MATCHERS, GalenColors.MATCHER)
            GalenTypes.TEXT_OPERATION ->
                ifKnown(element, GalenTypes.TEXT_OPERATIONS, GalenColors.TEXT_OPERATION)

            GalenTypes.ALIGN_DIRECTION ->
                ifKnown(element, GalenTypes.ALIGN_DIRECTIONS, GalenColors.ALIGN_KEYWORD)
            GalenTypes.ALIGN_EDGE ->
                ifKnown(element, GalenTypes.ALIGN_EDGES, GalenColors.ALIGN_KEYWORD)
            GalenTypes.CENTERED_DIRECTION ->
                ifKnown(element, GalenTypes.CENTERED_DIRECTIONS, GalenColors.ALIGN_KEYWORD)
            GalenTypes.CENTERED_RELATION ->
                ifKnown(element, GalenTypes.CENTERED_RELATIONS, GalenColors.ALIGN_KEYWORD)
            GalenTypes.COUNT_FILTER ->
                ifKnown(element, GalenTypes.COUNT_FILTERS, GalenColors.MODIFIER)

            GalenTypes.MODIFIER -> GalenColors.MODIFIER
            GalenTypes.UNIT, GalenTypes.RANGE_KEYWORD -> GalenColors.UNIT
            GalenTypes.CORNER -> null // its SIDE children are coloured individually

            GalenTypes.PROPERTY_NAME ->
                ifKnown(element, GalenTypes.RELATIVE_PROPERTIES, GalenColors.PROPERTY_NAME)
            GalenTypes.IMAGE_OPTION ->
                ifKnown(element, GalenTypes.IMAGE_OPTIONS, GalenColors.IMAGE_OPTION)
            GalenTypes.IMAGE_FILTER ->
                ifKnown(element, GalenTypes.IMAGE_FILTERS, GalenColors.IMAGE_OPTION)

            GalenTypes.COLOR_VALUE -> GalenColors.COLOR_VALUE
            GalenTypes.CSS_PROPERTY -> GalenColors.PROPERTY_NAME

            else -> null
        }

    /** True when this reference is the `name:` that opens an object statement. */
    private fun isStatementHeader(element: PsiElement): Boolean =
        element.parent?.node?.elementType == GalenTypes.OBJECT_REF_LIST

    private fun ifKnown(
        element: PsiElement,
        vocabulary: Set<String>,
        key: TextAttributesKey,
    ): TextAttributesKey? = if (element.text in vocabulary) key else null
}
