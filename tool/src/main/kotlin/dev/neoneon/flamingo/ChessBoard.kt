package dev.neoneon.flamingo

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicText
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawWithCache
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import com.thelightphone.sdk.ui.LightThemeTokens
import com.thelightphone.sdk.ui.designVerticalPxToDp
import com.thelightphone.sdk.ui.designVerticalPxToSp
import dev.neoneon.chesskit.Board
import dev.neoneon.chesskit.Piece
import dev.neoneon.chesskit.Position
import dev.neoneon.chesskit.Square
import kotlin.math.sqrt

// Same weight as the SDK's active code-input underline, so the check marker reads as a line
// rather than a hairline (see LightCodeInput's ACTIVE_UNDERLINE_THICKNESS_PX).
private const val CHECK_UNDERLINE_THICKNESS_PX = 7f
private const val CHECK_UNDERLINE_DURATION_MS = 220

// The board is an old newspaper diagram whatever the LightOS theme: black ink on almost-white
// newsprint, light squares bare and dark squares hatched. The paper is a neutral grey rather than
// the warm #F4EFE2 of iOS's newspaper-pieces preview, because LightOS is monochrome.
internal val NEWSPRINT_PAPER = Color(0xFFF2F2F2)
internal val NEWSPRINT_INK = Color.Black

// Dark-square hatching: "/" strokes, bottom-left to top-right as iOS's SquareView draws them.
// A whole number of strokes per square edge puts one through every corner, so the hatching runs on
// unbroken from one dark square into the next along a diagonal. Ink covers a quarter of the
// square, as in iOS's preview (1px of ink in every 4px, measured across the strokes).
private const val HATCH_STROKES_PER_SQUARE = 10
private const val HATCH_INK_COVERAGE = 0.25f

// The paper halo around a piece on a hatched square — the gap the old diagram blocks left so the
// hatching stops short of the piece's outline instead of running into it. A stroke of the piece's
// silhouette, as a share of the glyph's font size; half of it shows outside the silhouette.
private const val PIECE_HALO_STROKE_SHARE = 0.12f

// The last-move shade, as the alpha of the ink laid over a square's newsprint. Each sits clearly
// above a plain square (bare paper, 0) and below a selection (0.35), the way iOS lays a translucent
// fill over the from- and to-squares — a whole square is legible at a glance on the LPIII's
// screen, where an earlier dot-and-arrow marker was too small to notice. The dark square's shade
// is the deeper one, as it was before the hatching took over from a flat grey.
private const val LAST_MOVE_DARK_ALPHA = 0.28f
private const val LAST_MOVE_LIGHT_ALPHA = 0.18f

/**
 * The square holding the king that [state] reports as being in check, or `null` if neither is.
 *
 * Checkmate counts too: the king is in check there as well, and it's the moment most worth
 * marking.
 *
 * This reads [Board.state] rather than deriving check from [position] alone because chesskit keeps
 * its attack generation private. Note [Board] only evaluates the right king when its state was
 * updated *by a move* (see `Board.updateState`), so a board advanced with `move` /
 * `completePromotion` — including one rebuilt by replaying a game's moves — reports check
 * correctly, while one whose position was set via `Board.update` reads as `active` even from a
 * position that really is check.
 */
internal fun checkedKingSquare(state: Board.State, position: Position): Square? {
    val color = when (state) {
        is Board.State.check -> state.color
        is Board.State.checkmate -> state.color
        else -> null
    } ?: return null

    return position.pieces.firstOrNull { it.kind == Piece.Kind.king && it.color == color }?.square
}

/**
 * Interactive chess board rendering a chesskit [Position] with tap-to-select/tap-to-move squares.
 *
 * It's drawn as a newspaper diagram in either LightOS theme — ink on newsprint, dark squares
 * hatched — so nothing on it uses the theme's colors: white pieces are paper with an ink outline,
 * black pieces solid ink.
 *
 * [orientation] is the local player's color: their pieces sit at the bottom, so a black
 * player sees the board flipped (rank 1 on top, files h→a left-to-right).
 *
 * [boardSize] is the full side length of the (square) board; each square is [boardSize] / 8. The
 * caller sizes it to fit the space actually available (see GameView), so it never overflows the
 * screen regardless of device proportions.
 *
 * [checkedKingSquare] marks the king that's in check with a line along the bottom edge of that one
 * square, drawing in from its left edge to its right. Callers derive it from their board with the
 * `checkedKingSquare` function above.
 *
 * [lastMove] shades the move's origin and destination squares a step darker than their plain
 * color, as iOS does. Always shown for the move a finished game ended on (or, mid-replay, whichever
 * move produced the frame on screen); during live play it's the caller's choice whether to pass one.
 */
