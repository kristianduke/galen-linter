package com.galenlinter.lang

import com.intellij.lang.refactoring.NamesValidator
import com.intellij.openapi.project.Project

/**
 * What counts as a valid name when renaming.
 *
 * Without this, the platform falls back to `DefaultNamesValidator`, which defers to
 * `StringUtil.isJavaIdentifier` and therefore rejects `-`. Galen object names are full of dashes —
 * `menu_item-*`, `user-profile-*`, `comment-send-button` — so renaming `hero-header` to anything
 * with a dash was refused as "not a valid identifier" even though Galen is perfectly happy with it.
 *
 * The rule here is the lexer's: a name is valid when it lexes as a single word. That is exactly the
 * condition under which the rename can be written back without disturbing anything else on the line.
 */
class GalenNamesValidator : NamesValidator {

    /**
     * Galen reserves nothing for object names. A statement keyword cannot collide anyway, since
     * `@` does not lex as part of a word, and an object legitimately named `width` still parses —
     * an object statement is told from a spec line by its trailing `:`.
     */
    override fun isKeyword(name: String, project: Project?): Boolean = false

    override fun isIdentifier(name: String, project: Project?): Boolean {
        if (name.isEmpty()) return false
        if (name.any { it.isWhitespace() }) return false

        // A leading '#' would turn the declaration into a comment: Galen decides that a line is a
        // comment from its first non-blank character, before any structural parsing, so the object
        // would silently cease to exist. This is the GL005 trap, reached by rename.
        if (name.startsWith("#")) return false

        return name.all { it !in PUNCTUATION }
    }

    private companion object {
        /**
         * The characters the lexer treats as punctuation; any of them would split the name into
         * more than one token. Mirrors `GalenLexer.PUNCTUATION`.
         */
        const val PUNCTUATION = ":,|&=/%$@;()[]{}<>~+\""
    }
}
