package com.rignis.backup.core.crypto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.Random

class BackupCodeGeneratorTest {

    private val formatRegex = Regex("^[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}-[0-9A-Z]{4}$")
    private val ambiguousChars = setOf('O', 'I', 'L', 'U')

    @Test
    fun generate_matchesExpectedFormat() {
        val code = BackupCodeGenerator.generate()

        assertTrue("was: $code", formatRegex.matches(code))
    }

    @Test
    fun generate_neverContainsAmbiguousCharacters() {
        repeat(200) {
            val code = BackupCodeGenerator.generate()
            code.forEach { c ->
                assertFalse("code $code contained ambiguous char $c", c in ambiguousChars)
            }
        }
    }

    @Test
    fun generate_producesDifferentCodesEachTime() {
        val codes = (1..50).map { BackupCodeGenerator.generate() }.toSet()

        assertEquals(50, codes.size)
    }

    @Test
    fun generate_isDeterministicForASeededRandomSource() {
        val a = BackupCodeGenerator.generate(Random(42))
        val b = BackupCodeGenerator.generate(Random(42))

        assertEquals(a, b)
    }

    @Test
    fun normalize_lowercaseAndMissingHyphens_matchesGeneratedFormat() {
        val generated = BackupCodeGenerator.generate()
        val typedByUser = generated.replace("-", "").lowercase()

        assertEquals(generated, BackupCodeGenerator.normalize(typedByUser))
    }

    @Test
    fun normalize_trimsSurroundingWhitespace() {
        assertEquals("ABCD-EFGH", BackupCodeGenerator.normalize("  abcd-efgh  "))
    }
}
