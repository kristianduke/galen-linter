package com.galenlinter.inspections

import com.galenlinter.inspections.SpecPsiUtil.argsOf
import com.galenlinter.inspections.SpecPsiUtil.childrenOfType
import com.galenlinter.inspections.SpecPsiUtil.closestMatch
import com.galenlinter.inspections.SpecPsiUtil.descendantsOfType
import com.galenlinter.inspections.SpecPsiUtil.firstChildOfType
import com.galenlinter.inspections.SpecPsiUtil.isDynamic
import com.galenlinter.inspections.SpecPsiUtil.specNameOf
import com.galenlinter.inspections.SpecPsiUtil.typeOf
import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.intellij.codeInspection.LocalInspectionTool
import com.intellij.codeInspection.ProblemsHolder
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiElementVisitor

/** A "change to 'X'" fix, or none when nothing was close enough to suggest. */
internal fun fixFor(suggestion: String?): Array<com.intellij.codeInspection.LocalQuickFix> =
    if (suggestion == null) emptyArray() else arrayOf(GalenReplaceWordFix(suggestion))

/** Visits the two elements every spec rule cares about, in Galen files only. */
abstract class GalenSpecInspection : LocalInspectionTool() {

    final override fun buildVisitor(holder: ProblemsHolder, isOnTheFly: Boolean): PsiElementVisitor =
        object : PsiElementVisitor() {
            override fun visitElement(element: PsiElement) {
                if (element.containingFile !is GalenFile) return
                when (typeOf(element)) {
                    GalenTypes.SPEC_LINE -> checkSpecLine(element, holder)
                    GalenTypes.OBJECT_STATEMENT -> checkObjectStatement(element, holder)
                }
            }
        }

    protected open fun checkSpecLine(specLine: PsiElement, holder: ProblemsHolder) = Unit

    protected open fun checkObjectStatement(statement: PsiElement, holder: ProblemsHolder) = Unit
}

/**
 * Errors — everything here is rejected by Galen's own parser, so a file containing one of these
 * cannot run at all.
 *
 * Each rule cites the Galen class that establishes it; see `docs/galen-spec-reference.md`.
 */
class GalenInvalidSpecInspection : GalenSpecInspection() {

    override fun checkSpecLine(specLine: PsiElement, holder: ProblemsHolder) {
        val nameElement = firstChildOfType(specLine, GalenTypes.SPEC_NAME) ?: return
        val name = nameElement.text
        val args = argsOf(specLine)

        // GL301 — SpecReader registers exactly 21 specs; anything else throws.
        if (name !in GalenTypes.SPEC_NAMES && !isDynamic(name)) {
            val suggestion = closestMatch(name, GalenTypes.SPEC_NAMES)
            val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
            holder.registerProblem(
                nameElement,
                "GL301: Unknown spec '$name'.$hint",
                *fixFor(suggestion),
            )
            return
        }

        // GL319 — Side.fromString accepts only these four, case-sensitively.
        if (args != null) {
            for (side in descendantsOfType(args, GalenTypes.SIDE)) {
                val text = side.text
                if (text in GalenTypes.SIDES || isDynamic(text)) continue
                // A `to` in side position always means a malformed range — the side-group parser
                // simply took it because it was the next word. GL304 explains that properly.
                if (text == "to") continue
                val suggestion = closestMatch(text, GalenTypes.SIDES)
                val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
                holder.registerProblem(
                    side,
                    "GL319: '$text' is not a side. Galen accepts only left, right, top and bottom.$hint",
                    *fixFor(suggestion),
                )
            }
        }

        when (name) {
            "aligned" -> checkAligned(specLine, args, holder)
            "near" -> checkNearSides(specLine, args, holder)
            "on" -> checkOn(specLine, args, holder)
            "count" -> checkCount(args, holder)
            "absent", "visible" -> checkTakesNoArguments(specLine, args, name, holder)
        }

        if (name in UNIT_REQUIRED) checkRanges(specLine, args, name, holder)
    }