@Composable
fun ChessBoard(
    position: Position,
    selectedSquare: Square?,
    legalTargets: Set<Square>,
    onSquareTap: (Square) -> Unit,
    boardSize: Dp,
    modifier: Modifier = Modifier,
    orientation: Piece.Color = Piece.Color.white,
    checkedKingSquare: Square? = null,
    lastMove: Pair<Square, Square>? = null,
) {
    val squareSize = boardSize / 8
    val rankOrder = boardRankOrder(orientation)
    val fileOrder = boardFileOrder(orientation)
    Box(modifier = modifier.size(boardSize)) {
        Column {
            for (rankValue in rankOrder) {
                Row {
                    for (file in fileOrder) {
                        val square = Square(file, Square.Rank(rankValue))
                        ChessSquare(
                            piece = position.piece(at = square),
                            isDark = square.color == Square.Color.dark,
                            isSelected = square == selectedSquare,
                            isLegalTarget = square in legalTargets,
                            isInCheck = square == checkedKingSquare,
                            isLastMoveSquare = lastMove?.let { square == it.first || square == it.second } == true,
                            lastMoveDescription = lastMove
                                ?.takeIf { it.second == square }
                                ?.let { "Last move ${it.first.notation} to ${it.second.notation}" },
                            squareSize = squareSize,
                            onTap = { onSquareTap(square) },
                        )
                    }
                }
            }
        }
    }
}

/** The left-to-right file order the board lays out in, seen by a player of [orientation]. */
internal fun boardFileOrder(orientation: Piece.Color): List<Square.File> =
    if (orientation == Piece.Color.black) Square.File.entries.reversed() else Square.File.entries

/** The top-to-bottom rank order the board lays out in: your own back rank is nearest you. */
internal fun boardRankOrder(orientation: Piece.Color): List<Int> =
    (if (orientation == Piece.Color.black) (1..8) else (8 downTo 1)).toList()

/**
 * The alpha of the ink laid over a square's newsprint, under any hatching: a selection first, then
 * a legal target, then the last move's shade. A plain square is bare paper — a dark one is told
 * apart by its hatching, not by a tint. Pulled out of [ChessSquare] so the precedence is testable.
 */
internal fun squareBackgroundAlpha(
    isDark: Boolean,
    isSelected: Boolean,
    isLegalTarget: Boolean,
    isLastMoveSquare: Boolean,
): Float = when {
    isSelected -> 0.35f
    isLegalTarget -> 0.25f
    isLastMoveSquare -> if (isDark) LAST_MOVE_DARK_ALPHA else LAST_MOVE_LIGHT_ALPHA
    else -> 0f
}

/** A square's ground: its ink shade (see [squareBackgroundAlpha]) over the newsprint. */
internal fun squareGround(backgroundAlpha: Float): Color =
    NEWSPRINT_INK.copy(alpha = backgroundAlpha).compositeOver(NEWSPRINT_PAPER)

/**
 * Hatches a dark square in ink, over whatever its background already drew. The strokes start a
 * full square to the left so they reach the bottom-right corner too; the clip trims the overhang.
 */
private fun Modifier.newspaperHatch(): Modifier = drawWithCache {
    val spacing = size.width / HATCH_STROKES_PER_SQUARE
    val strokes = Path().apply {
        var x = -size.height
        while (x < size.width) {
            moveTo(x, size.height)
            lineTo(x + size.height, 0f)
            x += spacing
        }
    }
    // At 45° the strokes repeat every spacing/√2 measured across them.
    val stroke = Stroke(width = spacing / sqrt(2f) * HATCH_INK_COVERAGE)
    onDrawBehind {
        clipRect { drawPath(strokes, NEWSPRINT_INK, style = stroke) }
    }
}

