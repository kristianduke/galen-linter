package com.galenlinter.rules

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenFilePathRef
import com.galenlinter.resolve.GalenImportUtil
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.psi.util.PsiTreeUtil

/** A parameter captured from a rule's text: `%{name}` or `%{name: regex}`. */
data class RuleParameter(val name: String, val regex: String) {
    companion object {
        /** Galen's default when no regex is given. */
        const val DEFAULT_REGEX = ".*"
    }
}

/**
 * A custom rule, from either a `@rule` declaration or a `rule("...", ...)` call in a JavaScript
 * file loaded with `@script`.
 */
data class GalenRule(
    val text: String,
    val parameters: List<RuleParameter>,
    val pattern: Regex?,
    /** Null for a rule defined in JavaScript, which has no Galen element to point at. */
    val declaration: PsiElement?,
    val sourceName: String,
    val invokesBody: Boolean,
) {
    val isJavaScript: Boolean get() = declaration == null

    fun matches(invocation: String): Boolean = pattern?.matches(invocation.trim()) ?: false
}

object GalenRuleUtil {

    private val PARAMETER = Regex("""%\{\s*([A-Za-z_][A-Za-z0-9_]*)\s*(?::\s*([^}]*))?\}""")

    /** `rule("text", function ...)` in a JavaScript file. */
    private val JS_RULE = Regex("""\brule\s*\(\s*["']([^"']+)["']""")

    private const val MAX_IMPORT_DEPTH = 32

    /**
     * Every rule visible from [file] — its own, those of its transitive imports (Galen merges an
     * imported file's rules along with its objects and specs), and those defined in JavaScript
     * files it loads with `@script`.
     *
     * The JavaScript ones matter: without them, every invocation of a JS-defined rule would be
     * reported as matching nothing, which would make the check unusable in exactly the projects
     * that lean on rules most.
     */
    fun rulesInScope(file: PsiFile): List<GalenRule> {
        val rules = mutableListOf<GalenRule>()
        collect(file, mutableSetOf(), 0, rules)
        return rules
    }

    private fun collect(
        file: PsiFile,
        visited: MutableSet<VirtualFile>,
        depth: Int,
        into: MutableList<GalenRule>,
    ) {
        if (depth > MAX_IMPORT_DEPTH) return
        val virtual = file.virtualFile ?: file.originalFile.virtualFile
        if (virtual != null && !visited.add(virtual)) return

        into += declaredRulesIn(file)
        into += javaScriptRulesIn(file)

        val directory = virtual?.parent ?: return
        val manager = PsiManager.getInstance(file.project)
        for (path in GalenImportUtil.importPathsOf(file)) {
            val target = directory.findFileByRelativePath(path) ?: continue
            val imported = manager.findFile(target) as? GalenFile ?: continue
            collect(imported, visited, depth + 1, into)
        }
    }

    /** The `@rule` declarations in one file. */
    fun declaredRulesIn(file: PsiFile): List<GalenRule> =
        ruleElementsIn(file).mapNotNull { element ->
            val text = ruleTextOf(element) ?: return@mapNotNull null
            val parameters = parametersOf(text)
            GalenRule(
                text = text,
                parameters = parameters,
                pattern = compile(text, parameters),
                declaration = element,
                sourceName = file.name,
                invokesBody = invokesRuleBody(element),
            )
        }

    fun ruleElementsIn(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.RULE_DEFINITION }