    /**
     * GL324 — a spec that takes no arguments was given some.
     *
     * `visible matches 10px` parses, because the arguments simply go unread, and then means only
     * `visible` at run time. The matcher is silently discarded.
     */
    private fun checkTakesNoArguments(
        specLine: PsiElement,
        args: PsiElement?,
        name: String,
        holder: ProblemsHolder,
    ) {
        if (args == null) return
        val extra = meaningfulTokens(args)
        if (extra.isEmpty()) return

        val text = extra.joinToString(" ") { it.text }
        holder.registerProblem(
            args,
            "GL324: '$name' takes no arguments, so '$text' is ignored.",
        )
    }

    /**
     * GL304 — the range grammar.
     *
     * Three shapes come up repeatedly and all of them parse into something Galen then rejects or
     * misreads:
     *  - `height is 400 to 800px` — a matcher where a range belongs;
     *  - `width 154 to 164p` — a mistyped unit, which leaves the range with no unit at all;
     *  - `height 400px to 800px` — a unit on the first bound, so the range ends after `400px` and
     *    the rest of the line is left over.
     */
    private fun checkRanges(
        specLine: PsiElement,
        args: PsiElement?,
        name: String,
        holder: ProblemsHolder,
    ) {
        if (args == null) {
            if (name in RANGE_REQUIRED) {
                holder.registerProblem(specLine, "GL304: '$name' requires a range, e.g. '$name 100px'.")
            }
            return
        }

        val ranges = descendantsOfType(args, GalenTypes.RANGE)
        val loose = meaningfulTokens(args)

        // A matcher standing where the range should be.
        val matcher = loose.firstOrNull { it.text in GalenTypes.MATCHERS }
        if (matcher != null && name in RANGE_REQUIRED) {
            holder.registerProblem(
                matcher,
                "GL304: '$name' takes a range directly, with no '${matcher.text}'. " +
                    "Write '$name 400 to 800px'.",
                GalenReplaceRangeFix("", "Remove '${matcher.text}'"),
            )
            return
        }

        if (ranges.isEmpty()) {
            if (name in RANGE_REQUIRED) {
                holder.registerProblem(specLine, "GL304: '$name' requires a range, e.g. '$name 100px'.")
            }
            return
        }

        // A `to` outside a range means the range ended early, because something was attached to
        // the first bound. The `to` is only where the damage surfaces — the token before it is the
        // mistake, so that is what gets underlined and what the fix removes.
        // Searched across the whole argument list, not just its direct children: in `near` and
        // `inside` the side-group parser absorbs the stray `to` into a SIDE node, so looking only
        // at the top level would miss exactly the specs where side groups exist.
        val outside = tokensOutsideRanges(args)
        val strayTo = outside.firstOrNull { it.text == "to" }
        if (strayTo != null) {
            val firstBound = ranges.lastOrNull { it.textRange.endOffset <= strayTo.textRange.startOffset }

            // `400px to 800px` — a real unit in the wrong place, so it parsed into the range.
            val misplacedUnit = firstBound?.let { descendantsOfType(it, GalenTypes.UNIT).lastOrNull() }

            // `10p to 15px` — a malformed unit, which the range parser did not claim at all, so it
            // is left stranded between the first bound and the `to`.
            val malformedUnit = firstBound?.let { bound ->
                outside.firstOrNull {
                    it.textRange.startOffset >= bound.textRange.endOffset &&
                        it.textRange.endOffset <= strayTo.textRange.startOffset
                }
            }

            val offender = misplacedUnit ?: malformedUnit
            val detail = if (malformedUnit != null && misplacedUnit == null) {
                "'${malformedUnit.text}' is not a unit, and the unit belongs on the last bound anyway."
            } else {
                "Only the last bound of a range carries the unit."
            }

            holder.registerProblem(
                offender ?: strayTo,
                "GL304: $detail Write '400 to 800px', not '400px to 800px'.",
                *(if (offender != null) {
                    arrayOf(GalenReplaceRangeFix("", "Remove it from the first bound"))
                } else {
                    emptyArray()
                }),
            )
            return
        }

        for (range in ranges) {
            if (isDynamic(range.text)) continue
            if (descendantsOfType(range, GalenTypes.UNIT).isNotEmpty()) continue
            // `> 40`, `~ 100` and friends are complete without a unit.
            if (hasComparisonOperator(range)) continue

            // The token straight after the range is usually the mistyped unit.
            val suspect = loose.firstOrNull { it.textRange.startOffset >= range.textRange.endOffset }
            val suggestion = suspect?.text?.takeIf { closestMatch(it, UNITS) != null }

            if (suggestion != null) {
                holder.registerProblem(
                    suspect,
                    "GL304: '$suggestion' is not a unit. Galen accepts 'px' and '%'.",
                    GalenReplaceRangeFix("px", "Change to 'px'"),
                )
            } else {
                holder.registerProblem(
                    range,
                    "GL304: This range has no unit. Galen accepts 'px' and '%', " +
                        "e.g. '${range.text.trim()}px'.",
                )
            }
        }
    }

