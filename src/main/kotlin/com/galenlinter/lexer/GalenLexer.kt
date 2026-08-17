package com.galenlinter.lexer

import com.galenlinter.lang.GalenTypes
import com.intellij.lexer.LexerBase
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Hand-written lexer for Galen page specs.
 *
 * ### Why hand-written rather than JFlex
 * Two constructs are not regular languages and would require imperative action code inside JFlex
 * anyway:
 *  - `${ ... }` needs **balanced** brace matching (`${ {a:1}.a }` is legal, and braces may appear
 *    inside JavaScript string literals);
 *  - `@( ... )` corrections and `%{ ... }` rule params are similar bracket-matched spans.
 *
 * Writing it directly also removes a build-time code-generation dependency.
 *
 * ### Restartability
 * The entire lexer state is one of three `Int` values, so IntelliJ can restart lexing at any line
 * boundary. This is only possible because the lexer is **line-local**: it never tracks which block
 * it is inside. Deciding that a run of tokens is a locator, a spec name or an object reference is
 * the parser's job.
 */
class GalenLexer : LexerBase() {

    companion object {
        /** Start of a line; the next token may be [GalenTypes.LINE_INDENT]. */
        const val STATE_LINE_START = 0

        /** After indentation, before any content token. Only here can `#` open a comment. */
        const val STATE_FIRST_TOKEN = 1

        /** Somewhere in the middle of a line. */
        const val STATE_BODY = 2

        private const val PUNCTUATION = ":,|&=/%$@;()[]{}<>~+\""
    }

    private var buffer: CharSequence = ""
    private var bufferEnd = 0
    private var state = STATE_LINE_START
    private var tokenStart = 0
    private var tokenEnd = 0
    private var currentToken: IElementType? = null

    override fun start(buffer: CharSequence, startOffset: Int, endOffset: Int, initialState: Int) {
        this.buffer = buffer
        this.bufferEnd = endOffset
        this.state = initialState
        this.tokenStart = startOffset
        this.tokenEnd = startOffset
        advance()
    }

    override fun getState(): Int = state

    override fun getTokenType(): IElementType? = currentToken

    override fun getTokenStart(): Int = tokenStart

    override fun getTokenEnd(): Int = tokenEnd

    override fun getBufferSequence(): CharSequence = buffer

    override fun getBufferEnd(): Int = bufferEnd

    override fun advance() {
        tokenStart = tokenEnd
        locateToken()
    }

    // -----------------------------------------------------------------------

    private fun charAt(i: Int): Char = if (i in 0 until bufferEnd) buffer[i] else ' '

    private fun isLineBreak(c: Char) = c == '\n' || c == '\r'

    private fun isHorizontalSpace(c: Char) = c == ' ' || c == '\t'

    /**
     * Word continuation characters. `-`, `.` and `#` count as word parts so that `left-of`,
     * `menu_item-1`, `header.icon`, `#f845b7` and `#000-#555-#955` each lex as a single word.
     */
    private fun isWordPart(c: Char): Boolean =
        !isHorizontalSpace(c) && !isLineBreak(c) && PUNCTUATION.indexOf(c) < 0

    /**
     * `-` continues a word but cannot start one, so `-50 px` still lexes as MINUS + NUMBER
     * while `left-of` stays a single word.
     */
    private fun isWordStart(c: Char): Boolean = c != '-' && isWordPart(c)

