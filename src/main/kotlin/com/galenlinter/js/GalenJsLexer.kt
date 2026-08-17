package com.galenlinter.js

import com.intellij.lang.Language
import com.intellij.openapi.util.TextRange

/**
 * A deliberately small JavaScript tokenizer, used only when no JavaScript plugin is installed.
 *
 * IntelliJ IDEA Community bundles no JavaScript support, so language injection is unavailable there
 * and Galen's embedded JavaScript would otherwise be one flat, unchecked blob. This is not a
 * JavaScript implementation and does not try to be: it recognises the lexical surface — comments,
 * strings, numbers, keywords, identifiers — which is enough to colour the code and to catch the
 * small number of mistakes that are worth catching without a parser.
 *
 * When a real JavaScript plugin *is* present, [isFallbackNeeded] reports false and none of this
 * runs, leaving injection to do the job properly.
 */
object GalenJsLexer {

    enum class Kind { COMMENT, STRING, NUMBER, KEYWORD, GALEN_API, IDENTIFIER, PUNCTUATION }

    data class Token(val range: TextRange, val kind: Kind, val text: String)

    sealed interface Problem {
        val range: TextRange
        val message: String

        data class UnterminatedString(override val range: TextRange) : Problem {
            override val message = "Unterminated string literal."
        }

        data class UnbalancedBracket(override val range: TextRange, private val what: String) : Problem {
            override val message = what
        }

        data class NameCalledAsFunction(override val range: TextRange) : Problem {
            override val message =
                "'name' is a property, not a method. Galen's page element exposes '.name' " +
                    "directly — every other member (.width(), .isVisible()) is a call."
        }

        data class UnknownApi(
            override val range: TextRange,
            val used: String,
            val suggestion: String,
        ) : Problem {
            override val message = "Unknown Galen function '$used'. Did you mean '$suggestion'?"
        }
    }

    /** True when no JavaScript plugin is available and the fallback should be used. */
    fun isFallbackNeeded(): Boolean =
        Language.findLanguageByID("JavaScript") == null &&
            Language.findLanguageByID("ECMAScript 6") == null

    /**
     * Functions and objects Galen exposes to `${...}` blocks.
     * From the Galen Specs JS API; see docs/galen-spec-reference.md §16.
     */
    val GALEN_API: Set<String> = setOf(
        "count", "find", "findAll", "isVisible", "isPresent", "viewport", "screen",
    )

    /** Page-element members. `name` is a property; the rest are calls. */
    val ELEMENT_MEMBERS: Set<String> = setOf(
        "left", "right", "top", "bottom", "width", "height", "isVisible", "isPresent", "name",
    )

    private val KEYWORDS = setOf(
        "var", "let", "const", "function", "return", "if", "else", "for", "while", "do",
        "break", "continue", "new", "delete", "typeof", "instanceof", "in", "of", "this",
        "true", "false", "null", "undefined", "throw", "try", "catch", "finally", "switch",
        "case", "default", "void", "yield", "async", "await", "class", "extends", "super",
    )

    fun tokenize(text: CharSequence): List<Token> {
        val tokens = mutableListOf<Token>()
        var i = 0

        while (i < text.length) {
            val c = text[i]
            when {
                c.isWhitespace() -> i++

                c == '/' && i + 1 < text.length && text[i + 1] == '/' -> {
                    val end = text.indexOfFrom('\n', i).let { if (it < 0) text.length else it }
                    tokens += token(text, i, end, Kind.COMMENT)
                    i = end
                }

                c == '/' && i + 1 < text.length && text[i + 1] == '*' -> {
                    var end = i + 2
                    while (end + 1 < text.length && !(text[end] == '*' && text[end + 1] == '/')) end++
                    end = minOf(end + 2, text.length)
                    tokens += token(text, i, end, Kind.COMMENT)
                    i = end
                }

                c == '"' || c == '\'' || c == '`' -> {
                    val end = scanString(text, i, c)
                    tokens += token(text, i, end, Kind.STRING)
                    i = end
                }

                c.isDigit() -> {
                    var end = i
                    while (end < text.length && (text[end].isDigit() || text[end] == '.')) end++
                    tokens += token(text, i, end, Kind.NUMBER)
                    i = end
                }

                c.isLetter() || c == '_' || c == '$' -> {
                    var end = i
                    while (end < text.length && (text[end].isLetterOrDigit() || text[end] == '_' || text[end] == '$')) end++
                    val word = text.subSequence(i, end).toString()
                    val kind = when {
                        word in KEYWORDS -> Kind.KEYWORD
                        word in GALEN_API -> Kind.GALEN_API
                        else -> Kind.IDENTIFIER
                    }
                    tokens += token(text, i, end, kind)
                    i = end
                }

                else -> {
                    tokens += token(text, i, i + 1, Kind.PUNCTUATION)
                    i++
                }
            }
        }

        return tokens
    }

