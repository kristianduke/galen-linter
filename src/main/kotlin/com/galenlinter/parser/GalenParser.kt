package com.galenlinter.parser

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.ASTNode
import com.intellij.lang.PsiBuilder
import com.intellij.lang.PsiParser
import com.intellij.psi.tree.IElementType

/**
 * Recursive-descent parser for Galen page specs.
 *
 * It mirrors Galen's own two-phase design (`com.galenframework.parser.IndentationStructureParser`
 * followed by `MacroProcessor`):
 *
 *  1. **Block structure** comes purely from indentation. Blank and comment lines are skipped
 *     before any structural decision; siblings must share an identical indent width; a tab counts
 *     as 4 columns.
 *  2. **Line content** is then parsed by dispatching on the line's first token, exactly as
 *     `MacroProcessor` dispatches on the first word.
 *
 * Error recovery is per line: a malformed line is reported and consumed up to its newline, so a
 * single bad line never blanks out highlighting for the rest of the file.
 */
class GalenParser : PsiParser {

    override fun parse(root: IElementType, builder: PsiBuilder): ASTNode {
        val marker = builder.mark()
        Impl(builder).parseBlock(parentIndent = -1, blockKind = BlockKind.FILE)
        // Never leave tokens unconsumed: PsiBuilder requires the whole stream to be covered.
        while (!builder.eof()) builder.advanceLexer()
        marker.done(root)
        return builder.treeBuilt
    }
}

/** Which construct's body we are currently inside; decides how each child line is read. */
enum class BlockKind {
    FILE,
    /** Body of `@objects` — every line is an object definition. */
    OBJECTS,
    /** Body of `@groups`. */
    GROUPS,
    /** Body of a block-form `@set`. */
    SET,
    /** Body of a bare `@script` — raw JavaScript. */
    SCRIPT,
    /** A section, `@on`, `@if`, loop or rule body: ordinary statements. */
    STATEMENTS,
    /** Body of an `objectName:` statement — spec lines and rule invocations. */
    OBJECT_SPECS,
}

private data class LineResult(val elementType: IElementType, val childKind: BlockKind)

private class Impl(private val b: PsiBuilder) {

    companion object {
        /** Matches `IndentationStructureParser.TAB_SIZE` in Galen. */
        const val TAB_SIZE = 4

        /** Safety bound for single-line lookahead scans. */
        const val MAX_LINE_LOOKAHEAD = 2000
    }

    // ---- block structure (phase 1) ----------------------------------------

    fun parseBlock(parentIndent: Int, blockKind: BlockKind) {
        var siblingIndent = -1
        var previous: IElementType? = null

        while (true) {
            skipBlankLines()
            if (b.eof()) return

            val indent = currentIndent()
            if (indent <= parentIndent) return

            if (siblingIndent < 0) {
                siblingIndent = indent
            } else if (indent != siblingIndent) {
                // Galen raises SyntaxException("Inconsistent indentation") here. We report it but
                // keep parsing the line as a sibling so the remainder of the file still resolves.
                b.error("GL104: Inconsistent indentation (expected $siblingIndent, got $indent)")
            }

            previous = parseLine(indent, blockKind, previous).elementType
        }
    }

    private fun skipBlankLines() {
        while (!b.eof()) {
            when (b.tokenType) {
                GalenTypes.EOL -> b.advanceLexer()
                GalenTypes.LINE_INDENT -> {
                    // lookAhead skips whitespace and comments, so a comment-only line looks
                    // exactly like a blank one here — which is precisely Galen's behaviour.
                    val next = b.lookAhead(1)
                    if (next == GalenTypes.EOL || next == null) {
                        b.advanceLexer()
                        if (b.tokenType == GalenTypes.EOL) b.advanceLexer()
                    } else {
                        return
                    }
                }
                else -> return
            }
        }
    }

    private fun currentIndent(): Int =
        if (b.tokenType == GalenTypes.LINE_INDENT) indentWidth(b.tokenText ?: "") else 0

    private fun indentWidth(text: CharSequence): Int {
        var width = 0
        for (c in text) width += if (c == '\t') TAB_SIZE else 1
        return width
    }

