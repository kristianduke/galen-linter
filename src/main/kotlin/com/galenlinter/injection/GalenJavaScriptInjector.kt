package com.galenlinter.injection

import com.galenlinter.lang.GalenTypes
import com.galenlinter.psi.GalenExpressionElement
import com.galenlinter.psi.GalenFile
import com.galenlinter.psi.GalenRawJsLine
import com.intellij.lang.Language
import com.intellij.lang.injection.MultiHostInjector
import com.intellij.lang.injection.MultiHostRegistrar
import com.intellij.psi.PsiElement
import com.intellij.psi.util.PsiTreeUtil

/**
 * Injects JavaScript into Galen's embedded JavaScript, when a JavaScript plugin is available.
 *
 * Registered only from the optional `galen-withJavaScript.xml` descriptor, so nothing here loads in
 * an IDE without JavaScript support — IntelliJ IDEA Community bundles none.
 *
 * The language is looked up by ID at run time rather than referenced directly, so this compiles
 * without a dependency on the JavaScript plugin's API and keeps working if that API changes.
 */
class GalenJavaScriptInjector : MultiHostInjector {

    override fun elementsToInjectIn(): List<Class<out PsiElement>> =
        listOf(GalenExpressionElement::class.java, GalenRawJsLine::class.java)

    override fun getLanguagesToInject(registrar: MultiHostRegistrar, context: PsiElement) {
        if (context.containingFile !is GalenFile) return
        val javaScript = javaScriptLanguage() ?: return

        when (context) {
            is GalenExpressionElement -> injectExpression(registrar, javaScript, context)
            is GalenRawJsLine -> injectScriptBlock(registrar, javaScript, context)
            else -> Unit
        }
    }

    /** `${ ... }` — the interior is a JavaScript expression. */
    private fun injectExpression(
        registrar: MultiHostRegistrar,
        javaScript: Language,
        host: GalenExpressionElement,
    ) {
        if (!host.isValidHost) return
        val range = host.expressionRange() ?: return

        registrar.startInjecting(javaScript)
            // Wrapping in parentheses makes a bare expression a complete JavaScript fragment, so
            // `${a + b}` is analysed as an expression rather than reported as a broken statement.
            .addPlace("(", ")", host, range)
            .doneInjecting()
    }

    /**
     * A `@script` block is one JavaScript file spread over several lines, so all of its lines are
     * injected as a single fragment — otherwise a function declared on one line and closed on the
     * next would look like two broken snippets.
     *
     * Only the first line of the block starts the injection; the rest are joined onto it.
     */
    private fun injectScriptBlock(
        registrar: MultiHostRegistrar,
        javaScript: Language,
        host: GalenRawJsLine,
    ) {
        val block = host.parent ?: return
        if (block.node?.elementType != GalenTypes.SCRIPT_BLOCK) return

        val lines = PsiTreeUtil.getChildrenOfTypeAsList(block, GalenRawJsLine::class.java)
        if (lines.isEmpty() || lines.first() != host) return

        var started = false
        for (line in lines) {
            val range = line.contentRange() ?: continue
            if (!started) {
                registrar.startInjecting(javaScript)
                started = true
            }
            registrar.addPlace(null, "\n", line, range)
        }
        if (started) registrar.doneInjecting()
    }

    private fun javaScriptLanguage(): Language? =
        Language.findLanguageByID("JavaScript") ?: Language.findLanguageByID("ECMAScript 6")
}