    /**
     * The mistakes worth reporting without a parser.
     *
     * Deliberately narrow. Anything requiring scope or type analysis — an undefined variable, a
     * wrong argument count — is left alone, because a false positive on working JavaScript is far
     * worse than a missed one, and `@script` files can define anything.
     */
    fun problems(text: CharSequence, tokens: List<Token> = tokenize(text)): List<Problem> {
        val problems = mutableListOf<Problem>()

        // Unterminated strings: the scanner stops at end of input rather than a closing quote.
        for (token in tokens) {
            if (token.kind != Kind.STRING) continue
            val quote = token.text.first()
            val closed = token.text.length > 1 && token.text.last() == quote &&
                !token.text.endsWith("\\$quote")
            if (!closed) problems += Problem.UnterminatedString(token.range)
        }

        problems += bracketProblems(tokens)
        problems += nameCalledAsFunction(tokens)
        problems += unknownApiCalls(tokens)
        return problems
    }

    private fun bracketProblems(tokens: List<Token>): List<Problem> {
        val problems = mutableListOf<Problem>()
        val open = ArrayDeque<Token>()
        val pairs = mapOf(')' to '(', ']' to '[', '}' to '{')

        for (token in tokens) {
            if (token.kind != Kind.PUNCTUATION) continue
            when (token.text.firstOrNull()) {
                '(', '[', '{' -> open.addLast(token)
                ')', ']', '}' -> {
                    val expected = pairs[token.text.first()]
                    val last = open.removeLastOrNull()
                    if (last == null) {
                        problems += Problem.UnbalancedBracket(
                            token.range,
                            "Unmatched '${token.text}'.",
                        )
                    } else if (last.text.first() != expected) {
                        problems += Problem.UnbalancedBracket(
                            token.range,
                            "'${token.text}' does not match '${last.text}'.",
                        )
                    }
                }
            }
        }

        for (unclosed in open) {
            problems += Problem.UnbalancedBracket(unclosed.range, "'${unclosed.text}' is never closed.")
        }
        return problems
    }

    /**
     * `find("x").name()` — `.name` is the one page-element member that is a property rather than a
     * method, so calling it throws at run time. Easy to get wrong and invisible until then.
     */
    private fun nameCalledAsFunction(tokens: List<Token>): List<Problem> {
        val problems = mutableListOf<Problem>()
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.kind != Kind.IDENTIFIER || token.text != "name") continue
            val previous = tokens.getOrNull(index - 1) ?: continue
            if (previous.text != ".") continue
            val next = tokens.getOrNull(index + 1) ?: continue
            if (next.text == "(") problems += Problem.NameCalledAsFunction(token.range)
        }
        return problems
    }

    /**
     * A near-miss of a Galen API name, such as `isVisble`. Only reported when it is both called and
     * very close to a real one — a `@script` file may define any function it likes, so an unknown
     * name is not itself a mistake.
     */
    private fun unknownApiCalls(tokens: List<Token>): List<Problem> {
        val problems = mutableListOf<Problem>()
        for (index in tokens.indices) {
            val token = tokens[index]
            if (token.kind != Kind.IDENTIFIER) continue
            if (tokens.getOrNull(index + 1)?.text != "(") continue
            if (tokens.getOrNull(index - 1)?.text == ".") continue

            val suggestion = GALEN_API.firstOrNull { editDistance(token.text, it) == 1 } ?: continue
            problems += Problem.UnknownApi(token.range, token.text, suggestion)
        }
        return problems
    }

    // ---- helpers ----------------------------------------------------------

    private fun token(text: CharSequence, start: Int, end: Int, kind: Kind) =
        Token(TextRange(start, end), kind, text.subSequence(start, end).toString())

    private fun scanString(text: CharSequence, from: Int, quote: Char): Int {
        var i = from + 1
        while (i < text.length) {
            when {
                text[i] == '\\' -> i += 2
                text[i] == quote -> return i + 1
                else -> i++
            }
        }
        return text.length
    }

    private fun CharSequence.indexOfFrom(c: Char, from: Int): Int {
        for (i in from until length) if (this[i] == c) return i
        return -1
    }

    private fun editDistance(a: String, b: String): Int {
        if (a == b) return 0
        var previous = IntArray(b.length + 1) { it }
        var current = IntArray(b.length + 1)
        for (i in 1..a.length) {
            current[0] = i
            for (j in 1..b.length) {
                val substitution = previous[j - 1] + if (a[i - 1] == b[j - 1]) 0 else 1
                current[j] = minOf(current[j - 1] + 1, previous[j] + 1, substitution)
            }
            val swap = previous
            previous = current
            current = swap
        }
        return previous[b.length]
    }
}