    // ---- line dispatch (phase 2) ------------------------------------------

    private fun parseLine(indent: Int, blockKind: BlockKind, previous: IElementType?): LineResult {
        val marker = b.mark()
        if (b.tokenType == GalenTypes.LINE_INDENT) b.advanceLexer()

        val result = when (blockKind) {
            BlockKind.OBJECTS -> parseObjectDefinition()
            BlockKind.GROUPS -> parseSimpleLine(GalenTypes.GROUP_DEF, BlockKind.GROUPS)
            BlockKind.SET -> parseSimpleLine(GalenTypes.SET_ENTRY, BlockKind.SET)
            BlockKind.SCRIPT -> parseSimpleLine(GalenTypes.RAW_JS_LINE, BlockKind.SCRIPT)
            else -> parseStatement(previous)
        }

        consumeRestOfLine()
        parseBlock(indent, result.childKind)
        marker.done(result.elementType)
        return result
    }

    private fun parseStatement(previous: IElementType?): LineResult = when (b.tokenType) {
        GalenTypes.AT_KEYWORD -> parseAtStatement(previous)
        GalenTypes.EQ -> parseSection()
        GalenTypes.PIPE -> parseRuleInvocation()
        GalenTypes.PERCENT, GalenTypes.STRING -> parseSpecLine()
        else -> if (lineHasColon()) parseObjectStatement() else parseSpecLine()
    }

    // ---- @ statements -----------------------------------------------------

    private fun parseAtStatement(previous: IElementType?): LineResult {
        val keyword = b.tokenText ?: ""

        if (keyword !in GalenTypes.STATEMENT_KEYWORDS) {
            val error = b.mark()
            b.advanceLexer()
            error.error("GL101: Unknown statement '$keyword'")
            return LineResult(GalenTypes.UNKNOWN_STATEMENT, BlockKind.STATEMENTS)
        }

        // @elseif / @else are only legal directly after @if or @elseif. Galen throws
        // SyntaxException("elseif statement without if block") for the dangling case.
        if (keyword == "@elseif" || keyword == "@else") {
            val valid = previous == GalenTypes.IF_STATEMENT || previous == GalenTypes.ELSEIF_STATEMENT
            if (!valid) {
                val error = b.mark()
                b.advanceLexer()
                error.error("GL103: '$keyword' without a matching '@if'")
                return LineResult(
                    if (keyword == "@else") GalenTypes.ELSE_STATEMENT else GalenTypes.ELSEIF_STATEMENT,
                    BlockKind.STATEMENTS,
                )
            }
        }

        b.advanceLexer()
        val hasArgumentsOnLine = b.tokenType != GalenTypes.EOL && !b.eof()

        // Mark the path argument of the file-loading statements so it can host a file reference
        // (ctrl+click) and a "file not found" inspection. `@lib` is included for consistency even
        // though it names a library bundled inside the Galen jar rather than a path on disk.
        if (hasArgumentsOnLine && (keyword == "@import" || keyword == "@script" || keyword == "@lib")) {
            SpecArgumentParser(b).parseStatementPath()
        }

        return when (keyword) {
            "@objects" -> LineResult(GalenTypes.OBJECTS_BLOCK, BlockKind.OBJECTS)
            "@groups" -> LineResult(GalenTypes.GROUPS_BLOCK, BlockKind.GROUPS)

            // Both @set and @script have an inline form and a block form. Only the block form
            // (nothing else on the line) introduces a specially-parsed body.
            "@set" -> LineResult(
                GalenTypes.SET_BLOCK,
                if (hasArgumentsOnLine) BlockKind.STATEMENTS else BlockKind.SET,
            )
            "@script" -> LineResult(
                GalenTypes.SCRIPT_BLOCK,
                if (hasArgumentsOnLine) BlockKind.STATEMENTS else BlockKind.SCRIPT,
            )

            "@import" -> LineResult(GalenTypes.IMPORT_STATEMENT, BlockKind.STATEMENTS)
            "@lib" -> LineResult(GalenTypes.LIB_STATEMENT, BlockKind.STATEMENTS)
            "@rule" -> LineResult(GalenTypes.RULE_DEFINITION, BlockKind.STATEMENTS)
            "@ruleBody" -> LineResult(GalenTypes.RULE_BODY_STATEMENT, BlockKind.STATEMENTS)
            "@on" -> LineResult(GalenTypes.ON_STATEMENT, BlockKind.STATEMENTS)
            "@if" -> LineResult(GalenTypes.IF_STATEMENT, BlockKind.STATEMENTS)
            "@elseif" -> LineResult(GalenTypes.ELSEIF_STATEMENT, BlockKind.STATEMENTS)
            "@else" -> LineResult(GalenTypes.ELSE_STATEMENT, BlockKind.STATEMENTS)
            "@for" -> LineResult(GalenTypes.FOR_STATEMENT, BlockKind.STATEMENTS)
            "@forEach" -> LineResult(GalenTypes.FOREACH_STATEMENT, BlockKind.STATEMENTS)
            "@die" -> LineResult(GalenTypes.DIE_STATEMENT, BlockKind.STATEMENTS)
            else -> LineResult(GalenTypes.UNKNOWN_STATEMENT, BlockKind.STATEMENTS)
        }
    }

