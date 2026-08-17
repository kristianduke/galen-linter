package com.galenlinter.parser

import com.galenlinter.lang.GalenParserDefinition
import com.intellij.testFramework.ParsingTestCase

/**
 * Golden-file PSI dumps. Expected trees live beside the fixtures in
 * `src/test/testData/parsing/<Name>.txt` and are generated on the first run.
 */
class GalenParsingTest : ParsingTestCase("parsing", "gspec", GalenParserDefinition()) {

    override fun getTestDataPath(): String = "src/test/testData"

    override fun skipSpaces(): Boolean = false

    override fun includeRanges(): Boolean = true

    fun testObjects() = doTest(true)

    fun testSections() = doTest(true)

    fun testSpecs() = doTest(true)

    fun testControlFlow() = doTest(true)

    fun testRules() = doTest(true)

    fun testGroups() = doTest(true)
}
