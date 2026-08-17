package com.galenlinter.parser

import com.galenlinter.lang.GalenTypes
import com.intellij.lang.PsiBuilder
import com.intellij.psi.TokenType
import com.intellij.psi.tree.IElementType

/**
 * Per-spec argument parsing.
 *
 * ### What this does and does not do
 * It builds **precise nodes** and nothing else. Argument *validation* lives in
 * `inspections/SpecArgumentInspections.kt`, deliberately, for three reasons:
 *
 *  - `LocalQuickFix` can only attach to an inspection's `ProblemDescriptor`, never to a
 *    `PsiErrorElement`, and every one of these diagnostics wants a "did you mean" fix.
 *  - Severity becomes user-configurable, which matters because Galen's own parser is permissive
 *    in places (it accepts any unrecognised word as a text operation) and those cases must be
 *    warnings rather than errors.
 *  - The parse tree stays valid for files that merely contain a mistake, so folding, navigation
 *    and completion keep working while the user is mid-edit.
 *
 * ### The `${...}` rule
 * An `EXPRESSION` token is a wildcard in every argument position: it may stand for an object
 * name, a range, a side or a keyword. Accepting it silently — and marking the slot as dynamic by
 * simply not classifying it — is what stops expression-heavy specs from lighting up with false
 * positives.
 */
internal class SpecArgumentParser(private val b: PsiBuilder) {

    fun parse(specName: String) {
        when (specName) {
            // Galen shares one processor across these groups; so do we.
            "width", "height" -> parseRange()

            "above", "below", "left-of", "right-of" -> {
                parseObjectName()
                parseRange()
            }

            "absent", "visible" -> Unit

            "near" -> {
                parseObjectName()
                parseSideGroups()
            }

            "inside" -> {
                parseModifier("partly")
                parseObjectName()
                parseSideGroups()
            }

            "on" -> {
                parseCorner()
                parseObjectName()
                parseSideGroups()
            }

            "aligned" -> parseAligned()
            "centered" -> parseCentered()
            "text" -> parseTextLike(isOcr = false)
            "ocr" -> parseTextLike(isOcr = true)
            "css" -> parseCss()
            "contains" -> {
                parseModifier("partly")
                parseObjectNameList()
            }
            "component" -> parseComponent()
            "count" -> parseCount()
            "color-scheme" -> parseColorScheme()
            "image" -> parseImage()
        }
    }

    /** The path argument of `@import`, `@script` and `@lib`. */
    fun parseStatementPath() {
        parseFilePath()
    }

    // ---- primitives -------------------------------------------------------

    private fun atEol(): Boolean = b.eof() || b.tokenType == GalenTypes.EOL

    private fun text(): String = b.tokenText ?: ""

    private fun atWord(): Boolean = b.tokenType == GalenTypes.WORD

    private fun atExpression(): Boolean = b.tokenType == GalenTypes.EXPRESSION

    private fun atNumber(): Boolean = b.tokenType == GalenTypes.NUMBER

    private fun mark(type: IElementType) {
        val m = b.mark()
        b.advanceLexer()
        m.done(type)
    }

    /**
     * True when the very next token touches this one with no whitespace between.
     *
     * Needed because a single logical name can be several tokens: `menu_item-${index}` lexes as
     * WORD + EXPRESSION, and `imgs/logo.png` as WORD + SLASH + WORD. `rawLookup` sees whitespace
     * that `tokenType` hides, which is exactly what distinguishes one name from two arguments.
     */
    private fun adjacentNext(vararg types: IElementType): Boolean {
        val raw = b.rawLookup(1) ?: return false
        if (raw == TokenType.WHITE_SPACE) return false
        return types.any { it == raw }
    }

    private val nameParts = arrayOf<IElementType>(
        GalenTypes.WORD, GalenTypes.EXPRESSION, GalenTypes.NUMBER,
    )

    private val pathParts = arrayOf<IElementType>(
        GalenTypes.WORD, GalenTypes.EXPRESSION, GalenTypes.NUMBER,
        GalenTypes.SLASH, GalenTypes.MINUS,
    )

    private fun consumeRun(parts: Array<IElementType>) {
        while (true) {
            val more = adjacentNext(*parts)
            b.advanceLexer()
            if (!more) return
        }
    }

    // ---- names and paths --------------------------------------------------

    /** An object name, a `&group` reference, or a `${...}` standing in for either. */
    private fun parseObjectName(): Boolean {
        if (atEol()) return false

        if (b.tokenType == GalenTypes.AMP) {
            val m = b.mark()
            b.advanceLexer()
            if (atWord()) consumeRun(nameParts)
            m.done(GalenTypes.GROUP_REF)
            return true
        }

        if (!atWord() && !atExpression()) return false
        val m = b.mark()
        consumeRun(nameParts)
        m.done(GalenTypes.OBJECT_NAME_REF)
        return true
    }

