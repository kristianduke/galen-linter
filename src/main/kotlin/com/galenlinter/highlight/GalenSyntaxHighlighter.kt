package com.galenlinter.highlight

import com.galenlinter.lang.GalenTypes
import com.galenlinter.lexer.GalenLexer
import com.intellij.lexer.Lexer
import com.intellij.openapi.editor.colors.TextAttributesKey
import com.intellij.openapi.fileTypes.SyntaxHighlighter
import com.intellij.openapi.fileTypes.SyntaxHighlighterBase
import com.intellij.openapi.fileTypes.SyntaxHighlighterFactory
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Lexer-level (context-free) colouring.
 *
 * Context-sensitive colouring — is this word a spec name? is this run of tokens a locator? — is
 * applied on top by [com.galenlinter.highlight.GalenAnnotator], which works from the PSI tree.
 */
class GalenSyntaxHighlighter : SyntaxHighlighterBase() {

    override fun getHighlightingLexer(): Lexer = GalenLexer()

    override fun getTokenHighlights(tokenType: IElementType): Array<TextAttributesKey> =
        pack(ATTRIBUTES[tokenType])

    companion object {
        private val ATTRIBUTES: Map<IElementType, TextAttributesKey> = buildMap {
            put(GalenTypes.COMMENT, GalenColors.COMMENT)
            put(GalenTypes.AT_KEYWORD, GalenColors.STATEMENT)
            put(GalenTypes.STRING, GalenColors.STRING)
            put(GalenTypes.NUMBER, GalenColors.NUMBER)
            put(GalenTypes.EXPRESSION, GalenColors.EXPRESSION)
            put(GalenTypes.RULE_PARAM, GalenColors.RULE_PARAM)
            put(GalenTypes.CORRECTION, GalenColors.CORRECTION)
            put(GalenTypes.PERCENT, GalenColors.WARNING_PREFIX)
            put(GalenTypes.PIPE, GalenColors.STATEMENT)
            put(GalenTypes.EQ, GalenColors.SECTION)
            // `&` only ever introduces a group reference.
            put(GalenTypes.AMP, GalenColors.GROUP_REF)

            put(GalenTypes.LT, GalenColors.OPERATOR)
            put(GalenTypes.GT, GalenColors.OPERATOR)
            put(GalenTypes.LE, GalenColors.OPERATOR)
            put(GalenTypes.GE, GalenColors.OPERATOR)
            put(GalenTypes.TILDE, GalenColors.OPERATOR)
            put(GalenTypes.PLUS, GalenColors.OPERATOR)
            put(GalenTypes.MINUS, GalenColors.OPERATOR)
            put(GalenTypes.SLASH, GalenColors.OPERATOR)

            put(GalenTypes.COLON, GalenColors.PUNCTUATION)
            put(GalenTypes.COMMA, GalenColors.PUNCTUATION)
            put(GalenTypes.SEMICOLON, GalenColors.PUNCTUATION)
            put(GalenTypes.LBRACKET, GalenColors.BRACKETS)
            put(GalenTypes.RBRACKET, GalenColors.BRACKETS)
            put(GalenTypes.LPAREN, GalenColors.BRACKETS)
            put(GalenTypes.RPAREN, GalenColors.BRACKETS)
            put(GalenTypes.LBRACE, GalenColors.BRACKETS)
            put(GalenTypes.RBRACE, GalenColors.BRACKETS)

            put(TokenType.BAD_CHARACTER, com.intellij.openapi.editor.HighlighterColors.BAD_CHARACTER)
        }
    }
}

class GalenSyntaxHighlighterFactory : SyntaxHighlighterFactory() {
    override fun getSyntaxHighlighter(project: Project?, virtualFile: VirtualFile?): SyntaxHighlighter =
        GalenSyntaxHighlighter()
}