    /** True when the range is expressed with a comparison, which makes its unit optional. */
    private fun hasComparisonOperator(range: PsiElement): Boolean {
        var child = range.firstChild
        while (child != null) {
            if (child.node?.elementType in COMPARISON_OPERATORS) return true
            child = child.nextSibling
        }
        return false
    }

    /**
     * Meaningful tokens anywhere in the arguments that are *not* part of a parsed range.
     *
     * A range's own contents are by definition correctly placed; everything else is a candidate
     * for being in the wrong place. Collected across the whole subtree because side groups nest.
     */
    private fun tokensOutsideRanges(args: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        fun walk(element: PsiElement) {
            if (typeOf(element) == GalenTypes.RANGE) return
            val type = element.node?.elementType
            if (type == GalenTypes.WORD || type == GalenTypes.NUMBER || type == GalenTypes.STRING) {
                result += element
            }
            var child = element.firstChild
            while (child != null) {
                walk(child)
                child = child.nextSibling
            }
        }
        var child = args.firstChild
        while (child != null) {
            walk(child)
            child = child.nextSibling
        }
        return result.sortedBy { it.textRange.startOffset }
    }

    /** Direct-child tokens of the arguments that carry meaning, i.e. not punctuation or space. */
    private fun meaningfulTokens(args: PsiElement): List<PsiElement> {
        val result = mutableListOf<PsiElement>()
        var child = args.firstChild
        while (child != null) {
            val type = child.node?.elementType
            if (type == GalenTypes.WORD || type == GalenTypes.NUMBER || type == GalenTypes.STRING) {
                result += child
            }
            child = child.nextSibling
        }
        return result
    }

    /**
     * `SpecAlignedProcessor` requires both a direction and an edge, and rejects mismatched pairs
     * with "Incorrect side for <direction> alignment: <EDGE>".
     */
    private fun checkAligned(specLine: PsiElement, args: PsiElement?, holder: ProblemsHolder) {
        if (args == null) return
        val direction = firstChildOfType(args, GalenTypes.ALIGN_DIRECTION)
        val edge = firstChildOfType(args, GalenTypes.ALIGN_EDGE)

        if (direction == null) {
            holder.registerProblem(
                specLine,
                "GL320: 'aligned' requires a direction: horizontally or vertically.",
            )
            return
        }

        val directionText = direction.text
        if (directionText !in GalenTypes.ALIGN_DIRECTIONS && !isDynamic(directionText)) {
            val suggestion = closestMatch(directionText, GalenTypes.ALIGN_DIRECTIONS)
            val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
            holder.registerProblem(
                direction,
                "GL320: '$directionText' is not an alignment direction. Use horizontally or vertically.$hint",
                *fixFor(suggestion),
            )
            return
        }
        if (isDynamic(directionText)) return

        val valid = GalenTypes.alignEdgesFor(directionText).sorted().joinToString(", ")
        if (edge == null) {
            holder.registerProblem(
                specLine,
                "GL302: 'aligned $directionText' requires an edge before the object name ($valid).",
            )
            return
        }
        if (isDynamic(edge.text)) return
        if (edge.text !in GalenTypes.alignEdgesFor(directionText)) {
            holder.registerProblem(
                edge,
                "GL302: Incorrect side for $directionText alignment: ${edge.text.uppercase()}. Valid edges: $valid.",
                // Every edge legal for this direction is offered, since which one was meant is
                // genuinely ambiguous.
                *GalenTypes.alignEdgesFor(directionText).sorted()
                    .map { GalenReplaceWordFix(it) }.toTypedArray(),
            )
        }
    }