    private fun locateToken() {
        if (tokenStart >= bufferEnd) {
            currentToken = null
            tokenEnd = tokenStart
            return
        }

        val c = buffer[tokenStart]

        // Line breaks always reset to line-start state.
        if (isLineBreak(c)) {
            var i = tokenStart + 1
            if (c == '\r' && i < bufferEnd && buffer[i] == '\n') i++
            tokenEnd = i
            currentToken = GalenTypes.EOL
            state = STATE_LINE_START
            return
        }

        if (state == STATE_LINE_START) {
            if (isHorizontalSpace(c)) {
                var i = tokenStart
                while (i < bufferEnd && isHorizontalSpace(buffer[i])) i++
                tokenEnd = i
                currentToken = GalenTypes.LINE_INDENT
                state = STATE_FIRST_TOKEN
                return
            }
            // A line starting at column 0 goes straight to first-token state.
            state = STATE_FIRST_TOKEN
        }

        // Whitespace inside a line is ordinary, skippable whitespace.
        if (isHorizontalSpace(c)) {
            var i = tokenStart
            while (i < bufferEnd && isHorizontalSpace(buffer[i])) i++
            tokenEnd = i
            currentToken = TokenType.WHITE_SPACE
            return
        }

        // A comment only exists when `#` is the first non-whitespace character of the line.
        // Galen's IndentationStructureParser tests the *trimmed* line, so leading indentation
        // before `#` is legal. Anywhere else `#` is an ordinary character (CSS ids, hex colours).
        if (c == '#' && state == STATE_FIRST_TOKEN) {
            currentToken = GalenTypes.COMMENT
            tokenEnd = scanToEndOfLine(tokenStart)
            state = STATE_BODY
            return
        }

        state = STATE_BODY

        when {
            c == '$' && charAt(tokenStart + 1) == '{' -> {
                tokenEnd = scanBracketed(tokenStart + 1, '{', '}')
                currentToken = GalenTypes.EXPRESSION
            }

            c == '%' && charAt(tokenStart + 1) == '{' -> {
                tokenEnd = scanBracketed(tokenStart + 1, '{', '}')
                currentToken = GalenTypes.RULE_PARAM
            }

            c == '@' && charAt(tokenStart + 1) == '(' -> {
                tokenEnd = scanBracketed(tokenStart + 1, '(', ')')
                currentToken = GalenTypes.CORRECTION
            }

            c == '@' -> {
                var i = tokenStart + 1
                while (i < bufferEnd && isWordPart(buffer[i])) i++
                tokenEnd = i
                currentToken = GalenTypes.AT_KEYWORD
            }

            c == '"' -> {
                tokenEnd = skipQuoted(tokenStart, '"')
                currentToken = GalenTypes.STRING
            }

            c.isDigit() -> {
                var i = tokenStart
                while (i < bufferEnd && buffer[i].isDigit()) i++
                if (i < bufferEnd && buffer[i] == '.' && charAt(i + 1).isDigit()) {
                    i++
                    while (i < bufferEnd && buffer[i].isDigit()) i++
                }
                tokenEnd = i
                currentToken = GalenTypes.NUMBER
            }

            c == '<' && charAt(tokenStart + 1) == '=' -> single(2, GalenTypes.LE)
            c == '>' && charAt(tokenStart + 1) == '=' -> single(2, GalenTypes.GE)
            c == ':' -> single(1, GalenTypes.COLON)
            c == ',' -> single(1, GalenTypes.COMMA)
            c == '|' -> single(1, GalenTypes.PIPE)
            c == '&' -> single(1, GalenTypes.AMP)
            c == '=' -> single(1, GalenTypes.EQ)
            c == '/' -> single(1, GalenTypes.SLASH)
            c == '%' -> single(1, GalenTypes.PERCENT)
            c == '$' -> single(1, GalenTypes.DOLLAR)
            c == '[' -> single(1, GalenTypes.LBRACKET)
            c == ']' -> single(1, GalenTypes.RBRACKET)
            c == '(' -> single(1, GalenTypes.LPAREN)
            c == ')' -> single(1, GalenTypes.RPAREN)
            c == '{' -> single(1, GalenTypes.LBRACE)
            c == '}' -> single(1, GalenTypes.RBRACE)
            c == '<' -> single(1, GalenTypes.LT)
            c == '>' -> single(1, GalenTypes.GT)
            c == '~' -> single(1, GalenTypes.TILDE)
            c == '+' -> single(1, GalenTypes.PLUS)
            c == '-' -> single(1, GalenTypes.MINUS)
            c == ';' -> single(1, GalenTypes.SEMICOLON)

            isWordStart(c) -> {
                var i = tokenStart
                while (i < bufferEnd && isWordPart(buffer[i])) i++
                tokenEnd = i
                currentToken = GalenTypes.WORD
            }

            else -> single(1, TokenType.BAD_CHARACTER)
        }
    }

    private fun single(length: Int, type: IElementType) {
        tokenEnd = minOf(tokenStart + length, bufferEnd)
        currentToken = type
    }

    private fun scanToEndOfLine(from: Int): Int {
        var i = from
        while (i < bufferEnd && !isLineBreak(buffer[i])) i++
        return i
    }

    /**
     * Scans a bracketed span starting at [openIndex] (the index of the opening bracket), honouring
     * nesting and skipping over JavaScript string literals so that `${ "}" }` and `${ {a:1}.a }`
     * are scanned correctly.
     *
     * An unterminated span is consumed to end of line; the parser reports it.
     */
    private fun scanBracketed(openIndex: Int, open: Char, close: Char): Int {
        var i = openIndex + 1
        var depth = 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (isLineBreak(ch)) return i
            when {
                ch == '\\' -> i++ // skip the escaped character
                ch == '\'' || ch == '"' || ch == '`' -> i = skipQuoted(i, ch) - 1
                ch == open -> depth++
                ch == close -> {
                    depth--
                    if (depth == 0) return i + 1
                }
            }
            i++
        }
        return i
    }

    /** Returns the index just past the closing quote, or end of line if unterminated. */
    private fun skipQuoted(from: Int, quote: Char): Int {
        var i = from + 1
        while (i < bufferEnd) {
            val ch = buffer[i]
            if (isLineBreak(ch)) return i
            if (ch == '\\') {
                i += 2
                continue
            }
            if (ch == quote) return i + 1
            i++
        }
        return i
    }
}
