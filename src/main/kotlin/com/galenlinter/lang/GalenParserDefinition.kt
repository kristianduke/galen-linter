package com.galenlinter.lang

import com.galenlinter.lexer.GalenLexer
import com.galenlinter.parser.GalenParser
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenPsiElement
import com.intellij.lang.ASTNode
import com.intellij.lang.ParserDefinition
import com.intellij.lang.PsiParser
import com.intellij.lexer.Lexer
import com.intellij.openapi.project.Project
import com.intellij.psi.FileViewProvider
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IFileElementType
import com.intellij.psi.tree.TokenSet

class GalenParserDefinition : ParserDefinition {

    companion object {
        @JvmField
        val FILE = IFileElementType(GalenLanguage)

        /**
         * Only inner whitespace is skippable. [GalenTypes.LINE_INDENT] is deliberately absent:
         * the parser must see it to compute block structure.
         */
        @JvmField
        val WHITESPACES: TokenSet = TokenSet.create(TokenType.WHITE_SPACE)
    }

    override fun createLexer(project: Project?): Lexer = GalenLexer()

    override fun createParser(project: Project?): PsiParser = GalenParser()

    override fun getFileNodeType(): IFileElementType = FILE

    override fun getWhitespaceTokens(): TokenSet = WHITESPACES

    /**
     * Comments are auto-skipped by `PsiBuilder`, which mirrors Galen: its
     * IndentationStructureParser drops comment lines before any structural decision is made.
     */
    override fun getCommentTokens(): TokenSet = GalenTypes.COMMENTS

    override fun getStringLiteralElements(): TokenSet = GalenTypes.STRINGS

    override fun createElement(node: ASTNode): PsiElement = GalenPsiElement(node)

    override fun createFile(viewProvider: FileViewProvider): PsiFile = GalenFile(viewProvider)
}