    private fun parseObjectNameList() {
        while (!atEol()) {
            if (b.tokenType == GalenTypes.COMMA) {
                b.advanceLexer()
                continue
            }
            // `[a-*, b]` bracket form, used by image ignore-objects.
            if (b.tokenType == GalenTypes.LBRACKET || b.tokenType == GalenTypes.RBRACKET) {
                b.advanceLexer()
                continue
            }
            if (!parseObjectName()) break
        }
    }

    private fun parseFilePath(): Boolean {
        if (atEol() || (!atWord() && !atExpression() && b.tokenType != GalenTypes.SLASH)) return false
        val m = b.mark()
        consumeRun(pathParts)
        m.done(GalenTypes.FILE_PATH_REF)
        return true
    }

    /** Consumes a fixed keyword such as `partly` or `frame` when present. */
    private fun parseModifier(keyword: String) {
        if (atWord() && text() == keyword) mark(GalenTypes.MODIFIER)
    }

    // ---- ranges -----------------------------------------------------------

    private fun isRangeOperator(type: IElementType?): Boolean = type == GalenTypes.LT ||
        type == GalenTypes.GT || type == GalenTypes.LE || type == GalenTypes.GE ||
        type == GalenTypes.TILDE || type == GalenTypes.MINUS

    /**
     * `[op] number [to number] [px | % [of object/property]]`
     *
     * Decimals are accepted (confirmed in `ExpectRange`), as is a bare `${...}` for the whole
     * range — `@set` values legitimately carry their own operator and unit, e.g. `~ 20px`.
     */
    private fun parseRange(): Boolean {
        if (atEol()) return false

        val m = b.mark()
        if (isRangeOperator(b.tokenType)) b.advanceLexer()

        val hasValue = when {
            atExpression() -> { b.advanceLexer(); true }
            atNumber() -> { b.advanceLexer(); true }
            else -> false
        }
        if (!hasValue) {
            m.rollbackTo()
            return false
        }

        if (atWord() && text() == "to") {
            mark(GalenTypes.RANGE_KEYWORD)
            if (atNumber() || atExpression()) b.advanceLexer()
        }

        when {
            b.tokenType == GalenTypes.PERCENT -> {
                mark(GalenTypes.UNIT)
                if (atWord() && text() == "of") {
                    mark(GalenTypes.RANGE_KEYWORD)
                    parseRelativeRef()
                }
            }
            atWord() && text() == "px" -> mark(GalenTypes.UNIT)
        }

        m.done(GalenTypes.RANGE)
        return true
    }

    /** `object/width` — Galen does not check the property name at parse time; an inspection does. */
    private fun parseRelativeRef() {
        val m = b.mark()
        parseObjectName()
        if (b.tokenType == GalenTypes.SLASH) {
            b.advanceLexer()
            if (atWord()) mark(GalenTypes.PROPERTY_NAME)
        }
        m.done(GalenTypes.RELATIVE_REF)
    }

    /** A trailing `1px` tolerance on `aligned` / `centered`. */
    private fun parseErrorRate() {
        if (atNumber() || atExpression()) {
            val m = b.mark()
            b.advanceLexer()
            if (atWord() && text() == "px") mark(GalenTypes.UNIT)
            m.done(GalenTypes.ERROR_RATE)
        }
    }

    // ---- side groups ------------------------------------------------------

    /**
     * `range side... [, range side...]`
     *
     * After a range the only legal continuation is a side keyword, a comma or end of line, so any
     * other word here is a mis-typed side. It is still marked as a [GalenTypes.SIDE] node so the
     * inspection can report it precisely and offer a correction.
     */
    private fun parseSideGroups() {
        while (!atEol()) {
            val m = b.mark()
            val hadRange = parseRange()
            var sides = 0

            while (!atEol() && b.tokenType != GalenTypes.COMMA) {
                if (!atWord() && !atExpression()) break
                mark(GalenTypes.SIDE)
                sides++
            }

            if (hadRange || sides > 0) m.done(GalenTypes.SIDE_GROUP) else m.drop()

            if (b.tokenType == GalenTypes.COMMA) b.advanceLexer() else break
        }
    }

    /**
     * `on top left edge <object> ...`
     *
     * Only genuine side words are taken, so a malformed `on <object> 10px left` leaves the object
     * name for [parseObjectName] instead of silently swallowing it as a corner.
     */
    private fun parseCorner() {
        val m = b.mark()
        var consumed = 0
        while (!atEol() && consumed < 2) {
            val isSide = atWord() && text() in GalenTypes.SIDES
            if (!isSide && !atExpression()) break
            mark(GalenTypes.SIDE)
            consumed++
        }
        if (consumed > 0) m.done(GalenTypes.CORNER) else m.drop()
        parseModifier("edge")
    }

    // ---- individual specs -------------------------------------------------

    private fun parseAligned() {
        var direction: String? = null

        if (atWord() || atExpression()) {
            if (atWord()) direction = text()
            mark(GalenTypes.ALIGN_DIRECTION)
        }

        // The edge is required. When the next word is not an edge at all it means the edge was
        // omitted and this word is really the object name, so leave it for parseObjectName and
        // let the inspection report the omission.
        if (atExpression()) {
            mark(GalenTypes.ALIGN_EDGE)
        } else if (atWord() && text() in GalenTypes.ALIGN_EDGES) {
            mark(GalenTypes.ALIGN_EDGE)
        }

        parseObjectName()
        parseErrorRate()
    }

