package com.zyn.chat.app.ui.icons

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.graphics.vector.PathBuilder
import androidx.compose.ui.graphics.vector.path
import androidx.compose.ui.unit.dp

/**
 * Outline icon set for Zyn Chat.
 *
 * The icons are defined as [ImageVector]s with a shared 24x24 viewport and a 1.7dp round-capped
 * stroke, so every glyph keeps the same visual weight. They are tinted by `Icon(tint = …)`.
 */
public object ZynIcons {

    /** Search. Used by the drawer header. */
    public val Search: ImageVector = outline("ZynSearch") {
        circle(10.4f, 10.4f, 6.4f)
        segment(15.2f, 15.2f, 19.6f, 19.6f)
    }

    /** Plus. Used by the composer attachment button. */
    public val Plus: ImageVector = outline("ZynPlus") {
        segment(12f, 5.6f, 12f, 18.4f)
        segment(5.6f, 12f, 18.4f, 12f)
    }

    /** Hub with satellites. Used by the "Providers" entry. */
    public val Providers: ImageVector = outline("ZynProviders") {
        circle(12f, 12f, 2.6f)
        circle(12f, 4.7f, 1.9f)
        circle(19.3f, 12f, 1.9f)
        circle(12f, 19.3f, 1.9f)
        circle(4.7f, 12f, 1.9f)
        segment(12f, 9.4f, 12f, 6.6f)
        segment(14.6f, 12f, 17.4f, 12f)
        segment(12f, 14.6f, 12f, 17.4f)
        segment(9.4f, 12f, 6.6f, 12f)
    }

    /** Branching route. Used by the "Routing" entry. */
    public val Routing: ImageVector = outline("ZynRouting") {
        segment(5.4f, 4.6f, 5.4f, 19.4f)
        segment(5.4f, 8.6f, 16.7f, 8.6f)
        segment(5.4f, 15.4f, 16.7f, 15.4f)
        circle(19.1f, 8.6f, 2.4f)
        circle(19.1f, 15.4f, 2.4f)
    }

    /** Document with a folded corner. Used by the "Files" attachment action. */
    public val File: ImageVector = outline("ZynFile") {
        moveTo(13.6f, 3.7f)
        lineTo(8.1f, 3.7f)
        quadTo(5.7f, 3.7f, 5.7f, 6.1f)
        lineTo(5.7f, 17.9f)
        quadTo(5.7f, 20.3f, 8.1f, 20.3f)
        lineTo(15.9f, 20.3f)
        quadTo(18.3f, 20.3f, 18.3f, 17.9f)
        lineTo(18.3f, 8.4f)
        close()
        moveTo(13.6f, 3.7f)
        lineTo(13.6f, 8.4f)
        lineTo(18.3f, 8.4f)
        segment(9.1f, 13.6f, 14.9f, 13.6f)
    }

    /** Picture. Used by the "Photos" attachment action. */
    public val Image: ImageVector = outline("ZynImage") {
        roundRect(3.7f, 4.9f, 16.6f, 14.2f, 3.2f)
        circle(8.7f, 9.9f, 1.6f)
        moveTo(5.2f, 17.1f)
        lineTo(9.7f, 12.6f)
        lineTo(13.1f, 16f)
        lineTo(15.3f, 13.8f)
        lineTo(18.6f, 17.1f)
    }

    /** Chevron pointing down. Used by the model selector. */
    public val ChevronDown: ImageVector = outline("ZynChevronDown") {
        moveTo(7.2f, 10.2f)
        lineTo(12f, 15f)
        lineTo(16.8f, 10.2f)
    }

    /** Arrow pointing up. Used by the send button. */
    public val ArrowUp: ImageVector = outline("ZynArrowUp") {
        segment(12f, 19.2f, 12f, 5.2f)
        moveTo(5.6f, 11.6f)
        lineTo(12f, 5.2f)
        lineTo(18.4f, 11.6f)
    }

