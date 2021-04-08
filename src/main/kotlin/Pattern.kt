import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.pixels.Pixel
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlin.math.sqrt
import kotlin.time.measureTimedValue

internal sealed class Pattern : OptionGroup() {

    val name: String
        get() = this::class.simpleName!!.toLowerCase()

    val choice: Pair<String, Pattern>
        get() = name to this

    fun Sequence<Pixel>.sortPixels() = fold(
        initial = PixelSequenceSorter(PixelSorter.finalIntervalFunction, PixelSorter.sortingFunction),
        operation = PixelSequenceSorter::insert
    ).colors

    abstract suspend fun sort(input: ImmutableImage): ImmutableImage

    internal object Lines : Pattern() {

        override suspend fun sort(input: ImmutableImage): ImmutableImage {
            val output = input.rotateAntiClockwise(degrees = PixelSorter.angle)

            val colors = output.columns()
                .map { GlobalScope.async { it.sortPixels().map { it.awt() } } }
                .awaitAll()

            output.mapInPlace { colors[it.x][it.y] }

            return output.rotateClockwise(degrees = PixelSorter.angle)
                .resizeTo(input.width - 2, input.height - 2)
        }
    }

    internal object Circles : Pattern() {

        override suspend fun sort(input: ImmutableImage): ImmutableImage {
            val radii = 0..sqrt((input.width / 2).squared() + (input.height / 2).squared()).toInt()
            val circles = radii
                .map {
                    GlobalScope.async {
                        input.sortedCircle(xCenter = input.width / 2, yCenter = input.height / 2, radius = it)
                            .forEach { (point, color) -> input.setColor(point.first, point.second, color) }
                    }
                }.awaitAll()

            return input
        }

        private fun ImmutableImage.sortedCircle(
            xCenter: Int,
            yCenter: Int,
            radius: Int,
        ): List<Pair<Pair<Int, Int>, RGBColor>> {
            val circlePoints = circlePoints2(xCenter, yCenter, radius)
            val colors = circlePoints.asSequence().map { (x, y) -> pixel(x, y) }.sortPixels()
            return circlePoints.zip(colors)
        }
    }
}


internal suspend fun Pattern.sortTimed(input: ImmutableImage): ImmutableImage {
    TermUi.echo(message = "Sorting pixels")
    val timedValue = measureTimedValue { sort(input) }
    TermUi.echo(message = "Sorted pixels in ${timedValue.duration}")
    return timedValue.value
}

private fun ImmutableImage.circlePoints(
    xCenter: Int,
    yCenter: Int,
    radius: Int,
) = sequence {

    val xRange = 0 until width
    val yRange = 0 until height

    val r2: Int = radius * radius
    var y: Int
    var x: Int = -radius

    while (x <= radius) {
        val xPoint = xCenter + x
        if (xPoint in xRange) {
            y = (sqrt((r2 - x * x).toDouble()) + 0.5).toInt()
            val yPoint = yCenter + y
            if (yPoint in yRange) {
                yield(xPoint to yPoint)
            }
        }
        x++
    }
    while (x >= -radius) {
        val xPoint = xCenter + x
        if (xPoint in xRange) {
            y = (sqrt((r2 - x * x).toDouble()) + 0.5).toInt()
            val yPoint = yCenter - y
            if (yPoint in yRange) {
                yield(xPoint to yPoint)
            }
        }
        x--
    }
}.toList()

fun ImmutableImage.circlePoints2(xCenter: Int, yCenter: Int, radius: Int): List<Pair<Int, Int>> {

    val xRange = 0 until width
    val yRange = 0 until height

    val circle = mutableSetOf<Pair<Int, Int>>()

    fun point(x: Int, y: Int) {
        if (x in xRange && y in yRange) {
            circle.add(x to y)
        }
    }

    fun circlePoints(cx: Int, cy: Int, x: Int, y: Int) {
        if (x == 0) {
            point(cx, cy + y)
            point(cx, cy - y)
            point(cx + y, cy)
            point(cx - y, cy)
        } else if (x == y) {
            point(cx + x, cy + y)
            point(cx - x, cy + y)
            point(cx + x, cy - y)
            point(cx - x, cy - y)
        } else if (x < y) {
            point(cx + x, cy + y)
            point(cx - x, cy + y)
            point(cx + x, cy - y)
            point(cx - x, cy - y)
            point(cx + y, cy + x)
            point(cx - y, cy + x)
            point(cx + y, cy - x)
            point(cx - y, cy - x)
        }
    }

    var x = 0
    var y = radius
    var p = (5 - radius * 4) / 4
    circlePoints(xCenter, yCenter, x, y)
    while (x < y) {
        x++
        p += if (p < 0) {
            2 * x + 1
        } else {
            y--
            2 * (x - y) + 1
        }
        circlePoints(xCenter, yCenter, x, y)
    }

    fun Pair<Int, Int>.x(): Int = first - xCenter
    fun Pair<Int, Int>.y(): Int = second - yCenter

    fun Pair<Int, Int>.quadrant() = if (x() >= 0 && y() >= 0) {
        1
    } else if (x() >= 0 && y() < 0) {
        0
    } else if (x() < 0 && second < 0) {
        2
    } else {
        3
    }
    return circle.sortedWith(
        compareBy<Pair<Int, Int>> { it.quadrant() }
            .thenComparingInt { if (it.x() >= 0) it.y() else -it.y() }
            .thenComparingInt { if (it.y() > 0) -it.x() else it.x() }
    )
}