    // ---- sections ---------------------------------------------------------

    private fun parseSection(): LineResult {
        b.advanceLexer() // opening '='

        val title = b.mark()
        var sawClosingEq = false
        while (!b.eof() && b.tokenType != GalenTypes.EOL) {
            if (b.tokenType == GalenTypes.EQ) {
                sawClosingEq = true
                break
            }
            b.advanceLexer()
        }
        title.done(GalenTypes.SECTION_TITLE)

        if (sawClosingEq) {
            b.advanceLexer()
        } else {
            b.error("GL107: Section header is not closed with '='")
        }

        return LineResult(GalenTypes.SECTION, BlockKind.STATEMENTS)
    }

    // ---- object statements and specs --------------------------------------

    private fun parseObjectStatement(): LineResult {
        val refs = b.mark()
        while (!b.eof() && b.tokenType != GalenTypes.EOL && b.tokenType != GalenTypes.COLON) {
            if (b.tokenType == GalenTypes.COMMA) {
                b.advanceLexer()
                continue
            }
            val ref = b.mark()
            // A reference is `&group`, `${expr}` or a (possibly wildcarded) object name.
            if (b.tokenType == GalenTypes.AMP) b.advanceLexer()
            while (!b.eof() &&
                b.tokenType != GalenTypes.EOL &&
                b.tokenType != GalenTypes.COLON &&
                b.tokenType != GalenTypes.COMMA
            ) {
                b.advanceLexer()
            }
            ref.done(GalenTypes.OBJECT_REF)
        }
        refs.done(GalenTypes.OBJECT_REF_LIST)

        if (b.tokenType == GalenTypes.COLON) {
            b.advanceLexer()
        } else {
            b.error("GL105: Object statement must end with ':'")
        }

        return LineResult(GalenTypes.OBJECT_STATEMENT, BlockKind.OBJECT_SPECS)
    }

    /**
     * `[%] ["note"] specName args...`
     *
     * M1 records the shape and the spec name only. The 21 per-spec argument grammars arrive with
     * the GL3xx rules in M3; until then arguments are kept as a single [GalenTypes.SPEC_ARGS] node.
     */
    private fun parseSpecLine(): LineResult {
        if (b.tokenType == GalenTypes.PERCENT) b.advanceLexer()
        if (b.tokenType == GalenTypes.STRING) b.advanceLexer()

        var specName: String? = null
        if (b.tokenType == GalenTypes.WORD) {
            specName = b.tokenText
            val name = b.mark()
            b.advanceLexer()
            name.done(GalenTypes.SPEC_NAME)
        }

        if (!b.eof() && b.tokenType != GalenTypes.EOL) {
            val args = b.mark()
            if (specName != null && specName in GalenTypes.SPEC_NAMES) {
                SpecArgumentParser(b).parse(specName)
            }
            // Whatever the per-spec parser did not claim — and everything, when the spec name is
            // unrecognised — stays inside SPEC_ARGS as plain tokens. An unknown spec name is not a
            // parse error: the inspection reports it, so a quick fix can offer a correction.
            while (!b.eof() && b.tokenType != GalenTypes.EOL) b.advanceLexer()
            args.done(GalenTypes.SPEC_ARGS)
        }

        return LineResult(GalenTypes.SPEC_LINE, BlockKind.OBJECT_SPECS)
    }

