package com.galenlinter.highlight

import com.galenlinter.lang.GalenIcons
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.options.colors.AttributesDescriptor
import com.intellij.openapi.options.colors.ColorDescriptor
import com.intellij.openapi.options.colors.ColorSettingsPage
import javax.swing.Icon

class GalenColorSettingsPage : ColorSettingsPage {

    override fun getIcon(): Icon = GalenIcons.FILE

    override fun getHighlighter(): SyntaxHighlighter = GalenSyntaxHighlighter()

    override fun getDisplayName(): String = "Galen Spec"

    override fun getAttributeDescriptors(): Array<AttributesDescriptor> = DESCRIPTORS

    override fun getColorDescriptors(): Array<ColorDescriptor> = ColorDescriptor.EMPTY_ARRAY

    /**
     * Spec names, locators and object references are coloured by [GalenAnnotator] from the PSI
     * tree, not by the lexer, so the preview marks them up explicitly.
     */
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> =
        mutableMapOf(
            "spec" to GalenColors.SPEC_NAME,
            "obj" to GalenColors.OBJECT_REF,
            "loc" to GalenColors.LOCATOR,
            "title" to GalenColors.SECTION,
        )

    override fun getDemoText(): String = """
        # Object definitions
        @objects
            header              <loc>#header</loc>
            menu                <loc>ul.menu</loc>
            menu_item-*   css   <loc>#menu li a</loc>
            logo    @(0, 0, -50, 0)   id   <loc>logo-container</loc>

        @groups
            skeleton    header, menu

        @set
            gutter    10 to 20px

        = <title>Main section</title> =
            @on mobile, desktop
                <obj>header</obj>:
                    <spec>inside</spec> screen 0px top left
                    <spec>height</spec> 40 px
                    % <spec>width</spec> 100 % of screen/width
                    "should be squared" <spec>width</spec> 100% of header/height

            @forEach [menu_item-*] as item, next as nextItem
                ${'$'}{item}:
                    <spec>left-of</spec> ${'$'}{nextItem} 10px

            @if ${'$'}{isVisible("banner")}
                <obj>banner</obj>:
                    <spec>image</spec> file imgs/banner.png, error 4%, tolerance 80

        @rule %{name} should be squared
            ${'$'}{name}:
                <spec>width</spec> 100% of ${'$'}{name}/height
    """.trimIndent()

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Comment", GalenColors.COMMENT),
            AttributesDescriptor("Statement keyword", GalenColors.STATEMENT),
            AttributesDescriptor("Section title", GalenColors.SECTION),
            AttributesDescriptor("Spec name", GalenColors.SPEC_NAME),
            AttributesDescriptor("Unrecognised spec name", GalenColors.UNKNOWN_SPEC),
            AttributesDescriptor("Object reference", GalenColors.OBJECT_REF),
            AttributesDescriptor("Locator", GalenColors.LOCATOR),
            AttributesDescriptor("Expression", GalenColors.EXPRESSION),
            AttributesDescriptor("Rule parameter", GalenColors.RULE_PARAM),
            AttributesDescriptor("Object correction", GalenColors.CORRECTION),
            AttributesDescriptor("Warning-level prefix", GalenColors.WARNING_PREFIX),
            AttributesDescriptor("String", GalenColors.STRING),
            AttributesDescriptor("Number", GalenColors.NUMBER),
            AttributesDescriptor("Operator", GalenColors.OPERATOR),
            AttributesDescriptor("Punctuation", GalenColors.PUNCTUATION),
            AttributesDescriptor("Brackets", GalenColors.BRACKETS),
        )
    }
}