    fun invocationElementsIn(file: PsiFile): List<PsiElement> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.RULE_INVOCATION }

    /** The rule text: the header line with `@rule` stripped. */
    fun ruleTextOf(element: PsiElement): String? =
        headerLineOf(element).removePrefix("@rule").trim().ifEmpty { null }

    /** The invocation text: the line with its leading `|` stripped. */
    fun invocationTextOf(element: PsiElement): String? =
        headerLineOf(element).removePrefix("|").trim().ifEmpty { null }

    fun parametersOf(text: String): List<RuleParameter> =
        PARAMETER.findAll(text).map { match ->
            RuleParameter(
                name = match.groupValues[1],
                regex = match.groupValues[2].trim().ifEmpty { RuleParameter.DEFAULT_REGEX },
            )
        }.toList()

    /**
     * Turns rule text into a pattern that an invocation can be matched against: literal parts are
     * escaped, and each `%{...}` becomes a capturing group using its regex.
     *
     * Returns null when a custom regex does not compile — reported separately rather than silently
     * making the rule unmatchable.
     */
    fun compile(text: String, parameters: List<RuleParameter> = parametersOf(text)): Regex? {
        val builder = StringBuilder("^")
        var cursor = 0
        var index = 0

        for (match in PARAMETER.findAll(text)) {
            builder.append(Regex.escape(text.substring(cursor, match.range.first)))
            val parameter = parameters.getOrNull(index) ?: return null
            if (!isValidRegex(parameter.regex)) return null
            builder.append("(").append(parameter.regex).append(")")
            cursor = match.range.last + 1
            index++
        }
        builder.append(Regex.escape(text.substring(cursor)))
        builder.append("$")

        return runCatching { Regex(builder.toString()) }.getOrNull()
    }

    fun isValidRegex(regex: String): Boolean = runCatching { Regex(regex) }.isSuccess

    /** True when the rule's body contains `@ruleBody`, i.e. it expects a block at the call site. */
    fun invokesRuleBody(rule: PsiElement): Boolean =
        PsiTreeUtil.findChildrenOfAnyType(rule, PsiElement::class.java)
            .any { it.node?.elementType == GalenTypes.RULE_BODY_STATEMENT }

    /** True when the invocation carries an indented block beneath it. */
    fun hasBody(invocation: PsiElement): Boolean {
        val headerEnd = invocation.node?.findChildByType(GalenTypes.EOL)?.startOffset ?: return false
        var end = invocation.textRange.endOffset
        val text = invocation.containingFile.text
        while (end > headerEnd && (text[end - 1] == '\n' || text[end - 1] == '\r')) end--
        return end > headerEnd
    }

    /** Rules defined in JavaScript files loaded by `@script`. */
    private fun javaScriptRulesIn(file: PsiFile): List<GalenRule> {
        val directory = (file.virtualFile ?: file.originalFile.virtualFile)?.parent ?: return emptyList()
        val rules = mutableListOf<GalenRule>()

        for (path in scriptPathsOf(file)) {
            if (!path.endsWith(".js")) continue
            val target = directory.findFileByRelativePath(path) ?: continue
            val content = runCatching { String(target.contentsToByteArray()) }.getOrNull() ?: continue

            for (match in JS_RULE.findAll(content)) {
                val text = match.groupValues[1]
                val parameters = parametersOf(text)
                rules += GalenRule(
                    text = text,
                    parameters = parameters,
                    pattern = compile(text, parameters),
                    declaration = null,
                    sourceName = target.name,
                    // doRuleBody() is the JavaScript equivalent; assume any JS rule may call it,
                    // rather than reporting a body the rule might well use.
                    invokesBody = true,
                )
            }
        }
        return rules
    }

    private fun scriptPathsOf(file: PsiFile): List<String> =
        PsiTreeUtil.findChildrenOfAnyType(file, PsiElement::class.java)
            .filter { it.node?.elementType == GalenTypes.SCRIPT_BLOCK }
            .mapNotNull { PsiTreeUtil.findChildOfType(it, GalenFilePathRef::class.java)?.text?.trim() }

    private fun headerLineOf(element: PsiElement): String {
        val end = element.node?.findChildByType(GalenTypes.EOL)?.startOffset
            ?: element.textRange.endOffset
        val start = element.textRange.startOffset
        if (end <= start) return element.text.trim()
        return element.containingFile.text.substring(start, end).trim()
    }
}