    /** `SpecNearProcessor` throws when no location was given — sides are mandatory for `near`. */
    private fun checkNearSides(specLine: PsiElement, args: PsiElement?, holder: ProblemsHolder) {
        if (args == null) {
            holder.registerProblem(specLine, NEAR_SIDES_MESSAGE)
            return
        }
        val groups = childrenOfType(args, GalenTypes.SIDE_GROUP)
        if (groups.isEmpty()) {
            holder.registerProblem(specLine, NEAR_SIDES_MESSAGE)
            return
        }
        for (group in groups) {
            if (childrenOfType(group, GalenTypes.SIDE).isEmpty()) {
                holder.registerProblem(group, NEAR_SIDES_MESSAGE)
            }
        }
    }

    /**
     * `SpecOnProcessor` requires the literal word `edge`, and rejects a corner that combines two
     * horizontal or two vertical sides. Unlike `near`, its locations are optional.
     */
    private fun checkOn(specLine: PsiElement, args: PsiElement?, holder: ProblemsHolder) {
        if (args == null) return

        val hasEdge = childrenOfType(args, GalenTypes.MODIFIER).any { it.text == "edge" }
        if (!hasEdge) {
            holder.registerProblem(
                specLine,
                "GL322: 'on' requires the word 'edge' after the corner, e.g. 'on top left edge header 10px left'.",
            )
        }

        val corner = firstChildOfType(args, GalenTypes.CORNER) ?: return
        val sides = childrenOfType(corner, GalenTypes.SIDE).map { it.text }
        val vertical = sides.count { it == "top" || it == "bottom" }
        val horizontal = sides.count { it == "left" || it == "right" }
        if (vertical > 1 || horizontal > 1) {
            holder.registerProblem(
                corner,
                "GL323: A corner cannot combine two opposite sides ('${sides.joinToString(" ")}'). " +
                    "Use one of top/bottom with one of left/right.",
            )
        }
    }

    /** `count` uses a range with no unit; `ExpectRange` is configured with no ending word. */
    private fun checkCount(args: PsiElement?, holder: ProblemsHolder) {
        if (args == null) return
        for (range in descendantsOfType(args, GalenTypes.RANGE)) {
            for (unit in descendantsOfType(range, GalenTypes.UNIT)) {
                if (unit.text == "px") {
                    holder.registerProblem(
                        unit,
                        "GL309: 'count' takes a plain number range with no unit; remove 'px'.",
                    )
                }
            }
        }
    }

    /**
     * GL303 — every spec other than `absent` implicitly requires the element to be visible, so
     * pairing them on one object can never pass.
     */
    override fun checkObjectStatement(statement: PsiElement, holder: ProblemsHolder) {
        val specLines = descendantsOfType(statement, GalenTypes.SPEC_LINE)
            .filter { it.parent == statement }
        val byName = specLines.mapNotNull { line -> specNameOf(line)?.let { it to line } }

        val absent = byName.firstOrNull { it.first == "absent" } ?: return
        for ((name, line) in byName) {
            if (name == "absent") continue
            val conflicts = name == "visible" || name in REQUIRES_VISIBILITY
            if (conflicts) {
                holder.registerProblem(
                    line,
                    "GL303: This object is also declared 'absent' on line " +
                        "${lineNumberOf(absent.second)}, so '$name' can never pass.",
                )
            }
        }
    }

    private fun lineNumberOf(element: PsiElement): Int {
        val text = element.containingFile.text
        return text.substring(0, element.textRange.startOffset).count { it == '\n' } + 1
    }

