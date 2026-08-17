package com.galenlinter.lang

import com.intellij.lang.LanguageNamesValidation
import com.intellij.testFramework.fixtures.BasePlatformTestCase

class GalenNamesValidatorTest : BasePlatformTestCase() {

    private val validator = GalenNamesValidator()

    private fun valid(name: String) =
        assertTrue("'$name' should be a valid Galen object name", validator.isIdentifier(name, project))

    private fun invalid(name: String) =
        assertFalse("'$name' should not be a valid Galen object name", validator.isIdentifier(name, project))

    /** The reported bug: Java identifier rules reject the dashes Galen names routinely contain. */
    fun testDashesAreValid() {
        valid("hero-header")
        valid("comment-send-button")
        valid("menu_item-1")
        valid("user-profile-2")
    }

    fun testOrdinaryNamesAreValid() {
        valid("header")
        valid("search_panel")
        valid("item1")
    }

    /** Dotted names address nested objects, and wildcards declare families. */
    fun testDottedAndWildcardNamesAreValid() {
        valid("search_panel.input")
        valid("menu_item-*")
        valid("item-#")
    }

    fun testNamesThatWouldNotLexAsOneWordAreRejected() {
        invalid("")
        invalid("two words")
        invalid("has:colon")
        invalid("has,comma")
        invalid("has(paren)")
        invalid("has\$dollar")
    }

    /**
     * A leading `#` would turn the declaration into a comment, silently deleting the object —
     * the GL005 trap, reached through rename instead of through typing.
     */
    fun testLeadingHashIsRejected() {
        invalid("#footer")
        // A hash elsewhere is fine; it is only line-initial that starts a comment.
        valid("colour-#fff")
    }

    fun testNothingIsReservedForObjectNames() {
        assertFalse(validator.isKeyword("width", project))
        assertFalse(validator.isKeyword("visible", project))
    }

    /** The validator must actually be the one the platform picks up for Galen. */
    fun testValidatorIsRegisteredForTheLanguage() {
        val registered = LanguageNamesValidation.INSTANCE.forLanguage(GalenLanguage)
        assertTrue(
            "Expected GalenNamesValidator, got ${registered::class.java.name}",
            registered is GalenNamesValidator,
        )
        assertTrue(
            "The registered validator must accept dashes",
            registered.isIdentifier("hero-header", project),
        )
    }
}