    private fun parseRuleInvocation(): LineResult {
        b.advanceLexer() // '|'
        while (!b.eof() && b.tokenType != GalenTypes.EOL) b.advanceLexer()
        // A rule invocation may carry a body block (@ruleBody / doRuleBody).
        return LineResult(GalenTypes.RULE_INVOCATION, BlockKind.STATEMENTS)
    }

    // ---- @objects bodies --------------------------------------------------

    private fun parseObjectDefinition(): LineResult {
        if (b.tokenType != GalenTypes.WORD) {
            b.error("GL109: Object definition must start with an object name")
            return LineResult(GalenTypes.OBJECT_DEF, BlockKind.OBJECTS)
        }

        // The declared name, marked so it can act as a rename/find-usages target.
        val name = b.mark()
        b.advanceLexer()
        name.done(GalenTypes.OBJECT_NAME)

        // Optional `@(l, t, w, h)` correction and `@grouped(...)` annotation, in either order.
        while (b.tokenType == GalenTypes.CORRECTION || b.tokenType == GalenTypes.AT_KEYWORD) {
            b.advanceLexer()
            if (b.tokenType == GalenTypes.LPAREN) {
                while (!b.eof() && b.tokenType != GalenTypes.EOL && b.tokenType != GalenTypes.RPAREN) {
                    b.advanceLexer()
                }
                if (b.tokenType == GalenTypes.RPAREN) b.advanceLexer()
            }
        }

        // An explicit locator type is only a locator type if something follows it; otherwise the
        // word is the locator itself (Galen defaults an omitted type to css).
        if (b.tokenType == GalenTypes.WORD &&
            GalenTypes.LOCATOR_TYPES.contains(b.tokenText ?: "") &&
            b.lookAhead(1) != GalenTypes.EOL &&
            b.lookAhead(1) != null
        ) {
            val locatorType = b.mark()
            b.advanceLexer()
            locatorType.done(GalenTypes.LOCATOR_TYPE)
        }

        if (!b.eof() && b.tokenType != GalenTypes.EOL) {
            // The locator runs to end of line and may contain spaces, '#', '[', '@' and '/'.
            val locator = b.mark()
            while (!b.eof() && b.tokenType != GalenTypes.EOL) b.advanceLexer()
            locator.done(GalenTypes.LOCATOR)
        } else {
            b.error("GL109: Object definition has no locator")
        }

        return LineResult(GalenTypes.OBJECT_DEF, BlockKind.OBJECTS)
    }

    // ---- helpers ----------------------------------------------------------

    private fun parseSimpleLine(type: IElementType, childKind: BlockKind): LineResult {
        while (!b.eof() && b.tokenType != GalenTypes.EOL) b.advanceLexer()
        return LineResult(type, childKind)
    }

    private fun consumeRestOfLine() {
        while (!b.eof() && b.tokenType != GalenTypes.EOL) b.advanceLexer()
        if (b.tokenType == GalenTypes.EOL) b.advanceLexer()
    }

    /**
     * True when the current line contains a top-level `:`.
     *
     * Safe against false positives: a colon inside a quoted expectation or a `${...}` block is
     * part of a single STRING / EXPRESSION token and is therefore invisible here.
     */
    private fun lineHasColon(): Boolean {
        var step = 0
        while (step < MAX_LINE_LOOKAHEAD) {
            when (b.lookAhead(step)) {
                null, GalenTypes.EOL -> return false
                GalenTypes.COLON -> return true
                else -> step++
            }
        }
        return false
    }
}
