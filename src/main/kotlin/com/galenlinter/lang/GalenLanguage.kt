package com.galenlinter.lang

import com.intellij.lang.Language

/**
 * Galen Framework page spec language (spec language v2.x).
 *
 * Galen keywords are case sensitive (`@forEach` is camelCase), so this language is too.
 */
object GalenLanguage : Language("Galen") {
    private fun readResolve(): Any = GalenLanguage

    override fun getDisplayName(): String = "Galen Spec"

    override fun isCaseSensitive(): Boolean = true
}
