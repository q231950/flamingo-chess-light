package dev.neoneon.flamingo

import androidx.compose.ui.graphics.luminance
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class LastMoveShadingTest {

    private fun alpha(isDark: Boolean, isLastMoveSquare: Boolean = false) =
        squareBackgroundAlpha(isDark, isLastMoveSquare)

    @Test
    fun aPlainSquareIsBareNewsprint() {
        // Dark squares are told apart by their hatching, not by a tint.
        for (isDark in listOf(true, false)) {
            assertEquals(0f, alpha(isDark))
            assertEquals(NEWSPRINT_PAPER, squareGround(alpha(isDark)))
        }
    }

    @Test
    fun aShadedSquareIsDarkerNewsprint() {
        val plain = squareGround(alpha(isDark = false)).luminance()
        for (isDark in listOf(true, false)) {
            assertTrue(squareGround(alpha(isDark, isLastMoveSquare = true)).luminance() < plain)
        }
    }

    @Test
    fun aLastMoveSquareIsShadedAboveItsPlainColor() {
        for (isDark in listOf(true, false)) {
            assertTrue(alpha(isDark, isLastMoveSquare = true) > alpha(isDark))
        }
    }

    @Test
    fun aLastMoveSquareStaysDistinctBetweenLightAndDark() {
        assertTrue(alpha(isDark = true, isLastMoveSquare = true) > alpha(isDark = false, isLastMoveSquare = true))
    }
}