@Composable
private fun ChessSquare(
    piece: Piece?,
    isDark: Boolean,
    isSelected: Boolean,
    isLegalTarget: Boolean,
    isInCheck: Boolean,
    isLastMoveSquare: Boolean,
    lastMoveDescription: String?,
    squareSize: Dp,
    onTap: () -> Unit,
) {
    val ground = squareGround(squareBackgroundAlpha(isDark, isSelected, isLegalTarget, isLastMoveSquare))

    // The check line draws in from this square's left edge to its right edge — screen-left to
    // screen-right, so it looks the same however the board is oriented. At 0f there's nothing to
    // draw, which is every square but the checked king's.
    val checkProgress by animateFloatAsState(
        targetValue = if (isInCheck) 1f else 0f,
        animationSpec = tween(durationMillis = CHECK_UNDERLINE_DURATION_MS, easing = LinearEasing),
        label = "checkUnderline",
    )
    // A square is never both the checked king's and a last move's endpoint at once — a king can't
    // move into check on itself — so there's never a second description competing for this slot.
    val markerDescription = if (isInCheck) "King in check" else lastMoveDescription
    val markerSemantics = markerDescription?.let { description ->
        Modifier.semantics { contentDescription = description }
    } ?: Modifier

    Box(
        modifier = Modifier
            .size(squareSize)
            .background(ground)
            .then(if (isDark) Modifier.newspaperHatch() else Modifier)
            .clickable(onClick = onTap)
            .then(markerSemantics),
        contentAlignment = Alignment.Center,
    ) {
        if (piece != null) {
            NewspaperPieceGlyph(piece = piece, haloColor = ground.takeIf { isDark })
        }
        if (checkProgress > 0f) {
            Box(
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .fillMaxWidth(checkProgress)
                    .height(CHECK_UNDERLINE_THICKNESS_PX.designVerticalPxToDp())
                    .background(NEWSPRINT_INK),
            )
        }
    }
}

/**
 * [piece]'s glyph as a newspaper diagram prints it: a white piece is the outline glyph in ink over
 * the solid one in paper, so the square's hatching can't show through it; a black piece is the
 * solid glyph in ink.
 *
 * [haloColor], given on a hatched square, rings the piece in the square's own ground, so the
 * hatching stops short of its outline — the paper halo iOS's newspaper-pieces preview gives a piece
 * on a dark square. The ground rather than bare paper, so the halo doesn't cut a ring through a
 * selection or last-move shade.
 */
@Composable
private fun NewspaperPieceGlyph(piece: Piece, haloColor: Color?) {
    val style = pieceGlyphStyle()
    val solid = piece.copy(color = Piece.Color.black).graphic
    Box(contentAlignment = Alignment.Center) {
        if (haloColor != null) {
            val haloWidth = with(LocalDensity.current) { style.fontSize.toPx() } * PIECE_HALO_STROKE_SHARE
            BasicText(
                text = solid,
                modifier = Modifier.clearAndSetSemantics {},
                style = style.copy(
                    color = haloColor,
                    drawStyle = Stroke(width = haloWidth, join = StrokeJoin.Round),
                ),
            )
        }
        if (piece.color == Piece.Color.white) {
            BasicText(
                text = solid,
                modifier = Modifier.clearAndSetSemantics {},
                style = style.copy(color = NEWSPRINT_PAPER),
            )
        }
        BasicText(text = piece.graphic, style = style.copy(color = NEWSPRINT_INK))
    }
}

/**
 * LightText's Heading — the size the board's glyphs have always had — for [BasicText], which the
 * piece's layers need so they can carry a stroke and land on exactly the same spot. Scaled for the
 * screen's height as LightText scales it; the SDK keeps its own helper for that internal.
 */
@Composable
private fun pieceGlyphStyle(): TextStyle {
    val heading = LightThemeTokens.typography.heading
    return heading.copy(
        fontSize = heading.fontSize.value.designVerticalPxToSp(),
        lineHeight = heading.lineHeight.value.designVerticalPxToSp(),
        textAlign = TextAlign.Center,
    )
}
