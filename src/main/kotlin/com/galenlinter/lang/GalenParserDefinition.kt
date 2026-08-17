package com.galenlinter.lang

import com.galenlinter.lexer.GalenLexer
import com.galenlinter.parser.GalenParser
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenFilePathRef
import com.galenlinter.psi.GalenGroupRef
import com.galenlinter.psi.GalenObjectDefinition
import com.galenlinter.psi.GalenObjectName
import com.galenlinter.psi.GalenObjectNameRef
import com.galenlinter.psi.GalenPsiElement
import com.galenlinter.psi.GalenRawJsLine
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

    /**
     * Most nodes only need a generic wrapper; these few carry behaviour — a name that can be
     * renamed, or a reference that can be navigated.
     */
    override fun createElement(node: ASTNode): PsiElement = when (node.elementType) {
        GalenTypes.OBJECT_DEF -> GalenObjectDefinition(node)
        GalenTypes.OBJECT_NAME -> GalenObjectName(node)
        GalenTypes.OBJECT_NAME_REF -> GalenObjectNameRef(node)
        GalenTypes.GROUP_REF -> GalenGroupRef(node)
        GalenTypes.FILE_PATH_REF -> GalenFilePathRef(node)
        // A raw JavaScript line can host a language injection when a JS plugin is present.
        GalenTypes.RAW_JS_LINE -> GalenRawJsLine(node)
        else -> GalenPsiElement(node)
    }

    override fun createFile(viewProvider: FileViewProvider): PsiFile = GalenFile(viewProvider)
}
