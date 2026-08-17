package com.galenlinter.completion

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.resolve.GalenGroupUtil
import com.galenlinter.resolve.GalenObjectResolver
import com.intellij.codeInsight.completion.CompletionContributor
import com.intellij.codeInsight.completion.CompletionParameters
import com.intellij.codeInsight.completion.CompletionResultSet
import com.intellij.codeInsight.lookup.LookupElement
import com.intellij.codeInsight.lookup.LookupElementBuilder
import com.intellij.psi.PsiFile

/**
 * Context-aware completion.
 *
 * Almost everything here depends on *where* the caret is in a line's grammar rather than on the
 * word being typed: after `aligned vertically` only `left`, `right`, `centered` and `all` are legal,
 * and offering `top` there would be actively misleading.
 *
 * The decision is taken from the line prefix (see [GalenCompletionContext]) rather than the PSI,
 * because IntelliJ injects a dummy identifier at the caret during completion, which in a
 * line-oriented grammar can reshape the parse of the very line being analysed.
 */
class GalenCompletionContributor : CompletionContributor() {

    override fun fillCompletionVariants(parameters: CompletionParameters, result: CompletionResultSet) {
        val file = parameters.originalFile
        if (file !is GalenFile) return

        val context = GalenCompletionContext.of(file.text, parameters.offset)

        // Use our own prefix: the default matcher splits on `-` and `.`, which appear in the middle
        // of most Galen object names.
        val sink = result.withPrefixMatcher(context.currentWord)
        val suggestions = suggestionsFor(context, file)
        if (suggestions.isEmpty()) return

        sink.addAllElements(suggestions)
        // Nothing else can meaningfully contribute to a Galen spec line.
        sink.stopHere()
    }

    private fun suggestionsFor(context: GalenCompletionContext, file: PsiFile): List<LookupElement> {
        // `&` opens a group reference wherever an object may appear.
        if (context.currentWord.startsWith("&")) {
            return GalenGroupUtil.namesInScope(file).map { keyword("&$it", "group") }
        }

        if (context.inObjectsBlock) return objectDefinitionSuggestions(context)
        if (context.inGroupsBlock || context.inSetBlock || context.inScriptBlock) return emptyList()

        if (context.inObjectStatement) {
            return if (context.specName == null) {
                GalenTypes.SPEC_NAMES.map { keyword(it, "spec") }
            } else {
                specArgumentSuggestions(context, file)
            }
        }

        // A section, `@on`, `@if`, a loop or the top level: a statement, or an object statement.
        if (context.isFirstWordOnLine) {
            return GalenTypes.STATEMENT_KEYWORDS.map { keyword(it, "statement") } +
                objectNames(file).map { keyword(it, "object") }
        }
        return emptyList()
    }

    /** Inside `@objects`: the name is the user's own, but the locator type is ours to offer. */
    private fun objectDefinitionSuggestions(context: GalenCompletionContext): List<LookupElement> =
        if (context.words.size == 1) {
            GalenTypes.LOCATOR_TYPES.map { keyword(it, "locator type") }
        } else {
            emptyList()
        }

    private fun specArgumentSuggestions(
        context: GalenCompletionContext,
        file: PsiFile,
    ): List<LookupElement> {
        val spec = context.specName ?: return emptyList()
        val args = context.specArguments

        return when (spec) {
            "aligned" -> when (args.size) {
                0 -> GalenTypes.ALIGN_DIRECTIONS.map { keyword(it, "direction") }
                // Only the edges legal for the direction already chosen.
                1 -> GalenTypes.alignEdgesFor(args[0]).map { keyword(it, "edge") }
                2 -> objectNames(file).map { keyword(it, "object") }
                else -> emptyList()
            }

            "centered" -> when (args.size) {
                0 -> GalenTypes.CENTERED_DIRECTIONS.map { keyword(it, "direction") }
                1 -> GalenTypes.CENTERED_RELATIONS.map { keyword(it, "relation") }
                2 -> objectNames(file).map { keyword(it, "object") }
                else -> emptyList()
            }

            "text", "ocr" -> textSuggestions(spec, args)
            "css" -> if (args.isEmpty()) emptyList() else matcherSuggestions(args)

            "count" -> when (args.size) {
                0 -> GalenTypes.COUNT_FILTERS.map { keyword(it, "filter") }
                1 -> objectNames(file).map { keyword(it, "object") }
                2 -> listOf(keyword("is", "matcher"))
                else -> emptyList()
            }

            "image" -> GalenTypes.IMAGE_OPTIONS.map { keyword(it, "option") } +
                GalenTypes.IMAGE_FILTERS.map { keyword(it, "filter") }

            "component" -> if (args.isEmpty()) listOf(keyword("frame", "modifier")) else emptyList()

            "near", "inside", "on" -> when {
                args.isEmpty() -> objectNames(file).map { keyword(it, "object") } +
                    modifiersFor(spec)
                // After a range, only a side is legal.
                endsWithRange(args) -> GalenTypes.SIDES.map { keyword(it, "side") }
                else -> emptyList()
            }

            "above", "below", "left-of", "right-of" ->
                if (args.isEmpty()) objectNames(file).map { keyword(it, "object") } else emptyList()

            "contains" ->
                objectNames(file).map { keyword(it, "object") } +
                    if (args.isEmpty()) listOf(keyword("partly", "modifier")) else emptyList()

            "width", "height" ->
                // `100 % of <object>/width` — offer objects once `of` has been typed.
                if (args.lastOrNull() == "of") objectNames(file).map { keyword(it, "object") }
                else emptyList()

            else -> emptyList()
        }
    }

    private fun modifiersFor(spec: String): List<LookupElement> = when (spec) {
        "inside" -> listOf(keyword("partly", "modifier"))
        "on" -> GalenTypes.SIDES.map { keyword(it, "corner") } + keyword("edge", "modifier")
        else -> emptyList()
    }

    private fun textSuggestions(spec: String, args: List<String>): List<LookupElement> {
        if (spec == "ocr" && args.isEmpty()) return listOf(keyword("text", "keyword"))
        return matcherSuggestions(args)
    }

    /** Operations may be chained, so offer both until a matcher has been typed. */
    private fun matcherSuggestions(args: List<String>): List<LookupElement> {
        if (args.any { it in GalenTypes.MATCHERS }) return emptyList()
        return GalenTypes.MATCHERS.map { keyword(it, "matcher") } +
            GalenTypes.TEXT_OPERATIONS.map { keyword(it, "operation") }
    }

    /** True when the arguments so far end in a complete range, i.e. a side comes next. */
    private fun endsWithRange(args: List<String>): Boolean {
        val last = args.lastOrNull() ?: return false
        if (last in GalenTypes.SIDES) return true // already listing sides
        return last.endsWith("px") || last.endsWith("%") || last.toIntOrNull() != null ||
            last.toDoubleOrNull() != null
    }

    /**
     * Only objects in scope — this file plus what it imports. Offering a name from an unimported
     * file would produce a spec that cannot run without also adding the `@import`.
     */
    private fun objectNames(file: PsiFile): List<String> =
        GalenObjectResolver.declarationsInScope(file)
            .mapNotNull { it.qualifiedName }
            .plus(GalenTypes.SPECIAL_OBJECTS)
            .distinct()

    private fun keyword(text: String, type: String): LookupElement =
        LookupElementBuilder.create(text).withTypeText(type)
}