    /** Square. Used by the stop button while the assistant is answering. */
    public val Stop: ImageVector = solid("ZynStop") {
        roundRect(7.4f, 7.4f, 9.2f, 9.2f, 2.4f)
    }

    /**
     * Four blocks. Used as the glyph inside the model selector, echoing the compact
     * grid-of-providers metaphor.
     */
    public val ModelBlocks: ImageVector = solid("ZynModelBlocks") {
        roundRect(3.9f, 4.3f, 6.7f, 6.7f, 2f)
        roundRect(13.4f, 4.3f, 6.7f, 6.7f, 2f)
        roundRect(3.9f, 12.9f, 6.7f, 6.7f, 2f)
        roundRect(13.4f, 12.9f, 6.7f, 6.7f, 2f)
    }

    /** Cross. Used to remove a staged attachment. */
    public val Close: ImageVector = outline("ZynClose") {
        segment(6.6f, 6.6f, 17.4f, 17.4f)
        segment(17.4f, 6.6f, 6.6f, 17.4f)
    }
}

private const val VIEWPORT = 24f
private const val STROKE_WIDTH = 1.7f
private const val ICON_SIZE_DP = 24

private fun outline(name: String, block: PathBuilder.() -> Unit): ImageVector =
    icon(name = name, filled = false, block = block)

private fun solid(name: String, block: PathBuilder.() -> Unit): ImageVector =
    icon(name = name, filled = true, block = block)

private fun icon(
    name: String,
    filled: Boolean,
    block: PathBuilder.() -> Unit,
): ImageVector =
    ImageVector.Builder(
        name = name,
        defaultWidth = ICON_SIZE_DP.dp,
        defaultHeight = ICON_SIZE_DP.dp,
        viewportWidth = VIEWPORT,
        viewportHeight = VIEWPORT,
    ).path(
        fill = if (filled) SolidColor(Color.Black) else null,
        stroke = if (filled) null else SolidColor(Color.Black),
        strokeLineWidth = STROKE_WIDTH,
        strokeLineCap = StrokeCap.Round,
        strokeLineJoin = StrokeJoin.Round,
        pathBuilder = block,
    ).build()

/** Draws a straight line from ([x1], [y1]) to ([x2], [y2]). */
private fun PathBuilder.segment(x1: Float, y1: Float, x2: Float, y2: Float) {
    moveTo(x1, y1)
    lineTo(x2, y2)
}

/** Approximates a circle centred on ([cx], [cy]) with four cubic segments. */
private fun PathBuilder.circle(cx: Float, cy: Float, radius: Float) {
    val handle = radius * CIRCLE_HANDLE_RATIO
    moveTo(cx, cy - radius)
    curveTo(cx + handle, cy - radius, cx + radius, cy - handle, cx + radius, cy)
    curveTo(cx + radius, cy + handle, cx + handle, cy + radius, cx, cy + radius)
    curveTo(cx - handle, cy + radius, cx - radius, cy + handle, cx - radius, cy)
    curveTo(cx - radius, cy - handle, cx - handle, cy - radius, cx, cy - radius)
    close()
}

/** Draws a rectangle with rounded corners, from its top-left corner. */
private fun PathBuilder.roundRect(
    x: Float,
    y: Float,
    width: Float,
    height: Float,
    corner: Float,
) {
    moveTo(x + corner, y)
    lineTo(x + width - corner, y)
    quadTo(x + width, y, x + width, y + corner)
    lineTo(x + width, y + height - corner)
    quadTo(x + width, y + height, x + width - corner, y + height)
    lineTo(x + corner, y + height)
    quadTo(x, y + height, x, y + height - corner)
    lineTo(x, y + corner)
    quadTo(x, y, x + corner, y)
    close()
}

/** Offset that turns four cubic segments into a close circle approximation. */
private const val CIRCLE_HANDLE_RATIO = 0.5523f
