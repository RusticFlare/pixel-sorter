import com.github.ajalt.clikt.output.TermUi
import com.github.ajalt.clikt.parameters.groups.OptionGroup
import com.github.ajalt.clikt.parameters.options.default
import com.github.ajalt.clikt.parameters.options.option
import com.github.ajalt.clikt.parameters.options.pair
import com.github.ajalt.clikt.parameters.types.int
import com.sksamuel.scrimage.ImmutableImage
import com.sksamuel.scrimage.color.RGBColor
import com.sksamuel.scrimage.pixels.Pixel
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch
import java.awt.geom.Point2D.distance
import java.lang.Math.toDegrees
import kotlin.math.atan2
import kotlin.math.ceil
import kotlin.math.roundToInt
import kotlin.random.Random
import kotlin.time.measureTimedValue

internal sealed class Pattern : OptionGroup() {

    val name: String
        get() = this::class.simpleName!!.lowercase()

    val choice: Pair<String, Pattern>
        get() = name to this

    fun Sequence<Pixel>.sortPixels() = fold(
        initial = PixelSequenceSorter(PixelSorter.finalIntervalFunction, PixelSorter.finalSortingFunction),
        operation = PixelSequenceSorter::insert
    ).colors

    abstract suspend fun sort(input: ImmutableImage): ImmutableImage

    internal object Lines : Pattern() {

        override suspend fun sort(input: ImmutableImage): ImmutableImage {
            val output = input.rotateAntiClockwise(degrees = PixelSorter.angle ?: 0.0)

            val rows = 0 until output.height

            coroutineScope {
                (0 until output.width)
                    .forEach { x ->
                        launch {
                            rows.asSequence().map { y -> output.pixel(x, y) }.sortPixels()
                                .forEachIndexed { y, color -> output.setColor(x, y, color) }
                        }
                    }
            }

            return output.rotateClockwise(degrees = PixelSorter.angle ?: 0.0)
                .resizeTo(input.width - 2, input.height - 2)
        }
    }

    internal object Circles : Pattern() {

        private val center by option(
            "-c",
            "--center",
            help = "The center of circle pattern",
        ).int().pair().default(value = 0 to 0)

        private val ImmutableImage.xCenter: Int
            get() = ((width / 2) + center.first)

        private val ImmutableImage.yCenter: Int
            get() = ((height / 2) - center.second)

        private fun ImmutableImage.corners() = sequenceOf(0 to 0, 0 to height, width to 0, width to height)

        private fun ImmutableImage.distanceToCenter(b: Pair<Int, Int>) =
            distance(xCenter.toDouble(), yCenter.toDouble(), b.first.toDouble(), b.second.toDouble())

        override suspend fun sort(input: ImmutableImage): ImmutableImage {
            val written = Array(input.width) { BooleanArray(input.height) }

            coroutineScope {
                (0..ceil(input.corners().maxOf { input.distanceToCenter(it) }).roundToInt()).forEach {
                    launch {
                        input.sortedCircle(radius = it)
                            .onEach { (point, color) -> input.setColor(point.first, point.second, color) }
                            .forEach { (point) -> written[point.first][point.second] = true }
                    }
                }
            }

            val colors = listOf<(RGBColor) -> Int>({ it.red }, { it.green }, { it.blue })

            fun Pair<Int, Int>.colourOfNeighbors(): RGBColor {
                val (x, y) = this
                val neighbors = sequenceOf(
                    x - 1 to y - 1, x to y - 1, x + 1 to y - 1,
                    x - 1 to y, x + 1 to y,
                    x - 1 to y + 1, x to y + 1, x + 1 to y + 1,
                ).filter { it.first in written.indices }
                    .filter { it.second in written[it.first].indices }
                    .filter { written[it.first][it.second] }
                    .map { input.color(it.first, it.second) }

                val (red, green, blue) = colors.map { neighbors.map(it).average().toInt() }

                return RGBColor(red, green, blue)
            }

            coroutineScope {
                written.forEachIndexed { x, booleans ->
                    launch {
                        booleans.withIndex().filterNot { it.value }
                            .forEach { (y) -> input.setColor(x, y, (x to y).colourOfNeighbors()) }
                    }
                }
            }

            return input
        }

        private fun ImmutableImage.sortedCircle(
            radius: Int,
        ): List<Pair<Pair<Int, Int>, RGBColor>> {
            val circlePoints = circlePoints(xCenter, yCenter, radius)
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

internal fun ImmutableImage.circlePoints(xCenter: Int, yCenter: Int, radius: Int): List<Pair<Int, Int>> {

    val xRange = 0 until width
    val yRange = 0 until height

    val circle = mutableSetOf<Pair<Int, Int>>()

    fun point(x: Int, y: Int) {
        if (x in xRange && y in yRange) {
            circle.add(x to y)
        }
    }

    fun circlePoints(cx: Int, cy: Int, x: Int, y: Int) {
        when {
            x == 0 -> {
                point(cx, cy + y)
                point(cx, cy - y)
                point(cx + y, cy)
                point(cx - y, cy)
            }
            x == y -> {
                point(cx + x, cy + y)
                point(cx - x, cy + y)
                point(cx + x, cy - y)
                point(cx - x, cy - y)
            }
            x < y -> {
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

    fun Pair<Int, Int>.x(): Double = first.toDouble() - xCenter
    fun Pair<Int, Int>.y(): Double = second.toDouble() - yCenter

    val angle = PixelSorter.angle ?: Random.nextDouble(until = 360.0)
    return circle.sortedBy { (toDegrees(atan2(it.y(), it.x())) - 270 - angle).rem(360) }
}