    private fun parseCentered() {
        if (atWord() || atExpression()) mark(GalenTypes.CENTERED_DIRECTION)
        if (atWord() && text() in GalenTypes.CENTERED_RELATIONS) {
            mark(GalenTypes.CENTERED_RELATION)
        } else if (atExpression()) {
            mark(GalenTypes.CENTERED_RELATION)
        }
        parseObjectName()
        parseErrorRate()
    }

    /**
     * `text [operations...] matcher "expected"`, and `ocr text [operations...] matcher "expected"`.
     *
     * Mirrors `SpecTextProcessor`: words are accumulated as operations until a matcher appears.
     * Unrecognised words are still marked as operations — Galen accepts them too — and the
     * inspection flags them as probable typos.
     */
    private fun parseTextLike(isOcr: Boolean) {
        if (isOcr) parseModifier("text")

        while (!atEol()) {
            if (atExpression()) {
                b.advanceLexer()
                continue
            }
            if (!atWord()) break
            if (text() in GalenTypes.MATCHERS) {
                mark(GalenTypes.MATCHER)
                break
            }
            mark(GalenTypes.TEXT_OPERATION)
        }

        if (b.tokenType == GalenTypes.STRING) b.advanceLexer()
    }

    private fun parseCss() {
        if (atWord() || atExpression()) mark(GalenTypes.CSS_PROPERTY)

        while (!atEol()) {
            if (atExpression()) {
                b.advanceLexer()
                continue
            }
            if (!atWord()) break
            if (text() in GalenTypes.MATCHERS) {
                mark(GalenTypes.MATCHER)
                break
            }
            mark(GalenTypes.TEXT_OPERATION)
        }

        if (b.tokenType == GalenTypes.STRING) b.advanceLexer()
    }

    /** `component [frame] <file.gspec>[, name value]...` */
    private fun parseComponent() {
        parseModifier("frame")
        parseFilePath()

        while (b.tokenType == GalenTypes.COMMA) {
            b.advanceLexer()
            if (atEol()) break
            val m = b.mark()
            if (atWord() || atExpression()) b.advanceLexer() // argument name
            if (!atEol() && b.tokenType != GalenTypes.COMMA) b.advanceLexer() // value
            m.done(GalenTypes.COMPONENT_ARG)
        }
    }

    /** `count any|visible|absent <pattern> is <range>` — the range carries no unit. */
    private fun parseCount() {
        if (atWord() || atExpression()) mark(GalenTypes.COUNT_FILTER)
        parseObjectName()
        if (atWord() && text() == "is") mark(GalenTypes.MATCHER)
        parseRange()
    }

    /** `color-scheme 10% white, 4 to 5 % black, < 30% #f845b7` */
    private fun parseColorScheme() {
        while (!atEol()) {
            val m = b.mark()
            parseRange()
            if (atWord() || atExpression()) mark(GalenTypes.COLOR_VALUE)
            m.done(GalenTypes.COLOR_ENTRY)
            if (b.tokenType == GalenTypes.COMMA) b.advanceLexer() else break
        }
    }

    /**
     * `image file <path>, error 4%, tolerance 80, filter blur 4, ...`
     *
     * Options are comma-separated; each is a keyword plus a variable number of arguments, so the
     * parser marks the keyword and then consumes to the next comma.
     */
    private fun parseImage() {
        while (!atEol()) {
            if (b.tokenType == GalenTypes.COMMA) {
                b.advanceLexer()
                continue
            }
            if (!atWord()) {
                b.advanceLexer()
                continue
            }

            val option = text()
            mark(GalenTypes.IMAGE_OPTION)

            when {
                option == "file" || option == "mask" -> parseFilePath()

                option in GalenTypes.IMAGE_FILTER_PREFIXES -> {
                    if (atWord() || atExpression()) mark(GalenTypes.IMAGE_FILTER)
                    consumeToNextOption()
                }

                // `ignore-objects [a-*, b]` uses commas *inside* brackets, where every other
                // image option uses them as separators. Track depth so the right commas end the
                // option.
                option == "ignore-objects" -> {
                    var depth = 0
                    objects@ while (!atEol()) {
                        when (b.tokenType) {
                            GalenTypes.LBRACKET -> {
                                depth++
                                b.advanceLexer()
                            }
                            GalenTypes.RBRACKET -> {
                                depth--
                                b.advanceLexer()
                            }
                            GalenTypes.COMMA -> {
                                if (depth <= 0) break@objects
                                b.advanceLexer()
                            }
                            else -> if (!parseObjectName()) b.advanceLexer()
                        }
                    }
                }

                else -> consumeToNextOption()
            }
        }
    }

    private fun consumeToNextOption() {
        while (!atEol() && b.tokenType != GalenTypes.COMMA) b.advanceLexer()
    }
}