    private companion object {
        const val NEAR_SIDES_MESSAGE =
            "GL318: 'near' requires at least one side after the range " +
                "(left, right, top or bottom). Galen rejects a bare distance."

        val UNITS = setOf("px", "%")

        /** Specs whose ranges must carry a unit. `count` is excluded: its range never has one. */
        val UNIT_REQUIRED = setOf(
            "width", "height", "above", "below", "left-of", "right-of", "near", "inside", "on",
        )

        /**
         * Specs that cannot work without a range.
         *
         * Only these two: `SpecWithRangeProcessor` reads a range unconditionally, whereas
         * `SpecWithObjectAndRangeProcessor` — which backs `above`, `below`, `left-of` and
         * `right-of` — defaults to `>= 0` when none is given, so `left-of button` is valid.
         */
        val RANGE_REQUIRED = setOf("width", "height")

        /**
         * A range carrying one of these needs no unit.
         *
         * `ExpectRange` returns early for any range with a comparison operator, so `width > 40` is
         * accepted while a bare `width 40` throws. `-` is excluded: it makes a value negative, not
         * a comparison, and `-10px` still needs its unit.
         */
        val COMPARISON_OPERATORS = setOf(
            GalenTypes.LT, GalenTypes.GT, GalenTypes.LE, GalenTypes.GE, GalenTypes.TILDE,
        )

        val REQUIRES_VISIBILITY = setOf(
            "near", "inside", "on", "above", "below", "left-of", "right-of",
            "width", "height", "aligned", "centered", "text", "css", "image",
            "color-scheme", "contains", "ocr",
        )
    }
}

/**
 * Warnings — Galen's own parser accepts all of these, so they are not syntax errors, but each is
 * far more likely to be a mistake than an intention.
 *
 * This is the class of bug a linter exists to catch: `text lowercse is "x"` runs, silently doing
 * no case folding at all.
 */
class GalenSuspiciousSpecInspection : GalenSpecInspection() {

    override fun checkSpecLine(specLine: PsiElement, holder: ProblemsHolder) {
        val name = specNameOf(specLine) ?: return
        if (name !in GalenTypes.SPEC_NAMES) return
        val args = argsOf(specLine) ?: return

        checkTextOperations(name, args, holder)
        checkRegex(args, holder)
        checkRelativeProperties(args, holder)
        if (name == "image") checkImage(specLine, args, holder)
    }

    /**
     * `SpecTextProcessor` accumulates *any* unrecognised word as a text operation, so a typo here
     * parses cleanly and quietly does nothing.
     */
    private fun checkTextOperations(specName: String, args: PsiElement, holder: ProblemsHolder) {
        for (operation in descendantsOfType(args, GalenTypes.TEXT_OPERATION)) {
            val text = operation.text
            if (isDynamic(text)) continue

            if (specName == "css" && text in GalenTypes.CSS_UNSUPPORTED_OPERATIONS) {
                holder.registerProblem(
                    operation,
                    "GL306: The 'css' spec does not support the '$text' operation; it is ignored.",
                )
                continue
            }

            if (text in GalenTypes.TEXT_OPERATIONS) continue

            val suggestion = closestMatch(text, GalenTypes.TEXT_OPERATIONS)
            val hint = if (suggestion != null) " Did you mean '$suggestion'?" else
                " Valid operations: ${GalenTypes.TEXT_OPERATIONS.sorted().joinToString(", ")}."
            holder.registerProblem(
                operation,
                "GL306: '$text' is not a known text operation, so it has no effect.$hint",
                *fixFor(suggestion),
            )
        }
    }

    /** `matches` compiles a Java regular expression at run time. */
    private fun checkRegex(args: PsiElement, holder: ProblemsHolder) {
        val matcher = descendantsOfType(args, GalenTypes.MATCHER).firstOrNull { it.text == "matches" }
            ?: return
        var sibling = matcher.nextSibling
        while (sibling != null && typeOf(sibling) != GalenTypes.STRING) sibling = sibling.nextSibling
        val literal = sibling ?: return

        val pattern = literal.text.trim('"')
        if (isDynamic(pattern)) return
        try {
            java.util.regex.Pattern.compile(pattern)
        } catch (e: java.util.regex.PatternSyntaxException) {
            holder.registerProblem(
                literal,
                "GL305: Not a valid Java regular expression: ${e.description}.",
            )
        }
    }

