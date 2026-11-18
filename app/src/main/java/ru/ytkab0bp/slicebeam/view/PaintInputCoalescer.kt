package ru.ytkab0bp.slicebeam.view

/** Keeps GL-thread paint work bounded while preserving the path covered by coalesced input. */
internal class PaintInputCoalescer {
    data class Point(val x: Float, val y: Float)
    data class Segment(val points: List<Point>) {
        val start: Point get() = points.first()
        val end: Point get() = points.last()
    }

    private val pendingPoints = ArrayList<Point>()
    private var drainScheduled = false

    @Synchronized
    fun submit(x: Float, y: Float): Boolean {
        val point = Point(x, y)
        pendingPoints.add(point)
        if (drainScheduled) return false
        drainScheduled = true
        return true
    }

    @Synchronized
    fun takePending(): Segment? {
        if (pendingPoints.isEmpty()) return null
        val points = pendingPoints.toList()
        pendingPoints.clear()
        return Segment(points)
    }

    /** Returns true when another drain must be queued. */
    @Synchronized
    fun finishDrain(): Boolean {
        if (pendingPoints.isNotEmpty()) return true
        drainScheduled = false
        return false
    }

    /** Flushes the complete pending segment before a stroke ends and releases the scheduled flag. */
    @Synchronized
    fun flush(): Segment? {
        val segment = pendingPoints.takeIf { it.isNotEmpty() }?.let { Segment(it.toList()) }
        pendingPoints.clear()
        drainScheduled = false
        return segment
    }
}
