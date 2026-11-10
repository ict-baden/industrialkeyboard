package de.ictbaden.industrialkeyboard.scanner

import de.ictbaden.industrialkeyboard.config.AimMode
import kotlin.math.hypot
import kotlin.math.max

/**
 * Which of the codes in a camera frame is under the aiming frame or crosshair.
 *
 * zxing-cpp crops the camera image and turns it upright before decoding, so its corner points are in
 * the upright image. The preview (PreviewView, FILL_CENTER) shows that image scaled by
 * `max(viewW / imgW, viewH / imgH)` and centred; the same mapping puts a code on the screen.
 */
object AimGeometry {

    data class Point(val x: Float, val y: Float)

    /** A code found in the image: its four corners in upright image coordinates. */
    data class Found<T>(val item: T, val corners: List<Point>)

    /** The preview on the screen and the aiming frame inside it (view coordinates). */
    data class Screen(val viewWidth: Float, val viewHeight: Float, val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val centerX get() = (left + right) / 2
        val centerY get() = (top + bottom) / 2
        val width get() = right - left
    }

    /** Maps a point of the upright image of [imageWidth]×[imageHeight] into the view. */
    fun toView(p: Point, imageWidth: Float, imageHeight: Float, screen: Screen): Point {
        val scale = max(screen.viewWidth / imageWidth, screen.viewHeight / imageHeight)
        return Point(
            p.x * scale + (screen.viewWidth - imageWidth * scale) / 2,
            p.y * scale + (screen.viewHeight - imageHeight * scale) / 2,
        )
    }

    /**
     * The code to take, or `null`. [ANYWHERE][AimMode.ANYWHERE]: the one nearest the middle;
     * [FRAME][AimMode.FRAME]: the nearest one whose centre is inside the frame; [CENTER][AimMode.CENTER]:
     * the one whose outline contains the crosshair, else the nearest within 15 % of the frame width.
     */
    fun <T> pick(found: List<Found<T>>, imageWidth: Float, imageHeight: Float, screen: Screen?, mode: AimMode): T? {
        if (found.isEmpty()) return null
        if (screen == null || imageWidth <= 0 || imageHeight <= 0) return if (mode == AimMode.ANYWHERE) found.first().item else null
        val mapped = found.map { f -> f to f.corners.map { toView(it, imageWidth, imageHeight, screen) } }
        fun center(points: List<Point>) = Point(points.map { it.x }.average().toFloat(), points.map { it.y }.average().toFloat())
        fun distance(points: List<Point>) = center(points).let { hypot(it.x - screen.centerX, it.y - screen.centerY) }
        return when (mode) {
            AimMode.ANYWHERE -> mapped.minByOrNull { distance(it.second) }?.first?.item
            AimMode.FRAME -> mapped.filter { (_, points) ->
                val c = center(points)
                c.x in screen.left..screen.right && c.y in screen.top..screen.bottom
            }.minByOrNull { distance(it.second) }?.first?.item
            AimMode.CENTER -> {
                val cross = Point(screen.centerX, screen.centerY)
                mapped.firstOrNull { contains(it.second, cross) }?.first?.item
                    ?: mapped.filter { distance(it.second) <= screen.width * 0.15f }.minByOrNull { distance(it.second) }?.first?.item
            }
        }
    }

    /** Point in a (convex or not) polygon, ray casting. */
    fun contains(polygon: List<Point>, p: Point): Boolean {
        var inside = false
        var j = polygon.size - 1
        for (i in polygon.indices) {
            val a = polygon[i]
            val b = polygon[j]
            if ((a.y > p.y) != (b.y > p.y) && p.x < (b.x - a.x) * (p.y - a.y) / (b.y - a.y) + a.x) inside = !inside
            j = i
        }
        return inside
    }
}
