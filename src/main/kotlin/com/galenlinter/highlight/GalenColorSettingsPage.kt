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
     * Most Galen keywords are bare words that only [GalenAnnotator] can classify, so the preview
     * has to mark them up explicitly — the lexer-based highlighter used for this panel cannot
     * reach them.
     */
    override fun getAdditionalHighlightingTagToDescriptorMap(): MutableMap<String, TextAttributesKey> =
        mutableMapOf(
            "spec" to GalenColors.SPEC_NAME,
            "obj" to GalenColors.OBJECT_REF,
            "header" to GalenColors.OBJECT_HEADER,
            "special" to GalenColors.SPECIAL_OBJECT,
            "loc" to GalenColors.LOCATOR,
            "loctype" to GalenColors.LOCATOR_TYPE,
            "title" to GalenColors.SECTION,
            "side" to GalenColors.SIDE,
            "match" to GalenColors.MATCHER,
            "textop" to GalenColors.TEXT_OPERATION,
            "align" to GalenColors.ALIGN_KEYWORD,
            "mod" to GalenColors.MODIFIER,
            "unit" to GalenColors.UNIT,
            "prop" to GalenColors.PROPERTY_NAME,
            "imgopt" to GalenColors.IMAGE_OPTION,
            "path" to GalenColors.FILE_PATH,
            "color" to GalenColors.COLOR_VALUE,
            "group" to GalenColors.GROUP_REF,
        )

    override fun getDemoText(): String = """
        # Object definitions
        @objects
            header                    <loc>#header</loc>
            menu_item-*   <loctype>css</loctype>   <loc>#menu li a</loc>
            logo    @(0, 0, -50, 0)   <loctype>id</loctype>   <loc>logo-container</loc>

        @groups
            skeleton    header, menu_item-*

        @set
            gutter    10 to 20px

        @import <path>shared/header.gspec</path>

        = <title>Main section</title> =
            @on mobile, desktop
                <header>header</header>:
                    <spec>inside</spec> <special>screen</special> 0<unit>px</unit> <side>top</side> <side>left</side>
                    <spec>height</spec> 40 <unit>px</unit>
                    <spec>width</spec> 100 <unit>%</unit> <unit>of</unit> <special>screen</special>/<prop>width</prop>
                    <spec>aligned</spec> <align>horizontally</align> <align>all</align> <obj>logo</obj> 1<unit>px</unit>
                    <spec>text</spec> <textop>lowercase</textop> <match>is</match> "welcome"
                    % <spec>width</spec> 100<unit>px</unit>
                    "should be squared" <spec>width</spec> 100<unit>%</unit> <unit>of</unit> <obj>header</obj>/<prop>height</prop>

                <header>&skeleton</header>:
                    <spec>inside</spec> <special>viewport</special> 0<unit>px</unit> <side>left</side> <side>right</side>

            <header>logo</header>:
                <spec>image</spec> <imgopt>file</imgopt> <path>imgs/logo.png</path>, <imgopt>error</imgopt> 4<unit>%</unit>, <imgopt>map-filter</imgopt> <imgopt>denoise</imgopt> 5
                <spec>color-scheme</spec> ~80<unit>%</unit> <color>white</color>, ~20<unit>%</unit> <color>#000-#555</color>

            <header>global</header>:
                <spec>count</spec> <mod>any</mod> <obj>menu_item-*</obj> <match>is</match> 4

            @forEach [menu_item-*] as item, next as nextItem
                ${'$'}{item}:
                    <spec>left-of</spec> ${'$'}{nextItem} 10<unit>px</unit>

        @rule %{name} should be squared
            ${'$'}{name}:
                <spec>width</spec> 100<unit>%</unit> <unit>of</unit> ${'$'}{name}/<prop>height</prop>
    """.trimIndent()

    private companion object {
        val DESCRIPTORS = arrayOf(
            AttributesDescriptor("Comment", GalenColors.COMMENT),
            AttributesDescriptor("Statement keyword", GalenColors.STATEMENT),
            AttributesDescriptor("Section title", GalenColors.SECTION),
            AttributesDescriptor("Spec name", GalenColors.SPEC_NAME),
            AttributesDescriptor("Unrecognised spec name", GalenColors.UNKNOWN_SPEC),
            AttributesDescriptor("Object statement header", GalenColors.OBJECT_HEADER),
            AttributesDescriptor("Object reference", GalenColors.OBJECT_REF),
            AttributesDescriptor("Special object", GalenColors.SPECIAL_OBJECT),
            AttributesDescriptor("Object group reference", GalenColors.GROUP_REF),
            AttributesDescriptor("Locator", GalenColors.LOCATOR),
            AttributesDescriptor("Locator type", GalenColors.LOCATOR_TYPE),
            AttributesDescriptor("File path", GalenColors.FILE_PATH),
            AttributesDescriptor("Side keyword", GalenColors.SIDE),
            AttributesDescriptor("Text matcher", GalenColors.MATCHER),
            AttributesDescriptor("Text operation", GalenColors.TEXT_OPERATION),
            AttributesDescriptor("Alignment keyword", GalenColors.ALIGN_KEYWORD),
            AttributesDescriptor("Modifier", GalenColors.MODIFIER),
            AttributesDescriptor("Unit and range keyword", GalenColors.UNIT),
            AttributesDescriptor("Relative property", GalenColors.PROPERTY_NAME),
            AttributesDescriptor("Image option", GalenColors.IMAGE_OPTION),
            AttributesDescriptor("Colour value", GalenColors.COLOR_VALUE),
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
