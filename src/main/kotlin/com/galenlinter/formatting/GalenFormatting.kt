package com.galenlinter.formatting

import com.galenlinter.lang.GalenLanguage
import com.galenlinter.psi.GalenFile
import com.intellij.application.options.IndentOptionsEditor
import com.intellij.application.options.SmartIndentOptionsEditor
import com.intellij.formatting.FormattingContext
import com.intellij.formatting.FormattingModel
import com.intellij.formatting.FormattingModelBuilder
import com.intellij.formatting.FormattingModelProvider
import com.intellij.formatting.Indent
import com.intellij.formatting.Spacing
import com.intellij.formatting.Wrap
import com.intellij.formatting.Block
import com.intellij.lang.ASTNode
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.codeStyle.CodeStyleSettings
import com.intellij.psi.codeStyle.LanguageCodeStyleSettingsProvider
import com.intellij.psi.impl.source.codeStyle.PostFormatProcessor
import com.intellij.psi.formatter.common.AbstractBlock

/**
 * Reformat Code for Galen.
 *
 * The real work is in [GalenReindenter], applied here as a post-format processor. A conventional
 * formatting model cannot do it: it rewrites whitespace between blocks, and Galen's indentation is
 * a token the parser depends on rather than whitespace.
 *
 * The model below still has to exist, because without one registered the platform does not offer
 * Reformat Code for the language at all.
 */
class GalenFormattingModelBuilder : FormattingModelBuilder {

    override fun createModel(formattingContext: FormattingContext): FormattingModel {
        val settings = formattingContext.codeStyleSettings
        return FormattingModelProvider.createFormattingModelForPsiFile(
            formattingContext.containingFile,
            GalenRootBlock(formattingContext.node, settings),
            settings,
        )
    }
}

/**
 * A single block covering the file.
 *
 * Deliberately inert: it must not try to move anything, because every whitespace decision that
 * matters is made by [GalenReindenter] from the parse tree.
 */
private class GalenRootBlock(node: ASTNode, private val settings: CodeStyleSettings) :
    AbstractBlock(node, Wrap.createWrap(com.intellij.formatting.WrapType.NONE, false), null) {

    override fun buildChildren(): List<Block> = emptyList()

    override fun getIndent(): Indent = Indent.getNoneIndent()

    override fun getSpacing(child1: Block?, child2: Block): Spacing? = null

    override fun isLeaf(): Boolean = true
}

/** Applies the computed indentation after the platform's own formatting pass. */
class GalenPostFormatProcessor : PostFormatProcessor {

    override fun processElement(source: PsiElement, settings: CodeStyleSettings): PsiElement {
        val file = source.containingFile
        if (file is GalenFile) reformat(file, settings)
        return source
    }

    override fun processText(
        source: PsiFile,
        rangeToReformat: TextRange,
        settings: CodeStyleSettings,
    ): TextRange {
        if (source !is GalenFile) return rangeToReformat
        val delta = reformat(source, settings)
        return TextRange(
            rangeToReformat.startOffset,
            (rangeToReformat.endOffset + delta).coerceAtLeast(rangeToReformat.startOffset),
        )
    }

    /** Returns the net change in document length, so the caller's range stays meaningful. */
    private fun reformat(file: GalenFile, settings: CodeStyleSettings): Int {
        val documentManager = PsiDocumentManager.getInstance(file.project)
        val document = documentManager.getDocument(file) ?: return 0

        val indentSize = settings.getIndentOptions(file.fileType).INDENT_SIZE
            .takeIf { it > 0 } ?: DEFAULT_INDENT

        val edits = GalenReindenter.computeEdits(file, indentSize)
        if (edits.isEmpty()) return 0

        var delta = 0
        for (edit in edits) {
            delta += edit.text.length - edit.range.length
            document.replaceString(edit.range.startOffset, edit.range.endOffset, edit.text)
        }
        documentManager.commitDocument(document)
        return delta
    }

    private companion object {
        const val DEFAULT_INDENT = 4
    }
}

/**
 * Code style settings, so the indent step is configurable.
 *
 * Four spaces by default: it matches every example in Galen's documentation, and it is the width
 * Galen itself assigns to a tab.
 */
class GalenCodeStyleSettingsProvider : LanguageCodeStyleSettingsProvider() {

    override fun getLanguage() = GalenLanguage

    override fun getIndentOptionsEditor(): IndentOptionsEditor = SmartIndentOptionsEditor()

    override fun customizeDefaults(
        commonSettings: com.intellij.psi.codeStyle.CommonCodeStyleSettings,
        indentOptions: com.intellij.psi.codeStyle.CommonCodeStyleSettings.IndentOptions,
    ) {
        indentOptions.INDENT_SIZE = 4
        indentOptions.CONTINUATION_INDENT_SIZE = 4
        indentOptions.TAB_SIZE = 4
        // Galen counts a tab as four columns, but mixing the two makes a file read differently in
        // any editor configured otherwise, so new indentation is always spaces.
        indentOptions.USE_TAB_CHARACTER = false
    }

    override fun getCodeSample(settingsType: SettingsType): String = """
        @objects
            header          #header
            menu_item-*     css   #menu li a

        = Main section =
            header:
                inside screen 0px top left
                height 40 px
    """.trimIndent()
}