    /** `ExpectRange` accepts any relative path; only width and height mean anything. */
    private fun checkRelativeProperties(args: PsiElement, holder: ProblemsHolder) {
        for (property in descendantsOfType(args, GalenTypes.PROPERTY_NAME)) {
            val text = property.text
            if (text in GalenTypes.RELATIVE_PROPERTIES || isDynamic(text)) continue
            val suggestion = closestMatch(text, GalenTypes.RELATIVE_PROPERTIES)
            val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
            holder.registerProblem(
                property,
                "GL315: '$text' is not a relative property; Galen only resolves width and height.$hint",
                *fixFor(suggestion),
            )
        }
    }

    private fun checkImage(specLine: PsiElement, args: PsiElement, holder: ProblemsHolder) {
        val options = descendantsOfType(args, GalenTypes.IMAGE_OPTION)

        // GL312 — without a sample there is nothing to compare against.
        if (options.none { it.text == "file" }) {
            holder.registerProblem(
                specLine,
                "GL312: An 'image' spec needs at least one 'file <path>' sample to compare against.",
            )
        }

        for (option in options) {
            val text = option.text
            if (text in GalenTypes.IMAGE_OPTIONS || isDynamic(text)) continue
            val suggestion = closestMatch(text, GalenTypes.IMAGE_OPTIONS)
            val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
            holder.registerProblem(option, "GL313: Unknown image option '$text'.$hint", *fixFor(suggestion))
        }

        for (filter in descendantsOfType(args, GalenTypes.IMAGE_FILTER)) {
            val text = filter.text
            if (isDynamic(text)) continue

            if (text !in GalenTypes.IMAGE_FILTERS) {
                val suggestion = closestMatch(text, GalenTypes.IMAGE_FILTERS)
                val hint = if (suggestion != null) " Did you mean '$suggestion'?" else ""
                holder.registerProblem(filter, "GL313: Unknown image filter '$text'.$hint", *fixFor(suggestion))
                continue
            }

            // GL311 — denoise only works on the black/white comparison map.
            if (text in GalenTypes.MAP_ONLY_FILTERS) {
                val prefix = previousOptionOf(filter)
                if (prefix != null && prefix != "map-filter") {
                    holder.registerProblem(
                        filter,
                        "GL311: '$text' only works on the comparison map; use 'map-filter $text' " +
                            "rather than '$prefix $text'.",
                    )
                }
            }

            // GL310 — documented valid range for contrast.
            if (text == "contrast") {
                val level = firstNumberAfter(filter)
                if (level != null && (level < 0 || level > 258)) {
                    holder.registerProblem(
                        filter,
                        "GL310: Contrast level $level is outside the supported range of 0 to 258.",
                    )
                }
            }
        }
    }

    /** The image option keyword immediately preceding this filter name (`filter`, `map-filter`, …). */
    private fun previousOptionOf(filter: PsiElement): String? {
        var sibling = filter.prevSibling
        while (sibling != null) {
            if (typeOf(sibling) == GalenTypes.IMAGE_OPTION) return sibling.text
            sibling = sibling.prevSibling
        }
        return null
    }

    /** The first numeric argument of a filter, i.e. before the next comma. */
    private fun firstNumberAfter(filter: PsiElement): Int? {
        var sibling = filter.nextSibling
        while (sibling != null) {
            when (typeOf(sibling)) {
                GalenTypes.COMMA -> return null
                GalenTypes.NUMBER -> return sibling.text.toIntOrNull()
            }
            sibling = sibling.nextSibling
        }
        return null
    }

    /** GL316 — the same check written twice on one object is dead weight. */
    override fun checkObjectStatement(statement: PsiElement, holder: ProblemsHolder) {
        val seen = mutableMapOf<String, PsiElement>()
        for (line in descendantsOfType(statement, GalenTypes.SPEC_LINE)) {
            if (line.parent != statement) continue
            val normalised = line.text.trim().replace(WHITESPACE, " ")
            if (normalised.isEmpty()) continue
            val existing = seen.putIfAbsent(normalised, line)
            if (existing != null) {
                holder.registerProblem(line, "GL316: This spec is already declared on this object.")
            }
        }
    }

    private companion object {
        val WHITESPACE = Regex("\\s+")
    }
}
